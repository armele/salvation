package com.deathfrog.salvationmod.core.commands;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.commands.AbstractCommands;
import com.deathfrog.salvationmod.ModDimensions;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData;
import com.deathfrog.salvationmod.core.portal.ExteritioBossStructureManager;
import com.deathfrog.salvationmod.core.portal.ExteritioBossStructureManager.BossArenaRegenerationResult;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class CommandRegenerateBoss extends AbstractCommands
{
    public CommandRegenerateBoss(final String name)
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

    /**
     * Checks the saved Exteritio boss arena for an Overlord anchor and clears stale arena data if the
     * anchor is missing.
     *
     * @param context the command context
     * @return 1 when the command handled the saved arena state, otherwise 0
     */
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

        final SalvationSavedData data = SalvationSavedData.get(exteritio);
        final BlockPos previousBase = data.getVoraxianBaseLocation();
        final BlockPos previousSpawn = data.getVoraxianOverlordSpawnLocation();
        final BossArenaRegenerationResult result = ExteritioBossStructureManager.regenerateSavedArenaIfMissingAnchor(exteritio);

        switch (result)
        {
            case WRONG_DIMENSION ->
            {
                source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("The boss arena can only be regenerated in Exteritio.")));
                return 0;
            }
            case NO_SAVED_ARENA ->
            {
                source.sendSuccess(() -> Component.literal("No saved Voraxian boss arena exists. Use the locator or /mcsv exteritio location to create one."), false);
                return 1;
            }
            case SAVED_SPAWN_ALREADY_PRESENT ->
            {
                source.sendSuccess(() -> Component.literal("Saved Voraxian boss arena already has an anchor-derived spawn at " + format(previousSpawn) + "."), false);
                return 1;
            }
            case ANCHOR_FOUND_AND_RECORDED ->
            {
                final BlockPos updatedSpawn = data.getVoraxianOverlordSpawnLocation();
                source.sendSuccess(() -> Component.literal("Found and recorded Voraxian Overlord anchor at " + format(updatedSpawn) + "."), true);
                return 1;
            }
            case CLEARED_MISSING_ANCHOR ->
            {
                source.sendSuccess(() -> Component.literal("No Voraxian Overlord anchor found near saved arena " + format(previousBase)
                    + ". Cleared saved base and boss spawn locations; the next arena ensure pass will place the updated structure."), true);
                return 1;
            }
            default ->
            {
                source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("Unexpected boss regeneration result: " + result)));
                return 0;
            }
        }
    }

    private static String format(final BlockPos pos)
    {
        return pos == null ? "none" : pos.toShortString();
    }
}
