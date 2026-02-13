package com.deathfrog.salvationmod.api.tileentities;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

public class SalvationTileEntities 
{
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SalvationTileEntityColonyBuilding>> BUILDING;
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SalvationTileEntityColonyBuilding>> ENVIRONMENTALLAB;
}
