package com.deathfrog.salvationmod.entity;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.core.engine.CombatEffects;
import com.deathfrog.salvationmod.entity.goals.CorruptedCreatureHurtByTargetGoal;
import com.deathfrog.salvationmod.entity.goals.FollowAnimalGoal;
import com.deathfrog.salvationmod.entity.goals.StageDependentAttackableTargetGoal;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.Log;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CorruptedRabbitEntity extends Monster
{
    private static final float JUMP_POWER_MULTIPLIER = 1.5F;

    public CorruptedRabbitEntity(final EntityType<? extends Monster> type, final Level level)
    {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return CombatEffects.corruptionAttributeEffects(null, 16.0D, 0.35D, 1.0D, 28.0D, 2.0D);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.15D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new FollowAnimalGoal<>(
            this, Rabbit.class, 1.0D, 6.0F, 14.0F, 0.35F, true,
            FollowAnimalGoal.TargetSelection.NEAREST, null));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new CorruptedCreatureHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new StageDependentAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new StageDependentAttackableTargetGoal<>(
            this, AbstractEntityCitizen.class, true, CorruptedCreatureTargeting::canAttackCivilian));
    }

    @Override
    public boolean isAggressive()
    {
        return this.getTarget() != null;
    }

    @Override
    protected float getJumpPower()
    {
        return super.getJumpPower() * JUMP_POWER_MULTIPLIER;
    }

    @Override
    public boolean doHurtTarget(final @Nonnull Entity target)
    {
        final DamageSource source = CorruptionDamage.mobAttack(this);
        if (source == null)
        {
            return false;
        }

        final float damage = CorruptionDamage.getModifiedMeleeDamage(this, target, source);
        try
        {
            if (!target.hurt(source, damage))
            {
                return false;
            }
        }
        catch (Exception e)
        {
            Log.getLogger().error("Exception damaging target. {}", e);
            return false;
        }

        final float knockback = this.getKnockback(target, source);
        if (knockback > 0.0F && target instanceof LivingEntity livingTarget)
        {
            livingTarget.knockback(knockback * 0.5D,
                Mth.sin(this.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(this.getYRot() * Mth.DEG_TO_RAD));
            final Vec3 movement = this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D);
            this.setDeltaMovement(NullnessBridge.assumeNonnull(movement));
        }

        CorruptionDamage.doPostMeleeAttackEffects(this, target, source);
        this.playAttackSound();
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return SoundEvents.RABBIT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(final @Nonnull DamageSource source)
    {
        return SoundEvents.RABBIT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return SoundEvents.RABBIT_DEATH;
    }

    @Override
    protected void playStepSound(final @Nonnull BlockPos pos, final @Nonnull BlockState state)
    {
        this.playSound(NullnessBridge.assumeNonnull(SoundEvents.RABBIT_JUMP), 0.15F, 1.0F);
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.9F;
    }
}
