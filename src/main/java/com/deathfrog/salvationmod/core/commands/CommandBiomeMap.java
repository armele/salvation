package com.deathfrog.salvationmod.core.commands;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.commands.AbstractCommands;
import com.deathfrog.salvationmod.core.engine.BiomeMappingsManager;
import com.deathfrog.salvationmod.core.engine.BiomeMapGenerationService;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

public class CommandBiomeMap extends AbstractCommands
{
    private static final String CMD_GENERATE = "generate";

    public CommandBiomeMap(final String name)
    {
        super(name);
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
            .executes(this::checkPreConditionAndExecute)
            .then(IMCCommand.newLiteral(CMD_GENERATE)
                .requires(source -> source.hasPermission(2))
                .executes(this::generateMappings));
    }

    @SuppressWarnings("null")
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();
        final ServerLevel level = source.getLevel();

        if (level == null)
        {
            source.sendFailure(Component.literal("This command must be run in a world."));
            return 0;
        }

        final HolderLookup.RegistryLookup<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        final BiomeMappingsManager mappings = BiomeMappingsManager.get();
        final List<ResourceLocation> biomeIds = biomeRegistry.listElements()
            .map(reference -> reference.key().location())
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .toList();

        source.sendSuccess(() -> Component.literal("Biome mappings (" + biomeIds.size() + " registered biomes):"), false);

        for (ResourceLocation biomeId : biomeIds)
        {
            final BiomeMappingView mappingView = describeBiome(mappings, biomeId);
            final String corruptedText = mappingView.corrupted().map(ResourceLocation::toString).orElse("-");
            final String purifiedText = mappingView.purified().map(ResourceLocation::toString).orElse("-");
            final String vanillaText = mappingView.vanilla().map(ResourceLocation::toString).orElse("-");

            source.sendSuccess(() -> Component.literal(
                biomeId + " | vanilla=" + vanillaText + " | corrupted=" + corruptedText + " | purified=" + purifiedText), false);
        }

        return 1;
    }

    private int generateMappings(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();
        final ServerLevel level = source.getLevel();

        if (level == null)
        {
            source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("This command must be run in a world.")));
            return 0;
        }

        if (!source.hasPermission(2))
        {
            source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("You must be a server operator to generate biome mappings.")));
            return 0;
        }

        try
        {
            final BiomeMapGenerationService.GenerationResult result = BiomeMapGenerationService.generateMissingBiomeMappings(level);
            source.sendSuccess(result::toComponent, true);
            source.sendSuccess(() -> Component.literal("Run /reload to load the generated datapack contents."), false);
            return 1;
        }
        catch (Exception ex)
        {
            source.sendFailure(NullnessBridge.assumeNonnull(Component.literal("Failed to generate biome mappings: " + ex.getMessage())));
            return 0;
        }
    }

    private static BiomeMappingView describeBiome(final BiomeMappingsManager mappings, final ResourceLocation biomeId)
    {
        if (mappings.isCorruptedBiome(biomeId))
        {
            final Optional<ResourceLocation> vanilla = mappings.getVanillaForCorrupted(biomeId);
            final Optional<ResourceLocation> purified = vanilla.flatMap(mappings::getPurifiedForVanilla);
            return new BiomeMappingView(vanilla, Optional.of(biomeId), purified);
        }

        if (mappings.isPurifiedBiome(biomeId))
        {
            final Optional<ResourceLocation> vanilla = mappings.getVanillaForPurified(biomeId);
            final Optional<ResourceLocation> corrupted = vanilla.flatMap(mappings::getCorruptedForVanilla);
            return new BiomeMappingView(vanilla, corrupted, Optional.of(biomeId));
        }

        return new BiomeMappingView(Optional.of(biomeId), mappings.getCorruptedForVanilla(biomeId), mappings.getPurifiedForVanilla(biomeId));
    }

    private record BiomeMappingView(Optional<ResourceLocation> vanilla,
        Optional<ResourceLocation> corrupted,
        Optional<ResourceLocation> purified)
    {
    }
}
