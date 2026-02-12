package com.deathfrog.salvationmod.core.engine;

import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.core.colony.ColonyHandlerState;
import com.deathfrog.salvationmod.core.engine.SalvationManager.CorruptionStage;
import com.minecolonies.api.colony.IColony;
import com.mojang.logging.LogUtils;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class SalvationSavedData extends SavedData
{
    public static final Logger LOGGER = LogUtils.getLogger();

    // -------------------------
    // Existing colony state data
    // -------------------------
    private final Map<String, ColonyHandlerState> colonyStates = new HashMap<>();
    private static final String TAG_COLONIES = "colonies";
    private static final String TAG_INITIALIZED = "initialized";
    private static final String TAG_LAST_EVAL = "lastEval";
    private static final String TAG_PROGRESSION = "progression";

    public static final String NAME = "salvation_savedata";

    private ServerLevel levelForSave = null;
    private boolean initialized = false;
    private long lastLoopGameTime = 0L;
    private Map<ProgressionSource, Long> progressionMeasure = new EnumMap<>(ProgressionSource.class);

    public enum ProgressionSource { 
        COLONY, CONSTRUCTION, DEFAULT, FUEL, RESOURCEGATHERING, ANIMALS;
    };

    // -------------------------
    // Chunk corruption layer (sparse)
    // -------------------------
    private static final String TAG_CHUNK_CORRUPTION = "chunkCorruption";
    private static final String TAG_CHUNK_KEY = "k";
    private static final String TAG_CHUNK_VALUE = "v";
    private static final String TAG_CHUNK_TOUCHED = "t";
    private static final String TAG_LAST_CORRUPTION_EVENT = "c";
    private static final String TAG_LAST_PURIFICATION_EVENT = "p";

    // key: ChunkPos.toLong()
    // value: 0..255 ChunkCorruptionSystem.CORRUPTION_MAX
    private final Long2IntOpenHashMap chunkCorruption = new Long2IntOpenHashMap();
    private final Long2LongOpenHashMap chunkLastTouched = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap lastCorruptionEvent = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap lastPurificationEvent = new Long2LongOpenHashMap();

    public SalvationSavedData()
    {

        for (ProgressionSource s : ProgressionSource.values())
        {
            progressionMeasure.put(s, 0L);
        }

        // fastutil maps return 0 by default; make that explicit
        chunkCorruption.defaultReturnValue(0);
        chunkLastTouched.defaultReturnValue(0L);
        lastCorruptionEvent.defaultReturnValue(0L);
        lastPurificationEvent.defaultReturnValue(0L);
    }

    /**
     * Returns the SalvationSavedData associated with the given level.
     * If no such data exists, a new instance is created and stored in the data storage.
     */
    public static SalvationSavedData get(@Nonnull ServerLevel level)
    {
        SalvationSavedData data = level.getDataStorage().computeIfAbsent(
            new Factory<>(SalvationSavedData::new, SalvationSavedData::load),
            NAME
        );

        // Dimension tag-based initialization of *global* corruption progression.
        if (!data.isInitialized() && ModTags.Dimensions.isInDimensionTag(level, ModTags.Dimensions.DIMENSIONS_STAGE_6))
        {
            long defaultStartingMeasure = CorruptionStage.STAGE_6_TERMINAL.getThreshold() * 2;
            data.addProgress(ProgressionSource.DEFAULT, defaultStartingMeasure);
            data.markInitialized();
        }

        if (!data.isInitialized() && ModTags.Dimensions.isInDimensionTag(level, ModTags.Dimensions.DIMENSIONS_STAGE_5))
        {
            long defaultStartingMeasure = CorruptionStage.STAGE_5_CRITICAL.getThreshold() + 1;
            data.addProgress(ProgressionSource.DEFAULT, defaultStartingMeasure);
            data.markInitialized();
        }

        if (!data.isInitialized() && ModTags.Dimensions.isInDimensionTag(level, ModTags.Dimensions.DIMENSIONS_STAGE_4))
        {
            long defaultStartingMeasure = CorruptionStage.STAGE_4_DANGEROUS.getThreshold() + 1;
            data.addProgress(ProgressionSource.DEFAULT, defaultStartingMeasure);
            data.markInitialized();
        }

        if (!data.isInitialized() && ModTags.Dimensions.isInDimensionTag(level, ModTags.Dimensions.DIMENSIONS_STAGE_3))
        {
            long defaultStartingMeasure = CorruptionStage.STAGE_3_SPREADING.getThreshold() + 1;
            data.addProgress(ProgressionSource.DEFAULT, defaultStartingMeasure);
            data.markInitialized();
        }

        if (!data.isInitialized() && ModTags.Dimensions.isInDimensionTag(level, ModTags.Dimensions.DIMENSIONS_STAGE_2))
        {
            long defaultStartingMeasure = CorruptionStage.STAGE_2_AWAKENED.getThreshold() + 1;
            data.addProgress(ProgressionSource.DEFAULT, defaultStartingMeasure);
            data.markInitialized();
        }

        if (!data.isInitialized() && ModTags.Dimensions.isInDimensionTag(level, ModTags.Dimensions.DIMENSIONS_STAGE_1))
        {
            long defaultStartingMeasure = CorruptionStage.STAGE_1_NORMAL.getThreshold() + 1;
            data.addProgress(ProgressionSource.DEFAULT, defaultStartingMeasure);
            data.markInitialized();
        }

        if (!data.isInitialized() && ModTags.Dimensions.isInDimensionTag(level, ModTags.Dimensions.DIMENSIONS_STAGE_0))
        {
            long defaultStartingMeasure = 0;
            data.addProgress(ProgressionSource.DEFAULT, defaultStartingMeasure);
            data.markInitialized();
        }

        data.levelForSave = level;

        return data;
    }


    public ServerLevel getLevelForSave()
    {
        return levelForSave;
    }

    /**
     * Returns the colony state for the given key, or creates a new one if it doesn't exist.
     */
    public ColonyHandlerState getOrCreateColonyState(String key)
    {
        return colonyStates.computeIfAbsent(key, k -> new ColonyHandlerState());
    }

    /**
     * Updates the colony state for the given key with the given state.
     */
    public void updateColonyState(String key, ColonyHandlerState state)
    {
        colonyStates.put(key, state);
        setDirty();
    }

    /**
     * Returns a unique key for the given colony and level.
     * This key is in the format of "dimension:colony_center".
     */
    public static String colonyKey(Level level, IColony colony)
    {
        return level.dimension().location() + ":" + colony.getCenter().asLong();
    }

    /**
     * Loads the Salvation saved data from the given compound tag.
     */
    public static SalvationSavedData load(CompoundTag tag, Provider registries)
    {
        SalvationSavedData data = new SalvationSavedData();
        data.lastLoopGameTime = tag.getLong(TAG_LAST_EVAL);

        CompoundTag prog = tag.getCompound(TAG_PROGRESSION);
        for (ProgressionSource source : ProgressionSource.values())
        {
            String sourceTag = source.name();

            if (sourceTag == null) continue;

            data.progressionMeasure.put(source, prog.getLong(sourceTag));
        }

        data.initialized = tag.getBoolean(TAG_INITIALIZED);

        // colonies
        CompoundTag coloniesTag = tag.getCompound(TAG_COLONIES);
        for (String key : coloniesTag.getAllKeys())
        {
            if (key == null) continue;
            data.colonyStates.put(key, ColonyHandlerState.fromTag(coloniesTag.getCompound(key)));
        }

        // chunk corruption list
        if (tag.contains(TAG_CHUNK_CORRUPTION, Tag.TAG_LIST))
        {
            ListTag list = tag.getList(TAG_CHUNK_CORRUPTION, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++)
            {
                CompoundTag e = list.getCompound(i);
                if (!e.contains(TAG_CHUNK_KEY, Tag.TAG_LONG)) continue;
                if (!e.contains(TAG_CHUNK_VALUE, Tag.TAG_INT)) continue;

                long k = e.getLong(TAG_CHUNK_KEY);
                int v = e.getInt(TAG_CHUNK_VALUE);
                long t = e.contains(TAG_CHUNK_TOUCHED, Tag.TAG_LONG) ? e.getLong(TAG_CHUNK_TOUCHED) : 0L;
                long c = e.contains(TAG_LAST_CORRUPTION_EVENT, Tag.TAG_LONG) ? e.getLong(TAG_LAST_CORRUPTION_EVENT) : 0L;
                long p = e.contains(TAG_LAST_PURIFICATION_EVENT, Tag.TAG_LONG) ? e.getLong(TAG_LAST_PURIFICATION_EVENT) : 0L;

                if (v > 0)
                {
                    data.chunkCorruption.put(k, v);
                    if (t > 0) data.chunkLastTouched.put(k, t);
                    if (c > 0) data.lastCorruptionEvent.put(k, c);
                    if (p > 0) data.lastPurificationEvent.put(k, p);
                }
            }
        }

        return data;
    }

    /**
     * Saves the current state of the Salvation saved data to the given compound tag.
     */
    @Override
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull Provider registries)
    {
        TraceUtils.dynamicTrace(ModCommands.TRACE_COLONYLOOP, () -> LOGGER.info("Salvation: Saving corruption data in {}.", levelForSave));

        // colonies
        CompoundTag coloniesTag = new CompoundTag();
        for (Map.Entry<String, ColonyHandlerState> e : colonyStates.entrySet())
        {
            String key = e.getKey();
            CompoundTag colonyTag = e.getValue().toTag();
            if (key == null || colonyTag == null) continue;
            coloniesTag.put(key, colonyTag);
        }
        tag.put(TAG_COLONIES, coloniesTag);

        // scalar fields
        tag.putLong(TAG_LAST_EVAL, lastLoopGameTime);

        CompoundTag prog = new CompoundTag();
        for (ProgressionSource source : ProgressionSource.values())
        {
            String sourceTag = source.name();
            if (sourceTag == null) continue;
            prog.putLong(sourceTag, progressionMeasure.getOrDefault(source, 0L));
        }
        tag.put(TAG_PROGRESSION, prog);

        tag.putBoolean(TAG_INITIALIZED, initialized);

        // chunk corruption list (sparse)
        ListTag chunks = new ListTag();
        for (Long2IntMap.Entry e : chunkCorruption.long2IntEntrySet())
        {
            int v = e.getIntValue();
            if (v <= 0) continue;

            long k = e.getLongKey();
            CompoundTag ct = new CompoundTag();
            ct.putLong(TAG_CHUNK_KEY, k);
            ct.putInt(TAG_CHUNK_VALUE, v);

            long t = chunkLastTouched.get(k);
            if (t > 0) ct.putLong(TAG_CHUNK_TOUCHED, t);

            long c = lastCorruptionEvent.get(k);
            if (c > 0) ct.putLong(TAG_LAST_CORRUPTION_EVENT, c);

            long p = lastPurificationEvent.get(k);
            if (p > 0) ct.putLong(TAG_LAST_PURIFICATION_EVENT, p);

            chunks.add(ct);
        }
        tag.put(TAG_CHUNK_CORRUPTION, chunks);

        TraceUtils.dynamicTrace(ModCommands.TRACE_COLONYLOOP, () -> LOGGER.info("Salvation: Ended corruption data save in {}.", levelForSave));

        return tag;
    }

    // -------------------------
    // Getters/setters
    // -------------------------
    public long getLastLoopGameTime()
    {
        return lastLoopGameTime;
    }

    /**
     * Sets the last loop game time.
     */
    public void setLastLoopGameTime(long t)
    {
        lastLoopGameTime = t;
        setDirty();
    }

    public long getProgressionMeasure(ProgressionSource source)
    {
        return progressionMeasure.getOrDefault(source, 0L);
    }

    /**
     * Adds the given amount of corruption progression to the given source.
     * This will increase the total amount of corruption progression by the given amount.
     * The given amount can be either positive (more corruption) or negative (less corruption).
     * 
     * @param source the source to add the corruption progression to
     * @param progressionMeasure the amount to add to the given source
     */
    public void addProgress(ProgressionSource source, long delta)
    {
        long current = progressionMeasure.getOrDefault(source, 0L);
        long next = current + delta;
        
        if (next < 0) next = 0;

        progressionMeasure.put(source, next);
        setDirty();
    }

    /**
     * Clears the corruption progression stored in the given source.
     * This will reset the amount of corruption progression stored in this object
     * for the given source to 0.
     * 
     * @param source the source to clear the corruption progression from
     */
    public void clearProgressionMeasure(ProgressionSource source)
    {
        progressionMeasure.put(source, 0L);
        setDirty();
    }

    /**
     * Resets all corruption progression sources to 0.
     * This will clear all corruption progression stored in this object.
     */
    public void clearAllProgression()
    {
        for (ProgressionSource source : ProgressionSource.values())
        {
            progressionMeasure.put(source, 0L);
        }
        setDirty();
    }

    /**
     * Returns the total amount of corruption progression across all sources.
     * This is the sum of all individual sources' progression measures.
     * 
     * @return the total amount of corruption progression
     */
    public long getTotalProgression()
    {
        long total = 0L;
        for (ProgressionSource source : ProgressionSource.values())
        {
            total += getProgressionMeasure(source);
        }
        return total;
    }

    public boolean isInitialized()
    {
        return initialized;
    }

    public void reset()
    {
        clearAllProgression();
        lastLoopGameTime = 0L;
        initialized = false;
        colonyStates.clear();

        // clear chunk corruption too
        chunkCorruption.clear();
        chunkLastTouched.clear();
        lastCorruptionEvent.clear();
        lastPurificationEvent.clear();

        setDirty();
    }

    public void markInitialized()
    {
        initialized = true;
        setDirty();
    }

    // -------------------------
    // NEW: Chunk corruption API (used by ChunkCorruptionSystem)
    // -------------------------

    public int getChunkCorruption(final long chunkKey)
    {
        return chunkCorruption.get(chunkKey);
    }

    /**
     * Set corruption and mark last-touched time.
     */
    public void setChunkCorruption(final long chunkKey, final int value, final long gameTime)
    {
        int oldValue = chunkCorruption.get(chunkKey);
        int change = value - oldValue;

        if (value <= 0)
        {
            removeChunkCorruption(chunkKey);
            return;
        }

        chunkCorruption.put(chunkKey, value);
        if (gameTime > 0) chunkLastTouched.put(chunkKey, gameTime);
        if (gameTime > 0 && change > 0) lastCorruptionEvent.put(chunkKey, gameTime);
        if (gameTime > 0 && change < 0) lastPurificationEvent.put(chunkKey, gameTime);
        setDirty();
    }

    /**
     * Set corruption without touching last-touched time.
     * Used for decay logic where we *don't* want to keep chunks "fresh".
     */
    public void setChunkCorruptionRaw(final long chunkKey, final int value)
    {
        if (value <= 0)
        {
            removeChunkCorruption(chunkKey);
            return;
        }

        chunkCorruption.put(chunkKey, value);
        setDirty();
    }

    /**
     * Returns the last time the chunk was touched (in game ticks) if it has been touched at all.
     * Otherwise, returns 0.
     * @param chunkKey the key of the chunk to query
     * @return the last time the chunk was touched, or 0 if never touched
     */
    public long getChunkLastTouched(final long chunkKey)
    {
        return chunkLastTouched.get(chunkKey);
    }

    /**
     * Returns the last time a corruption event occurred in the given chunk.
     * If no corruption event has ever occurred in the chunk, returns 0.
     * @param chunkKey the key of the chunk to query
     * @return the last time a corruption event occurred in the chunk, or 0 if never occurred
     */
    public long getLastCorruptionEvent(final long chunkKey)
    {
        return lastCorruptionEvent.get(chunkKey);
    }

    public long getLastPurificationEvent(final long chunkKey)
    {
        return lastPurificationEvent.get(chunkKey);
    }

    public void removeChunkCorruption(final long chunkKey)
    {
        boolean had = (chunkCorruption.remove(chunkKey) != 0);
        chunkLastTouched.remove(chunkKey);
        lastCorruptionEvent.remove(chunkKey);
        lastPurificationEvent.remove(chunkKey);
        if (had) setDirty();
    }

    public int getCorruptedChunkCount()
    {
        return chunkCorruption.size();
    }

    /**
     * Snapshot keys so callers can iterate safely while mutating the underlying map.
     */
    public long[] copyCorruptedChunkKeys()
    {
        return chunkCorruption.keySet().toLongArray();
    }
}