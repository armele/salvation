package com.deathfrog.salvationmod;

import javax.annotation.Nonnull;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems 
{
    // Create a Deferred Register to hold Items which will all be registered under the "salvation" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SalvationMod.MODID);

    // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path
    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<BlockItem> SCARRED_STONE_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem("scarred_stone", ModBlocks.SCARRED_STONE_BLOCK);
        
    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<BlockItem> SCARRED_COBBLE_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem("scarred_cobble", ModBlocks.SCARRED_COBBLE_BLOCK); 

    @SuppressWarnings("null")
    public static final DeferredHolder<Item, BucketItem> CORRUPTED_WATER_BUCKET =
        ModItems.ITEMS.register("corrupted_water_bucket",
            () -> new BucketItem(
                ModFluids.CORRUPTED_WATER_SOURCE.get(),
                new Item.Properties()
                    .craftRemainder(Items.BUCKET)
                    .stacksTo(1)
            ));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> CORRUPTED_FLESH =
        ITEMS.register("corrupted_flesh", () -> new Item(new Item.Properties()));
}
