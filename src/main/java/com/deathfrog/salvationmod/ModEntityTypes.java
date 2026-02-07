package com.deathfrog.salvationmod;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.entity.CorruptedCowEntity;
import com.deathfrog.salvationmod.entity.CorruptedSheepEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntityTypes
{
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(NullnessBridge.assumeNonnull(BuiltInRegistries.ENTITY_TYPE), SalvationMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedSheepEntity>> CORRUPTED_SHEEP =
        ENTITY_TYPES.register("corrupted_sheep", () ->
            EntityType.Builder.<CorruptedSheepEntity>of(CorruptedSheepEntity::new, MobCategory.MONSTER)
                .sized(.9F, 1.3F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_sheep")
        );

    public static final DeferredHolder<EntityType<?>, EntityType<CorruptedCowEntity>> CORRUPTED_COW =
        ENTITY_TYPES.register("corrupted_cow", () ->
            EntityType.Builder.<CorruptedCowEntity>of(CorruptedCowEntity::new, MobCategory.MONSTER)
                .sized(.9F, 2.1F)
                .clientTrackingRange(8)
                .build(SalvationMod.MODID + ":corrupted_cow")
        );



    private ModEntityTypes() {}
}