package com.deathfrog.salvationmod;

import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

public class ModWorldgen 
{
    @SuppressWarnings("null")
    public static final ResourceKey<ConfiguredFeature<?, ?>> BLIGHTWOOD_TREE =
        ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "blightwood_tree")
        );
        
    @SuppressWarnings("null")
    public static final ResourceKey<ConfiguredFeature<?, ?>> BLIGHTWOOD_TREE_TALL =
        ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "blightwood_tree_tall")
        );

    @SuppressWarnings("null")
    public static final ResourceKey<ConfiguredFeature<?, ?>> PURIFYING_TREE =
        ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purifying_tree")
        );

    // Mostly normal, sometimes tall
    @SuppressWarnings("null")
    public static final TreeGrower BLIGHTWOOD =
        new TreeGrower(
            "blightwood",
            0.15F,                      // secondaryChance (15%)
            Optional.empty(),           // megaTree (2x2)
            Optional.empty(),           // secondaryMegaTree
            Optional.of(BLIGHTWOOD_TREE),       // tree (normal)
            Optional.of(BLIGHTWOOD_TREE_TALL),  // secondaryTree (tall)
            Optional.empty(),           // flowers
            Optional.empty()            // secondaryFlowers
        );

    @SuppressWarnings("null")
    public static final TreeGrower PURIFYING_TREE_GROWER =
        new TreeGrower(
            "purifying_tree",
            0.0F,
            Optional.empty(),
            Optional.empty(),
            Optional.of(PURIFYING_TREE),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
}
