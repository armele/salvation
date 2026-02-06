package com.deathfrog.salvationmod.core;

import java.util.List;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SalvationMod.MODID)
public final class SalvationManager 
{
    /**
     * This method is called every tick by the EventBus and is responsible for running the salvation logic for all levels every 20 ticks (1 second).
     * It is a static method and is called automatically by the EventBus.
     * 
     * @param event The ServerTickEvent that triggered this method.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) 
    {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();

        long gameTime = overworld.getGameTime();

        // Run manager every 20 ticks (1 second)
        if ((gameTime % 20) != 0) return;

        for (ServerLevel level : server.getAllLevels())
        {
            salvationLogicLoop(level);
        }
    }

    /**
     * Run the salvation logic for the given level.
     * This method is responsible for advancing the storyline of the given level.
     * It is called every 20 ticks (1 second) by the onServerTick method.
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
}