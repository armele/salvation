package com.deathfrog.salvationmod.api.tileentities;

import com.deathfrog.salvationmod.core.blockentity.PurificationBeaconCoreBlockEntity;
import com.deathfrog.salvationmod.core.blockentity.PurifyingFurnaceBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

public class SalvationTileEntities 
{
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SalvationTileEntityColonyBuilding>> BUILDING;
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SalvationTileEntityColonyBuilding>> ENVIRONMENTALLAB;
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<PurifyingFurnaceBlockEntity>> PURIFYING_FURNACE;
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<PurificationBeaconCoreBlockEntity>> PURIFICATION_BEACON_CORE;
}
