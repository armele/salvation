package com.deathfrog.salvationmod.core.engine;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.core.engine.SalvationManager.CorruptionStage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class CombatEffects
{
    public static AttributeSupplier.Builder corruptionAttributeEffects(final Level level,
        double baseHealth,
        double baseSpeed,
        double baseDamage,
        double baseFollowRange,
        double baseArmor)
    {
        // Always return a usable builder; never return null.
        // If we can't evaluate corruption (client side / missing server level), just return base stats.
        final AttributeSupplier.Builder baseBuilder = Monster.createMonsterAttributes()
            .add(NullnessBridge.assumeNonnull(Attributes.MAX_HEALTH), baseHealth)
            .add(NullnessBridge.assumeNonnull(Attributes.MOVEMENT_SPEED), baseSpeed)
            .add(NullnessBridge.assumeNonnull(Attributes.ATTACK_DAMAGE), baseDamage)
            .add(NullnessBridge.assumeNonnull(Attributes.FOLLOW_RANGE), baseFollowRange)
            .add(NullnessBridge.assumeNonnull(Attributes.ARMOR), baseArmor);

        if (level == null || level.isClientSide() || !(level instanceof ServerLevel serverLevel))
        {
            return baseBuilder;
        }

        final CorruptionStage stage = SalvationManager.stageForLevel(serverLevel);
        if (stage == null)
        {
            return baseBuilder;
        }

        // Roll once per mob creation: determines if this spawn is "tiered", and which tier.
        // Implementation matches stage table by checking highest tier first.
        final int roll = serverLevel.getRandom().nextInt(100); // 0..99

        // Tier 1 = baseline (no multipliers)
        int tier = 1;

        switch (stage)
        {
            case CorruptionStage.STAGE_0_UNTRIGGERED -> tier = 1;

            case CorruptionStage.STAGE_1_NORMAL -> {
                // 1 in 100 are Tier 2
                if (roll < 1) tier = 2;
            }

            case CorruptionStage.STAGE_2_AWAKENED -> {
                // 1 in 100 are Tier 3; 5 in 100 are Tier 2
                if (roll < 1) tier = 3;
                else if (roll < 6) tier = 2;
            }

            case CorruptionStage.STAGE_3_SPREADING -> {
                // 1 in 100 are Tier 4; 5 in 100 are Tier 3; 10 in 100 are Tier 2
                if (roll < 1) tier = 4;
                else if (roll < 6) tier = 3;
                else if (roll < 16) tier = 2;
            }

            case CorruptionStage.STAGE_4_DANGEROUS -> {
                // 1 in 100 are Tier 5; 5 in 100 are Tier 4; 10 in 100 are Tier 3; 15 in 100 are Tier 2
                if (roll < 1) tier = 5;
                else if (roll < 6) tier = 4;
                else if (roll < 16) tier = 3;
                else if (roll < 31) tier = 2;
            }

            case CorruptionStage.STAGE_5_CRITICAL -> {
                // 1 in 100 are Tier 6; 5 in 100 are Tier 5; 10 in 100 are Tier 4; 15 in 100 are Tier 3; 20 in 100 are Tier 2
                if (roll < 1) tier = 6;
                else if (roll < 6) tier = 5;
                else if (roll < 16) tier = 4;
                else if (roll < 31) tier = 3;
                else if (roll < 51) tier = 2;
            }

            case CorruptionStage.STAGE_6_TERMINAL -> {
                // 1 in 100 are Tier 7; 5 in 100 are Tier 6; 10 in 100 are Tier 5; 15 in 100 are Tier 4; 20 in 100 are Tier 3; 25 in
                // 100 are Tier 2
                if (roll < 1) tier = 7;
                else if (roll < 6) tier = 6;
                else if (roll < 16) tier = 5;
                else if (roll < 31) tier = 4;
                else if (roll < 51) tier = 3;
                else if (roll < 76) tier = 2;
            }

            default -> tier = 1;
        }

        if (tier <= 1)
        {
            return baseBuilder;
        }

        // Multipliers
        double healthMul;
        double speedMul;
        double damageMul;
        double followMul;
        double armorMul;

        switch (tier)
        {
            case 2 -> {
                healthMul = 1.25;
                speedMul = 1.10;
                damageMul = 1.25;
                followMul = 1.00;
                armorMul = 1.25;
            }
            case 3 -> {
                healthMul = 1.75;
                speedMul = 1.20;
                damageMul = 1.75;
                followMul = 1.20;
                armorMul = 1.75;
            }
            case 4 -> {
                healthMul = 2.50;
                speedMul = 1.30;
                damageMul = 2.00;
                followMul = 1.40;
                armorMul = 2.50;
            }
            case 5 -> {
                healthMul = 3.00;
                speedMul = 1.40;
                damageMul = 2.50;
                followMul = 1.60;
                armorMul = 3.00;
            }
            case 6 -> {
                healthMul = 3.50;
                speedMul = 1.50;
                damageMul = 3.00;
                followMul = 1.80;
                armorMul = 3.50;
            }

            default -> {
                healthMul = 1.0;
                speedMul = 1.0;
                damageMul = 1.0;
                followMul = 1.0;
                armorMul = 1.0;
            }
        }

        // Optional safety clamps (especially for speed).
        final double newHealth = Math.max(1.0, baseHealth * healthMul);
        final double newSpeed = Math.min(0.60, Math.max(0.01, baseSpeed * speedMul)); // clamp to avoid absurd values
        final double newDamage = Math.max(0.0, baseDamage * damageMul);
        final double newFollow = Math.max(0.0, baseFollowRange * followMul);
        final double newArmor = Math.max(0.0, baseArmor * armorMul);

        return Monster.createMonsterAttributes()
            .add(NullnessBridge.assumeNonnull(Attributes.MAX_HEALTH), newHealth)
            .add(NullnessBridge.assumeNonnull(Attributes.MOVEMENT_SPEED), newSpeed)
            .add(NullnessBridge.assumeNonnull(Attributes.ATTACK_DAMAGE), newDamage)
            .add(NullnessBridge.assumeNonnull(Attributes.FOLLOW_RANGE), newFollow)
            .add(NullnessBridge.assumeNonnull(Attributes.ARMOR), newArmor);
    }
}
