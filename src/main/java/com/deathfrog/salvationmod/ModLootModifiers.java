package com.deathfrog.salvationmod;

import com.deathfrog.salvationmod.core.engine.LootCorruptionModifier;
import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModLootModifiers
{
    @SuppressWarnings("null")
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SalvationMod.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<LootCorruptionModifier>>
        ADD_COIN = LOOT_MODIFIERS.register("corrupt_items", () -> LootCorruptionModifier.CODEC);
}