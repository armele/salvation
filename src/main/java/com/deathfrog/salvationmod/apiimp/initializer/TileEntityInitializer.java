package com.deathfrog.salvationmod.apiimp.initializer;


import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.salvationmod.api.colony.buildings.ModBuildings;
import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntities;
import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntityColonyBuilding;

import net.minecraft.core.registries.Registries;
import com.mojang.datafixers.DSL;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;


@SuppressWarnings("null")
public class TileEntityInitializer
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MCTradePostMod.MODID);

    static
    {

        SalvationTileEntities.BUILDING = BLOCK_ENTITIES.register("salvation_colonybuilding", 
            () -> BlockEntityType.Builder.of(SalvationTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(DSL.remainderType())); 

        SalvationTileEntities.ENVIRONMENTALLAB = BLOCK_ENTITIES.register(ModBuildings.ENVIRONMENTAL_LAB,
            () -> BlockEntityType.Builder.of(SalvationTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(DSL.remainderType()));  
 
            
    }
}