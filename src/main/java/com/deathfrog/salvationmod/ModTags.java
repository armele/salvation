package com.deathfrog.salvationmod;

import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.LevelStem;

public final class ModTags
{
    public static final class Entities
    {

        @Nonnull public static final TagKey<EntityType<?>> CORRUPTION_KILL_MINOR =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_kill_minor"));


        @Nonnull public static final TagKey<EntityType<?>> CORRUPTION_KILL_MAJOR =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_kill_major"));


        @Nonnull public static final TagKey<EntityType<?>> CORRUPTION_KILL_EXTREME =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_kill_extreme"));


        @Nonnull public static final TagKey<EntityType<?>> CORRUPTED_ENTITY =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_entity"));


        @Nonnull public static final TagKey<EntityType<?>> CORRUPTABLE_ENTITY =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruptable_entity"));


        @Nonnull public static final TagKey<EntityType<?>> PURIFICATION_KILL_MINOR =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_kill_minor"));


        @Nonnull public static final TagKey<EntityType<?>> PURIFICATION_KILL_MAJOR =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_kill_major"));


        @Nonnull public static final TagKey<EntityType<?>> PURIFICATION_KILL_EXTREME =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_kill_extreme"));
    }

    public static final class Blocks
    {

        @Nonnull public static final TagKey<Block> CORRUPTION_BREAK_TRIVIAL =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_break_trivial"));


        @Nonnull public static final TagKey<Block> CORRUPTION_BREAK_MINOR =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_break_minor"));


        @Nonnull public static final TagKey<Block> CORRUPTION_BREAK_MAJOR =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_break_major"));


        @Nonnull public static final TagKey<Block> CORRUPTION_BREAK_EXTREME =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_break_extreme"));


        @Nonnull public static final TagKey<Block> PURIFICATION_BREAK_TRIVIAL =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_break_trivial"));


        @Nonnull public static final TagKey<Block> PURIFICATION_BREAK_MINOR =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_break_minor"));


        @Nonnull public static final TagKey<Block> PURIFICATION_BREAK_MAJOR =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_break_major"));


        @Nonnull public static final TagKey<Block> PURIFICATION_BREAK_EXTREME =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_break_extreme"));


        @Nonnull public static final TagKey<Block> CORRUPTION_PLACE_TRIVIAL =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_place_trivial"));


        @Nonnull public static final TagKey<Block> CORRUPTION_PLACE_MINOR =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_place_minor"));


        @Nonnull public static final TagKey<Block> CORRUPTION_PLACE_MAJOR =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_place_major"));


        @Nonnull public static final TagKey<Block> CORRUPTION_PLACE_EXTREME =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_place_extreme"));


        @Nonnull public static final TagKey<Block> PURIFICATION_PLACE_TRIVIAL =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_place_trivial"));


        @Nonnull public static final TagKey<Block> PURIFICATION_PLACE_MINOR =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_place_minor"));


        @Nonnull public static final TagKey<Block> PURIFICATION_PLACE_MAJOR =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_place_major"));


        @Nonnull public static final TagKey<Block> PURIFICATION_PLACE_EXTREME =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_place_extreme"));


        @Nonnull public static final TagKey<Block> BEACON_POST =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "beacon_post"));
    }

    public static final class Items
    {

        @Nonnull public static final TagKey<Item> CORRUPTABLE_ITEMS =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruptable_items"));


        @Nonnull public static final TagKey<Item> CORRUPTED_ITEMS =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_items"));


        @Nonnull public static final TagKey<Item> CORRUPTED_ITEMS_COMMON =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_items_common"));


        @Nonnull public static final TagKey<Item> CORRUPTED_ITEMS_UNCOMMON =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_items_uncommon"));


        @Nonnull public static final TagKey<Item> CCORRUPTED_ITEMS_RARE =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_items_rare"));


        @Nonnull public static final TagKey<Item> CORRUPTED_ITEMS_EPIC =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_items_epic"));


        @Nonnull public static final TagKey<Item> CORRUPTION_FUEL_TRIVIAL =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_fuel_trivial"));


        @Nonnull public static final TagKey<Item> CORRUPTION_FUEL_MINOR =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_fuel_minor"));


        @Nonnull public static final TagKey<Item> CORRUPTION_FUEL_MAJOR =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_fuel_major"));


        @Nonnull public static final TagKey<Item> CORRUPTION_FUEL_EXTREME =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_fuel_extreme"));


        @Nonnull public static final TagKey<Item> PURIFICATION_FUEL_TRIVIAL =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_fuel_trivial"));


        @Nonnull public static final TagKey<Item> PURIFICATION_FUEL_MINOR =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_fuel_minor"));


        @Nonnull public static final TagKey<Item> PURIFICATION_FUEL_MAJOR =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_fuel_major"));


        @Nonnull public static final TagKey<Item> PURIFICATION_FUEL_EXTREME =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_fuel_extreme"));


        @Nonnull public static final TagKey<Item> PURIFIED_ITEMS =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purified_items"));


        @Nonnull public static final TagKey<Item> PURIFIED_ARMOR =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purified_armor"));
    }

    public static final class DamageTypes
    {

        @Nonnull public static final TagKey<DamageType> CORRUPTION_DAMAGE =
            TagKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_damage"));
    }

    public static final class Dimensions
    {

        public static final TagKey<LevelStem> DIMENSIONS_STAGE_0 =
            TagKey.create(Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_stage0"));


        public static final TagKey<LevelStem> DIMENSIONS_STAGE_1 =
            TagKey.create(Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_stage1"));


        public static final TagKey<LevelStem> DIMENSIONS_STAGE_2 =
            TagKey.create(Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_stage2"));


        public static final TagKey<LevelStem> DIMENSIONS_STAGE_3 =
            TagKey.create(Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_stage3"));


        public static final TagKey<LevelStem> DIMENSIONS_STAGE_4 =
            TagKey.create(Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_stage4"));


        public static final TagKey<LevelStem> DIMENSIONS_STAGE_5 =
            TagKey.create(Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_stage5"));


        public static final TagKey<LevelStem> DIMENSIONS_STAGE_6 =
            TagKey.create(Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_stage6"));


        public static boolean isInDimensionTag(ServerLevel level, TagKey<LevelStem> tag)
        {
            // Convert ResourceKey<Level> -> ResourceKey<LevelStem> using the same location
            ResourceKey<LevelStem> stemKey =
                ResourceKey.create(Registries.LEVEL_STEM, level.dimension().location());

            HolderLookup.RegistryLookup<LevelStem> stems =
                level.getServer().registryAccess().lookupOrThrow(Registries.LEVEL_STEM);

            Optional<Holder.Reference<LevelStem>> holder = stems.get(stemKey);
            return holder.map(h -> h.is(tag)).orElse(false);
        }
    }
}
