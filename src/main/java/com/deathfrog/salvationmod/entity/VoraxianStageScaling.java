package com.deathfrog.salvationmod.entity;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.core.engine.CorruptionStage;
import com.deathfrog.salvationmod.core.engine.SalvationManager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class VoraxianStageScaling
{
    private static final String SCALE_STAGE_TAG = "SalvationVoraxianScaleStage";
    private static final String SCALE_INITIALIZED_TAG = "SalvationVoraxianScaleInitialized";

    private VoraxianStageScaling()
    {
    }

    /**
     * Apply the given base health, damage, and armor values to the provided entity according to the current corruption stage
     * of the entity's level. The entity's health is also scaled accordingly to maintain the ratio of health to max health.
     *
     * @param entity the entity to modify
     * @param baseHealth the base health value to scale
     * @param baseDamage the base damage value to scale
     * @param baseArmor the base armor value to scale
     */
    public static void apply(@Nonnull final LivingEntity entity,
        final double baseHealth,
        final double baseDamage,
        final double baseArmor)
    {
        if (!(entity.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        final CorruptionStage stage = SalvationManager.stageForLevel(serverLevel);

        final CompoundTag persistentData = entity.getPersistentData();
        if (persistentData.getBoolean(SCALE_INITIALIZED_TAG) && persistentData.getInt(SCALE_STAGE_TAG) == stage.ordinal())
        {
            return;
        }

        final ScalingProfile profile = profileFor(stage);
        final float oldMaxHealth = entity.getMaxHealth();
        final float oldHealth = entity.getHealth();

        setBaseValue(entity, NullnessBridge.assumeNonnull(Attributes.MAX_HEALTH), Math.max(1.0D, baseHealth * profile.healthMultiplier()));
        setBaseValue(entity, NullnessBridge.assumeNonnull(Attributes.ATTACK_DAMAGE), Math.max(0.0D, baseDamage * profile.damageMultiplier()));
        setBaseValue(entity, NullnessBridge.assumeNonnull(Attributes.ARMOR), Math.max(0.0D, baseArmor * profile.armorMultiplier()));

        final float newMaxHealth = entity.getMaxHealth();
        if (!persistentData.getBoolean(SCALE_INITIALIZED_TAG))
        {
            entity.setHealth(newMaxHealth);
        }
        else if (oldMaxHealth > 0.0F)
        {
            final float preservedHealthRatio = oldHealth / oldMaxHealth;
            entity.setHealth(Math.max(1.0F, Math.min(newMaxHealth, newMaxHealth * preservedHealthRatio)));
        }

        persistentData.putBoolean(SCALE_INITIALIZED_TAG, true);
        persistentData.putInt(SCALE_STAGE_TAG, stage.ordinal());
    }

    public static float scaleProjectileDamage(@Nonnull final ServerLevel level, final float baseDamage)
    {
        final ScalingProfile profile = profileFor(SalvationManager.stageForLevel(level));
        return (float) (baseDamage * profile.damageMultiplier());
    }

    private static void setBaseValue(@Nonnull final LivingEntity entity,
        @Nonnull final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
        final double value)
    {
        final AttributeInstance attributeInstance = entity.getAttribute(NullnessBridge.assumeNonnull(attribute));
        if (attributeInstance != null)
        {
            attributeInstance.setBaseValue(value);
        }
    }

    /**
     * Returns a scaling profile based on the given world stage.
     * The scaling profile contains health, damage, and armor multipliers.
     * These multipliers are used to scale entity attributes based on the world stage.
     *
     * @param stage the world stage to retrieve the scaling profile for
     * @return the scaling profile for the given world stage
     */
    private static ScalingProfile profileFor(@Nonnull final CorruptionStage stage)
    {
        return switch (stage)
        {
            case STAGE_0_UNTRIGGERED -> new ScalingProfile(0.84D, 0.86D, 0.84D);
            case STAGE_1_NORMAL -> new ScalingProfile(0.90D, 0.92D, 0.90D);
            case STAGE_2_AWAKENED -> new ScalingProfile(0.96D, 0.97D, 0.96D);
            case STAGE_3_SPREADING -> new ScalingProfile(1.00D, 1.00D, 1.00D);
            case STAGE_4_DANGEROUS -> new ScalingProfile(1.10D, 1.12D, 1.10D);
            case STAGE_5_CRITICAL -> new ScalingProfile(1.22D, 1.26D, 1.22D);
            case STAGE_6_TERMINAL -> new ScalingProfile(1.36D, 1.42D, 1.36D);
        };
    }

    private record ScalingProfile(double healthMultiplier, double damageMultiplier, double armorMultiplier)
    {
    }
}
