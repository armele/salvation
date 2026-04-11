package com.deathfrog.salvationmod.core.engine;

import java.util.Optional;
import javax.annotation.Nonnull;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModAttachments;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.ModDimensions;
import com.deathfrog.salvationmod.ModEntityTypes;
import com.deathfrog.salvationmod.ModEnchantments;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.blocks.PurifyingFurnace;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.deathfrog.salvationmod.core.portal.ExteritioBossStructureManager;
import com.deathfrog.salvationmod.entity.CorruptionDamage;
import com.deathfrog.salvationmod.utils.ArmorUtils;
import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent;
import com.mojang.logging.LogUtils;
import com.deathfrog.salvationmod.core.entity.ai.workers.minimal.EntityAIRefugeeWanderTask;

@EventBusSubscriber(modid = SalvationMod.MODID)
public class SalvationEventListener 
{
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final float PURIFIED_ARMOR_CORRUPTION_REDUCTION_PER_PIECE = 0.10F;
    private static final float UNSTABLE_ARMOR_CORRUPTION_VULNERABILITY_PER_PIECE = 0.10F;
    private static final float UNSTABLE_ARMOR_BACKLASH_CHANCE_PER_PIECE = 0.08F;
    private static final float UNSTABLE_ARMOR_BACKLASH_DAMAGE = 1.0F;

    /**
     * Called when an entity is about to spawn and needs to be checked against the spawn placement rules.
     * This function is used to enforce the spawn override rules for corrupted and voraxian entities.
     * The spawn override rules are based on the corruption progression and level of light.
     * If the entity is not a corrupted or voraxian entity, the function does nothing.
     * @param event the spawn placement check event to apply the rules to
     */
    @SubscribeEvent
    public static void onSpawnPlacementCheck(final MobSpawnEvent.SpawnPlacementCheck event)
    {
        EntityType<?> entityType = event.getEntityType();

        // Only intervene for our entities; everyone else stays vanilla/default.
        if (SalvationManager.isCorruptedEntity(entityType) || SalvationManager.isVoraxian(entityType))
        {
            SalvationManager.enforceSpawnOverride(event);
        }
    }

    /**
     * Called when an entity is spawned and finalized.
     * This function is used to apply the corruption rules to the entity.
     * It checks if the entity is corruptible and if so, applies the corruption rules.
     * The corruption rules are based on the corruption progression and level of light.
     * 
     * @param event the finalize spawn event to apply the rules to
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(final FinalizeSpawnEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel serverLevel))
            return;

        final Mob mob = event.getEntity();

        // Don’t recurse / double-corrupt
        if (SalvationManager.isCorruptedEntity(mob.getType()))
        {
            if (!allowCorruptedFinalizeSpawn(serverLevel, mob, event.getSpawnType()))
            {
                event.setSpawnCancelled(true);
                mob.discard();
            }
            return;
        }

        if ( SalvationManager.isCorruptableEntity(mob.getType()))
        {
            SalvationManager.corruptOnSpawn(event, mob);
        }
    }

    /**
     * Checks if a corrupted mob can finalize its spawn at the given position.
     * The rules are as follows:
     * <ul>
     *     <li>Exteritio dimension: always allow</li>
     *     <li>Spawner or command spawn type: always allow</li>
     *     <li>Any other spawn type: only allow if the level is not at the untriggered corruption stage and the position is allowed for corrupted spawns</li>
     * </ul>
     *
     * @param level the level the mob is spawned in
     * @param mob the mob to check
     * @param spawnType the spawn type of the mob
     * @return true if the mob can finalize its spawn, false otherwise
     */
    private static boolean allowCorruptedFinalizeSpawn(
        @Nonnull final ServerLevel level,
        @Nonnull final Mob mob,
        final MobSpawnType spawnType)
    {
        if (level.dimension() == ModDimensions.EXTERITIO)
        {
            return true;
        }

        if (spawnType != null && (MobSpawnType.isSpawner(spawnType) || spawnType == MobSpawnType.COMMAND))
        {
            return true;
        }

        final BlockPos pos = mob.blockPosition();
        if (pos == null)
        {
            return false;
        }

        if (SalvationManager.stageForLevel(level) == CorruptionStage.STAGE_0_UNTRIGGERED)
        {
            return false;
        }

        return SalvationManager.isCorruptedSpawnAllowed(level, pos);
    }

    /**
     * This method is called once per server load by the EventBus and is responsible for registering
     * all the corrupted mobs for spawning.
     * 
     * It is a static method and is called automatically by the EventBus.
     * 
     * @param event The RegisterSpawnPlacementsEvent that triggered this method.
     */
    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRegisterSpawnPlacements(final RegisterSpawnPlacementsEvent event)
    {
        registerCorruptedGroundMonster(event, ModEntityTypes.CORRUPTED_COW.get());
        registerCorruptedGroundMonster(event, ModEntityTypes.CORRUPTED_SHEEP.get());
        registerCorruptedGroundMonster(event, ModEntityTypes.CORRUPTED_CHICKEN.get());
        registerCorruptedGroundMonster(event, ModEntityTypes.CORRUPTED_PIG.get());
        registerCorruptedGroundMonster(event, ModEntityTypes.CORRUPTED_CAT.get());
        registerCorruptedGroundMonster(event, ModEntityTypes.CORRUPTED_FOX.get());
        registerCorruptedGroundMonster(event, ModEntityTypes.CORRUPTED_POLARBEAR.get());
        registerMonster(event, ModEntityTypes.VORAXIAN_OBSERVER.get());
        registerMonster(event, ModEntityTypes.VORAXIAN_MAW.get());
        registerGroundMonster(event, ModEntityTypes.VORAXIAN_STINGER.get());
        registerMonster(event, ModEntityTypes.VORAXIAN_OVERLORD.get());
    }

    /**
     * Registers a monster type for spawning with the given spawn placement rules.
     * <p>
     * This method is a convenience wrapper around {@link RegisterSpawnPlacementsEvent#register}.
     * <p>
     * @param event The event to register the type with.
     * @param type The type of monster to register.
     */
    private static <T extends Monster> void registerMonster(final RegisterSpawnPlacementsEvent event, @Nonnull final EntityType<T> type)
    {
        event.register(
            type,
            NullnessBridge.assumeNonnull(SpawnPlacementTypes.NO_RESTRICTIONS),
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkMonsterSpawnRules,
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
    }

    /**
     * Registers a ground-dwelling monster type with the given spawn placement rules.
     * This method is a convenience wrapper around {@link RegisterSpawnPlacementsEvent#register}.
     * @param event The event to register the type with.
     * @param type The type of monster to register.
     */
    private static <T extends Monster> void registerGroundMonster(final RegisterSpawnPlacementsEvent event, @Nonnull final EntityType<T> type)
    {
        event.register(
            type,
            NullnessBridge.assumeNonnull(SpawnPlacementTypes.ON_GROUND),
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkMonsterSpawnRules,
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
    }

    /**
     * Registers a corrupted ground-dwelling monster while preserving Salvation's corruption-stage spawn gates.
     * @param event The event to register the type with.
     * @param type The corrupted monster type.
     */
    @SuppressWarnings("null")
    private static <T extends Monster> void registerCorruptedGroundMonster(final RegisterSpawnPlacementsEvent event, @Nonnull final EntityType<T> type)
    {
        event.register(
            type,
            NullnessBridge.assumeNonnull(SpawnPlacementTypes.ON_GROUND),
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (entityType, level, spawnType, pos, random) -> Monster.checkMonsterSpawnRules(entityType, level, spawnType, pos, random)
                && (level.getLevel().dimension() == ModDimensions.EXTERITIO || SalvationManager.isCorruptedSpawnAllowed(level.getLevel(), pos)),
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
    }

    /**
     * This method is called every tick by the EventBus and is responsible for 
     * running the salvation logic for all levels when needed.
     * 
     * It is a static method and is called automatically by the EventBus.
     * 
     * @param event The ServerTickEvent that triggered this method.
     */
    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final MinecraftServer server = event.getServer();
        final ServerLevel overworld = server.overworld();
        final long gameTime = overworld.getGameTime();

        // Furnace polling cadence (e.g., every 3 ticks ~ 6-7 times/sec)
        final boolean doFurnacePoll = (gameTime % 3L) == 0L;

        // Salvation cadence (every 18 ticks ~ 1.11 times/sec)
        final boolean doSalvation = (gameTime % 18L) == 0L;

        if (!doFurnacePoll && !doSalvation) return;

        for (final ServerLevel level : server.getAllLevels())
        {
            if (doFurnacePoll)
            {
                FurnaceCookLedgerTracker.poll(level);
            }

            if (doSalvation)
            {
                SalvationManager.salvationLogicLoop(level);

                if (level.dimension() == ModDimensions.EXTERITIO)
                {
                    ExteritioBossStructureManager.ensureSpawned(level);
                }
            }
        }
    }

    /**
     * This method is called by the EventBus whenever a LivingEntity dies.
     * It is responsible for adding progression to the salvation logic
     * whenever a certain type of entity dies.
     *
     * @param event The LivingDeathEvent that triggered this method.
     */
    @SubscribeEvent
    public static void onLivingDeath(final LivingDeathEvent event)
    {
        // Only meaningful server-side.
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;

        final LivingEntity dead = event.getEntity();
        if (dead.getType() == ModEntityTypes.VORAXIAN_OVERLORD.get())
        {
            ExteritioBossStructureManager.onOverlordSlain(serverLevel);
        }

        final DamageSource source = event.getSource();
        final BlockPos pos = dead.blockPosition();

        // Only meaningful if killed by player or citizen
        final Entity killer = source.getEntity();
        if (!(killer instanceof Player || killer instanceof AbstractEntityCitizen)) return;

        SalvationManager.applyMobProgression(dead, pos, (LivingEntity) killer);
    }

    /**
     * Called by the EventBus whenever a living entity takes damage.
     * This function is used to reduce the damage taken by a living entity
     * if they are wearing purified armor pieces.
     * The amount of reduction is directly proportional to the number of
     * purified armor pieces worn.
     * 
     * @param event The LivingIncomingDamageEvent that triggered this method.
     */
    @SubscribeEvent
    public static void onLivingIncomingDamage(final LivingIncomingDamageEvent event)
    {
        final DamageSource source = event.getSource();
        
        if (source == null)
        {
            return;
        }

        final LivingEntity entity = event.getEntity();

        if (!(entity.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        final int unstableArmorPieces = SalvationManager.unstableArmorPieces(entity);

        if (CorruptionDamage.isCorruptionDamage(source))
        {
            final int purifiedArmorPieces = ArmorUtils.countTaggedArmorPieces(event.getEntity(), ModTags.Items.PURIFIED_ARMOR);
            float multiplier = 1.0F;

            if (purifiedArmorPieces > 0)
            {
                multiplier -= purifiedArmorPieces * PURIFIED_ARMOR_CORRUPTION_REDUCTION_PER_PIECE;
            }

            if (unstableArmorPieces > 0)
            {
                multiplier += unstableArmorPieces * UNSTABLE_ARMOR_CORRUPTION_VULNERABILITY_PER_PIECE;
            }

            event.setAmount(event.getAmount() * Math.max(0.0F, multiplier));
            return;
        }

        applyCorruptionDisruptionBonus(event, source, entity);
        tryTriggerFightBackCorruption(event, source, entity, serverLevel);

        if (unstableArmorPieces <= 0)
        {
            return;
        }

        final float backlashChance = Math.min(0.50F, unstableArmorPieces * UNSTABLE_ARMOR_BACKLASH_CHANCE_PER_PIECE);
        if (entity.getRandom().nextFloat() < backlashChance)
        {
            DamageSource backlash = CorruptionDamage.source(serverLevel);

            if (backlash == null)
            {
                return;
            }

            entity.hurt(backlash, UNSTABLE_ARMOR_BACKLASH_DAMAGE);
        }
    }

    private static void applyCorruptionDisruptionBonus(
        final LivingIncomingDamageEvent event,
        final DamageSource source,
        final LivingEntity target)
    {
        if (!target.getType().is(ModTags.Entities.CORRUPTED_ENTITY))
        {
            return;
        }

        if (!(source.getEntity() instanceof LivingEntity attacker))
        {
            return;
        }

        if (source.getDirectEntity() != attacker)
        {
            return;
        }

        final ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty())
        {
            return;
        }

        final float multiplier = ModEnchantments.getCorruptionDisruptionDamageMultiplier(attacker.level(), weapon);
        if (multiplier <= 1.0F)
        {
            return;
        }

        event.setAmount(event.getAmount() * multiplier);
    }

    /**
     * Attempts to trigger a fight back corruption on the given target.
     * This will start a retaliation conversion on the target, if the target is corruptible, the source entity is a living entity, and the chance to trigger a fight back corruption is greater than 0.
     *
     * @param event the event to trigger the fight back corruption from
     * @param source the source of the damage that triggered the fight back corruption
     * @param target the target of the fight back corruption
     * @param level the level that the target is in
     */
    private static void tryTriggerFightBackCorruption(
        final LivingIncomingDamageEvent event,
        final DamageSource source,
        final LivingEntity target,
        final ServerLevel level)
    {
        if (level == null || event.getAmount() <= 0.0F)
        {
            return;
        }

        if (target.getType().is(ModTags.Entities.CORRUPTED_ENTITY) || !SalvationManager.isCorruptableEntity(target.getType()))
        {
            return;
        }

        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == target)
        {
            return;
        }

        final AttachmentType<ModAttachments.ConversionData> attachmentType = ModAttachments.CONVERSION.get();
        if (attachmentType == null)
        {
            return;
        }

        final ModAttachments.ConversionData existing = target.getData(attachmentType);
        if (existing != null && existing.ticksRemaining() > 0)
        {
            return;
        }

        final CorruptionStage stage = SalvationManager.stageForLevel(level);
        final float chance = stage.getFightBackCorruptionChance();
        if (chance <= 0.0F || level.random.nextFloat() >= chance)
        {
            return;
        }

        EntityConversion.startRetaliationCorruption(level, target, attacker);
    }

    /**
     * This method is called by the EventBus whenever a block is broken.
     * It is responsible for adding progression to the salvation logic
     * whenever a certain type of block is broken.
     * 
     * @param event The BlockEvent.BreakEvent that triggered this method.
     */
    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;

        final BlockState state = event.getState();
        final BlockPos pos = event.getPos();

        if (state == null || pos == null) return; 

        // IDEA: (Phase 2) Research to create "safe" tools for breaking blocks.
        Player player = event.getPlayer();

        SalvationManager.applyBlockBreakProgression(level, state, pos, player);
    }


    /**
     * This method is called by the EventBus whenever a block is placed.
     * It is responsible for adding progression to the salvation logic
     * whenever a certain type of block is placed.
     * 
     * @param event The BlockEvent.EntityPlaceEvent that triggered this method.
     */
    @SubscribeEvent
    public static void onBlockPlace(final BlockEvent.EntityPlaceEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;

        final BlockState state = event.getState();
        final BlockPos pos = event.getPos();

        if (state == null || pos == null) return; 

        SalvationManager.applyBlockPlaceProgression(level, state, pos);

        if (isConvertibleSapling(state))
        {
            tryCorruptPlacedSapling(level, pos, state);
        }
    }

    /**
     * Attempts to corrupt a placed sapling into blightwood.
     * 
     * Checks if the placed block is a convertible sapling, then checks if the corruption chance is above 0.
     * If both conditions are met, it attempts to corrupt the sapling into blightwood.
     * If the corruption chance fails, it does nothing.
     * If the corruption succeeds, it sets the block at the given position to blightwood and applies a corruption effect at the position.
     * 
     * @param level The server level that the sapling was placed in.
     * @param pos The position of the placed sapling.
     * @param state The block state of the placed sapling.
     */    
    private static void tryCorruptPlacedSapling(final @Nonnull ServerLevel level, final @Nonnull BlockPos pos, final @Nonnull BlockState state)
    {
        final float chance = SalvationManager.locationCorruptionChance(level, pos);
        if (chance <= 0.0F)
        {
            return;
        }

        if (level.random.nextFloat() >= chance)
        {
            return;
        }

        if (!state.hasProperty(NullnessBridge.assumeNonnull(SaplingBlock.STAGE)))
        {
            return;
        }

        final int saplingStage = state.getValue(NullnessBridge.assumeNonnull(SaplingBlock.STAGE));

        final BlockState blightwoodState = ModBlocks.BLIGHTWOOD_SAPLING.get()
            .defaultBlockState()
            .setValue(NullnessBridge.assumeNonnull(SaplingBlock.STAGE), saplingStage);

        if (blightwoodState == null)
        {
            return;
        }

        level.setBlock(pos, blightwoodState, 3);
        SalvationManager.corruptionEffect(level, pos, ProgressionSource.CONSTRUCTION, 1);
    }

    /**
     * Checks if the given block state is a convertible sapling.
     * 
     * A convertible sapling is one that can be corrupted into blightwood.
     * A sapling is convertible if it is a sapling block and it is not already blightwood.
     * Additionally, the block state must also match the SAPLINGS block tag.
     * 
     * @param state The block state to check.
     * @return true if the block state is a convertible sapling, false otherwise.
     */
    private static boolean isConvertibleSapling(final BlockState state)
    {
        if (state.is(NullnessBridge.assumeNonnull(ModBlocks.BLIGHTWOOD_SAPLING.get())))
        {
            return false;
        }

        return state.is(NullnessBridge.assumeNonnull(BlockTags.SAPLINGS));
    }

    /**
     * Called every tick on every living entity in the world.
     * If the entity has a {@link CleansingData} attachment, this method will decrement the remaining ticks and update the entity's data.
     * If the remaining ticks reaches 0, this method will call {@link EntityConversion#finishCleansing(ServerLevel, LivingEntity)} to finish the cleansing process.
     * <p>This method will also play visual effects every 5 ticks to give a "ticking" feeling to the player.
     * <p>This method will not do anything if the entity does not have a {@link CleansingData} attachment.
     */
    @SubscribeEvent
    public static void onLivingTick(final EntityTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof LivingEntity entity))
            return;

        if (!(entity.level() instanceof ServerLevel level))
            return;

        if (entity instanceof AbstractEntityCitizen citizen)
        {
            EntityAIRefugeeWanderTask.tryRehydrate(citizen);
        }

        final AttachmentType<ModAttachments.ConversionData> attachmentType = ModAttachments.CONVERSION.get();

        if (attachmentType == null)
        {
            throw new IllegalStateException("Failed to get CleansingData attachment type.");
        }

        final ModAttachments.ConversionData data = entity.getData(attachmentType);

        if (data == null || data.ticksRemaining() <= 0) return;

        boolean isCleansing = data.isCleansing();

        final int remaining = data.ticksRemaining() - 1;
        entity.setData(
            attachmentType,
            new ModAttachments.ConversionData(
                remaining,
                isCleansing,
                data.sourcePlayerUuid(),
                data.retaliationTargetUuid(),
                data.preserveSourceHealth()
            )
        );

        // Finished cleansing → convert (do this BEFORE TICK FX)
        if (remaining <= 0)
        {
            EntityConversion.finishConversion(level, entity, isCleansing);
            return;
        }   

        // Visual feedback every 5 ticks (while still cleansing)
        if ((entity.tickCount % 5) == 0)
        {
            EntityConversion.playConversionEffects(level, entity, EntityConversion.ConversionFxPhase.TICK, isCleansing);
        }
    }

    /**
     * Called when an item is extracted from a furnace.
     * This is registered to listen to the FurnaceCookLedgerTrcker.
     * 
     * @param level The level the furnace is in.
     * @param pos The position of the furnace.
     * @param colonyAnchor The position of the colony anchor, if any.
     * @param extractedStack The item that was extracted.
     * @param extractedCount The count of the item that was extracted.
     * @param fuelPoints The amount of fuel points used.
     * @param recipeType The type of recipe that was used.
     * @param recipeId The ID of the recipe that was used.
     */
    public static void onCookOutputExtracted(@Nonnull ServerLevel level, BlockPos pos, ItemStack extractedStack, int extractedCount, int fuelPoints, final RecipeType<?> recipeType, final Optional<ResourceLocation> recipeId)
    {
        // No-op.  Leaving the stub for potential future functionality.
    }

    public static void onCookingComplete(@Nonnull ServerLevel level, @Nonnull BlockPos pos, ItemStack cookedOutput, int craftsCompleted, int fuelPoints, ItemStack fuelSnapshot, final RecipeType<?> recipeType, final Optional<ResourceLocation> recipeId)
    {
        // LOGGER.info("On Cooking Complete: Pos: {} Cooked: {} Crafts Completed: {} fuelPoints: {} fuelSnapshot: {} recipeType: {} recipeId: {}", pos, cookedOutput, craftsCompleted, fuelPoints, fuelSnapshot, recipeType, recipeId);

        ItemStorage output = new ItemStorage(cookedOutput, craftsCompleted);
        ItemStorage fuel = new ItemStorage(fuelSnapshot, fuelPoints);
        final float corruptionMultiplier = level.getBlockState(pos).getBlock() instanceof PurifyingFurnace ? 0.8F : 1.0F;

        SalvationManager.applySmeltingProgression(level, pos, output, fuel, corruptionMultiplier);
    }

    /**
     * Called when a citizen is recruited to the colony.
     * @param event the citizen added event.
     */
    public static void onCitizenAdded(final CitizenAddedModEvent event)
    {
        if (event.getSource() == CitizenAddedModEvent.CitizenAddedSource.HIRED)
        {
            // Significant bump in purification for taking in refugees
            int purification = 144;

            ICitizen citizen = event.getCitizen();
            IColony colony = event.getColony();
            
            if (colony != null && (colony.getWorld() instanceof ServerLevel serverLevel))
            {
                ICitizenData data = colony.getCitizenManager().getCivilian(citizen.getId());

                SalvationManager.recordCorruption(serverLevel, ProgressionSource.COLONY, data.getLastPosition(), -purification);
            }
        }
    }
}
