package com.deathfrog.salvationmod.entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.core.engine.CombatEffects;
import com.deathfrog.salvationmod.entity.goals.FollowAnimalGoal;
import com.deathfrog.salvationmod.entity.goals.VoraxianHurtByTargetGoal;
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
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class VoraxianStingerEntity extends Monster
{
    private static final double BASE_HEALTH = 10.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.38D;
    private static final double BASE_ATTACK_DAMAGE = 3.0D;
    private static final double BASE_FOLLOW_RANGE = 20.0D;
    private static final double BASE_ARMOR = 1.0D;

    public VoraxianStingerEntity(final EntityType<? extends Monster> type, final Level level)
    {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return CombatEffects.corruptionAttributeEffects(null,
            BASE_HEALTH,
            BASE_MOVEMENT_SPEED,
            BASE_ATTACK_DAMAGE,
            BASE_FOLLOW_RANGE,
            BASE_ARMOR);
    }

    @Override
    public void tick()
    {
        super.tick();
        VoraxianStageScaling.apply(this, BASE_HEALTH, BASE_ATTACK_DAMAGE, BASE_ARMOR);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new FollowAnimalGoal<>(
            this,
            IronGolem.class,
            1.0D, 5.0F, 12.0F,
            0.35F,
            true,
            FollowAnimalGoal.TargetSelection.NEAREST,
            golem -> true
        ));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new VoraxianHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3,
            new NearestAttackableTargetGoal<>(this, AbstractEntityCitizen.class, true, VoraxianTargeting::canAttackCivilian));
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
        if (source == null)
        {
            return false;
        }

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
            livingTarget.knockback(knockback * 0.4D, Mth.sin(this.getYRot() * ((float) Math.PI / 180.0F)), -Mth.cos(this.getYRot() * ((float) Math.PI / 180.0F)));
            final Vec3 slowedMomentum = this.getDeltaMovement().multiply(0.7D, 1.0D, 0.7D);
            this.setDeltaMovement(NullnessBridge.assumeNonnull(slowedMomentum));
        }

        CorruptionDamage.doPostMeleeAttackEffects(this, target, source);
        this.playAttackSound();
        return true;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(final @Nonnull ServerLevelAccessor level,
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
        return SoundEvents.SILVERFISH_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(final @Nonnull DamageSource source)
    {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return SoundEvents.SILVERFISH_DEATH;
    }

    @Override
    protected void playStepSound(final @Nonnull BlockPos pos, final @Nonnull BlockState state)
    {
        this.playSound(NullnessBridge.assumeNonnull(SoundEvents.SILVERFISH_STEP), 0.15F, 1.0F);
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.8F;
    }
}
