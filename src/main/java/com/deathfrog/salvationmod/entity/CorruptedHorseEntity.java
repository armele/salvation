package com.deathfrog.salvationmod.entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CorruptedHorseEntity extends Monster
{
    public CorruptedHorseEntity(final EntityType<? extends Monster> type, final Level level)
    {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return CombatEffects.corruptionAttributeEffects(null, 34.0D, .34D, 5.0D, 30.0D, 3.0D);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.05D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9,
            new FollowAnimalGoal<>(
                this,
                Horse.class,
                1.0D, 6.0F, 14.0F,
                0.35F,
                true,
                FollowAnimalGoal.TargetSelection.NEAREST,
                horse -> !horse.isBaby()
            )
        );
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new CorruptedCreatureHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new StageDependentAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3,
            new StageDependentAttackableTargetGoal<>(this, AbstractEntityCitizen.class, true, CorruptedCreatureTargeting::canAttackCivilian));
    }

    @Override
    public boolean isAggressive()
    {
        return this.getTarget() != null;
    }

    @Override
    public boolean doHurtTarget(final @Nonnull Entity target)
    {
        final DamageSource source = CorruptionDamage.mobAttack(this);

        if (source == null) return false;

        final float damage = CorruptionDamage.getModifiedMeleeDamage(this, target, source);

        try
        {
            final boolean hit = target.hurt(source, damage);
            if (!hit)
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
            livingTarget.knockback(knockback * 0.5D, Mth.sin(this.getYRot() * ((float) Math.PI / 180.0F)), -Mth.cos(this.getYRot() * ((float) Math.PI / 180.0F)));
            Vec3 deltaMovement = this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D);
            this.setDeltaMovement(NullnessBridge.assumeNonnull(deltaMovement));
        }

        CorruptionDamage.doPostMeleeAttackEffects(this, target, source);
        this.playAttackSound();
        return true;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        final @Nonnull ServerLevelAccessor level,
        final @Nonnull DifficultyInstance difficulty,
        final @Nonnull MobSpawnType reason,
        @Nullable final SpawnGroupData spawnData)
    {
        @SuppressWarnings("deprecation")
        final SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);

        return data;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return SoundEvents.HORSE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(final @Nonnull DamageSource source)
    {
        return SoundEvents.HORSE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return SoundEvents.HORSE_DEATH;
    }

    @Override
    protected void playStepSound(final @Nonnull BlockPos pos, final @Nonnull BlockState state)
    {
        this.playSound(NullnessBridge.assumeNonnull(SoundEvents.HORSE_STEP), 0.15F, 1.0F);
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.95F;
    }
}
