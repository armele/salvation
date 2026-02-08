package com.deathfrog.salvationmod.core.engine;

import java.util.List;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

public final class SalvationManager 
{
    public enum CorruptionStage 
    {
        // TODO: Make this datapacked
        STAGE_0_UNTRIGGERED (0, 0.0f, 0.0f),
        STAGE_1_NORMAL (200, 0.02f, .03f),
        STAGE_2_AWAKENED (400, .04f, .09f),
        STAGE_3_SPREADING (600, .08f, .27f),
        STAGE_4_DANGEROUS (800, .12f, .81f),
        STAGE_5_CRITICAL (10000, .20f, 1.0f),
        STAGE_6_TERMINAL (20000, .32f, 1.0f);

        private final int threshold;
        private final float lootCorruptionChance;
        private final float entitySpawnChance;

        CorruptionStage(int threshold, float lootCorruptionChance, float entitySpawnChance) 
        {
            this.threshold = threshold;
            this.lootCorruptionChance = lootCorruptionChance;
            this.entitySpawnChance = entitySpawnChance;
        }

        public int getThreshold() 
        {
            return threshold;
        }

        public float getLootCorruptionChance() 
        {
            return lootCorruptionChance;
        }

        public float getEntitySpawnChance() 
        {
            return entitySpawnChance;
        }
    }

    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Run the salvation logic for the given level.
     * This method is responsible for advancing the storyline of the given level.
     * It is called about once per second by the onServerTick method.
     *  
     * @param level The level to run the salvation logic for.
     */
    public static void salvationLogicLoop(ServerLevel level)
    {   
        if (level == null || level.isClientSide) return;

        SalvationSavedData data = SalvationSavedData.get(level);
        long gameTime = level.getGameTime();

        data.setLastLoopGameTime(gameTime);

        List<IColony> colonies = IColonyManager.getInstance().getColonies(level);

        // TODO: Colony independent logic goes here.

        // Cycle through all colonies and see which need processing
        for (IColony colony : colonies)
        {
            SalvationColonyHandler handler = SalvationColonyHandler.getHandler(level, colony);

            if (gameTime < handler.getNextProcessTick())
            {
                continue;
            }

            // Colony-specific logic goes in the handler class
            handler.processColonyLogic();
        }
    }

    /**
     * Checks if the given entity is a corrupted entity
     * 
     * @param entity The entity to check.
     * @return true if the entity is a corrupted entity, false otherwise.
     */
    public static boolean isCorruptedEntity(EntityType<?> entityType)
    {
        return entityType.is(ModTags.Entities.CORRUPTED_ENTITY);
    }

    /**
     * Returns the current stage of the salvation logic for the given level.
     * A higher stage number indicates a greater level of progression.
     * The exact meaning of each stage number is not specified and is up to the mod implementer.
     * The default implementation returns the progression measure divided by 100, which can be overridden by mods.
     * 
     * @param level the level to get the stage for
     * @return the current stage of the salvation logic for the given level
     */
    public static CorruptionStage stageForLevel(ServerLevel level)
    {
        SalvationSavedData salvationData = SalvationSavedData.get(level);
        long progressionMeasure = salvationData.getProgressionMeasure();

        if (progressionMeasure > CorruptionStage.STAGE_6_TERMINAL.getThreshold()) return CorruptionStage.STAGE_6_TERMINAL;
        if (progressionMeasure > CorruptionStage.STAGE_5_CRITICAL.getThreshold()) return CorruptionStage.STAGE_5_CRITICAL;
        if (progressionMeasure > CorruptionStage.STAGE_4_DANGEROUS.getThreshold()) return CorruptionStage.STAGE_4_DANGEROUS;
        if (progressionMeasure > CorruptionStage.STAGE_3_SPREADING.getThreshold()) return CorruptionStage.STAGE_3_SPREADING;
        if (progressionMeasure > CorruptionStage.STAGE_2_AWAKENED.getThreshold()) return CorruptionStage.STAGE_2_AWAKENED;
        if (progressionMeasure > CorruptionStage.STAGE_1_NORMAL.getThreshold()) return CorruptionStage.STAGE_1_NORMAL;

        return CorruptionStage.STAGE_0_UNTRIGGERED;
    }

    /**
     * Returns the current progression measure of the Salvation saved data for the given level.
     * This is a measure of how much progress the player has made in the Salvation mod.
     * The exact meaning of this value is not specified and is up to the mod implementer.
     * 
     * @param level the level to get the progression measure for
     * @return the current progression measure of the Salvation saved data for the given level
     */
    public static long getProgressionMeasure(ServerLevel level)
    {
        SalvationSavedData salvationData = SalvationSavedData.get(level);
        return salvationData.getProgressionMeasure();
    }

    /**
     * Applies the spawn override rules for the given spawn event.
     * This function is called by the MobSpawnEvent.SpawnPlacementCheck event.
     * It checks if the entity is a corrupted entity and if so, applies the spawn override rules.
     * The spawn override rules are based on corruption progression and level of light.
     * 
     * @param event the spawn event to apply the rules to
     */
    public static void applySpawnOverride(final MobSpawnEvent.SpawnPlacementCheck event)
    {
        final ServerLevelAccessor accessor = event.getLevel();
        final ServerLevel level = accessor.getLevel();
        final BlockPos pos = event.getPos();

        if (level == null || level.isClientSide || pos == null)
            return;

        // Only run for corrupted mobs (safety even if caller forgets)
        if (!isCorruptedEntity(event.getEntityType()))
            return;

        final CorruptionStage stage = stageForLevel(level);

        // Progression -> increased spawn chance
        float spawnChance = stage.getEntitySpawnChance();
        if (spawnChance == 0.0F) 
        {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            return;
        }

        if (level.random.nextFloat() > spawnChance) 
        {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            return;
        }

        // Progression -> brighter allowed. Tune these numbers.
        final int stageIndex = Math.max(0, stage.ordinal() - CorruptionStage.STAGE_1_NORMAL.ordinal()); // 0..N
        final int maxLocalLight = Math.min(15, 7 + stageIndex);

        final int localLight = level.getMaxLocalRawBrightness(pos);
        final boolean night = level.isNight();

        final boolean allowed =
            (stage.ordinal() >= CorruptionStage.STAGE_5_CRITICAL.ordinal())
                ? (localLight <= Math.min(15, maxLocalLight + 3)) // late game gets very permissive
                : (night && localLight <= maxLocalLight);

        event.setResult(allowed
            ? MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED
            : MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    /**
     * Changes the corruption progress by the designated amount.
     * This can be either positive (more corruption) or negative (less corruption).
     * 
     * @param level the level to add the progression measure to
     * @param amount the amount to add to the progression measure
     * @return the current stage of the salvation logic for the given level after adding the progression measure
     */
    public static CorruptionStage progress(@Nonnull ServerLevel level, int amount) 
    {
        SalvationSavedData salvationData = SalvationSavedData.get(level);
        long progressionMeasure = salvationData.getProgressionMeasure();
        salvationData.setProgressionMeasure(progressionMeasure + amount);

        return stageForLevel(level);
    }

    /**
     * Adds a given number of purification credits to the colony's state.
     * Purification credits are used to measure the progress of the colony in purifying the world.
     * They are used to determine when the colony can progress to the next stage of the Salvation logic.
     * @param colony the colony to add the purification credits to
     * @param amount the number of purification credits to add
     */
    public static void colonyPurificationCredit(IColony colony, int amount) 
    {
        Level level = colony.getWorld();

        if (level == null || level.isClientSide()) return;

        SalvationColonyHandler handler = SalvationColonyHandler.getHandler((ServerLevel) level, colony);
        handler.addPurificationCredits(amount);
    }
}