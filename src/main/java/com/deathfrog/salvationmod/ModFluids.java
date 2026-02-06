package com.deathfrog.salvationmod;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.core.fluids.PollutedWaterFluid;
import com.deathfrog.salvationmod.core.fluids.PollutedWaterFluidType;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModFluids 
{
    // Fluids (source + flowing) live in the vanilla FLUID registry
    @SuppressWarnings("null")
    public static final @Nonnull DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, SalvationMod.MODID);

    // FluidType is a NeoForge registry
    @SuppressWarnings("null")
    public static final @Nonnull DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_TYPES,
                                   SalvationMod.MODID);
                                   
    public static final DeferredHolder<FluidType, FluidType> POLLUTED_WATER_TYPE =
        FLUID_TYPES.register("polluted_water",
            PollutedWaterFluidType::new);

    public static final DeferredHolder<Fluid, FlowingFluid> POLLUTED_WATER_SOURCE =
        FLUIDS.register("polluted_water",
            PollutedWaterFluid.Source::new);

    public static final DeferredHolder<Fluid, FlowingFluid> POLLUTED_WATER_FLOWING =
        FLUIDS.register("flowing_polluted_water",
            PollutedWaterFluid.Flowing::new);
}
