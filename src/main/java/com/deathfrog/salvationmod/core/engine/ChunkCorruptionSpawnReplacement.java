package com.deathfrog.salvationmod.core.engine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModCommands;
import com.mojang.logging.LogUtils;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import com.deathfrog.salvationmod.SalvationMod;

@EventBusSubscriber(modid = SalvationMod.MODID)
public final class ChunkCorruptionSpawnReplacement
{
    public static final Logger LOGGER = LogUtils.getLogger();

    // ---- tuning knobs ----
    // Once per Minecraft day per chunk:
    private static final int COOLDOWN_TICKS_PER_CHUNK = 20 * 60 * 3; // Every 3 minutes

    // How widely to spread scans within time:
    // e.g. 1200 ticks = 60 seconds spread window
    private static final int SPREAD_TICKS = 1200;

    // Work budgets (keep tiny)
    private static final int MAX_CONVERSIONS_PER_CHUNK = 1;
    private static final int MAX_PENDING_CHUNKS_PER_TICK = 2;   // chunk-load queue budget
    private static final int MAX_SWEEP_CHUNKS_PER_TICK = 1;     // always-loaded sweep budget

    // Periodic sweep sampling region
    private static final int PLAYER_RADIUS_CHUNKS = 6;          // 96 blocks
    private static final int SWEEP_SAMPLES_PER_PLAYER = 2;      // candidates created per tick per player (lightweight)

    // chunkKey -> dueGameTime (when this chunk is allowed to be processed)
    private static final WeakHashMap<ServerLevel, Long2LongOpenHashMap> PENDING = new WeakHashMap<>();
    // chunkKey -> lastCheckedGameTime (cooldown)
    private static final WeakHashMap<ServerLevel, Long2LongOpenHashMap> LAST_CHECK = new WeakHashMap<>();

    @SubscribeEvent
    public static void onChunkLoad(final ChunkEvent.Load event)
    {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;

        if (!(event.getChunk() instanceof LevelChunk chunk))
            return;

        final long now = level.getGameTime();
        final long key = chunk.getPos().toLong();

        final Long2LongOpenHashMap last = LAST_CHECK.computeIfAbsent(level, l -> new Long2LongOpenHashMap());
        final long lastTime = last.getOrDefault(key, Long.MIN_VALUE);

        if (lastTime != Long.MIN_VALUE && now - lastTime < COOLDOWN_TICKS_PER_CHUNK)
        {
            return;
        }

        // Schedule this chunk sometime within the next SPREAD_TICKS, based on stable bucket
        final int offset = bucketOffset(key, SPREAD_TICKS);
        final long due = now + offset;

        final Long2LongOpenHashMap pending = PENDING.computeIfAbsent(level, l -> new Long2LongOpenHashMap());

        // Keep earliest due time if it’s already pending
        final long existingDue = pending.getOrDefault(key, Long.MAX_VALUE);

        if (due < existingDue)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_SPAWN,
                () -> LOGGER.info("ChunkCorruptionSpawnReplacement chunk loading hook. now={}, lastTime={}, bucketOffset={}, due={}. Scheduling corruption check for {}", now, lastTime, offset, due, key));
            pending.put(key, due);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event)
    {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;

        final long now = level.getGameTime();

        // ---- 1) Process pending chunk-load checks that are due ----
        final Long2LongOpenHashMap pending = PENDING.get(level);
        
        TraceUtils.dynamicTrace(ModCommands.TRACE_SPAWN,
            () -> LOGGER.info("ChunkCorruptionSpawnReplacement check at game time {}. {} pending", now, pending == null ? "none" : pending.size()));

        if (pending != null && !pending.isEmpty())
        {
            int processed = 0;
            final Iterator<Long2LongMap.Entry> it = pending.long2LongEntrySet().fastIterator();

            while (it.hasNext() && processed < MAX_PENDING_CHUNKS_PER_TICK)
            {
                final Long2LongMap.Entry e = it.next();
                final long chunkKey = e.getLongKey();
                final long due = e.getLongValue();

                if (now < due) continue;

                it.remove();

                // Re-check cooldown right before work (race-proof)
                if (!markCheckedIfAllowed(level, chunkKey, now)) continue;

                attemptConversionsInChunk(level, new ChunkPos(chunkKey));
                processed++;
            }
        }

        // ---- 2) Always-loaded sweep: run every tick, but only process a small, bucketed set ----
        // Bucket gating: only chunks whose bucket matches this tick get processed,
        // which spaces checks evenly across SPREAD_TICKS.
        sweepLoadedChunksNearPlayers(level, now);
    }


    /**
     * Always-loaded sweep: run every tick, but only process a small, bucketed set
     * of chunks near players. This is a performance-critical path, so we try to
     * minimize work here while still being semi-fair.
     *
     * Bucket gating: only chunks whose bucket matches this tick get processed, which
     * spaces checks evenly across SPREAD_TICKS.
     *
     * For each player, we pick a few random chunks near them to check for corruption.
     * If a chunk is eligible for processing this tick (bucket matches), we attempt
     * conversions in that chunk.
     *
     * @param level Server level to process on
     * @param now Current game time (long)
     */
    private static void sweepLoadedChunksNearPlayers(final ServerLevel level, final long now)
    {
        if (level.players().isEmpty())
            return;

        final int currentBucket = (int) (Math.floorMod(now, SPREAD_TICKS));
        final List<ChunkPos> candidates = new ArrayList<>(level.players().size() * SWEEP_SAMPLES_PER_PLAYER);

        for (final ServerPlayer player : level.players())
        {
            BlockPos pos = player.blockPosition();

            if (pos == null) continue;

            final ChunkPos center = new ChunkPos(pos);

            for (int i = 0; i < SWEEP_SAMPLES_PER_PLAYER; i++)
            {
                final int dx = level.random.nextInt(-PLAYER_RADIUS_CHUNKS, PLAYER_RADIUS_CHUNKS + 1);
                final int dz = level.random.nextInt(-PLAYER_RADIUS_CHUNKS, PLAYER_RADIUS_CHUNKS + 1);
                candidates.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }

        int processed = 0;

        while (processed < MAX_SWEEP_CHUNKS_PER_TICK && !candidates.isEmpty())
        {
            final int idx = level.random.nextInt(candidates.size());
            final ChunkPos cp = candidates.remove(idx);

            if (!level.hasChunk(cp.x, cp.z))
                continue;

            final long key = cp.toLong();

            // Bucket gate: this chunk only eligible on its assigned “slot” within SPREAD_TICKS
            final int bucket = bucketOffset(key, SPREAD_TICKS);

            if (bucket != currentBucket) continue;

            if (!markCheckedIfAllowed(level, key, now)) continue;

            attemptConversionsInChunk(level, cp);
            processed++;
        }
    }

    /**
     * Returns true and updates LAST_CHECK if the chunk is past cooldown; otherwise false.
     */
    private static boolean markCheckedIfAllowed(final ServerLevel level, final long chunkKey, final long now)
    {
        final Long2LongOpenHashMap last = LAST_CHECK.computeIfAbsent(level, l -> new Long2LongOpenHashMap());
        final long lastTime = last.getOrDefault(chunkKey, Long.MIN_VALUE);

        if (lastTime != Long.MIN_VALUE && now - lastTime < COOLDOWN_TICKS_PER_CHUNK)
        {
            return false;
        }

        last.put(chunkKey, now);
        return true;
    }

    /**
     * Stable bucket assignment in [0, mod).
     * Uses a cheap 64-bit mix so neighboring chunks don’t cluster.
     */
    private static int bucketOffset(final long key, final int mod)
    {
        long z = key;
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return (int) Math.floorMod(z, mod);
    }

    /**
     * Iterate over all entities in the given chunk and attempt to replace them with corrupted versions if applicable.
     * Stops after MAX_CONVERSIONS_PER_CHUNK successful conversions to prevent performance issues.
     * @param level the level to process
     * @param cp the chunk position to process
     */
    private static void attemptConversionsInChunk(final ServerLevel level, final ChunkPos cp)
    {
        final int minX = cp.getMinBlockX();
        final int minZ = cp.getMinBlockZ();
        final int maxX = minX + 15;
        final int maxZ = minZ + 15;

        final int minY = level.getMinBuildHeight();
        final int maxY = level.getMaxBuildHeight() - 1;

        final AABB box = new AABB(
            minX, minY, minZ,
            maxX + 1, maxY + 1, maxZ + 1
        );

        int conversions = 0;

        TraceUtils.dynamicTrace(ModCommands.TRACE_SPAWN,
                    () -> LOGGER.info("Attempting entity conversions in chunk {}.", cp));

        for (final Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> true))
        {
            if (conversions >= MAX_CONVERSIONS_PER_CHUNK)
                break;

            if (!mob.isAlive())
                continue;

            if (SalvationManager.isCorruptedEntity(mob.getType()))
                continue;

            if (!SalvationManager.isCorruptableEntity(mob.getType()))
                continue;

            // After corruptable/corrupted checks, before startConversion(...)
            final BlockPos pos = mob.blockPosition();
            if (pos == null) continue;

            // Match corruptOnSpawn semantics
            final SalvationManager.CorruptionStage stage = SalvationManager.stageForLevel(level);
            float replacementChance = stage.getEntitySpawnChance();

            replacementChance *= ChunkCorruptionSystem.spawnChanceMultiplier(level, pos);

            if (replacementChance <= 0.0F || Float.isNaN(replacementChance)) continue;

            replacementChance = Math.min(1.0F, Math.max(0.0F, replacementChance));

            if (level.random.nextFloat() > replacementChance) continue;

            final boolean replacementStarted = EntityConversion.startConversion(level, mob, false);

            if (replacementStarted)
            {
                TraceUtils.dynamicTrace(ModCommands.TRACE_SPAWN,
                    () -> LOGGER.info("Corruption initiated replacement of {} during chunk check.", mob));
                conversions++;
            }
        }
    }
}