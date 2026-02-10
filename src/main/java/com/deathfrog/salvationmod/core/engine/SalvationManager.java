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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

public final class SalvationManager 
{
    public enum CorruptionStage 
    {
        // IDEA: (Phase 3) Make this datapacked
        STAGE_0_UNTRIGGERED (0      , 0.00f, 0.00f, 20 * 60 * 1),
        STAGE_1_NORMAL      (200    , 0.02f, 0.03f, 20 * 60 * 3),
        STAGE_2_AWAKENED    (2000   , 0.04f, 0.09f, 20 * 60 * 4),
        STAGE_3_SPREADING   (6000   , 0.08f, 0.27f, 20 * 60 * 8),
        STAGE_4_DANGEROUS   (18000  , 0.12f, 0.81f, 20 * 60 * 12),
        STAGE_5_CRITICAL    (36000  , 0.20f, 1.00f, 20 * 60 * 20),
        STAGE_6_TERMINAL    (72000  , 0.32f, 1.00f, 20 * 60 * 32);

        private final int threshold;
        private final float lootCorruptionChance;
        private final float entitySpawnChance;
        private final int decayCooldown;

        CorruptionStage(int threshold, float lootCorruptionChance, float entitySpawnChance, int decayCooldown) 
        {
            this.threshold = threshold;
            this.lootCorruptionChance = lootCorruptionChance;
            this.entitySpawnChance = entitySpawnChance;
            this.decayCooldown = decayCooldown;
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

        public int getDecayCooldown() 
        {
            return decayCooldown;
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

        // Colony independent logic goes here.
        ChunkCorruptionSystem.tick(level, data);

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

    public static CorruptionStage applyBlockProgression(@Nonnull ServerLevel level, @Nonnull BlockState state, @Nonnull BlockPos pos) 
    {
        int progress = 0;

        // Generic corruption-triggering blocks
        if (state.is(ModTags.Blocks.CORRUPTION_BLOCK_MINOR))
        {
            progress += 2;
        }

        // Stronger trigger blocks
        if (state.is(ModTags.Blocks.CORRUPTION_BLOCK_MAJOR))
        {
            progress += 5;
        }

        // Even stronger trigger blocks
        if (state.is(ModTags.Blocks.CORRUPTION_BLOCK_EXTREME))
        {
            progress += 13;
        }

        SalvationManager.progress(level, progress);
        ChunkCorruptionSystem.onCorruptingAction(level, pos, progress);

        return stageForLevel(level);
    }


    /**
     * Applies mob progression to the given level based on the entity type.
     * This method is responsible for adding corruption progression to the level
     * based on the type of entity killed.
     * 
     * @param entity The entity that was killed.
     * @param location The position where the entity was killed.
     * @return The current corruption stage of the level.
     */
    public static CorruptionStage applyMobProgression(LivingEntity entity, BlockPos location)
    {
        Level entityLevel = entity.level();

        if (entityLevel == null || entityLevel.isClientSide) return CorruptionStage.STAGE_0_UNTRIGGERED;

        ServerLevel level = (ServerLevel) entityLevel;

        int corruption = 0;
        if (entity.getType().is(ModTags.Entities.CORRUPTION_KILL_MINOR))
        {
            corruption += 2;
        }

        if (entity.getType().is(ModTags.Entities.CORRUPTION_KILL_MAJOR))
        {
            corruption += 5;
        }

        if (entity.getType().is(ModTags.Entities.CORRUPTION_KILL_EXTREME))
        {
            corruption += 13;
        }

        SalvationManager.progress(level, corruption);
        ChunkCorruptionSystem.onCorruptingAction(level, location, corruption);


        int purification = 0;
        if (entity.getType().is(ModTags.Entities.PURIFICATION_KILL_MINOR))
        {
            purification += 2;
        }

        if (entity.getType().is(ModTags.Entities.PURIFICATION_KILL_MAJOR))
        {
            purification += 5;
        }

        if (entity.getType().is(ModTags.Entities.PURIFICATION_KILL_EXTREME))
        {
            purification += 13;
        }

        SalvationManager.progress(level, -purification);

        if (location != null)
        {
            if (purification > 0) 
            {
                ChunkCorruptionSystem.onPurifyingAction(level, location, purification);
            }

            // If this entity was killed in a colony, add purification credits
            IColony colony = IColonyManager.getInstance().getIColony(level, location);

            if (colony != null)
            {
                SalvationManager.colonyPurificationCredit(colony, purification);
            }
        }

        return stageForLevel(level);
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
    public static CorruptionStage stageForLevel(@Nonnull ServerLevel level)
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
    public static long getProgressionMeasure(@Nonnull ServerLevel level)
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

        // Spawn chance increases even more if the chunk is corrupted
        spawnChance *= ChunkCorruptionSystem.spawnChanceMultiplier(level, pos);

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

        boolean allowed = isCorruptedSpawnAllowed(level, pos);

        event.setResult(allowed
            ? MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT
            : MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    /**
     * Checks if a corrupted animal can spawn at the given position.
     * The rules are the same as for {@link #applySpawnOverride(MobSpawnEvent.SpawnPlacementCheck)}.
     * 
     * @param type the entity type to check
     * @param levelAccessor the level accessor to check the level of
     * @param reason the reason the entity is being spawned
     * @param pos the position to check
     * @param random a random source
     * @return true if the entity can be spawned, false otherwise
     */
    public static boolean checkCorruptedAnimalPlacement(
        EntityType<? extends Mob> type,
        ServerLevelAccessor levelAccessor,
        MobSpawnType reason,
        BlockPos pos,
        RandomSource random
    ) 
    {   
        if (levelAccessor == null || levelAccessor.isClientSide() || pos == null)
            return false;

        Level level = levelAccessor.getLevel();

        if ((level == null) || !(level instanceof ServerLevel serverlevel)) return false;

        return isCorruptedEntity(type) && isCorruptedSpawnAllowed(serverlevel, pos);
    }

    /**
     * Checks if a corrupted entity is allowed to spawn at the given position, given the current corruption stage and level light.
     * The rules are as follows:
     * - Early game (before STAGE_5_CRITICAL), entities are only allowed to spawn at night, and only if the block light is <= maxLocalLight (which increases as the corruption stage progresses).
     * - Late game (at or after STAGE_5_CRITICAL), entities are allowed to spawn as long as the block light is <= maxLocalLight + 3.
     * 
     * @param stage the current corruption stage
     * @param level the level to check
     * @param pos the position to check
     * @return true if the entity is allowed to spawn at the given position, false otherwise
     */
    public static boolean isCorruptedSpawnAllowed(@Nonnull ServerLevel level, @Nonnull BlockPos pos)
    {
        final CorruptionStage stage = stageForLevel(level);
        boolean allowed = false;

        if (stage == CorruptionStage.STAGE_0_UNTRIGGERED) return false;

        // Progression -> brighter allowed. Tune these numbers.
        final int stageIndex = Math.max(0, stage.ordinal() - CorruptionStage.STAGE_1_NORMAL.ordinal()); // 0..N
        final int maxLocalLight = Math.min(15, 7 + stageIndex);

        final int localLight = level.getMaxLocalRawBrightness(pos);
        final boolean night = level.isNight();

        allowed =
            (stage.ordinal() >= CorruptionStage.STAGE_5_CRITICAL.ordinal())
                ? (localLight <= Math.min(15, maxLocalLight + 3)) // late game gets very permissive
                : (night && localLight <= maxLocalLight);

        return allowed;
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
        salvationData.setProgressionMeasure(Math.max(progressionMeasure + amount, 0));

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