package com.deathfrog.salvationmod.core.engine;

import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.SalvationMod;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
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
        if (!(event.getEntity().level() instanceof ServerLevel level))
            return;

        final LivingEntity dead = event.getEntity();
        final DamageSource source = event.getSource();
        final BlockPos pos = dead.blockPosition();

        // Only meaningful if killed by player or citizen
        if (!(source.getEntity() instanceof Player || source.getEntity() instanceof AbstractEntityCitizen)) return;

        if (dead.getType().is(ModTags.Entities.CORRUPTION_KILL_MINOR))
        {
            SalvationManager.progress(level, 2);
        }

        if (dead.getType().is(ModTags.Entities.CORRUPTION_KILL_MAJOR))
        {
            SalvationManager.progress(level, 5);
        }

        if (dead.getType().is(ModTags.Entities.CORRUPTION_KILL_EXTREME))
        {
            SalvationManager.progress(level, 13);
        }

        int purification = 0;
        if (dead.getType().is(ModTags.Entities.PURIFICATION_KILL_MINOR))
        {
            purification += 2;
        }

        if (dead.getType().is(ModTags.Entities.PURIFICATION_KILL_MAJOR))
        {
            purification += 5;
        }

        if (dead.getType().is(ModTags.Entities.PURIFICATION_KILL_EXTREME))
        {
            purification += 13;
        }

        SalvationManager.progress(level, purification);

        // If this entity was killed in a colony, add credits
        if (pos != null)
        {
            IColony colony = IColonyManager.getInstance().getIColony(level, pos);

            if (colony != null)
            {
                SalvationManager.colonyPurificationCredit(colony, purification);
            }
        }

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
}
