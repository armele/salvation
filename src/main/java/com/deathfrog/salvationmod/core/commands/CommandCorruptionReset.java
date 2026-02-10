package com.deathfrog.salvationmod.core.commands;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.core.commands.AbstractCommands;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.engine.SalvationManager.CorruptionStage;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class CommandCorruptionReset extends AbstractCommands
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public CommandCorruptionReset(String name)
    {
        super(name);
    }


    @Override
    public int onExecute(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = context.getSource().getPlayer();

        LOGGER.info("Running corruption progress command.");


        if (player == null)
        {
            return 0;
        }

        /*
        BlockPos pos = player.blockPosition();
        
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), pos);
        if (colony == null)
        {
            source.sendSuccess(() -> Component.literal("These commands are only valid from within a colony."), false);
            return 0;
        }
        */

        ServerLevel level = player.serverLevel();

        if (!(level instanceof ServerLevel serverLevel)) return 0;

        SalvationSavedData data = SalvationSavedData.get(level);
        data.reset();
        source.sendSuccess(() -> Component.literal("Reset progression measure in level: " + serverLevel.dimension()), false);
        long progressionMeasure = SalvationManager.getProgressionMeasure(serverLevel);
        source.sendSuccess(() -> Component.literal("Progression measure: " + progressionMeasure), false);
        CorruptionStage stage = SalvationManager.stageForLevel(serverLevel);
        source.sendSuccess(() -> Component.literal("Stage: " + stage), false);


        return 1;
    }
}
