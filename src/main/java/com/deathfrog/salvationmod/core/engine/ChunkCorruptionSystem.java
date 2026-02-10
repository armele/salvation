package com.deathfrog.salvationmod.core.engine;

import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.deathfrog.salvationmod.core.engine.SalvationManager.CorruptionStage;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.ColonyUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Spatial corruption layer (per-dimension, per-chunk).
 *
 * Responsibilities:
 *  - Seed initial corruption (based on global stage)
 *  - Spread corruption slowly (budgeted per tick, stage-based)
 *  - Provide subtle visibility (particles) to players
 *  - Provide spawn weighting multiplier (optional but recommended)
 *
 * Storage lives in SalvationSavedData (persistent SavedData).
 */
public final class ChunkCorruptionSystem
{
    private ChunkCorruptionSystem() {}

    // --- Tuning constants (start conservative; tweak later) ---
    public static final int CORRUPTION_MAX = 255;

    /** Below this, chunk is considered effectively clean (and will be evicted). */
    public static final int EVICT_AT_OR_BELOW = 0;

    /** Chunks at/above this can be used as spread sources. */
    public static final int ACTIVE_THRESHOLD = 20;

    /** Chunks at/above this can emit subtle particles near players. */
    public static final int VISIBLE_THRESHOLD = 12;

    /** Base decay per tick (tick here = your ~1-second loop). */
    public static final int BASE_DECAY_PER_TICK = 1;

    /** If a chunk hasn't been "touched" in a while, decay faster to keep the map sparse. */
    public static final long STALE_AFTER_TICKS = 20L * 60L * 10L; // 10 minutes

    /** Seed when the world becomes meaningfully corrupted. */
    public static final CorruptionStage SEED_STAGE = CorruptionStage.STAGE_2_AWAKENED;

    /** Initial corruption strength per seed chunk. */
    public static final int SEED_STRENGTH = 28;

    /** How many seed chunks to create when seeding triggers. */
    public static final int MIN_SEEDS = 4;
    public static final int MAX_SEEDS = 10;

    /**
     * Called from SalvationManager.salvationLogicLoop(level) (about once per second).
     */
    public static void tick(final ServerLevel level, final SalvationSavedData data)
    {
        if (level == null || level.isClientSide || data == null) return;

        final long gameTime = level.getGameTime();
        final CorruptionStage stage = SalvationManager.stageForLevel(level);

        // 1) Initial seeding (only once; safe to call every tick)
        seedIfNeeded(level, data, stage, gameTime);

        // 2) Decay / eviction (keep map small)
        decayAndEvict(level, data, stage, gameTime);

        // 3) Spread (budgeted per tick; stage-based)
        spread(level, data, stage, gameTime);

        // 4) Visibility (subtle particles near players)
        emitVisibilityHints(level, data, stage, gameTime);
    }

    // ---------------------------------------------------------------------
    // Event inputs (call from your event listeners)
    // ---------------------------------------------------------------------

    /** Add corruption centered on the chunk containing pos. */
    public static void onCorruptingAction(final ServerLevel level, final BlockPos pos, final int delta)
    {
        if (level == null || level.isClientSide || pos == null || delta <= 0) return;
        final SalvationSavedData data = SalvationSavedData.get(level);
        addChunkCorruption(data, chunkKey(pos), delta, level.getGameTime());
    }

    /** Reduce corruption centered on the chunk containing pos. */
    public static void onPurifyingAction(final ServerLevel level, final BlockPos pos, final int delta)
    {
        if (level == null || level.isClientSide || pos == null || delta <= 0) return;
        final SalvationSavedData data = SalvationSavedData.get(level);
        addChunkCorruption(data, chunkKey(pos), -delta, level.getGameTime());
    }

    // ---------------------------------------------------------------------
    // Query helpers
    // ---------------------------------------------------------------------

    /**
     * Get the corruption level for the chunk containing pos.
     * Returns 0 if level or pos is null.
     * @param level the server level
     * @param pos the position to query
     * @return the corruption level (0 - {@link #CORRUPTION_MAX})
     */
    public static int getChunkCorruption(final ServerLevel level, final BlockPos pos)
    {
        if (level == null || pos == null) return 0;
        final SalvationSavedData data = SalvationSavedData.get(level);
        return data.getChunkCorruption(chunkKey(pos));
    }

    /**
     * Multiplier to apply to your corrupted entity spawn chance.
     * Recommended usage inside SalvationManager.applySpawnOverride():
     *
     *   spawnChance *= ChunkCorruptionSystem.spawnChanceMultiplier(level, pos);
     *
     * Returns ~0.5..2.0 depending on local chunk corruption and global stage.
     */
    public static float spawnChanceMultiplier(final ServerLevel level, final BlockPos pos)
    {
        if (level == null || pos == null) return 1.0f;

        final CorruptionStage stage = SalvationManager.stageForLevel(level);
        if (stage.ordinal() < CorruptionStage.STAGE_2_AWAKENED.ordinal())
        {
            // Before stage 2, keep spatial influence minimal; system is mostly "dormant".
            return 1.0f;
        }

        final int c = getChunkCorruption(level, pos);
        if (c <= 0) return 0.85f; // slightly suppress corrupted spawns outside corrupted areas

        final float norm = Mth.clamp(c / (float)CORRUPTION_MAX, 0.0f, 1.0f);

        // Stage ramps how strongly spatial corruption matters.
        final float stageScalar = switch (stage)
        {
            case STAGE_2_AWAKENED -> 0.6f;
            case STAGE_3_SPREADING -> 0.9f;
            case STAGE_4_DANGEROUS -> 1.2f;
            case STAGE_5_CRITICAL -> 1.5f;
            case STAGE_6_TERMINAL -> 1.8f;
            default -> 0.0f;
        };

        // Baseline 1.0 plus a bump.
        return 1.0f + (norm * stageScalar);
    }

    // ---------------------------------------------------------------------
    // Core implementation
    // ---------------------------------------------------------------------

    /**
     * Called from SalvationManager.salvationLogicLoop(level) (about once per second).
     * If the world stage is >= SEED_STAGE, seeds the world with corruption
     * around player chunks (simple + effective), unless the world already has
     * chunk corruption entries (in which case assume we've seeded).
     *
     * @param level the level to seed
     * @param data the salvation data for the level
     * @param stage the current world stage
     * @param time the current world time in ticks
     */
    private static void seedIfNeeded(final ServerLevel level, final SalvationSavedData data, final CorruptionStage stage, final long gameTime)
    {
        if (stage.ordinal() < SEED_STAGE.ordinal()) return;

        // If we already have chunk corruption entries, assume we've seeded.
        if (data.getCorruptedChunkCount() > 0) return;

        final List<ServerPlayer> players = level.players();
        if (players == null || players.isEmpty()) return;

        final int seeds = Mth.clamp(MIN_SEEDS + level.random.nextInt(MAX_SEEDS - MIN_SEEDS + 1), MIN_SEEDS, MAX_SEEDS);

        // Seed around player chunks (simple + effective).
        for (int i = 0; i < seeds; i++)
        {
            final ServerPlayer p = players.get(level.random.nextInt(players.size()));
            if (p == null) continue;

            BlockPos pos = p.blockPosition();
            
            if (pos == null) continue;

            final ChunkPos base = new ChunkPos(pos);
            final int dx = level.random.nextInt(9) - 4; // -4..+4
            final int dz = level.random.nextInt(9) - 4;

            final ChunkPos target = new ChunkPos(base.x + dx, base.z + dz);
            addChunkCorruption(data, target.toLong(), SEED_STRENGTH, gameTime);
        }
    }

    /**
     * Decay and evict chunk corruption entries.
     * 
     * Stage 0/1: spatial layer should naturally fade away if somehow present.
     * 
     * Decay is stage-based, with a base rate of {@link #BASE_DECAY_PER_TICK}.
     * If the chunk is stale (i.e. untouched for {@link #STALE_AFTER_TICKS} ticks or more),
     * the decay rate is increased by 2.
     * 
     * If the corruption level falls at or below {@link #EVICT_AT_OR_BELOW}, the entry is removed.
     * Otherwise, the decayed value is stored without marking the chunk as "touched".
     * 
     * @param level the level to decay and evict
     * @param data the salvation data for the level
     * @param stage the current world stage
     * @param time the current world time in ticks
     */
    private static void decayAndEvict(final ServerLevel level, final SalvationSavedData data, final CorruptionStage stage, final long gameTime)
    {
        int baseDecay = 0;

        if (stage == CorruptionStage.STAGE_3_SPREADING) 
        {
            // At exactly stage 3, corruption neither decays nor increases.
            baseDecay = 0;
        } 
        else if (stage.ordinal() < CorruptionStage.STAGE_3_SPREADING.ordinal())
        {
            // Below stage 3, corruption slowly decays.
            baseDecay = BASE_DECAY_PER_TICK;
        }
        else if (stage.ordinal() > CorruptionStage.STAGE_3_SPREADING.ordinal())
        {
            // Above stage 3, corruption slowly increases. (Negative decay = spread)
            baseDecay = -BASE_DECAY_PER_TICK;
        }

        // Snapshot keys to avoid concurrent modification.
        final long[] keys = data.copyCorruptedChunkKeys();
        for (long key : keys)
        {
            final int cur = data.getChunkCorruption(key);
            if (cur <= 0)
            {
                data.removeChunkCorruption(key);
                continue;
            }
            
            // Continue to check eviction, but no decay/spread.
            if (baseDecay == 0) continue;

            // When a corruption event occurs, decay of corruption cannot happen until the cooldown for the stage is over.
            if (baseDecay > 0 && data.getLastCorruptionEvent(key) + stage.getDecayCooldown() > gameTime) continue;

            // When a purification event occurs, spread of corruption cannot happen until the cooldown for the stage is over.
            if (baseDecay < 0 && data.getLastPurificationEvent(key) + stage.getDecayCooldown() > gameTime) continue;

            // IDEA: (Phase 3) - Chunk will convert to a corrupted biome if the corruption level exceeds a threshold (and the biome must be purified - it will not "decay" to clean).

            final long lastTouched = data.getChunkLastTouched(key);

            final boolean stale = baseDecay > 0 && lastTouched > 0 && (gameTime - lastTouched) > STALE_AFTER_TICKS;

            int decay = baseDecay;
            if (stale) decay += 2; // faster cleanup of old regions

            final int next = cur - decay;
            if (next <= EVICT_AT_OR_BELOW)
            {
                data.removeChunkCorruption(key);
            }
            else
            {
                // Don't mark "touched" when decaying; we want stale cleanup to work.
                data.setChunkCorruptionRaw(key, next);
            }
        }
    }

    /**
     * Spread corruption from sources to neighbors.
     * Budgeted per tick, based on global stage.
     * For each iteration, picks a random source and a random neighbor direction (cardinal or occasional diagonal).
     * Adds a random amount of corruption to the neighbor, and optionally transfers a tiny amount of pressure from the source.
     * If the source is weak, it's skipped.
     * @param level the server level
     * @param data the salvation saved data
     * @param stage the current corruption stage
     * @param etime the current game time
     */
    private static void spread(final ServerLevel level, final SalvationSavedData data, final CorruptionStage stage, final long gameTime)
    {
        final int budget = spreadBudget(stage);
        if (budget <= 0) return;

        // Gather potential sources (sparse and cheap; we already have a snapshot array).
        final long[] keys = data.copyCorruptedChunkKeys();
        if (keys.length == 0) return;

        for (int i = 0; i < budget; i++)
        {
            // Pick random source; skip weak sources.
            final long sourceKey = keys[level.random.nextInt(keys.length)];
            final int sourceVal = data.getChunkCorruption(sourceKey);
            if (sourceVal < ACTIVE_THRESHOLD) continue;

            final ChunkPos src = new ChunkPos(sourceKey);

            // Pick one neighbor direction (N/E/S/W + occasional diagonal)
            final int dir = level.random.nextInt(10);
            final int ox;
            final int oz;

            if (dir < 4) // cardinal
            {
                ox = (dir == 1) ? 1 : (dir == 3) ? -1 : 0;
                oz = (dir == 0) ? 1 : (dir == 2) ? -1 : 0;
            }
            else // diagonal-ish, rarer
            {
                ox = level.random.nextBoolean() ? 1 : -1;
                oz = level.random.nextBoolean() ? 1 : -1;
            }

            final ChunkPos dst = new ChunkPos(src.x + ox, src.z + oz);
            final long dstKey = dst.toLong();

            final int add = spreadAmount(stage, level.random);
            addChunkCorruption(data, dstKey, add, gameTime);

            // Optional: tiny "pressure transfer" so sources don’t grow without bound
            if (level.random.nextFloat() < 0.25f)
            {
                addChunkCorruption(data, sourceKey, -1, gameTime);
            }
        }
    }

    /**
     * Returns the number of chunks to spread corruption to per tick
     * given the current world stage.
     * 
     * The number of chunks to spread corruption to increases as the
     * world stage progresses, with early stages having little to no
     * spreading and late stages having a high amount of spreading.
     * 
     * @param stage the current world stage
     * @return the number of chunks to spread corruption to per tick
     */
    private static int spreadBudget(final CorruptionStage stage)
    {
        return switch (stage)
        {
            case STAGE_0_UNTRIGGERED, STAGE_1_NORMAL -> 0;
            case STAGE_2_AWAKENED -> 1;
            case STAGE_3_SPREADING -> 3;
            case STAGE_4_DANGEROUS -> 6;
            case STAGE_5_CRITICAL -> 10;
            case STAGE_6_TERMINAL -> 14;
        };
    }

    /**
     * Returns a random spread amount given the current world stage.
     * The spread amount will be between the base amount for the given world stage and the base amount plus 2.
     * The base amounts are as follows: Stage 2: 3, Stage 3: 4, Stage 4: 5, Stage 5: 6, Stage 6: 7.
     * 
     * @param stage the current world stage
     * @param rnd a random source
     * @return a random spread amount between the base amount and the base amount plus 2
     */
    private static int spreadAmount(final CorruptionStage stage, final net.minecraft.util.RandomSource rnd)
    {
        final int base = switch (stage)
        {
            case STAGE_2_AWAKENED -> 3;
            case STAGE_3_SPREADING -> 4;
            case STAGE_4_DANGEROUS -> 5;
            case STAGE_5_CRITICAL -> 6;
            case STAGE_6_TERMINAL -> 7;
            default -> 0;
        };
        return base + rnd.nextInt(3); // +0..2
    }

    // IDEA: (Phase 3) Biome mutation at higher stages

    /**
     * Emit subtle visibility hints (particles + occasional sounds) to players.
     * Called from SalvationManager.salvationLogicLoop(level) (about once per second-ish),
     * but in practice may be called every ~18 ticks depending on your loop gating.
     *
     * This implementation does NOT rely on gameTime modulo. Instead it uses a probabilistic gate
     * that approximates "once every interval" given the current call cadence.
     *
     * @param level the level to emit visibility hints for
     * @param data the salvation saved data for the level
     * @param stage the current stage of the salvation logic for the level
     * @param gameTime the current game time (in ticks)
     */
    private static void emitVisibilityHints(final ServerLevel level, final SalvationSavedData data, final CorruptionStage stage, final long gameTime)
    {
        if (level == null || data == null || level.isClientSide) return;
        if (stage == null || stage == CorruptionStage.STAGE_0_UNTRIGGERED) return;

        // System is not truly "every tick". This is the approximate cadence of this method being called.
        // If we later change the cadence, update this constant (or compute it from your loop).
        final int CALL_PERIOD_TICKS = 18;

        // Baseline interval target per stage (in ticks). We approximate these with a random gate.
        final int intervalTicks = switch (stage)
        {
            case STAGE_1_NORMAL   -> 200; // ~10s
            case STAGE_2_AWAKENED -> 120; // ~6s
            case STAGE_3_SPREADING-> 60;  // ~3s
            case STAGE_4_DANGEROUS-> 40;  // ~2s
            case STAGE_5_CRITICAL -> 30;  // ~1.5s
            case STAGE_6_TERMINAL -> 18;  // ~1s
            default              -> 200;
        };

        // Convert desired interval into probability per call:
        // approx once per intervalTicks => p ~= CALL_PERIOD_TICKS / intervalTicks
        // clamp to [0, 1]
        final float particleChancePerCall = Mth.clamp(CALL_PERIOD_TICKS / (float) intervalTicks, 0.0F, 1.0F);

        // Sounds should be rarer than particles. Stage increases chance a bit.
        final float soundChanceScalar = switch (stage)
        {
            case STAGE_1_NORMAL    -> 0.08F;
            case STAGE_2_AWAKENED  -> 0.10F;
            case STAGE_3_SPREADING -> 0.12F;
            case STAGE_4_DANGEROUS -> 0.14F;
            case STAGE_5_CRITICAL  -> 0.16F;
            case STAGE_6_TERMINAL  -> 0.18F;
            default                -> 0.10F;
        };

        for (ServerPlayer player : level.players())
        {
            if (player == null) continue;

            final BlockPos ppos = player.blockPosition();
            if (ppos == null) continue;

            final long ck = chunkKey(ppos);
            final int here = data.getChunkCorruption(ck);
            if (here < VISIBLE_THRESHOLD) continue;

            // Gate particles per-player per-call.
            // Also scale up slightly with local corruption (so "hot chunks" feel hotter).
            final float localScalar = Mth.clamp(here / (float) CORRUPTION_MAX, 0.0F, 1.0F);
            final float pParticles = Mth.clamp(particleChancePerCall * (0.65F + 0.85F * localScalar), 0.0F, 0.95F);

            if (level.random.nextFloat() < pParticles)
            {
                emitParticlesNearPlayer(level, player, here);
            }

            // Gate sound even more aggressively. Also avoid spamming if multiple players are nearby:
            // tie to localScalar, but keep very low.
            final float pSound = Mth.clamp((particleChancePerCall * 0.15F + 0.02F) * soundChanceScalar * (0.35F + 0.75F * localScalar), 0.0F, 0.08F);

            if (level.random.nextFloat() < pSound)
            {
                playOminousSound(level, player, here);
            }
        }
    }

    /**
     * Particle progression based on local corruption value.
     */
    private static void emitParticlesNearPlayer(final ServerLevel level, final ServerPlayer player, final int localCorruption)
    {
        // Pick particle type based on corruption intensity
        // (uses your earlier progression: gentle -> weird -> ominous)
        final float norm = Mth.clamp(localCorruption / (float) CORRUPTION_MAX, 0.0F, 1.0F);

        // Thresholds: adjust freely. These are intentionally conservative.
        final var particle = (norm < 0.25F)
            ? ParticleTypes.SPORE_BLOSSOM_AIR
            : (norm < 0.55F)
                ? ParticleTypes.REVERSE_PORTAL
                : (norm < 0.85F)
                    ? ParticleTypes.WARPED_SPORE
                    : ParticleTypes.OMINOUS_SPAWNING;

        // Particle "strength" ramps
        final int count = (norm < 0.25F) ? 2 : (norm < 0.55F) ? 3 : (norm < 0.85F) ? 4 : 5;

        final double radius = 6.0;
        final double x = player.getX() + (level.random.nextDouble() - 0.5) * radius;
        final double y = player.getY() + 0.5 + level.random.nextDouble() * 1.5;
        final double z = player.getZ() + (level.random.nextDouble() - 0.5) * radius;

        level.sendParticles(
            NullnessBridge.assumeNonnull(particle),
            x, y, z,
            count,
            0.20, 0.25, 0.20,
            0.01
        );
    }

    /**
     * Occasional ominous sound, chosen to be "off" rather than loud/hostile.
     * Uses player-relative sound so it feels local.
     */
    private static void playOminousSound(final ServerLevel level, final ServerPlayer player, final int localCorruption)
    {
        final float norm = Mth.clamp(localCorruption / (float) CORRUPTION_MAX, 0.0F, 1.0F);

        // Pick a subtle sound palette. These are intentionally not "raid horns".
        // - AMBIENT_CAVE: classic unease
        // - ENDerman stare: faint "something noticed you" vibe
        // - Warden heartbeat: extremely rare, very high corruption only
        final SoundEvent sound = (norm < 0.55F)
            ? net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value()
            : (norm < 0.90F)
                ? net.minecraft.sounds.SoundEvents.ENDERMAN_STARE
                : net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT;

        // Volume/pitch keep it subtle
        final float volume = 0.15F + (0.25F * norm);          // 0.15 .. 0.40
        final float pitch  = 0.90F + (level.random.nextFloat() * 0.25F); // 0.90 .. 1.15

        // If you prefer it to be "in world" rather than centered on player,
        // offset the sound position a little bit.
        final double radius = 8.0;
        final double sx = player.getX() + (level.random.nextDouble() - 0.5) * radius;
        final double sy = player.getY() + 0.5;
        final double sz = player.getZ() + (level.random.nextDouble() - 0.5) * radius;

        level.playSound(
            null, // null = broadcast to nearby players, not just the target player
            sx, sy, sz,
            NullnessBridge.assumeNonnull(sound),
            net.minecraft.sounds.SoundSource.AMBIENT,
            volume,
            pitch
        );
}

    // ---------------------------------------------------------------------
    // Utilities / SavedData bridge
    // ---------------------------------------------------------------------

    private static long chunkKey(final @Nonnull BlockPos pos)
    {
        return new ChunkPos(pos).toLong();
    }

    /**
     * Modify the corruption level of a chunk by the given delta.
     * If the resulting corruption level is at or below {@link #EVICT_AT_OR_BELOW}, remove the entry.
     * Otherwise, update the corruption level and mark the chunk as "touched" at the given time.
     * @param data the salvation data to modify
     * @param chunkKey the key of the chunk to modify
     * @param delta the amount to add to the current corruption level
     * @param time the time to mark as "touched" if the entry is updated
     */
    private static void addChunkCorruption(final SalvationSavedData data, final long chunkKey, final int delta, final long gameTime)
    {
        if (data == null) return;

        ServerLevel level = data.getLevelForSave();

        if (level == null) return;

        ChunkPos chunkPos = new ChunkPos(chunkKey);
        final LevelChunk thisChunk = new LevelChunk(level, chunkPos);
        final IColony owningColony = IColonyManager.getInstance().getColonyByWorld(ColonyUtils.getOwningColony(thisChunk), data.getLevelForSave());

        int impact = delta;

        if (owningColony != null)
        {
            double corruptionProtection = 1 + owningColony.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_IMMUNITY);
            impact = (int) (delta * (1 - corruptionProtection));  
        }

        final int cur = data.getChunkCorruption(chunkKey);
        int next = cur + impact;
        next = Mth.clamp(next, 0, CORRUPTION_MAX);

        if (next <= EVICT_AT_OR_BELOW)
        {
            data.removeChunkCorruption(chunkKey);
            return;
        }

        data.setChunkCorruption(chunkKey, next, gameTime);
    }
}