package com.deathfrog.salvationmod;

import java.util.EnumMap;
import java.util.List;
import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.core.items.CorruptedItem;
import com.deathfrog.salvationmod.core.items.CorruptionExtractorItem;
import com.deathfrog.salvationmod.core.items.CorruptionInverterItem;
import com.deathfrog.salvationmod.core.items.PurifyingFurnaceItem;
import com.deathfrog.salvationmod.core.items.ResearchCreditItem;
import com.deathfrog.salvationmod.core.items.TooltipBlockItem;
import com.deathfrog.salvationmod.core.items.TooltipItem;
import com.deathfrog.salvationmod.core.items.VoraxianLocatorItem;

import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;
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
    @Nonnull public static final DeferredItem<BlockItem> VORAXIUM_ORE_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem("voraxium_ore", ModBlocks.VORAXIUM_ORE);

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<BlockItem> VORAXIUM_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem("voraxium_block", ModBlocks.VORAXIUM_BLOCK);

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<BlockItem> UNSTABLE_VORAXIUM_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem("unstable_voraxium_block", ModBlocks.UNSTABLE_VORAXIUM_BLOCK);

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<BlockItem> VORAXIAN_OVERLORD_ANCHOR_ITEM =
        ITEMS.registerSimpleBlockItem("voraxian_overlord_anchor", ModBlocks.VORAXIAN_OVERLORD_ANCHOR);

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<BlockItem> SCARRED_COBBLE_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem("scarred_cobble", ModBlocks.SCARRED_COBBLE_BLOCK); 

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<PurifyingFurnaceItem> PURIFYING_FURNACE_ITEM =
        ITEMS.register("purifying_furnace", () -> new PurifyingFurnaceItem(ModBlocks.PURIFYING_FURNACE.get(), new Item.Properties()));

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
    @Nonnull public static final DeferredItem<CorruptionExtractorItem> CORRUPTION_EXTRACTOR =
        ITEMS.register("corruption_extractor", () -> new CorruptionExtractorItem(new Item.Properties().durability(100)));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<ResearchCreditItem> RESEARCH_CREDIT =
        ITEMS.register("research_credit", () -> new ResearchCreditItem(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<CorruptedItem> ESSENCE_OF_CORRUPTION =
        ITEMS.register("essence_of_corruption", () -> new CorruptedItem(new Item.Properties(), "tooltip.salvation.essence_of_corruption.flavor"));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> PURIFICATION_FILTER =
        ITEMS.register("purification_filter", () -> new Item(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> PURIFIED_IRON_INGOT =
        ITEMS.register("purified_iron_ingot", () -> new Item(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> PURIFIED_IRON_NUGGET =
        ITEMS.register("purified_iron_nugget", () -> new Item(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> RAW_VORAXIUM =
        ITEMS.register("raw_voraxium", () -> new Item(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> UNSTABLE_RAW_VORAXIUM =
        ITEMS.register("unstable_raw_voraxium", () -> new Item(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> UNSTABLE_VORAXIUM_SCRAP =
        ITEMS.register("unstable_voraxium_scrap", () -> new Item(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> UNSTABLE_VORAXIUM_INGOT =
        ITEMS.register("unstable_voraxium_ingot", () -> new Item(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> VORAXIUM_INGOT =
        ITEMS.register("voraxium_ingot", () -> new Item(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> VORAXIUM_NUGGET =
        ITEMS.register("voraxium_nugget", () -> new Item(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<CorruptionInverterItem> CORRUPTION_INVERTER =
        ITEMS.register("corruption_inverter", () -> new CorruptionInverterItem(new Item.Properties().durability(100)));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> STABILIZATION_TEMPLATE =
        ITEMS.register("stabilization_template", () -> new Item(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> WARD_BINDING =
        ITEMS.register("ward_binding", () -> new Item(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> BEACON_LOGIC_UNIT =
        ITEMS.register("beacon_logic_unit", () -> new Item(new Item.Properties()));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> BEACON_UPGRADE_BASE =
        ITEMS.register("beacon_upgrade_base", () -> new TooltipItem(new Item.Properties().stacksTo(1), "tooltip.salvation.beacon_upgrade_base"));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> BEACON_UPGRADE_EXTRACTION =
        ITEMS.register("beacon_upgrade_extraction", () -> new TooltipItem(new Item.Properties().stacksTo(1), "tooltip.salvation.beacon_upgrade_extraction"));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> BEACON_UPGRADE_SOLAR =
        ITEMS.register("beacon_upgrade_solar", () -> new TooltipItem(new Item.Properties().stacksTo(1), "tooltip.salvation.beacon_upgrade_solar"));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> BEACON_UPGRADE_HARVEST =
        ITEMS.register("beacon_upgrade_harvest", () -> new TooltipItem(new Item.Properties().stacksTo(1), "tooltip.salvation.beacon_upgrade_harvest"));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<Item> BEACON_UPGRADE_SHIELDING =
        ITEMS.register("beacon_upgrade_shielding", () -> new TooltipItem(new Item.Properties().stacksTo(1), "tooltip.salvation.beacon_upgrade_shielding"));

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<VoraxianLocatorItem> VORAXIAN_LOCATOR =
        ITEMS.register("voraxian_locator", () -> new VoraxianLocatorItem(new Item.Properties().durability(10)));

    @SuppressWarnings("null")
    public static final DeferredItem<SwordItem> PURIFIED_IRON_SWORD =
        ITEMS.register("purified_iron_sword",
            () -> new SwordItem(
                ModTiers.PURIFIED_IRON,
                new Item.Properties().attributes(SwordItem.createAttributes(ModTiers.PURIFIED_IRON, 3, -2.4F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<PickaxeItem> PURIFIED_IRON_PICKAXE =
        ITEMS.register("purified_iron_pickaxe",
            () -> new PickaxeItem(
                ModTiers.PURIFIED_IRON,
                new Item.Properties().attributes(PickaxeItem.createAttributes(ModTiers.PURIFIED_IRON, 1.0F, -2.8F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<AxeItem> PURIFIED_IRON_AXE =
        ITEMS.register("purified_iron_axe",
            () -> new AxeItem(
                ModTiers.PURIFIED_IRON,
                new Item.Properties().attributes(AxeItem.createAttributes(ModTiers.PURIFIED_IRON, 6.0F, -3.1F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<ShovelItem> PURIFIED_IRON_SHOVEL =
        ITEMS.register("purified_iron_shovel",
            () -> new ShovelItem(
                ModTiers.PURIFIED_IRON,
                new Item.Properties().attributes(ShovelItem.createAttributes(ModTiers.PURIFIED_IRON, 1.5F, -3.0F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<HoeItem> PURIFIED_IRON_HOE =
        ITEMS.register("purified_iron_hoe",
            () -> new HoeItem(
                ModTiers.PURIFIED_IRON,
                new Item.Properties().attributes(HoeItem.createAttributes(ModTiers.PURIFIED_IRON, -2.0F, -1.0F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<SwordItem> VORAXIUM_SWORD =
        ITEMS.register("voraxium_sword",
            () -> new SwordItem(
                ModTiers.VORAXIUM,
                new Item.Properties().attributes(SwordItem.createAttributes(ModTiers.VORAXIUM, 3, -2.4F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<PickaxeItem> VORAXIUM_PICKAXE =
        ITEMS.register("voraxium_pickaxe",
            () -> new PickaxeItem(
                ModTiers.VORAXIUM,
                new Item.Properties().attributes(PickaxeItem.createAttributes(ModTiers.VORAXIUM, 1.0F, -2.8F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<AxeItem> VORAXIUM_AXE =
        ITEMS.register("voraxium_axe",
            () -> new AxeItem(
                ModTiers.VORAXIUM,
                new Item.Properties().attributes(AxeItem.createAttributes(ModTiers.VORAXIUM, 6.5F, -3.0F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<ShovelItem> VORAXIUM_SHOVEL =
        ITEMS.register("voraxium_shovel",
            () -> new ShovelItem(
                ModTiers.VORAXIUM,
                new Item.Properties().attributes(ShovelItem.createAttributes(ModTiers.VORAXIUM, 1.5F, -3.0F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<HoeItem> VORAXIUM_HOE =
        ITEMS.register("voraxium_hoe",
            () -> new HoeItem(
                ModTiers.VORAXIUM,
                new Item.Properties().attributes(HoeItem.createAttributes(ModTiers.VORAXIUM, -3.0F, 0.0F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<SwordItem> UNSTABLE_VORAXIUM_SWORD =
        ITEMS.register("unstable_voraxium_sword",
            () -> new SwordItem(
                ModTiers.VORAXIUM,
                new Item.Properties().attributes(SwordItem.createAttributes(ModTiers.VORAXIUM, 3, -2.4F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<PickaxeItem> UNSTABLE_VORAXIUM_PICKAXE =
        ITEMS.register("unstable_voraxium_pickaxe",
            () -> new PickaxeItem(
                ModTiers.VORAXIUM,
                new Item.Properties().attributes(PickaxeItem.createAttributes(ModTiers.VORAXIUM, 1.0F, -2.8F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<AxeItem> UNSTABLE_VORAXIUM_AXE =
        ITEMS.register("unstable_voraxium_axe",
            () -> new AxeItem(
                ModTiers.VORAXIUM,
                new Item.Properties().attributes(AxeItem.createAttributes(ModTiers.VORAXIUM, 6.5F, -3.0F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<ShovelItem> UNSTABLE_VORAXIUM_SHOVEL =
        ITEMS.register("unstable_voraxium_shovel",
            () -> new ShovelItem(
                ModTiers.VORAXIUM,
                new Item.Properties().attributes(ShovelItem.createAttributes(ModTiers.VORAXIUM, 1.5F, -3.0F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<HoeItem> UNSTABLE_VORAXIUM_HOE =
        ITEMS.register("unstable_voraxium_hoe",
            () -> new HoeItem(
                ModTiers.VORAXIUM,
                new Item.Properties().attributes(HoeItem.createAttributes(ModTiers.VORAXIUM, -3.0F, 0.0F))
            ));

    @SuppressWarnings("null")
    public static final DeferredItem<ArmorItem> PURIFIED_IRON_HELMET =
        ITEMS.register("purified_iron_helmet", () -> new ArmorItem(
            ModArmorMaterials.PURIFIED_IRON,
            ArmorItem.Type.HELMET,
            new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(15))
        ));

    @SuppressWarnings("null")
    public static final DeferredItem<ArmorItem> PURIFIED_IRON_CHESTPLATE =
        ITEMS.register("purified_iron_chestplate", () -> new ArmorItem(
            ModArmorMaterials.PURIFIED_IRON,
            ArmorItem.Type.CHESTPLATE,
            new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(15))
        ));

    @SuppressWarnings("null")
    public static final DeferredItem<ArmorItem> PURIFIED_IRON_LEGGINGS =
        ITEMS.register("purified_iron_leggings", () -> new ArmorItem(
            ModArmorMaterials.PURIFIED_IRON,
            ArmorItem.Type.LEGGINGS,
            new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(15))
        ));

    @SuppressWarnings("null")
    public static final DeferredItem<ArmorItem> PURIFIED_IRON_BOOTS =
        ITEMS.register("purified_iron_boots", () -> new ArmorItem(
            ModArmorMaterials.PURIFIED_IRON,
            ArmorItem.Type.BOOTS,
            new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(15))
        ));

    @SuppressWarnings("null")
    public static final DeferredItem<ArmorItem> VORAXIUM_HELMET =
        ITEMS.register("voraxium_helmet", () -> new ArmorItem(
            ModArmorMaterials.VORAXIUM,
            ArmorItem.Type.HELMET,
            new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(18))
        ));

    @SuppressWarnings("null")
    public static final DeferredItem<ArmorItem> VORAXIUM_CHESTPLATE =
        ITEMS.register("voraxium_chestplate", () -> new ArmorItem(
            ModArmorMaterials.VORAXIUM,
            ArmorItem.Type.CHESTPLATE,
            new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(18))
        ));

    @SuppressWarnings("null")
    public static final DeferredItem<ArmorItem> VORAXIUM_LEGGINGS =
        ITEMS.register("voraxium_leggings", () -> new ArmorItem(
            ModArmorMaterials.VORAXIUM,
            ArmorItem.Type.LEGGINGS,
            new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(18))
        ));

    @SuppressWarnings("null")
    public static final DeferredItem<ArmorItem> VORAXIUM_BOOTS =
        ITEMS.register("voraxium_boots", () -> new ArmorItem(
            ModArmorMaterials.VORAXIUM,
            ArmorItem.Type.BOOTS,
            new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(18))
        ));

    @SuppressWarnings("null")
    public static final DeferredItem<ArmorItem> UNSTABLE_VORAXIUM_HELMET =
        ITEMS.register("unstable_voraxium_helmet", () -> new ArmorItem(
            ModArmorMaterials.VORAXIUM,
            ArmorItem.Type.HELMET,
            new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(18))
        ));

    @SuppressWarnings("null")
    public static final DeferredItem<ArmorItem> UNSTABLE_VORAXIUM_CHESTPLATE =
        ITEMS.register("unstable_voraxium_chestplate", () -> new ArmorItem(
            ModArmorMaterials.VORAXIUM,
            ArmorItem.Type.CHESTPLATE,
            new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(18))
        ));

    @SuppressWarnings("null")
    public static final DeferredItem<ArmorItem> UNSTABLE_VORAXIUM_LEGGINGS =
        ITEMS.register("unstable_voraxium_leggings", () -> new ArmorItem(
            ModArmorMaterials.VORAXIUM,
            ArmorItem.Type.LEGGINGS,
            new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(18))
        ));

    @SuppressWarnings("null")
    public static final DeferredItem<ArmorItem> UNSTABLE_VORAXIUM_BOOTS =
        ITEMS.register("unstable_voraxium_boots", () -> new ArmorItem(
            ModArmorMaterials.VORAXIUM,
            ArmorItem.Type.BOOTS,
            new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(18))
        ));

    public static final DeferredHolder<Item, Item> INERT_FUEL =
        ITEMS.register("inert_fuel",
            () -> new Item(new Item.Properties())
        );

    @SuppressWarnings("null")
    public static final DeferredItem<BlockItem> INERT_FUEL_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem("inert_fuel_block", ModBlocks.INERT_FUEL_BLOCK);

    @SuppressWarnings("null")
    public static final DeferredItem<BlockItem> PURIFICATION_BEACON_CORE_ITEM =
        ITEMS.registerSimpleBlockItem("purification_beacon_core", ModBlocks.PURIFICATION_BEACON_CORE);

    @SuppressWarnings("null")
    @Nonnull public static final DeferredItem<BlockItem> EXPLORATION_BEACON_ITEM =
        ITEMS.register("exploration_beacon",
            () -> new TooltipBlockItem(
                ModBlocks.EXPLORATION_BEACON.get(),
                new Item.Properties(),
                "tooltip.salvation.exploration_beacon"
            ));

    public static final DeferredHolder<Item, Item> PURIFICATION_FUEL_NUGGET =
        ITEMS.register("purification_fuel_nugget",
            () -> new Item(new Item.Properties())
        );

    public static final DeferredHolder<Item, Item> PURIFICATION_FUEL =
        ITEMS.register("purification_fuel",
            () -> new Item(new Item.Properties())
        );

    @SuppressWarnings("null")
    public static final DeferredItem<BlockItem> PURIFICATION_FUEL_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem("purification_fuel_block", ModBlocks.PURIFICATION_FUEL_BLOCK);

    @SuppressWarnings("null")
    public static final DeferredItem<BlockItem> BLIGHTWOOD_SAPLING_ITEM =
        ITEMS.registerSimpleBlockItem("blightwood_sapling", ModBlocks.BLIGHTWOOD_SAPLING);

    @SuppressWarnings("null")
    public static final DeferredItem<BlockItem> BLIGHTWOOD_LOG_ITEM =
        ITEMS.registerSimpleBlockItem("blightwood_log", ModBlocks.BLIGHTWOOD_LOG);

    @SuppressWarnings("null")
    public static final DeferredItem<BlockItem> BLIGHTWOOD_LEAVES_ITEM =
        ITEMS.registerSimpleBlockItem("blightwood_leaves", ModBlocks.BLIGHTWOOD_LEAVES);

    @SuppressWarnings("null")
    public static final DeferredItem<BlockItem> NEUTRALIZED_BLIGHTWOOD_ITEM =
        ITEMS.registerSimpleBlockItem("neutralized_blightwood", ModBlocks.NEUTRALIZED_BLIGHTWOOD);

    public final class ModTiers
    {
        private ModTiers() {}

        // Between iron (250 uses, speed 6, bonus 2) and diamond (1561, speed 8, bonus 3)
        @SuppressWarnings("null")
        public static final Tier PURIFIED_IRON = new SimpleTier(
            BlockTags.INCORRECT_FOR_IRON_TOOL, // keep mining level aligned with iron (tier 2)
            850,                                // durability
            7.0F,                               // mining speed
            2.5F,                               // attack damage bonus
            12,                                 // enchantability (iron is high; keep reasonable)
            () -> Ingredient.of(ModItems.PURIFIED_IRON_INGOT.get())
        );

        @SuppressWarnings("null")
        public static final Tier VORAXIUM = new SimpleTier(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, // a modest step above purified iron
            980,
            7.5F,
            2.8F,
            14,
            () -> Ingredient.of(ModItems.VORAXIUM_INGOT.get())
        );
    }

    public final class ModArmorMaterials
    {
        private ModArmorMaterials() {}

        @SuppressWarnings("null")
        public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, SalvationMod.MODID);

        @SuppressWarnings("null")
        public static final DeferredHolder<ArmorMaterial, ArmorMaterial> PURIFIED_IRON =
            ARMOR_MATERIALS.register("purified_iron", () -> new ArmorMaterial(
                Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                    map.put(ArmorItem.Type.BOOTS, 2);
                    map.put(ArmorItem.Type.LEGGINGS, 6);
                    map.put(ArmorItem.Type.CHESTPLATE, 7);
                    map.put(ArmorItem.Type.HELMET, 2);
                }),
                11,
                SoundEvents.ARMOR_EQUIP_IRON,
                () -> Ingredient.of(ModItems.PURIFIED_IRON_INGOT.get()),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purified_iron"))),
                1.0F,
                0.0F
            ));

        @SuppressWarnings("null")
        public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VORAXIUM =
            ARMOR_MATERIALS.register("voraxium", () -> new ArmorMaterial(
                Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                    map.put(ArmorItem.Type.BOOTS, 3);
                    map.put(ArmorItem.Type.LEGGINGS, 7);
                    map.put(ArmorItem.Type.CHESTPLATE, 8);
                    map.put(ArmorItem.Type.HELMET, 3);
                }),
                13,
                SoundEvents.ARMOR_EQUIP_IRON,
                () -> Ingredient.of(ModItems.VORAXIUM_INGOT.get()),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "voraxium"))),
                1.5F,
                0.0F
            ));
    }

}
