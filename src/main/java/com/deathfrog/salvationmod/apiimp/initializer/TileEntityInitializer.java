package com.deathfrog.salvationmod.apiimp.initializer;


import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.api.colony.buildings.ModBuildings;
import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntities;
import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntityColonyBuilding;
import com.deathfrog.salvationmod.core.blockentity.PurifyingFurnaceBlockEntity;

import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.datafix.fixes.References;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.Type;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;


@SuppressWarnings("null")
public class TileEntityInitializer
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SalvationMod.MODID);

    static
    {

        SalvationTileEntities.BUILDING = BLOCK_ENTITIES.register("salvation_colonybuilding", 
            () -> BlockEntityType.Builder.of(SalvationTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(DSL.remainderType())); 

        SalvationTileEntities.ENVIRONMENTALLAB = BLOCK_ENTITIES.register(ModBuildings.ENVIRONMENTAL_LAB,
            () -> BlockEntityType.Builder.of(SalvationTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(DSL.remainderType()));  
 
        // Non-null DFU type (use the vanilla furnace choice type)
        final @Nonnull Type<?> FURNACE_DFU_TYPE = Util.fetchChoiceType(References.BLOCK_ENTITY, "furnace");

        // This is the line that makes ModBlockEntities.DUAL_OUTPUT_SMELTER.get() work.
        SalvationTileEntities.PURIFYING_FURNACE =
            BLOCK_ENTITIES.register("purifying_furnace",
                () -> BlockEntityType.Builder.of(PurifyingFurnaceBlockEntity::new, ModBlocks.PURIFYING_FURNACE.get())
                    .build(FURNACE_DFU_TYPE));
    }
}