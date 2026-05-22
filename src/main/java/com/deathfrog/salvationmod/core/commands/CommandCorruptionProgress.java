package com.deathfrog.salvationmod.core.commands;

import java.util.Locale;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.core.commands.AbstractCommands;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.deathfrog.salvationmod.core.engine.ChunkCorruptionSystem;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.deathfrog.salvationmod.core.engine.CorruptionStage;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class CommandCorruptionProgress extends AbstractCommands
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public CommandCorruptionProgress(String name)
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

        BlockPos pos = player.blockPosition();

        ServerLevel level = player.serverLevel();

        if (!(level instanceof ServerLevel serverLevel)) return 0;

        SalvationSavedData salvationData = SalvationSavedData.get(serverLevel);
        long progressionMeasure = SalvationManager.getProgressionMeasure(serverLevel);
        source.sendSuccess(() -> Component.literal("Progression measure: " + progressionMeasure), false);
        source.sendSuccess(() -> Component.literal("Raw progression measure: " + salvationData.getRawProgression()), false);
        source.sendSuccess(() -> Component.literal("Recovery pool: " + salvationData.getRecoveryPool()), false);
        source.sendSuccess(() -> Component.literal("Colony threshold multiplier: " + SalvationManager.getColonyThresholdMultiplier(serverLevel)), false);
        CorruptionStage stage = SalvationManager.stageForLevel(serverLevel);
        long nextThreshold = -1L;

        for (CorruptionStage eachStage : CorruptionStage.values())
        {
            if (eachStage.ordinal() == stage.ordinal() + 1)
            {
                nextThreshold = SalvationManager.getEffectiveStageThreshold(serverLevel, eachStage);
                break;
            }
        }

        final String thresholdMessage = " (" + progressionMeasure + "/" + (nextThreshold > 0 ? nextThreshold : "UNENDING") + ")";
        source.sendSuccess(() -> Component.literal("Stage: " + stage.name() + thresholdMessage), false);

        for (ProgressionSource corruptionSource : SalvationSavedData.ProgressionSource.values())
        {
            long amount = salvationData.getProgressionMeasure(corruptionSource);
            source.sendSuccess(() -> Component.literal(corruptionSource.name() + ": " + amount), false);
        }

        int local = ChunkCorruptionSystem.getChunkCorruption(serverLevel, pos);
        source.sendSuccess(() -> Component.literal("Local chunk corruption at " + pos.toShortString() + ": " + local), false);
        source.sendSuccess(() -> Component.literal("Local chunk biome mutated: " + salvationData.hasMutatedCorruptedBiomeChunk(player.chunkPosition().toLong())), false);

        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), pos);

        if (colony != null)
        {
            SalvationColonyHandler handler = SalvationColonyHandler.getHandler(serverLevel, colony);
            source.sendSuccess(() -> Component.literal("Colony sustainability level: " + String.format(Locale.ROOT, "%.2f", handler.getSustainabilityLevel())), false);
            source.sendSuccess(() -> Component.literal("Colony purification credits: " + handler.getPurificationCredits()), false);
            source.sendSuccess(() -> Component.literal("Colony corruption contribution: " + handler.getCorruptionContribution()), false);
            source.sendSuccess(() -> Component.literal("Colony net contribution: " + handler.getNetColonyContribution()), false);
        }

        return 1;
    }
}
