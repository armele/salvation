package com.deathfrog.salvationmod.entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.core.engine.CombatEffects;
import com.deathfrog.salvationmod.entity.goals.AquaticMeleeAttackGoal;
import com.deathfrog.salvationmod.entity.goals.VoraxianHurtByTargetGoal;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

public class VoraxianDarterEntity extends Monster
{
    private static final double BASE_HEALTH = 10.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.38D;
    private static final double BASE_ATTACK_DAMAGE = 3.0D;
    private static final double BASE_FOLLOW_RANGE = 20.0D;
    private static final double BASE_ARMOR = 1.0D;

    public VoraxianDarterEntity(final EntityType<? extends Monster> type, final Level level)
    {
        super(type, level);
        this.xpReward = 6;
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.04F, 0.12F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
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

    /**
     * Registers goals for the entity, such as attacking and following targets.
     */
    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(2, new AquaticMeleeAttackGoal(this, 1.25D, true));
        this.goalSelector.addGoal(5, new RandomSwimmingGoal(this, 1.0D, 20));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

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
    protected PathNavigation createNavigation(final @Nonnull Level level)
    {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public void tick()
    {
        super.tick();
        VoraxianStageScaling.apply(this, BASE_HEALTH, BASE_ATTACK_DAMAGE, BASE_ARMOR);
        this.setAirSupply(this.getMaxAirSupply());

        final Vec3 movement = this.getDeltaMovement();
        if (movement.horizontalDistanceSqr() > 1.0E-4D)
        {
            final float yaw = (float) (Math.atan2(movement.z, movement.x) * (180.0D / Math.PI)) - 90.0F;
            this.setYRot(yaw);
            this.yRotO = yaw;
            this.yBodyRot = yaw;
            this.yHeadRot = yaw;
        }
    }

    /**
     * Custom travel method for Voraxian Darters to make them swim correctly.
     * If the AI is enabled and the entity is in water, it will move relative to its current speed and direction,
     * and set its delta movement to 90% of its current value. If the entity has no target, it will also move downwards slightly.
     * Otherwise, it will use the default travel method.
     *
     * @param travelVector the vector to travel
     */
    @SuppressWarnings("null")
    @Override
    public void travel(final @Nonnull Vec3 travelVector)
    {
        if (this.isEffectiveAi() && this.isInWater())
        {
            this.moveRelative(this.getSpeed(), travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
            if (this.getTarget() == null)
            {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.005D, 0.0D));
            }
        }
        else
        {
            super.travel(travelVector);
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(final @Nonnull ServerLevelAccessor level,
        final @Nonnull DifficultyInstance difficulty,
        final @Nonnull MobSpawnType reason,
        @Nullable final SpawnGroupData spawnData)
    {
        this.setAirSupply(this.getMaxAirSupply());
        this.setXRot(0.0F);
        @SuppressWarnings("deprecation")
        final SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        return data;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return this.isInWaterOrBubble() ? SoundEvents.GUARDIAN_AMBIENT : SoundEvents.GUARDIAN_AMBIENT_LAND;
    }

    @Override
    protected SoundEvent getHurtSound(final @Nonnull DamageSource source)
    {
        return this.isInWaterOrBubble() ? SoundEvents.GUARDIAN_HURT : SoundEvents.GUARDIAN_HURT_LAND;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return this.isInWaterOrBubble() ? SoundEvents.GUARDIAN_DEATH : SoundEvents.GUARDIAN_DEATH_LAND;
    }

    @Override
    protected void playStepSound(final @Nonnull BlockPos pos, final @Nonnull BlockState state)
    {
        this.playSound(NullnessBridge.assumeNonnull(
            this.isInWaterOrBubble() ? SoundEvents.GUARDIAN_FLOP : SoundEvents.SPIDER_STEP), 0.12F, 1.0F);
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.8F;
    }
}
