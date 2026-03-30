package com.deathfrog.salvationmod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModDimensions
{
    @SuppressWarnings("null")
    public static final ResourceKey<Level> EXTERITIO =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "exteritio"));

    private ModDimensions()
    {
    }
}
