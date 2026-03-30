package com.deathfrog.salvationmod.core.commands;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.commands.AbstractCommands;
import com.deathfrog.salvationmod.ModDimensions;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData;
import com.deathfrog.salvationmod.core.portal.ExteritioBossStructureManager;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class CommandExteritioLocation extends AbstractCommands
{
    public CommandExteritioLocation(final String name)
    {
        super(name);
    }

    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();
        final ServerLevel exteritio = source.getServer().getLevel(NullnessBridge.assumeNonnull(ModDimensions.EXTERITIO));

        if (exteritio == null)
        {
            source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("Exteritio is not currently available on this server.")));
            return 0;
        }

        ExteritioBossStructureManager.ensureSpawned(exteritio);

        final SalvationSavedData data = SalvationSavedData.get(exteritio);
        final BlockPos location = data.getVoraxianBaseLocation();

        if (location == null)
        {
            source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("The Voraxian base location has not been established yet.")));
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal(
                "Voraxian base locator target in Exteritio: "
                    + location.getX() + ", " + location.getY() + ", " + location.getZ()
            ),
            false
        );

        return 1;
    }
}
