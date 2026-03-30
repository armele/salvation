package com.deathfrog.salvationmod.core.commands;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.commands.AbstractCommands;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class CommandExteritioRaid extends AbstractCommands
{
    public CommandExteritioRaid(final String name)
    {
        super(name);
    }

    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();
        final ServerPlayer player = source.getPlayer();

        if (player == null)
        {
            source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("This command must be run by a player.")));
            return 0;
        }

        final ServerLevel level = player.serverLevel();

        if (level == null)
        {
            source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("This command must be run in a world.")));
            return 0;
        }

        final BlockPos playerPos = player.blockPosition();
        final IColony colony = IColonyManager.getInstance().getClosestIColony(level, playerPos);

        if (colony == null)
        {
            source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("No colony was found near your current position.")));
            return 0;
        }

        final SalvationColonyHandler handler = SalvationColonyHandler.getHandler(level, colony);
        final boolean placed = handler.placeRaidPortal(colony, level);

        if (!placed)
        {
            source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("Unable to place the Exteritio raid portal near colony " + colony.getName() + ".")));
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal(
                "Spawned an Exteritio raid portal near colony "
                    + colony.getName()
                    + " centered at "
                    + colony.getCenter().toShortString()
            ),
            true
        );

        return 1;
    }
}
