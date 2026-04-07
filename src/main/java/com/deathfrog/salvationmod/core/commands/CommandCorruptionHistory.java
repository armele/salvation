package com.deathfrog.salvationmod.core.commands;

import java.util.List;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.core.commands.AbstractCommands;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.StageHistoryEntry;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class CommandCorruptionHistory extends AbstractCommands
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public CommandCorruptionHistory(final String name)
    {
        super(name);
    }

    @SuppressWarnings("null")
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();
        final ServerLevel level = source.getLevel();

        LOGGER.info("Running corruption history command.");

        if (level == null)
        {
            source.sendFailure(Component.literal("This command must be run in a world."));
            return 0;
        }

        final List<StageHistoryEntry> history = SalvationSavedData.get(level).getStageHistory();

        if (history.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("No corruption stage changes have been recorded for this level."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Corruption stage history (" + history.size() + " changes):"), false);

        for (StageHistoryEntry entry : history)
        {
            source.sendSuccess(() -> Component.literal(formatEntry(entry)), false);
        }

        return 1;
    }

    private static String formatEntry(final StageHistoryEntry entry)
    {
        final String delta = entry.delta() >= 0 ? "+" + entry.delta() : Long.toString(entry.delta());

        return "[" + entry.gameTime() + "] "
            + entry.fromStage().name()
            + " -> "
            + entry.toStage().name()
            + ", progression "
            + entry.progression()
            + ", source "
            + entry.source().name()
            + " ("
            + delta
            + ")";
    }
}
