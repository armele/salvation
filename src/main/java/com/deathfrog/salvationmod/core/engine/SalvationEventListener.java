package com.deathfrog.salvationmod.core.engine;

import java.util.Optional;
import javax.annotation.Nonnull;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModAttachments;
import com.deathfrog.salvationmod.ModEntityTypes;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent;
import com.mojang.logging.LogUtils;

@EventBusSubscriber(modid = SalvationMod.MODID)
public class SalvationEventListener 
{
    public static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onSpawnPlacementCheck(final MobSpawnEvent.SpawnPlacementCheck event)
    {
        EntityType<?> entityType = event.getEntityType();

        // Only intervene for our entities; everyone else stays vanilla/default.
        if (SalvationManager.isCorruptedEntity(entityType))
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
        if (!(event.getLevel() instanceof ServerLevel))
            return;

        final Mob mob = event.getEntity();

        // Don’t recurse / double-corrupt
        if (SalvationManager.isCorruptedEntity(mob.getType())) return;

        if ( SalvationManager.isCorruptableEntity(mob.getType()))
        {
            SalvationManager.corruptOnSpawn(event, mob);
        }
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRegisterSpawnPlacements(final RegisterSpawnPlacementsEvent event)
    {
        // If your corrupted mobs extend the vanilla Animal class (most of yours likely do),
        // use Animal::checkAnimalSpawnRules to mirror vanilla restrictions.
        registerAnimal(event, ModEntityTypes.CORRUPTED_COW.get());
        registerAnimal(event, ModEntityTypes.CORRUPTED_SHEEP.get());
        registerAnimal(event, ModEntityTypes.CORRUPTED_CHICKEN.get());
        registerAnimal(event, ModEntityTypes.CORRUPTED_PIG.get());
        registerAnimal(event, ModEntityTypes.CORRUPTED_CAT.get());
    }

    private static <T extends Mob> void registerAnimal(final RegisterSpawnPlacementsEvent event, @Nonnull final EntityType<T> type)
    {
        event.register(
            type,
            NullnessBridge.assumeNonnull(SpawnPlacementTypes.ON_GROUND),
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            SalvationManager::checkCorruptedAnimalPlacement,
            RegisterSpawnPlacementsEvent.Operation.OR
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
        if (!(event.getEntity().level() instanceof ServerLevel)) return;

        final LivingEntity dead = event.getEntity();
        final DamageSource source = event.getSource();
        final BlockPos pos = dead.blockPosition();

        // Only meaningful if killed by player or citizen
        final Entity killer = source.getEntity();
        if (!(killer instanceof Player || killer instanceof AbstractEntityCitizen)) return;

        SalvationManager.applyMobProgression(dead, pos, (LivingEntity) killer);
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

        final AttachmentType<ModAttachments.ConversionData> attachmentType = ModAttachments.CONVERSION.get();

        if (attachmentType == null)
        {
            throw new IllegalStateException("Failed to get CleansingData attachment type.");
        }

        final ModAttachments.ConversionData data = entity.getData(attachmentType);

        if (data == null || data.ticksRemaining() <= 0) return;

        boolean isCleansing = data.isCleansing();

        final int remaining = data.ticksRemaining() - 1;
        entity.setData(attachmentType, new ModAttachments.ConversionData(remaining, isCleansing));

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

        SalvationManager.applyFuelProgression(level, pos, output, fuel);
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
