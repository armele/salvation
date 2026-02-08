package com.deathfrog.salvationmod;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.core.blocks.CorruptedWaterBlock;
import com.deathfrog.salvationmod.core.blocks.ScarredStoneBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks 
{

    // Create a Deferred Register to hold Blocks which will all be registered under the "salvation" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SalvationMod.MODID);

    // Creates a new Block with the id "salvation:example_block", combining the namespace and path
    @SuppressWarnings("null")
    public static final DeferredBlock<ScarredStoneBlock> SCARRED_STONE_BLOCK =
        BLOCKS.register("scarred_stone", () -> new ScarredStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)));
        
    @SuppressWarnings("null")
    public static final DeferredBlock<Block> SCARRED_COBBLE_BLOCK =
        BLOCKS.registerSimpleBlock("scarred_cobble", BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE));

    public static final DeferredHolder<Block, LiquidBlock> CORRUPTED_WATER_BLOCK =
        BLOCKS.register("corrupted_water",
            () -> new CorruptedWaterBlock(
                ModFluids.CORRUPTED_WATER_SOURCE.get(),
                BlockBehaviour.Properties.of()
                    .noCollission()
                    .strength(100.0F)
                    .noLootTable()
                    .mapColor(NullnessBridge.assumeNonnull(MapColor.GLOW_LICHEN))
            ));

}
