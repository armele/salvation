package com.deathfrog.salvationmod;

import javax.annotation.Nonnull;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags
{
    public static final class Entities
    {
        @SuppressWarnings("null")
        @Nonnull public static final TagKey<EntityType<?>> CORRUPTION_KILL_MINOR =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_kill_minor"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<EntityType<?>> CORRUPTION_KILL_MAJOR =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_kill_major"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<EntityType<?>> CORRUPTION_KILL_EXTREME =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_kill_extreme"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<EntityType<?>> CORRUPTED_ENTITY =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_entity"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<EntityType<?>> PURIFICATION_KILL_MINOR =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_kill_minor"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<EntityType<?>> PURIFICATION_KILL_MAJOR =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_kill_major"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<EntityType<?>> PURIFICATION_KILL_EXTREME =
            TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purification_kill_extreme"));
    }

    public static final class Blocks
    {
        @SuppressWarnings("null")
        @Nonnull public static final TagKey<Block> CORRUPTION_BLOCK_MINOR =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_block_minor"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<Block> CORRUPTION_BLOCK_MAJOR =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_block_major"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<Block> CORRUPTION_BLOCK_EXTREME =
            TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_block_extreme"));
    }

    public static final class Items
    {
        @SuppressWarnings("null")
        @Nonnull public static final TagKey<Item> CORRUPTABLE_ITEMS =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruptable_items"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<Item> CORRUPTED_ITEMS =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_items"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<Item> CORRUPTED_ITEMS_COMMON =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_items_common"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<Item> CORRUPTED_ITEMS_UNCOMMON =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_items_uncommon"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<Item> CCORRUPTED_ITEMS_RARE =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_items_rare"));

        @SuppressWarnings("null")
        @Nonnull public static final TagKey<Item> CORRUPTED_ITEMS_EPIC =
            TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_items_epic"));

    }
}