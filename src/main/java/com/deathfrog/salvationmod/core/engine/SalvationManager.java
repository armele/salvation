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
import com.deathfrog.salvationmod.ModDimensions;
import com.deathfrog.salvationmod.ModEnchantments;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.deathfrog.salvationmod.entity.CorruptionDamage;
import com.deathfrog.salvationmod.utils.ArmorUtils;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.crafting.ItemStorage;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
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
    private static final int UNSTABLE_USE_CORRUPTION_SURCHARGE = 2;
    private static final float UNSTABLE_TOOL_BACKLASH_CHANCE = 0.12F;
    private static final float UNSTABLE_TOOL_BACKLASH_DAMAGE = 1.0F;
    private static final String DOWNWARD_TRANSITION_MESSAGE_KEY = "message.salvation.corruption.stage_downward";

    // Percent chance that a notification will be sent
    protected final static int WORLD_NOTIFICATION_CHANCE = 15;

    // Minimum time between notifications, extended by 50% to reduce overlap with colony messaging.
    protected final static int WORLD_NOTIFICATION_COOLDOWN = 20 * 60 * Config.globalNotificationCooldown.get();

    // Prefix for flavor messages
    protected final static String FLAVORMESSAGE_PREFIX = "com.salvation.flavormessage.stage";

    // Last time a notification was sent
    protected static long lastNotificationGameTime = 0L;

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

        processNotifications(level);
    }


    /**
     * Process notifications for the given level.
     * This method is responsible for sending out messages to all players in the overworld
     * at a random interval to inform them of the current state of the Salvation line.
     * Messages are chosen randomly from a list of 10 for each stage of the Salvation line.
     * Notifications are only sent out if the current game time is greater than the last notification game time plus a coldown period.
     * @param level the level to process notifications for
     */
    private static void processNotifications(ServerLevel level) 
    {
        if (level == null || level.isClientSide()) return;

        if (level.dimension() != Level.OVERWORLD) return;

        RandomSource random = level.getRandom();
        long gameTime = level.getGameTime();

        if (WORLD_NOTIFICATION_COOLDOWN <= 0) return;

        if (gameTime < lastNotificationGameTime + WORLD_NOTIFICATION_COOLDOWN) return;

        if (random.nextInt(100) <= WORLD_NOTIFICATION_CHANCE) 
        {   
            CorruptionStage stage = SalvationManager.stageForLevel(level);
            int notificationStage = stage.ordinal();
            int notificationNumber = random.nextInt(10);

            lastNotificationGameTime = gameTime;
            Component message = Component.translatable(FLAVORMESSAGE_PREFIX + notificationStage + "." + notificationNumber);

            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers())
            {
                if (message == null) continue;
                if (player.level().dimension() != Level.OVERWORLD) continue;
                
                player.sendSystemMessage(message);
            }
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
     * Checks if the given entity is a Voraxian entity
     * 
     * @param entityType The entity type to check.
     * @return true if the entity is a Voraxian entity, false otherwise.
     */
    public static boolean isVoraxian(EntityType<?> entityType)
    {
        return entityType.is(ModTags.Entities.VORAXIAN);
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

        if (purification > 0 && source != null)
        {
            purification = applyPurificationBonus(purification, source);
        }

        if (source != null)
        {
            corruption += unstableUseCorruptionSurcharge(source);
            tryApplyUnstableToolBacklash(source);
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

        if (purification > 0 && source != null)
        {
            purification = applyPurificationBonus(purification, source);
        }

        if (source != null)
        {
            corruption += unstableUseCorruptionSurcharge(source);
            tryApplyUnstableToolBacklash(source);
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
    public static CorruptionStage applySmeltingProgression(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull ItemStorage cookedOutput, @Nonnull ItemStorage fuel)
    {
        return applySmeltingProgression(level, pos, cookedOutput, fuel, 1.0F, 0.0D, 0.0D);
    }

    public static CorruptionStage applySmeltingProgression(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull ItemStorage cookedOutput, @Nonnull ItemStorage fuel, final float corruptionMultiplier)
    {
        return applySmeltingProgression(level, pos, cookedOutput, fuel, corruptionMultiplier, 0.0D, 0.0D);
    }

    public static CorruptionStage applySmeltingProgression(@Nonnull ServerLevel level,
                                                           @Nonnull BlockPos pos,
                                                           @Nonnull ItemStorage cookedOutput,
                                                           @Nonnull ItemStorage fuel,
                                                           final float corruptionMultiplier,
                                                           final double machineCorruptionPer1000,
                                                           final double machinePurificationPer1000)
    {
        int fuelPoints = fuel.getAmount();
        ItemStack fuelItem = fuel.getItemStack();

        if (fuelItem.isEmpty() && machineCorruptionPer1000 == 0.0D && machinePurificationPer1000 == 0.0D)
        {
            return stageForLevel(level);
        }

        CorruptionStage stage = calculateFuelCorruption(level, pos, fuelItem, fuelPoints, cookedOutput, corruptionMultiplier, machineCorruptionPer1000, machinePurificationPer1000);
        stage = calculateSmeltedItemCorruption(level, pos, cookedOutput, corruptionMultiplier);

        return stage;
    }

    protected static CorruptionStage calculateFuelCorruption(@Nonnull ServerLevel level,
                                                             @Nonnull BlockPos pos,
                                                             ItemStack fuelItem,
                                                             int fuelPoints,
                                                             ItemStorage cookedOutput,
                                                             final float corruptionMultiplier,
                                                             final double machineCorruptionPer1000,
                                                             final double machinePurificationPer1000)
    {
        double corruption = corruptionFromFuel(fuelItem);
        double purification = purificationFromFuel(fuelItem);

        corruption += machineCorruptionPer1000;
        purification += machinePurificationPer1000;

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

        corruption *= corruptionMultiplier;

        corruption = corruption - purification;

        if (corruption > 0 && corruption < 1.0) corruption = 1.0;
        if (corruption < 0 && corruption > -1.0) corruption = -1.0;

        CorruptionStage stage = recordCorruption(level, ProgressionSource.FUEL, pos, (int) corruption);

        return stage;
    }

    /**
     * Compute the amount of corruption to apply based on the fuel item stack's tag membership.
     * 
     * @param fuelItem the fuel item stack to compute the purification from
     * @return the amount of purification to apply to the chunk (0 - {@link #PURIFICATION_MAX})
     */
    protected static int corruptionFromFuel(ItemStack fuelItem)
    {
        int corruption = 0;

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

        return corruption;
    }


    /**
     * Compute the amount of purification to apply based on the fuel item stack's tag membership.
     * 
     * @param fuelItem the fuel item stack to compute the purification from
     * @return the amount of purification to apply to the chunk (0 - {@link #PURIFICATION_MAX})
     */
    protected static int purificationFromFuel(ItemStack fuelItem)
    {
        int purification = 0;

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

        return purification;
    }

    /**
     * Compute the amount of corruption to apply to the chunk centered on the given smelted item stack.
     * The amount of corruption is based on the smelted item stack's tag membership.
     * The amount of purification is also based on the smelted item stack's tag membership.
     * The final corruption amount is the corruption amount minus the purification amount.
     * If the final corruption amount is between 0 and 1, it is rounded up to 1.
     * If the final corruption amount is between -1 and 0, it is rounded up to -1.
     * The computed corruption amount is then recorded in the chunk's corruption stage data.
     * 
     * @param level The server level that the smelted item was produced in.
     * @param pos The position of the smelted item.
     * @param cookedOutput The smelted item stack to compute the corruption from.
     * @return the computed corruption stage of the chunk.
    */    
    protected static CorruptionStage calculateSmeltedItemCorruption(@Nonnull ServerLevel level, @Nonnull BlockPos pos, ItemStorage cookedOutput, final float corruptionMultiplier)
    {
        ItemStack smeltedItem = cookedOutput.getItemStack();
        double corruption = corruptionFromSmeltedItem(smeltedItem);
        double purification = purificationFromSmeltedItem(smeltedItem);

        corruption *= corruptionMultiplier;
        corruption = corruption - purification;

        if (corruption > 0 && corruption < 1.0) corruption = 1.0;
        if (corruption < 0 && corruption > -1.0) corruption = -1.0;

        CorruptionStage stage = recordCorruption(level, ProgressionSource.SMELTING, pos, (int) corruption);

        return stage;
    }

    /**
     * Compute the amount of corruption to apply based on the smelted item stack's tag membership.
     * 
     * @param smeltedItem the smelted item stack to compute the purification from
     * @return the amount of purification to apply to the chunk (0 - {@link #PURIFICATION_MAX})
     */
    protected static int corruptionFromSmeltedItem(ItemStack smeltedItem)
    {
        int corruption = 0;

        // Trival corruption-triggering fuel sources
        if (smeltedItem.is(ModTags.Items.CORRUPTED_ITEMS_COMMON))
        {
            corruption += 1;
        }

        // Minor corruption-triggering fuel sources
        if (smeltedItem.is(ModTags.Items.CORRUPTED_ITEMS_UNCOMMON))
        {
            corruption += 2;
        }

        // Stronger trigger fuel sources
        if (smeltedItem.is(ModTags.Items.CORRUPTED_ITEMS_RARE))
        {
            corruption += 5;
        }

        // Even stronger trigger fuel sources
        if (smeltedItem.is(ModTags.Items.CORRUPTED_ITEMS_EPIC))
        {
            corruption += 13;
        }

        return corruption;
    }


    /**
     * Compute the amount of purification to apply based on the smelted item stack's tag membership.
     * 
     * @param smeltedItem the smelted item stack to compute the purification from
     * @return the amount of purification to apply to the chunk (0 - {@link #PURIFICATION_MAX})
     */
    protected static int purificationFromSmeltedItem(ItemStack smeltedItem)
    {
        int purification = 0;

        // Trival purification-triggering output products
        if (smeltedItem.is(ModTags.Items.PURIFIED_ITEMS_COMMON))
        {
            purification += 1;
        }

        // Minor purification-triggering output products
        if (smeltedItem.is(ModTags.Items.PURIFIED_ITEMS_UNCOMMON))
        {
            purification += 2;
        }

        // Stronger trigger output products
        if (smeltedItem.is(ModTags.Items.PURIFIED_ITEMS_RARE))
        {
            purification += 5;
        }

        // Even stronger output products
        if (smeltedItem.is(ModTags.Items.PURIFIED_ITEMS_EPIC))
        {
            purification += 13;
        }

        return purification;
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
     * Returns the current corruption measure for the given level.
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

        // Only run for corrupted mobs or voraxians (safety even if caller forgets)
        if (!(isCorruptedEntity(event.getEntityType()) || isVoraxian(event.getEntityType())))
            return;

        // Exteritio uses its own authored biome spawn tables and should not be throttled
        // by overworld corruption progression, chunk corruption, or light restrictions.
        if (level.dimension() == ModDimensions.EXTERITIO)
        {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT);
            return;
        }

        MobSpawnType mobSpawnType = event.getSpawnType();

        if (mobSpawnType == null) return;

        if (MobSpawnType.isSpawner(mobSpawnType))
        {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
            return;
        }

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

        // The corruption replacement system is an overworld progression mechanic.
        // Exteritio should use its own native spawn tables without extra replacement rules.
        if (serverLevel.dimension() == ModDimensions.EXTERITIO)
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
        final CorruptionStage previousStage = stageForLevel(level);

        TraceUtils.dynamicTrace(ModCommands.TRACE_CORRUPTION, () -> LOGGER.info("Recording corruption from {} at {}: {}", source, pos, amount));

        if (amount == 0) return previousStage;

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
        final CorruptionStage currentStage = stageForLevel(level);
        salvationData.recordStageChange(previousStage, currentStage, level.getGameTime(), source, amount);
        broadcastStageTransition(level, previousStage, currentStage);

        // If no position provided, just return the current stage after applying the global progress.
        if (pos == null) return currentStage;

        if (purification > 0)
        {
            int localPurification = purification;
            TraceUtils.dynamicTrace(ModCommands.TRACE_CORRUPTION, () -> LOGGER.info("Recording {} purification from {} at {}.", localPurification, source, pos));

            ChunkCorruptionSystem.onPurifyingAction(level, pos, purification, source);
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
            ChunkCorruptionSystem.onCorruptingAction(level, pos, corruption, source);
            corruptionEffect(level, pos, source, corruption);

            IColony colony = IColonyManager.getInstance().getIColony(level, pos);
            if (colony != null)
            {
                SalvationColonyHandler handler = SalvationColonyHandler.getHandler(level, colony);
                handler.addCorruptionContribution(corruption);
            }
        }

        return currentStage;
    }


    /**
     * Broadcast a message to all players on the level about a corruption stage transition.
     * If the current stage is higher than the previous stage, the message is taken from the current stage's transition message key.
     * Otherwise, the message is taken from {@link com.deathfrog.salvationmod.core.engine.CorruptionStage#DOWNWARD_TRANSITION_MESSAGE_KEY}.
     * If the message is null (i.e. the stage transition does not have a message), nothing is broadcast.
     * The message is sent to all players on the level as a system message with red color, and a toast sound is played.
     * @param level the level to broadcast the message on
     * @param previousStage the previous corruption stage
     * @param currentStage the current corruption stage
     */
    @SuppressWarnings("null")
    private static void broadcastStageTransition(final ServerLevel level, final CorruptionStage previousStage, final CorruptionStage currentStage)
    {
        if (previousStage == currentStage)
        {
            return;
        }

        final Component message = currentStage.ordinal() > previousStage.ordinal()
            ? currentStage.getTransitionMessageKey().map(Component::translatable).orElse(null)
            : Component.translatable(DOWNWARD_TRANSITION_MESSAGE_KEY);

        if (message == null)
        {
            return;
        }

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers())
        {
            player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 0.85F);
        }
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
            case SMELTING:
                particleType = ParticleTypes.SCULK_SOUL;
                break;
            case FUEL:
                particleType = ParticleTypes.POOF;
                break;
            case EXTRACTION:
                particleType = ParticleTypes.ELECTRIC_SPARK;
                break;
            case SPREAD:
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

        // Even without a corruption ward there is a small beneficial impact.
        if (wardEffect == 0.0 && heldItem.is(ModTags.Items.PURIFIED_ITEMS))
        {
            wardedCorruption = wardedCorruption - 1;
        }

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
     * Applies the purification bonus effect to the given initial purification value.
     *
     * This method takes into account the held item in the player's main hand and
     * applies the purification bonus effect to the initial purification value. If the
     * held item is empty, this method returns the initial purification value. Otherwise,
     * If the held item is a purified item, the purification value is also boosted by one.
     *
     * @param initialPurification The initial purification value to apply the bonus to.
     * @param source The player to check the main hand of.
     * @return The boosted purification value.
     */    
    static protected int applyPurificationBonus(final int initialPurification, @Nullable LivingEntity source)
    {
        if (source == null || initialPurification == 0)
        {
            return initialPurification;
        }

        int boostedPurification = initialPurification;
        ItemStack heldItem = source.getMainHandItem();

        TraceUtils.dynamicTrace(ModCommands.TRACE_CORRUPTION, () -> LOGGER.info("{} purification and non-null player holding {}.", initialPurification, heldItem));

        // Using purified items is a small beneficial impact.
        if (heldItem.is(ModTags.Items.PURIFIED_ITEMS))
        {
            boostedPurification = boostedPurification + 1;
        }

        return boostedPurification;
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

    public static int unstableArmorPieces(@Nullable LivingEntity entity)
    {
        if (entity == null)
        {
            return 0;
        }

        return ArmorUtils.countTaggedArmorPieces(entity, ModTags.Items.CORRUPTED_ITEMS);
    }

    public static boolean isUsingUnstableMainHandItem(@Nullable LivingEntity entity)
    {
        if (entity == null)
        {
            return false;
        }

        final ItemStack heldItem = entity.getMainHandItem();
        return heldItem != null && heldItem.is(ModTags.Items.CORRUPTED_ITEMS);
    }

    private static int unstableUseCorruptionSurcharge(@Nullable LivingEntity source)
    {
        return isUsingUnstableMainHandItem(source) ? UNSTABLE_USE_CORRUPTION_SURCHARGE : 0;
    }

    /**
     * Attempts to apply the unstable tool backlash effect to the given source.
     * This method will only apply the effect if the source is not null, is not on the client side, and is using an unstable main hand item.
     * The effect has a chance of {@link #UNSTABLE_TOOL_BACKLASH_CHANCE} of being applied.
     * If the effect is applied, the source will be hurt for {@link #UNSTABLE_TOOL_BACKLASH_DAMAGE} damage.
     */
    private static void tryApplyUnstableToolBacklash(@Nullable LivingEntity source)
    {
        if (source == null || (!(source.level() instanceof ServerLevel severLevel) || source.level().isClientSide))
        {
            return;
        }
        
        if (!isUsingUnstableMainHandItem(source))
        {
            return;
        }

        if (source.getRandom().nextFloat() >= UNSTABLE_TOOL_BACKLASH_CHANCE)
        {
            return;
        }

        DamageSource backlash = CorruptionDamage.source(severLevel);

        if (backlash == null)
        {
            return;
        }

        source.hurt(backlash, UNSTABLE_TOOL_BACKLASH_DAMAGE);
    }

    /**
     * Computes the corruption chance for a given location.
     * This chance is the product of the global corruption stage and
     * the local spawn chance multiplier.
     * 
     * @param level the level to compute the corruption chance for
     * @param pos the position to compute the corruption chance at
     * @return the corruption chance for the given location
     */
    public static float locationCorruptionChance(@Nonnull ServerLevel level, @Nonnull BlockPos pos)
    {
        float chance = stageForLevel(level).getLootCorruptionChance();
        chance *= ChunkCorruptionSystem.spawnChanceMultiplier(level, pos);
        return Mth.clamp(chance, 0.0f, 1.0f);
    }

}
