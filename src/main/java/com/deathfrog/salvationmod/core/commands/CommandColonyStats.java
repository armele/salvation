package com.deathfrog.salvationmod.core.commands;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.commands.AbstractCommands;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class CommandColonyStats extends AbstractCommands
{
    private static final int MIN_NAME_WIDTH = 18;

    public CommandColonyStats(final String name)
    {
        super(name);
    }

    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();
        final ServerLevel level = source.getLevel();

        if (level == null)
        {
            source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("This command must be run in a world.")));
            return 0;
        }

        final List<IColony> colonies = IColonyManager.getInstance().getColonies(level).stream()
            .sorted(Comparator.comparing(CommandColonyStats::colonyName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        if (colonies.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("No colonies exist in this level."), false);
            return 1;
        }

        final List<ColonyStatsRow> rows = colonies.stream()
            .map(colony -> ColonyStatsRow.from(level, colony))
            .toList();

        final int nameWidth = Math.max(MIN_NAME_WIDTH, rows.stream()
            .mapToInt(row -> row.name().length())
            .max()
            .orElse(MIN_NAME_WIDTH));
        final int corruptionWidth = rows.stream()
            .mapToInt(row -> Long.toString(row.corruption()).length())
            .max()
            .orElse(1);
        final int purificationWidth = rows.stream()
            .mapToInt(row -> Long.toString(row.purification()).length())
            .max()
            .orElse(1);
        final int netWidth = rows.stream()
            .mapToInt(row -> Long.toString(row.netImpact()).length())
            .max()
            .orElse(1);

        source.sendSuccess(() -> Component.literal("Colony stats for " + level.dimension().location() + ":"), false);
        for (ColonyStatsRow row : rows)
        {
            source.sendSuccess(() -> Component.literal(formatRow(row, nameWidth, corruptionWidth, purificationWidth, netWidth)), false);
        }

        return rows.size();
    }

    private static @Nonnull String formatRow(
        final ColonyStatsRow row,
        final int nameWidth,
        final int corruptionWidth,
        final int purificationWidth,
        final int netWidth)
    {
        return String.format(
            Locale.ROOT,
            "%-" + nameWidth + "s  Corruption: %" + corruptionWidth + "d; Purification: %" + purificationWidth + "d  Net Impact: %" + netWidth + "d",
            row.name(),
            row.corruption(),
            row.purification(),
            row.netImpact()) + "";
    }

    private static String colonyName(final IColony colony)
    {
        return colony.getName() == null ? "" : colony.getName();
    }

    private record ColonyStatsRow(String name, long corruption, long purification, long netImpact)
    {
        private static ColonyStatsRow from(final @Nonnull ServerLevel level, final IColony colony)
        {
            final SalvationColonyHandler handler = SalvationColonyHandler.getHandler(level, colony);
            return new ColonyStatsRow(
                colonyName(colony),
                handler.getCorruptionContribution(),
                handler.getPurificationCredits(),
                handler.getNetColonyContribution());
        }
    }
}
