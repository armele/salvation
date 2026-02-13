package com.deathfrog.salvationmod.core.engine;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.engine.SalvationManager.CorruptionStage;
import com.mojang.logging.LogUtils;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Budgeted, surface-sampling corruption visuals:
 * - Occasionally replaces grass_block with a custom "blighted" block, based on chunk corruption.
 * - Tracks replaced positions for clean, budgeted reversion without scanning.
 *
 * Call from SalvationManager.salvationLogicLoop(level) (your ~1/sec loop):
 *   BlightSurfaceSystem.tick(level);
 */
public final class BlightSurfaceSystem
{
    private BlightSurfaceSystem() {}

    public static final Logger LOGGER = LogUtils.getLogger();
        
    // -------------------------
    // Tuning knobs
    // -------------------------

    /**
     * The custom block to place. Must exist in the registry.
     * Change this ID if your block name differs.
     */
    private static final ResourceLocation BLIGHT_BLOCK_ID = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "blighted_grass");

    /** Only start doing spatial blight once Salvation has "woken up". */
    private static final CorruptionStage MIN_STAGE_TO_APPLY = CorruptionStage.STAGE_2_AWAKENED;

    /** Player-proximity sampling radius, in chunks. (7 => ~112 blocks) */
    private static final int CHUNK_RADIUS = 7;

    /** Hard cap: maximum blighted blocks remembered per chunk (prevents runaway). */
    private static final int MAX_BLIGHTED_PER_CHUNK = 64;

    /** Hard cap: total apply attempts per tick (per level). */
    private static final int MAX_APPLY_ATTEMPTS_PER_TICK = 24;

    /** Hard cap: total revert attempts per tick (per level). */
    private static final int MAX_REVERT_ATTEMPTS_PER_TICK = 24;

    /**
     * Hysteresis thresholds to avoid flicker:
     * - Apply when normalized chunk corruption >= APPLY_THRESHOLD
     * - Revert when normalized chunk corruption <= REVERT_THRESHOLD
     */
    private static final float APPLY_THRESHOLD  = 0.22f;
    private static final float REVERT_THRESHOLD = 0.16f;

    /**
     * Base chance per surface probe, before scaling by corruption + stage.
     * Final chance is: BASE * stageScalar * f(norm)
     */
    private static final float BASE_PROBE_CHANCE = 0.22f;

    /** A tiny minimum chance once above APPLY_THRESHOLD (keeps it from feeling "stuck"). */
    private static final float MIN_CHANCE_ABOVE_THRESHOLD = 0.02f;

    // -------------------------
    // Public entry point
    // -------------------------

    /**
     * Called from SalvationManager.salvationLogicLoop(level) (about once per second).
     * - Applies blight to the surface of the world, if the global corruption stage is high enough.
     * - Reverts blight from the surface of the world, if the global corruption stage is low enough.
     * - Respects chunk cooldowns based on last events.
     * - Saves dirty state if any changes were made.
     * @param level the server level
     */
    public static void tick(@Nonnull final ServerLevel level)
    {
        if (level == null || level.isClientSide()) return;

        final CorruptionStage stage = SalvationManager.stageForLevel(level);
        if (stage.ordinal() < MIN_STAGE_TO_APPLY.ordinal()) return;

        final Block blightBlock = resolveBlightBlock();
        if (blightBlock == null || blightBlock == Blocks.AIR)
        {
            LOGGER.warn("Blight block not found: {}", BLIGHT_BLOCK_ID);
            return;
        }

        final BlightSavedData data = BlightSavedData.get(level);
        final RandomSource rand = level.random;

        if (rand == null || data == null) return;

        final long gameTime = level.getGameTime();
        final SalvationSavedData salvationData = SalvationSavedData.get(level);

        if (salvationData == null) return;

        // Apply first, then revert (but respect chunk cooldowns based on last events).
        final int applied = applyStep(level, data, salvationData, stage, blightBlock, rand, gameTime, MAX_APPLY_ATTEMPTS_PER_TICK);
        final int reverted = revertStep(level, data, salvationData, stage, rand, gameTime, MAX_REVERT_ATTEMPTS_PER_TICK);

        if ((applied > 0) || (reverted > 0))
        {
            data.setDirty();
        }
    }

    // -------------------------
    // Apply logic
    // -------------------------

    private static int applyStep(
        @Nonnull final ServerLevel level,
        @Nonnull final BlightSavedData data,
        @Nonnull final SalvationSavedData salvationData,
        @Nonnull final CorruptionStage stage,
        @Nonnull final Block blightBlock,
        @Nonnull final RandomSource rand,
        final long gameTime,
        final int budget)
    {
        int attemptsRemaining = budget;
        int applied = 0;

        final List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return 0;

        // Stage scalar: later stages = stronger + more frequent.
        final float stageScalar = stageApplyScalar(stage);

        TraceUtils.dynamicTrace(ModCommands.TRACE_BLIGHT, () -> LOGGER.info("Processing BlightSurfaceSystem.applyStep tick with stageScalar {} and attempt budget {}.", stageScalar, budget));

        if (stageScalar <= 0.0f) return 0;

        // How many chunk picks per player per tick (still bounded by global budget).
        final int chunkPicksPerPlayer = Mth.clamp(1 + (int)Math.floor(stageScalar * 2.0f), 1, 4);

        for (final ServerPlayer player : players)
        {
            if (attemptsRemaining <= 0) break;
            if (player == null) continue;

            TraceUtils.dynamicTrace(ModCommands.TRACE_BLIGHT, () -> LOGGER.info("BlightSurfaceSystem inner loop for player {}.", player.getScoreboardName()));

            final ChunkPos pc = player.chunkPosition();
            boolean firstpick = true;

            for (int pick = 0; pick < chunkPicksPerPlayer && attemptsRemaining > 0; pick++)
            {
                final int cx = firstpick ? player.chunkPosition().x : pc.x + rand.nextInt(-CHUNK_RADIUS, CHUNK_RADIUS + 1);
                final int cz = firstpick ? player.chunkPosition().z : pc.z + rand.nextInt(-CHUNK_RADIUS, CHUNK_RADIUS + 1);
                firstpick = false;

                if (!level.hasChunk(cx, cz)) 
                {
                    TraceUtils.dynamicTrace(ModCommands.TRACE_BLIGHT, () -> LOGGER.info("BlightSurfaceSystem skipping chunk {} {} for player {}.", cx, cz, player.getScoreboardName()));
                    continue;
                }

                // Cap density in this chunk.
                final long ckey = ChunkPos.asLong(cx, cz);

                final long cooldown = stage.getBlightCooldown();

                long lastPurificationEvent = salvationData.getLastPurificationEvent(ckey);
                long lastCorruptionEvent = salvationData.getLastCorruptionEvent(ckey);

                // When a purification event occurs, new blight cannot be applied until cooldown expires or another corruption event occurs.
                if (lastPurificationEvent + cooldown > gameTime && lastPurificationEvent > lastCorruptionEvent)
                {
                    TraceUtils.dynamicTrace(ModCommands.TRACE_BLIGHT, () -> LOGGER.info("Last purification event for chunk {} is too recent.  Next opportunity is in {}", lastPurificationEvent, (lastPurificationEvent + cooldown - gameTime)));
                    continue;
                }

                final int currentBlightedCount = data.countInChunk(ckey);

                if (currentBlightedCount >= MAX_BLIGHTED_PER_CHUNK) 
                {
                    TraceUtils.dynamicTrace(ModCommands.TRACE_BLIGHT, () -> LOGGER.info("BlightSurfaceSystem skipping chunk {} {} for player {} - already {} blighted.", cx, cz, player.getScoreboardName(), currentBlightedCount));
                    continue;
                }

                // Convert corruption (0..255) => 0..1
                final float norm = getChunkCorruptionNorm(level, cx, cz);
                if (norm < APPLY_THRESHOLD) 
                {
                    TraceUtils.dynamicTrace(ModCommands.TRACE_BLIGHT, () -> LOGGER.info("BlightSurfaceSystem skipping chunk {} {} for player {} - norm of {} below threshold of {}.", 
                        cx, cz, player.getScoreboardName(), norm, APPLY_THRESHOLD));
                    continue;
                }

                // Probability curve: make high corruption much more likely.
                // - Normalize within [APPLY_THRESHOLD..1]
                // - Square it for more “patchy early, aggressive late”
                final float t = Mth.clamp((norm - APPLY_THRESHOLD) / (1.0f - APPLY_THRESHOLD), 0.0f, 1.0f);
                final float curve = t * t;

                final float baseChance = BASE_PROBE_CHANCE * stageScalar * curve;
                final float finalChance = Math.max(baseChance, MIN_CHANCE_ABOVE_THRESHOLD);

                // One surface probe per picked chunk (cheap). You can bump this to 2 later if desired.
                attemptsRemaining--;

                final float roll = rand.nextFloat();

                TraceUtils.dynamicTrace(ModCommands.TRACE_BLIGHT, () -> LOGGER.info("Blight chance for chunk is {} - with a roll of {}.", finalChance, roll));

                if (roll >= finalChance) continue;

                BlockPos blightSpot = tryBlightOneSurfaceSpot(level, data, cx, cz, blightBlock, rand);

                if (blightSpot != null)
                {
                    TraceUtils.dynamicTrace(ModCommands.TRACE_BLIGHT, () -> LOGGER.info("Blighting {}", blightSpot));

                    applied++;
                    if (attemptsRemaining <= 0) break;
                }
                else
                {
                    TraceUtils.dynamicTrace(ModCommands.TRACE_BLIGHT, () -> LOGGER.info("Failed to blight a surface spot."));
                }
            }
        }

        return applied;
    }

    /**
     * Try to blight a single surface spot in the given chunk.
     *
     * @param level the level to blight in
     * @param data the blight saved data for the level
     * @param cx the x-coordinate of the chunk
     * @param cz the z-coordinate of the chunk
     * @param blightBlock the block to blight with
     * @param rand a random source
     *
     * @return true if a blight was successfully applied, false otherwise
     */
    private static BlockPos tryBlightOneSurfaceSpot(
        @Nonnull final ServerLevel level,
        @Nonnull final BlightSavedData data,
        final int cx,
        final int cz,
        @Nonnull final Block blightBlock,
        @Nonnull final RandomSource rand)
    {
        final int x = (cx << 4) + rand.nextInt(16);
        final int z = (cz << 4) + rand.nextInt(16);

        // Heightmap returns the first air block above the surface; surface block is y-1.
        final int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (y <= level.getMinBuildHeight()) return null;

        final BlockPos pos = new BlockPos(x, y, z);

        // Only replace vanilla grass_block (keep it conservative and reversible).
        if (!level.getBlockState(pos).is(NullnessBridge.assumeNonnull(Blocks.GRASS_BLOCK))) return null;

        // Prefer "exposed" grass (looks better; avoids converting under foliage).
        if (!level.getBlockState(NullnessBridge.assumeNonnull(pos.above())).isAir()) return null;

        // Record + replace.
        final long ckey = ChunkPos.asLong(cx, cz);
        if (data.countInChunk(ckey) >= MAX_BLIGHTED_PER_CHUNK) return null;

        BlockState defaultBlockState = blightBlock.defaultBlockState();

        if (defaultBlockState == null) return null;

        level.setBlock(pos, defaultBlockState, Block.UPDATE_CLIENTS);
        data.add(ckey, pos.asLong());
        return pos;
    }

    // -------------------------
    // Revert logic
    // -------------------------

    private static int revertStep(
        @Nonnull final ServerLevel level,
        @Nonnull final BlightSavedData data,
        @Nonnull final SalvationSavedData salvationData,
        @Nonnull final CorruptionStage stage,
        @Nonnull final RandomSource rand,
        final long gameTime,
        final int budget)
    {
        int remaining = budget;
        int reverted = 0;

        if (data.isEmpty()) return 0;

        // Reversion gets more aggressive if stage is lower (or purification is happening).
        // But we still keep it budgeted.
        final float stageScalar = stageRevertScalar(stage);

        // Iterate chunks we *know* have blight.
        // We do not scan world/chunks; we only touch our recorded set.
        final Iterator<Long2ObjectOpenHashMap.Entry<LongArrayList>> it = data.chunkEntriesIterator();
        while (it.hasNext() && remaining > 0)
        {
            final Long2ObjectOpenHashMap.Entry<LongArrayList> e = it.next();
            final long ckey = e.getLongKey();
            final LongArrayList positions = e.getValue();
            if (positions == null || positions.isEmpty())
            {
                it.remove();
                continue;
            }

            final int cx = ChunkPos.getX(ckey);
            final int cz = ChunkPos.getZ(ckey);

            final long cooldown = stage.getBlightCooldown();

            final long lastC = salvationData.getLastCorruptionEvent(ckey);
            final long lastP = salvationData.getLastPurificationEvent(ckey);

            // Must have a purification event that is the most recent event.
            if (lastP <= 0L || lastP <= lastC) continue;

            // Only allow clearing during [lastP, lastP + cooldown].
            if (gameTime > lastP + cooldown) continue;

            // Blight cannot clear if corruption was recent
            if (gameTime < lastC + cooldown) continue;

            // Only actively revert when corruption is sufficiently low OR stage scalar is pushing reversion.
            final float norm = getChunkCorruptionNorm(level, cx, cz);
            final boolean shouldRevert = (norm <= REVERT_THRESHOLD);

            // If not below revert threshold, we might still “trim” very slowly in earlier stages (optional).
            // Keep it conservative: only do it if stageScalar suggests it.
            if (!shouldRevert && stageScalar <= 0.0f) continue;

            // How many revert probes for this chunk this tick:
            // - If fully eligible (below threshold), do more.
            // - Otherwise, do at most 1 “maintenance” revert very rarely.
            final int maxChunkReverts = shouldRevert ? Mth.clamp((int)Math.ceil(2.0f * stageScalar), 1, 4) : 1;

            for (int k = 0; k < maxChunkReverts && remaining > 0; k++)
            {
                if (positions.isEmpty()) break;

                // Pick a random recorded position in this chunk.
                final int idx = rand.nextInt(positions.size());
                final long posLong = positions.getLong(idx);
                final BlockPos pos = BlockPos.of(posLong);

                // If chunk not loaded, skip without removing (we’ll try later).
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) break;

                // Revert only if the block is still our blight block; otherwise forget it.
                final Block current = level.getBlockState(pos).getBlock();

                if (current == resolveBlightBlock())
                {
                    TraceUtils.dynamicTrace(ModCommands.TRACE_BLIGHT, () -> LOGGER.info("Reverting blight at {}", pos));

                    level.setBlock(pos, NullnessBridge.assumeNonnull(Blocks.GRASS_BLOCK.defaultBlockState()), Block.UPDATE_CLIENTS);
                    reverted++;
                }

                // Remove from list either way to prevent “stale memory”.
                positions.removeLong(idx);
                remaining--;
            }

            // Clean up emptied chunk list.
            if (positions.isEmpty())
            {
                it.remove();
            }
        }

        return reverted;
    }

    // -------------------------
    // Helpers
    // -------------------------

    /**
     * Normalized chunk corruption value (0..1) for a given chunk in a level.
     * 
     * @param level the level to query
     * @param cx the x-coordinate of the chunk
     * @param cz the z-coordinate of the chunk
     * @return a value between 0 and 1, where 0 means no corruption and 1 means maximum corruption
     */
    private static float getChunkCorruptionNorm(@Nonnull final ServerLevel level, final int cx, final int cz)
    {
        // Query using an arbitrary position within the chunk; corruption is chunk-based anyway.
        final int x = (cx << 4) + 8;
        final int z = (cz << 4) + 8;
        final BlockPos pos = new BlockPos(x, 0, z);
        final int c = ChunkCorruptionSystem.getChunkCorruption(level, pos);

        // ChunkCorruptionSystem says 0..CORRUPTION_MAX (currently 255).
        final float norm = Mth.clamp(c / (float)ChunkCorruptionSystem.CORRUPTION_MAX, 0.0f, 1.0f);

        TraceUtils.dynamicTrace(ModCommands.TRACE_BLIGHT, () -> LOGGER.info("Chunk corruption {} at {} - normalized to {}.", c, pos, norm));

        return norm;
    }

    private static float stageApplyScalar(@Nonnull final CorruptionStage stage)
    {
        // Tuned similarly to ChunkCorruptionSystem.spawnChanceMultiplier,
        // but biased to keep visuals modest until later stages.
        return switch (stage)
        {
            case STAGE_2_AWAKENED -> 0.55f;
            case STAGE_3_SPREADING -> 0.85f;
            case STAGE_4_DANGEROUS -> 1.15f;
            case STAGE_5_CRITICAL -> 1.45f;
            case STAGE_6_TERMINAL -> 1.80f;
            default -> 0.0f;
        };
    }

    /**
     * Returns a scalar to apply to reversion progress based on the current world stage.
     * Reversion is “easier” in earlier stages and “harder” in later ones.
     * 
     * @param stage the current world stage
     * @return a scalar to apply to reversion progress (0..1.0)
     */
    private static float stageRevertScalar(@Nonnull final CorruptionStage stage)
    {
        // Reversion is “easier” in earlier stages and “harder” in later ones.
        return switch (stage)
        {
            case STAGE_2_AWAKENED -> 1.30f;
            case STAGE_3_SPREADING -> 1.05f;
            case STAGE_4_DANGEROUS -> 0.85f;
            case STAGE_5_CRITICAL -> 0.65f;
            case STAGE_6_TERMINAL -> 0.50f;
            default -> 1.0f;
        };
    }

    @Nullable
    private static Block resolveBlightBlock()
    {
        return BuiltInRegistries.BLOCK.getOptional(BLIGHT_BLOCK_ID).orElse(null);
    }

    // -------------------------
    // SavedData (self-contained)
    // -------------------------

    /**
     * Stores only what we changed, keyed by chunk:
     * chunkKey -> list(posLong)
     *
     * This makes reversion cheap and avoids scanning chunks.
     */
    public static final class BlightSavedData extends net.minecraft.world.level.saveddata.SavedData
    {
        private static final String DATA_NAME = "salvation_blight_surface";

        // chunkKey -> positions (as longs)
        private final Long2ObjectOpenHashMap<LongArrayList> blightedByChunk = new Long2ObjectOpenHashMap<>();

        public static BlightSavedData get(@Nonnull final ServerLevel level)
        {
            return level.getDataStorage().computeIfAbsent(
                new Factory<>(BlightSavedData::new, BlightSavedData::load),
                DATA_NAME
            );
        }

        public boolean isEmpty()
        {
            return blightedByChunk.isEmpty();
        }

        /**
         * Returns the number of positions in the given chunk that have been blighted.
         * 
         * @param chunkKey the key of the chunk to query
         * @return the number of blighted positions in the chunk
         */
        public int countInChunk(final long chunkKey)
        {
            final LongArrayList list = blightedByChunk.get(chunkKey);
            return (list == null) ? 0 : list.size();
        }

        public void add(final long chunkKey, final long posLong)
        {
            LongArrayList list = blightedByChunk.get(chunkKey);
            if (list == null)
            {
                list = new LongArrayList(8);
                blightedByChunk.put(chunkKey, list);
            }
            list.add(posLong);
        }

        public Iterator<Long2ObjectOpenHashMap.Entry<LongArrayList>> chunkEntriesIterator()
        {
            return blightedByChunk.long2ObjectEntrySet().fastIterator();
        }

        /**
         * Saves the blight data to the given compound tag.
         *
         * @param tag The compound tag to save to
         * @param registries The registries provider
         * @return The saved compound tag
         */
        @Override
        public CompoundTag save(@Nonnull final CompoundTag tag, @Nonnull Provider registries)
        {
            final ListTag chunks = new ListTag();

            for (final Long2ObjectOpenHashMap.Entry<LongArrayList> e : blightedByChunk.long2ObjectEntrySet())
            {
                final long ckey = e.getLongKey();
                final LongArrayList list = e.getValue();
                if (list == null || list.isEmpty()) continue;

                final CompoundTag c = new CompoundTag();
                c.putLong("k", ckey);

                long[] arr = list.toLongArray();

                if (arr == null || arr.length == 0) continue;

                c.put("p", new LongArrayTag(arr));
                chunks.add(c);
            }

            tag.put("chunks", chunks);
            return tag;
        }

        /**
         * Loads the blight data from the given compound tag.
         *
         * @param tag The compound tag to load from
         * @param registries The registries provider
         * @return The loaded blight data
         */
        public static BlightSavedData load(@Nonnull final CompoundTag tag, Provider registries)
        {
            final BlightSavedData data = new BlightSavedData();

            final ListTag chunks = tag.getList("chunks", Tag.TAG_COMPOUND);
            for (int i = 0; i < chunks.size(); i++)
            {
                final CompoundTag c = chunks.getCompound(i);
                final long ckey = c.getLong("k");

                final long[] arr = c.getLongArray("p");
                if (arr == null || arr.length == 0) continue;

                final LongArrayList list = new LongArrayList(arr.length);
                for (final long p : arr) list.add(p);

                data.blightedByChunk.put(ckey, list);
            }

            return data;
        }
    }
}