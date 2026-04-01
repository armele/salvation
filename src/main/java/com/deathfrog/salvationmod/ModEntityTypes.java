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
            EntityType.Builder.<CorruptedCatEntity>of(CorruptedCatEntity::new, MobCategory.CREATURE)
                .sized(.5F, 1.2F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_cat")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedChickenEntity>> CORRUPTED_CHICKEN =
        ENTITY_TYPES.register("corrupted_chicken", () ->
            EntityType.Builder.<CorruptedChickenEntity>of(CorruptedChickenEntity::new, MobCategory.CREATURE)
                .sized(.4F, 1.0F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_chicken")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedCowEntity>> CORRUPTED_COW =
        ENTITY_TYPES.register("corrupted_cow", () ->
            EntityType.Builder.<CorruptedCowEntity>of(CorruptedCowEntity::new, MobCategory.CREATURE)
                .sized(.9F, 1.8F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_cow")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedPigEntity>> CORRUPTED_PIG =
        ENTITY_TYPES.register("corrupted_pig", () ->
            EntityType.Builder.<CorruptedPigEntity>of(CorruptedPigEntity::new, MobCategory.CREATURE)
                .sized(.9F, 1.65F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_pig")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedPolarBearEntity>> CORRUPTED_POLARBEAR =
        ENTITY_TYPES.register("corrupted_polarbear", () ->
            EntityType.Builder.<CorruptedPolarBearEntity>of(CorruptedPolarBearEntity::new, MobCategory.CREATURE)
                .sized(1.1F, 1.8F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_polarbear")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedSheepEntity>> CORRUPTED_SHEEP =
        ENTITY_TYPES.register("corrupted_sheep", () ->
            EntityType.Builder.<CorruptedSheepEntity>of(CorruptedSheepEntity::new, MobCategory.CREATURE)
                .sized(.9F, 1.55F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_sheep")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedFoxEntity>> CORRUPTED_FOX =
        ENTITY_TYPES.register("corrupted_fox", () ->
            EntityType.Builder.<CorruptedFoxEntity>of(CorruptedFoxEntity::new, MobCategory.CREATURE)
                .sized(.6F, 1.3F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_fox")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<VoraxianObserverEntity>> VORAXIAN_OBSERVER =
        ENTITY_TYPES.register("voraxian_observer", () ->
            EntityType.Builder.<VoraxianObserverEntity>of(VoraxianObserverEntity::new, MobCategory.MONSTER)
                .sized(1.2F, 1.6F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":voraxian_observer")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<VoraxianMawEntity>> VORAXIAN_MAW =
        ENTITY_TYPES.register("voraxian_maw", () ->
            EntityType.Builder.<VoraxianMawEntity>of(VoraxianMawEntity::new, MobCategory.MONSTER)
                .sized(1.2F, 1.2F)
                .clientTrackingRange(12)
                .build(SalvationMod.MODID + ":voraxian_maw")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<VoraxianStingerEntity>> VORAXIAN_STINGER =
        ENTITY_TYPES.register("voraxian_stinger", () ->
            EntityType.Builder.<VoraxianStingerEntity>of(VoraxianStingerEntity::new, MobCategory.MONSTER)
                .sized(0.8F, 0.45F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":voraxian_stinger")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<VoraxianOverlordEntity>> VORAXIAN_OVERLORD =
        ENTITY_TYPES.register("voraxian_overlord", () ->
            EntityType.Builder.<VoraxianOverlordEntity>of(VoraxianOverlordEntity::new, MobCategory.MONSTER)
                .sized(2.4F, 3.6F)
                .clientTrackingRange(12)
                .build(SalvationMod.MODID + ":voraxian_overlord")
        );

 
    public static final DeferredHolder<EntityType<?>, EntityType<CorruptionBoltEntity>> CORRUPTION_BOLT =
        ENTITY_TYPES.register("corruption_bolt", () ->
            EntityType.Builder.<CorruptionBoltEntity>of(CorruptionBoltEntity::new, MobCategory.MISC)
                .sized(.5F, .5F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build(SalvationMod.MODID + ":corruption_bolt")
        );

    private ModEntityTypes() {}
}
