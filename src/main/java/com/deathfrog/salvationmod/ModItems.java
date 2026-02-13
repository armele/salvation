package com.deathfrog.salvationmod;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.core.items.CorruptedItem;
import com.deathfrog.salvationmod.core.items.CreativePurifierItem;
import com.deathfrog.salvationmod.core.items.ResearchCreditItem;

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
    @Nonnull public static final DeferredItem<BlockItem> BLIGHTED_GRASS_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem("blighted_grass", ModBlocks.BLIGHTED_GRASS);

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
    @Nonnull public static final DeferredItem<CorruptedItem> CORRUPTED_FLESH =
        ITEMS.register("corrupted_flesh", () -> new CorruptedItem(new Item.Properties()));
    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<CorruptedItem> CORRUPTED_CATCH =
        ITEMS.register("corrupted_catch", () -> new CorruptedItem(new Item.Properties()));
    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<CorruptedItem> CORRUPTED_HARVEST =
        ITEMS.register("corrupted_harvest", () -> new CorruptedItem(new Item.Properties()));
    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<CorruptedItem> CORRUPTED_MEAT =
        ITEMS.register("corrupted_meat", () -> new CorruptedItem(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<CreativePurifierItem> CREATIVE_PURIFIER =
        ITEMS.register("creative_purifier", () -> new CreativePurifierItem(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<ResearchCreditItem> RESEARCH_CREDIT =
        ITEMS.register("research_credit", () -> new ResearchCreditItem(new Item.Properties()));
}
