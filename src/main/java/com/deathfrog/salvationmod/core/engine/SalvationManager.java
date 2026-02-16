package com.deathfrog.salvationmod.core.engine;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3f;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.Config;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.ModEnchantments;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.crafting.ItemStorage;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

public final class SalvationManager 
{
    public enum CorruptionStage 
    {
        // IDEA: (Phase 3) Make this datapacked
        STAGE_0_UNTRIGGERED (0      , 0.00f, 0.00f, 20 * 60 * 1,  20 * 60 * 12),
        STAGE_1_NORMAL      (2000   , 0.02f, 0.03f, 20 * 60 * 3,  20 * 60 * 10),
        STAGE_2_AWAKENED    (6000   , 0.04f, 0.09f, 20 * 60 * 4,  20 * 60 * 8 ),
        STAGE_3_SPREADING   (12000  , 0.08f, 0.27f, 20 * 60 * 8,  20 * 60 * 6 ),
        STAGE_4_DANGEROUS   (24000  , 0.12f, 0.81f, 20 * 60 * 12, 20 * 60 * 4 ),
        STAGE_5_CRITICAL    (48000  , 0.20f, 1.00f, 20 * 60 * 20, 20 * 60 * 2 ),
        STAGE_6_TERMINAL    (96000  , 0.32f, 1.00f, 20 * 60 * 32, 20 * 60 * 1 );

        private final int threshold;
        private final float lootCorruptionChance;
        private final float entitySpawnChance;
        private final int decayCooldown;
        private final int blightCooldown;

        CorruptionStage(int threshold, float lootCorruptionChance, float entitySpawnChance, int decayCooldown, int blightCooldown) 
        {
            this.threshold = threshold;
            this.lootCorruptionChance = lootCorruptionChance;
            this.entitySpawnChance = entitySpawnChance;
            this.decayCooldown = decayCooldown;
            this.blightCooldown = blightCooldown;

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

        public int getBlightCooldown()
        {
            return blightCooldown;
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
        BlightSurfaceSystem.tick(level);

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
     * Checks if the given entity is a corruptable entity
     * 
     * A corruptable entity is one that can be corrupted by the salvation system.
     * Examples of corruptable entities are cows, sheep, and cats.
     * 
     * @param entityType The entity type to check.
     * @return true if the entity is a corruptable entity, false otherwise.
     */
    public static boolean isCorruptableEntity(EntityType<?> entityType)
    {
        return entityType.is(ModTags.Entities.CORRUPTABLE_ENTITY);
    }

    /**
     * Applies the given block break progress to the given level at the given position.
     * This method is responsible for advancing the corruption stage of the given level based on the given block broken.
     *
     * @param level The level to apply the block break progress to.
     * @param state The block state of the block that was broken.
     * @param pos The position of the block that was broken.
     * @return The new corruption stage of the given level.
     */
    public static CorruptionStage applyBlockBreakProgression(@Nonnull ServerLevel level, @Nonnull BlockState state, @Nonnull BlockPos pos, @Nullable LivingEntity source) 
    {
        int corruption = 0;
        int purification = 0;

        // Trival corruption-triggering blocks
        if (state.is(ModTags.Blocks.CORRUPTION_BREAK_TRIVIAL))
        {
            corruption += 1;
        }

        // Minor corruption-triggering blocks
        if (state.is(ModTags.Blocks.CORRUPTION_BREAK_MINOR))
        {
            corruption += 2;
        }

        // Stronger trigger blocks
        if (state.is(ModTags.Blocks.CORRUPTION_BREAK_MAJOR))
        {
            corruption += 5;
        }

        // Even stronger trigger blocks
        if (state.is(ModTags.Blocks.CORRUPTION_BREAK_EXTREME))
        {
            corruption += 13;
        }

        // Trivial purification-triggering blocks
        if (state.is(ModTags.Blocks.PURIFICATION_BREAK_TRIVIAL))
        {
            purification += 1;
        }

        // Minor purification-triggering blocks
        if (state.is(ModTags.Blocks.PURIFICATION_BREAK_MINOR))
        {
            purification += 2;
        }

        // Stronger trigger blocks
        if (state.is(ModTags.Blocks.PURIFICATION_BREAK_MAJOR))
        {
            purification += 5;
        }

        // Even stronger trigger blocks
        if (state.is(ModTags.Blocks.PURIFICATION_BREAK_EXTREME))
        {
            purification += 13;
        }

        if (corruption > 0 && source != null)
        {
            corruption = applyWard(corruption, source);
        }

        int progress = (corruption - purification);
        
        CorruptionStage stage = recordCorruption(level, ProgressionSource.RESOURCEGATHERING, pos, progress);

        return stage;
    }

    /**
     * Applies the given block break progress to the given level at the given position.
     * This method is responsible for advancing the corruption stage of the given level based on the given block broken.
     *
     * @param level The level to apply the block break progress to.
     * @param state The block state of the block that was broken.
     * @param pos The position of the block that was broken.
     * @return The new corruption stage of the given level.
     */
    public static CorruptionStage applyBlockPlaceProgression(@Nonnull ServerLevel level, @Nonnull BlockState state, @Nonnull BlockPos pos) 
    {
        int progress = 0;

        // Trivial corruption-triggering blocks
        if (state.is(ModTags.Blocks.CORRUPTION_PLACE_TRIVIAL))
        {
            progress += 1;
        }

        // Minor corruption-triggering blocks
        if (state.is(ModTags.Blocks.CORRUPTION_PLACE_MINOR))
        {
            progress += 2;
        }

        // Stronger trigger blocks
        if (state.is(ModTags.Blocks.CORRUPTION_PLACE_MAJOR))
        {
            progress += 5;
        }

        // Even stronger trigger blocks
        if (state.is(ModTags.Blocks.CORRUPTION_PLACE_EXTREME))
        {
            progress += 13;
        }

        // Trival corruption-triggering blocks
        if (state.is(ModTags.Blocks.PURIFICATION_PLACE_TRIVIAL))
        {
            progress -= 1;
        }

        // Minor corruption-triggering blocks
        if (state.is(ModTags.Blocks.PURIFICATION_PLACE_MINOR))
        {
            progress -= 2;
        }

        // Stronger trigger blocks
        if (state.is(ModTags.Blocks.PURIFICATION_PLACE_MAJOR))
        {
            progress -= 5;
        }

        // Even stronger trigger blocks
        if (state.is(ModTags.Blocks.PURIFICATION_PLACE_EXTREME))
        {
            progress -= 13;
        }

        CorruptionStage stage = recordCorruption(level, ProgressionSource.CONSTRUCTION, pos, progress);

        return stage;
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
    public static CorruptionStage applyMobProgression(LivingEntity entity, BlockPos location, @Nullable LivingEntity source)
    {
        Level entityLevel = entity.level();

        if (entityLevel == null || entityLevel.isClientSide) return CorruptionStage.STAGE_0_UNTRIGGERED;

        ServerLevel level = (ServerLevel) entityLevel;

        int corruption = 0;
        int purification = 0;

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

        if (corruption > 0 && source != null)
        {
            corruption = applyWard(corruption, source);
        }

        int progress = (corruption - purification);

        CorruptionStage stage = recordCorruption(level, ProgressionSource.ANIMALS, location, progress);

        return stage;
    }

    /**
     * Applies corruption progression to the given level based on the fuel used.
     * This method is responsible for adding corruption progression to the level
     * based on the type of fuel used.
     * 
     * @param level The level to apply the corruption progression to.
     * @param pos The position where the fuel was used.
     * @param cookedOutput The item that was cooked using the fuel.
     * @param fuel The fuel used to cook the item.
     * @return The current corruption stage of the level.
     */
    public static CorruptionStage applyFuelProgression(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull ItemStorage cookedOutput, @Nonnull ItemStorage fuel)
    {
        double corruption = 0;
        double purification = 0;
        int fuelPoints = fuel.getAmount();
        ItemStack fuelItem = fuel.getItemStack();

        if (fuelItem.isEmpty())
        {
            return stageForLevel(level);
        }

        // IDEA: For now we only care about what fuel was used, but future we might care about what was actually cooked (cookedOutput)

        // Trival corruption-triggering fuel sources
        if (fuelItem.is(ModTags.Items.CORRUPTION_FUEL_TRIVIAL))
        {
            corruption += 1;
        }

        // Minor corruption-triggering fuel sources
        if (fuelItem.is(ModTags.Items.CORRUPTION_FUEL_MINOR))
        {
            corruption += 2;
        }

        // Stronger trigger fuel sources
        if (fuelItem.is(ModTags.Items.CORRUPTION_FUEL_MAJOR))
        {
            corruption += 5;
        }

        // Even stronger trigger fuel sources
        if (fuelItem.is(ModTags.Items.CORRUPTION_FUEL_EXTREME))
        {
            corruption += 13;
        }

        // Trival purification-triggering fuel sources
        if (fuelItem.is(ModTags.Items.PURIFICATION_FUEL_TRIVIAL))
        {
            purification += 1;
        }

        // Minor purification-triggering fuel sources
        if (fuelItem.is(ModTags.Items.PURIFICATION_FUEL_MINOR))
        {
            purification += 2;
        }

        // Stronger trigger fuel sources
        if (fuelItem.is(ModTags.Items.PURIFICATION_FUEL_MAJOR))
        {
            purification += 5;
        }

        // Even stronger trigger fuel sources
        if (fuelItem.is(ModTags.Items.PURIFICATION_FUEL_EXTREME))
        {
            purification += 13;
        }

        if (cookedOutput != null)
        {
            corruption *= fuelPoints / 1000.0;
            purification *= fuelPoints / 1000.0;
        }

        final IColony sourceColony = IColonyManager.getInstance().getIColony(level, pos);

        if (sourceColony != null && corruption > 0)
        {
            double fuelFiltering = 1 - sourceColony.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_CLEANFUEL);
            corruption = corruption * fuelFiltering;
        } 

        corruption = corruption - purification;

        if (corruption > 0 && corruption < 1.0) corruption = 1.0;
        if (corruption < 0 && corruption > -1.0) corruption = -1.0;

        CorruptionStage stage = recordCorruption(level, ProgressionSource.FUEL, pos, (int) corruption);

        return stage;
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
        long progressionMeasure = salvationData.getTotalProgression();

        if (progressionMeasure > CorruptionStage.STAGE_6_TERMINAL.getThreshold()) return CorruptionStage.STAGE_6_TERMINAL;
        if (progressionMeasure > CorruptionStage.STAGE_5_CRITICAL.getThreshold()) return CorruptionStage.STAGE_5_CRITICAL;
        if (progressionMeasure > CorruptionStage.STAGE_4_DANGEROUS.getThreshold()) return CorruptionStage.STAGE_4_DANGEROUS;
        if (progressionMeasure > CorruptionStage.STAGE_3_SPREADING.getThreshold()) return CorruptionStage.STAGE_3_SPREADING;
        if (progressionMeasure > CorruptionStage.STAGE_2_AWAKENED.getThreshold()) return CorruptionStage.STAGE_2_AWAKENED;
        if (progressionMeasure > CorruptionStage.STAGE_1_NORMAL.getThreshold()) return CorruptionStage.STAGE_1_NORMAL;

        return CorruptionStage.STAGE_0_UNTRIGGERED;
    }

    /**
     * Returns the final corruption stage that the Salvation mod can progress to.
     * This is the highest possible corruption stage and is used as a boundary check.
     * 
     * @return the final corruption stage that the Salvation mod can progress to
     */
    public static CorruptionStage finalStage()
    {
        return CorruptionStage.STAGE_6_TERMINAL;
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
        return salvationData.getTotalProgression();
    }

    /**
     * Applies the spawn override rules for the given spawn event.
     * This function is called by the MobSpawnEvent.SpawnPlacementCheck event.
     * It checks if the entity is a corrupted entity and if so, applies the spawn override rules.
     * The spawn override rules are based on corruption progression and level of light.
     * 
     * @param event the spawn event to apply the rules to
     */
    public static void enforceSpawnOverride(final MobSpawnEvent.SpawnPlacementCheck event)
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
     * Called when an entity is spawned and finalized.
     * Applies the corruption rules to the entity.
     * Checks if the entity is corruptible and if so, applies the corruption rules.
     * The corruption rules are based on the corruption progression and level of light.
     *
     * @param event the finalize spawn event to apply the rules to
     * @param corruptableMob the mob to apply the rules to
     */
    public static void corruptOnSpawn(final FinalizeSpawnEvent event, final Mob corruptableMob)
    {
        if (!(event.getLevel() instanceof ServerLevel serverLevel))
            return;

        final BlockPos pos = corruptableMob.blockPosition();

        if (pos == null) return;

        // Only run for corruptable mobs (safety even if caller forgets)
        if (!isCorruptableEntity(corruptableMob.getType())) return;

        TraceUtils.dynamicTrace(ModCommands.TRACE_SPAWN, () -> LOGGER.info("Checking for corruption spawn replacement of {} at {}", corruptableMob, pos));

        final CorruptionStage stage = stageForLevel(serverLevel);

        // Progression -> increased spawn chance
        float replacementChance = stage.getEntitySpawnChance();

        // Spawn chance increases even more if the chunk is corrupted
        replacementChance *= ChunkCorruptionSystem.spawnChanceMultiplier(serverLevel, pos);

        // Bail fast
        if (replacementChance <= 0.0F) return;

        // Keep sane bounds (protects against multipliers > 1.0, negatives, NaN)
        if (Float.isNaN(replacementChance)) return;

        replacementChance = Math.min(1.0F, Math.max(0.0F, replacementChance));

        if (serverLevel.random.nextFloat() > replacementChance) return;

        // If a corrupted spawn is not allowed here, stop before doing anything costly
        if (!isCorruptedSpawnAllowed(serverLevel, pos)) return;

        EntityType<?> entityType = corruptableMob.getType();

        if (entityType == null) return;

        // Find mapping: vanilla type -> corrupted type id
        final ResourceLocation vanillaId = EntityType.getKey(entityType);
        final Optional<ResourceLocation> corruptedIdOpt = SalvationMod.CURE_MAPPINGS.getCorruptedForVanilla(vanillaId);

        if (corruptedIdOpt.isEmpty()) return;

        final ResourceLocation corruptedId = corruptedIdOpt.get();

        // Resolve entity type from registry
        final EntityType<?> corruptedType = BuiltInRegistries.ENTITY_TYPE.get(corruptedId);

        // Create entity
        final Entity created = corruptedType.create(serverLevel);
        
        if (!(created instanceof Mob corruptedMob)) return;

        TraceUtils.dynamicTrace(ModCommands.TRACE_SPAWN, () -> LOGGER.info("Corruption causes spawn replacement of {} to {} at {}", corruptableMob, created, pos));

        // Only NOW do we cancel the vanilla spawn
        event.setSpawnCancelled(true);

        // Place and spawn
        corruptedMob.moveTo(
            corruptableMob.getX(),
            corruptableMob.getY(),
            corruptableMob.getZ(),
            corruptableMob.getYRot(),
            corruptableMob.getXRot()
        );

        serverLevel.addFreshEntity(corruptedMob);
    }

    /**
     * Corrupts a mob on chunk load, replacing it with a corrupted variant if applicable.
     * This function is called for all entities in a chunk when it is first loaded.
     * It is intended to be used for replacing entities that are not yet corrupted with corrupted variants.
     *
     * The chance of corruption is determined by the current corruption stage of the level and the local chunk corruption level.
     * If the corruption stage is 0 or less, this function will not corrupt any entities.
     * If the local chunk corruption level is 0 or less, this function will not corrupt any entities outside of corrupted areas.
     *
     * If a corrupted spawn is not allowed at the given position, this function will not replace the entity.
     *
     * @param serverLevel the server level to corrupt the entity in
     * @param corruptableMob the mob to corrupt
     */
    public static boolean corruptOnChunkLoad(final @Nonnull ServerLevel serverLevel, final Mob corruptableMob)
    {
        final BlockPos pos = corruptableMob.blockPosition();
        if (pos == null) return false;

        if (!isCorruptableEntity(corruptableMob.getType())) return false;

        final CorruptionStage stage = stageForLevel(serverLevel);

        float replacementChance = stage.getEntitySpawnChance();
        replacementChance *= ChunkCorruptionSystem.spawnChanceMultiplier(serverLevel, pos);

        if (replacementChance <= 0.0F) return false;
        if (Float.isNaN(replacementChance)) return false;
        replacementChance = Math.min(1.0F, Math.max(0.0F, replacementChance));

        if (serverLevel.random.nextFloat() > replacementChance) return false;

        if (!isCorruptedSpawnAllowed(serverLevel, pos)) return false;

        final EntityType<?> vanillaType = corruptableMob.getType();
        if (vanillaType == null) return false;

        final ResourceLocation vanillaId = EntityType.getKey(vanillaType);
        final Optional<ResourceLocation> corruptedIdOpt =
            SalvationMod.CURE_MAPPINGS.getCorruptedForVanilla(vanillaId);

        if (corruptedIdOpt.isEmpty()) return false;

        final EntityType<?> corruptedType = BuiltInRegistries.ENTITY_TYPE.get(corruptedIdOpt.get());
        final Entity created = corruptedType.create(serverLevel);
        if (!(created instanceof Mob corruptedMob)) return false;

        // Replace in-world
        corruptedMob.moveTo(
            corruptableMob.getX(),
            corruptableMob.getY(),
            corruptableMob.getZ(),
            corruptableMob.getYRot(),
            corruptableMob.getXRot()
        );

        // Optional: preserve baby/adult and name
        if (corruptableMob instanceof net.minecraft.world.entity.AgeableMob a
            && corruptedMob instanceof net.minecraft.world.entity.AgeableMob b)
        {
            b.setAge(a.getAge());
        }
        if (corruptableMob.hasCustomName())
            corruptedMob.setCustomName(corruptableMob.getCustomName());

        TraceUtils.dynamicTrace(ModCommands.TRACE_SPAWN, () -> LOGGER.info("Corruption causes replacement of {} to {} at {} during chunk processing.", corruptableMob, created, pos));

        // Remove original first to avoid momentary double-counting
        corruptableMob.discard();
        serverLevel.addFreshEntity(corruptedMob);

        return true;
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
     * Records the given amount of corruption/purification at the given position and source.
     * If the amount is positive, it will be added to the corruption measure.
     * If the amount is negative, it will be added to the purification measure.
     * If no position is provided, the function will only modify the global corruption state.
     * 
     * @param level the level to modify
     * @param source the source of the corruption/purification
     * @param pos the position at which the corruption/purification is being recorded
     * @param amount the amount of corruption/purification to record
     * @return the current stage of the salvation logic for the given level after recording the corruption/purification
     */
    public static CorruptionStage recordCorruption(@Nonnull ServerLevel level, ProgressionSource source, @Nullable BlockPos pos, int amount) 
    {
        Boolean corruptionDisabled = Config.corruptionDisabled.get();

        // Global override to corruption system checked here.
        if (corruptionDisabled != null && corruptionDisabled) return stageForLevel(level);

        SalvationSavedData salvationData = SalvationSavedData.get(level);

        TraceUtils.dynamicTrace(ModCommands.TRACE_CORRUPTION, () -> LOGGER.info("Recording corruption from {} at {}: {}", source, pos, amount));

        if (amount == 0) return stageForLevel(level);

        int purification = 0;
        int corruption = 0;

        if (amount < 0)
        {
            purification = -amount;
        }
        else
        {
            corruption = amount;
        }

        salvationData.addProgress(source, amount);

        // If no position provided, just return the current stage after applying the global progress.
        if (pos == null) return stageForLevel(level);

        if (purification > 0)
        {
            int localPurification = purification;
            TraceUtils.dynamicTrace(ModCommands.TRACE_CORRUPTION, () -> LOGGER.info("Recording {} purification from {} at {}.", localPurification, source, pos));

            ChunkCorruptionSystem.onPurifyingAction(level, pos, purification);
            purificationEffect(level, pos, purification);

            IColony colony = IColonyManager.getInstance().getIColony(level, pos);
            if (colony != null)
            {
                SalvationColonyHandler handler = SalvationColonyHandler.getHandler(level, colony);
                handler.addPurificationCredits(purification);
            }
        }

        if (corruption > 0)
        {
            int localCorruption = corruption;
            TraceUtils.dynamicTrace(ModCommands.TRACE_CORRUPTION, () -> LOGGER.info("Recording {} corruption from {} at {}.", localCorruption, source, pos));
            ChunkCorruptionSystem.onCorruptingAction(level, pos, corruption);
            corruptionEffect(level, pos, source, corruption);
        }

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

    /**
     * Play a visual effect centered on the given position in the world.
     * The type and magnitude of the effect depend on the given source and magnitude.
     * The effect is played only on the server side.
     * 
     * @param level the level to play the effect in
     * @param pos the position to center the effect on
     * @param source the source of the corruption (COLONY, CONSTRUCTION, DEFAULT, HUNTING, FUEL, MINING)
     * @param magnitude the magnitude of the effect (1 - 10)
     */
    public static void corruptionEffect(final Level level, BlockPos pos, ProgressionSource source, final int magnitude)
    {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        SimpleParticleType particleType = null;

        switch (source)
        {
            case COLONY:
                particleType = null;
                break;
            case CONSTRUCTION:
                particleType = ParticleTypes.MYCELIUM;
                break;
            case DEFAULT:
                particleType = null;
                break;
            case ANIMALS:
                particleType = ParticleTypes.SOUL;
                break;
            case FUEL:
                particleType = ParticleTypes.POOF;
                break;
            case RESOURCEGATHERING:
                particleType = ParticleTypes.MYCELIUM;
                break;
        }

        if (particleType == null)
            return;

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.8;
        double z = pos.getZ() + 0.5;
        double offset = 0.45;
        int particleCount = 3 * magnitude;

        // --- Base white poof (vanilla look) ---
        serverLevel.sendParticles(
            particleType,
            x, y, z,
            particleCount,              // count
            offset, offset, offset,    // spread
            0.01                // speed
        );

        double tintOffset = offset * 1.10;
        int tintParticleCount = 2 * particleCount;

        // --- Sickly green haze overlay ---
        // ENTITY_EFFECT supports tinting via RGB
        serverLevel.sendParticles(
            new DustParticleOptions(
                new Vector3f(0.55f, 0.85f, 0.35f), // sickly green
                1.0f                                // scale
            ),
            x, y + 0.15, z,                      // slightly above the poof center
            tintParticleCount,                   // count (denser than poof)
            tintOffset, tintOffset, tintOffset, // tighter spread
            0.02                         // gentle outward motion
        );
    }


    /**
     * Visual effect for when a block is purified.
     *
     * @param level Level to render the effect in.
     * @param pos Position of the block to render the effect at.
     * @param magnitude Magnitude of the effect (affects particle count and spread).
     */
    public static void purificationEffect(final Level level, BlockPos pos, final int magnitude)
    {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        SimpleParticleType particleType = ParticleTypes.END_ROD;

        if (particleType == null)
            return;

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.8;
        double z = pos.getZ() + 0.5;
        double offset = 0.45;
        int particleCount = 3 * magnitude;

        // --- Base white poof (vanilla look) ---
        serverLevel.sendParticles(
            particleType,
            x, y, z,
            particleCount,              // count
            offset, offset, offset,    // spread
            0.01                // speed
        );
    }

    /**
     * Apply the corruption ward effect to the given initial corruption value.
     *
     * This method takes into account the held item in the player's main hand and
     * applies the corrosion ward effect to the initial corruption value. If the
     * scaled value is between 0 and 1, it is clamped to 1. Otherwise, the
     * scaled value is floored to the nearest integer.
     *
     * @param initialCorruption The initial corruption value to apply the ward to.
     * @param player The player to check the main hand of.
     * @return The warded corruption value.
     */
    static protected int applyWard(final int initialCorruption, @Nullable LivingEntity source)
    {
        if (source == null)
        {
            return initialCorruption;
        }

        int wardedCorruption = initialCorruption;
        ItemStack heldItem = source.getMainHandItem();

        TraceUtils.dynamicTrace(ModCommands.TRACE_CORRUPTION, () -> LOGGER.info("{} corruption and non-null player holding {}.", initialCorruption, heldItem));

        final double wardEffect = wardEffect(source);
        final double scaled = (double) initialCorruption * (1 - wardEffect);

        TraceUtils.dynamicTrace(ModCommands.TRACE_CORRUPTION, () -> LOGGER.info("Pre-clamp scaling. Ward effect: {}, scaled: {}.", wardEffect, scaled)); 

        if (wardEffect < 1.0 && scaled > 0.0 && scaled < 1.0) 
        {
            wardedCorruption = 1;
        } 
        else 
        {
            wardedCorruption = (int) Math.floor(scaled);
        }

        return wardedCorruption;
    }

    /**
     * Computes the corruption ward reduction effect from the given source.
     * This effect is based on the held item in the source's main hand and
     * is calculated as follows: 1->0.8, 2->0.6, 3->0.4, 4->0.2, 5->0.0.
     * If the source is null or the held item is empty or does not have the
     * corruption ward enchantment, this method returns 0.0.
     *
     * @param source the source to compute the ward effect for
     * @return the corruption ward reduction effect for the given source
     */
    static public float wardEffect(@Nullable LivingEntity source)
    {
        if (source == null)
        {
            return 0.0f;
        }

        ItemStack heldItem = source.getMainHandItem();

        if (heldItem == null || heldItem.isEmpty())
        {
            return 0.0f;
        }

        final float wardEffect = Mth.clamp(ModEnchantments.getCorruptionWardReduction(source.level(), heldItem), 0.0f, 1.0f);

        return wardEffect;
    }
}