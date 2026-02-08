package com.deathfrog.salvationmod.core.fluids;

import com.deathfrog.salvationmod.SalvationMod;

import net.neoforged.neoforge.fluids.FluidType;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public class CorruptedWaterFluidType extends FluidType
{
    private static final ResourceLocation STILL  = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "block/corrupted_water_still");
    private static final ResourceLocation FLOW   = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "block/corrupted_water_flow");
    // private static final ResourceLocation STILL  = ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");
    // private static final ResourceLocation FLOW   = ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_flow");

    // Use the vanilla water overlay so underwater rendering works correctly
    private static final ResourceLocation OVERLAY = ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_overlay");

    public CorruptedWaterFluidType()
    {
        super(Properties.create()
            .density(1000)
            .viscosity(1000)
            .canExtinguish(true)
            .canHydrate(true)
            .supportsBoating(true)
        );
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(@Nonnull Consumer<IClientFluidTypeExtensions> consumer)
    {
        consumer.accept(new IClientFluidTypeExtensions()
        {
            @Override
            public ResourceLocation getStillTexture()
            {
                return STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture()
            {
                return FLOW;
            }

            @Override
            public ResourceLocation getOverlayTexture()
            {
                return OVERLAY;
            }

            @Override
            public int getTintColor()
            {
                // ARGB.
                return 0xFF556B3F;
            }
        });
    }
}
