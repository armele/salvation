package com.deathfrog.salvationmod;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.entity.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntityTypes
{
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(NullnessBridge.assumeNonnull(BuiltInRegistries.ENTITY_TYPE), SalvationMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedCatEntity>> CORRUPTED_CAT =
        ENTITY_TYPES.register("corrupted_cat", () ->
            EntityType.Builder.<CorruptedCatEntity>of(CorruptedCatEntity::new, MobCategory.MONSTER)
                .sized(.5F, 1.2F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_cat")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedChickenEntity>> CORRUPTED_CHICKEN =
        ENTITY_TYPES.register("corrupted_chicken", () ->
            EntityType.Builder.<CorruptedChickenEntity>of(CorruptedChickenEntity::new, MobCategory.MONSTER)
                .sized(.4F, 1.0F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_chicken")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedCowEntity>> CORRUPTED_COW =
        ENTITY_TYPES.register("corrupted_cow", () ->
            EntityType.Builder.<CorruptedCowEntity>of(CorruptedCowEntity::new, MobCategory.MONSTER)
                .sized(.9F, 1.8F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_cow")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedPigEntity>> CORRUPTED_PIG =
        ENTITY_TYPES.register("corrupted_pig", () ->
            EntityType.Builder.<CorruptedPigEntity>of(CorruptedPigEntity::new, MobCategory.MONSTER)
                .sized(.9F, 1.65F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_pig")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedPolarBearEntity>> CORRUPTED_POLARBEAR =
        ENTITY_TYPES.register("corrupted_polarbear", () ->
            EntityType.Builder.<CorruptedPolarBearEntity>of(CorruptedPolarBearEntity::new, MobCategory.MONSTER)
                .sized(1.1F, 1.8F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_polarbear")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedSheepEntity>> CORRUPTED_SHEEP =
        ENTITY_TYPES.register("corrupted_sheep", () ->
            EntityType.Builder.<CorruptedSheepEntity>of(CorruptedSheepEntity::new, MobCategory.MONSTER)
                .sized(.9F, 1.55F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_sheep")
        );

    private ModEntityTypes() {}
}