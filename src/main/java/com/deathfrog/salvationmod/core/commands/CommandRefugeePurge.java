package com.deathfrog.salvationmod.core.commands;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.commands.AbstractCommands;
import com.deathfrog.salvationmod.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class CommandRefugeePurge extends AbstractCommands
{
    public CommandRefugeePurge(final String name)
    {
        super(name);
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
            .requires(source -> source.hasPermission(2))
            .executes(this::checkPreConditionAndExecute);
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
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, playerPos);

        if (colony == null)
        {
            source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("This command must be run from within a colony.")));
            return 0;
        }

        int removed = 0;
        int refugeeModules = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.hasModule(BuildingModules.REFUGEE_MODULE))
            {
                refugeeModules++;
                removed += building.getModule(BuildingModules.REFUGEE_MODULE).purgeRefugees();
            }
        }

        if (refugeeModules == 0)
        {
            source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("Colony " + colony.getName() + " has no refugee module.")));
            return 0;
        }

        final int removedCount = removed;
        source.sendSuccess(
            () -> Component.literal("Purged " + removedCount + " refugees from colony " + colony.getName() + "."),
            true);

        return Math.max(1, removedCount);
    }
}
