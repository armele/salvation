package com.deathfrog.salvationmod.core.engine;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModAttachments;
import com.deathfrog.salvationmod.ModAttachments.CleansingData;
import com.deathfrog.salvationmod.ModEntityTypes;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.SalvationMod;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
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
            SalvationManager.applySpawnOverride(event);
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
     * This method is called every tick by the EventBus and is responsible for running the salvation logic for all levels every 20 ticks (1 second).
     * It is a static method and is called automatically by the EventBus.
     * 
     * @param event The ServerTickEvent that triggered this method.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) 
    {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();

        long gameTime = overworld.getGameTime();

        // Run manager about once per second
        if ((gameTime % 18) != 0) return;

        for (ServerLevel level : server.getAllLevels())
        {
            // LOGGER.info("Running salvation logic for level: {}", level);
            SalvationManager.salvationLogicLoop(level);
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
        if (!(source.getEntity() instanceof Player || source.getEntity() instanceof AbstractEntityCitizen)) return;

        SalvationManager.applyMobProgression(dead, pos);
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

        // Generic corruption-triggering blocks
        if (state.is(ModTags.Blocks.CORRUPTION_BLOCK_MINOR))
        {
            SalvationManager.progress(level, 2);
        }

        // Stronger trigger blocks
        if (state.is(ModTags.Blocks.CORRUPTION_BLOCK_MAJOR))
        {
            SalvationManager.progress(level, 5);
        }

        // Stronger trigger blocks
        if (state.is(ModTags.Blocks.CORRUPTION_BLOCK_EXTREME))
        {
            SalvationManager.progress(level, 13);
        }
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

        final AttachmentType<ModAttachments.CleansingData> attachmentType = ModAttachments.CLEANSING.get();

        if (attachmentType == null)
        {
            throw new IllegalStateException("Failed to get CleansingData attachment type.");
        }

        final ModAttachments.CleansingData data = entity.getData(attachmentType);

        if (data.ticksRemaining() <= 0)
            return;

        final int remaining = data.ticksRemaining() - 1;
        entity.setData(attachmentType, new ModAttachments.CleansingData(remaining));

        // Finished cleansing → convert (do this BEFORE TICK FX)
        if (remaining <= 0)
        {
            EntityConversion.finishCleansing(level, entity);
            return;
        }

        // Visual feedback every 5 ticks (while still cleansing)
        if ((entity.tickCount % 5) == 0)
        {
            EntityConversion.playCureEffects(level, entity, EntityConversion.CureFxPhase.TICK);
        }
    }
}
