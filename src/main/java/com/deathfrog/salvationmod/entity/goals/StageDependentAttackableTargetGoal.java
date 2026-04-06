package com.deathfrog.salvationmod.entity.goals;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

import com.deathfrog.salvationmod.Config;
import com.deathfrog.salvationmod.core.engine.SalvationManager;

import javax.annotation.Nullable;

import java.util.function.Predicate;

public class StageDependentAttackableTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T>
{
    private static final double STALKING_SPEED_MODIFIER = 1.0D;
    private static final double STALKING_STOP_DISTANCE_SQR = 4.0D;

    @Nullable
    private LivingEntity stalkingTarget;

    private int timeToRecalcPath;

    public StageDependentAttackableTargetGoal(final Mob mob, final Class<T> targetType, final boolean mustSee)
    {
        this(mob, targetType, 10, mustSee, false, null);
    }

    public StageDependentAttackableTargetGoal(
        final Mob mob,
        final Class<T> targetType,
        final boolean mustSee,
        final Predicate<LivingEntity> targetPredicate)
    {
        this(mob, targetType, 10, mustSee, false, targetPredicate);
    }

    public StageDependentAttackableTargetGoal(
        final Mob mob,
        final Class<T> targetType,
        final boolean mustSee,
        final boolean mustReach)
    {
        this(mob, targetType, 10, mustSee, mustReach, null);
    }

    @SuppressWarnings("null")
    public StageDependentAttackableTargetGoal(
        final Mob mob,
        final Class<T> targetType,
        final int randomInterval,
        final boolean mustSee,
        final boolean mustReach,
        @Nullable final Predicate<LivingEntity> targetPredicate)
    {
        super(mob, targetType, randomInterval, mustSee, mustReach, targetPredicate);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET, Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /**
     * Checks if the goal can be used by the mob.
     * If the mob has reached the attack stage, this method will delegate to the superclass.
     * Otherwise, it will check if the superclass can use the goal, and if so, will set the stalking target to the target and return true.
     * @return true if the goal can be used, false otherwise
     */
    @Override
    public boolean canUse()
    {
        if (this.hasReachedAttackStage())
        {
            return super.canUse();
        }

        if (!super.canUse())
        {
            return false;
        }

        this.stalkingTarget = this.target;
        this.targetMob = this.target;
        return this.stalkingTarget != null;
    }

    /**
     * Checks if the goal can continue to be used by the mob.
     * If the mob has reached the attack stage, this method will delegate to the superclass.
     * Otherwise, it will check if the stalking target is null, not alive, or not attackable by the mob.
     * If any of these conditions are true, it will return false.
     * It will then check if the mob is within the follow distance of the stalking target, and if so, will return true.
     * @return true if the goal can continue to be used, false otherwise
     */
    @Override
    public boolean canContinueToUse()
    {
        if (this.hasReachedAttackStage())
        {
            return super.canContinueToUse();
        }

        final LivingEntity target = this.stalkingTarget;
        if (target == null || !target.isAlive() || !this.mob.canAttack(target))
        {
            return false;
        }

        final double followDistance = this.getFollowDistance();

        TargetingConditions localTargetingConditions = this.targetConditions;

        if (localTargetingConditions == null)
        {
            return false;
        }

        return this.mob.distanceToSqr(target) <= followDistance * followDistance
            && this.canAttack(target, localTargetingConditions);
    }

    /**
     * Starts the goal by clearing the mob's target and setting the time to recalculate the path to 0.
     * If the mob has reached the attack stage, this method will delegate to the superclass.
     */
    @Override
    public void start()
    {
        if (this.hasReachedAttackStage())
        {
            super.start();
            return;
        }

        this.mob.setTarget(null);
        this.timeToRecalcPath = 0;
    }

    /**
     * Stops the goal by clearing the mob's target, setting the stalking target, target, and target mob to null, and stopping the mob's navigation.
     * If the mob had an attack target, it will also call the superclass's stop method.
     */
    @Override
    public void stop()
    {
        final boolean hadAttackTarget = this.mob.getTarget() == this.target;
        this.stalkingTarget = null;
        this.target = null;
        this.targetMob = null;
        this.mob.getNavigation().stop();

        if (hadAttackTarget)
        {
            super.stop();
        }
    }

    /**
     * Called every tick to update the goal's state.
     * If the mob has reached the attack stage, the goal will check if the mob has a target and if the mob's target is null,
     * it will set the mob's target to the stalking target.
     * If the mob has not reached the attack stage, the goal will clear the mob's target and set the mob's look control to look at the stalking target.
     * If the time to recalculate the path has expired, the time is reset to the adjusted tick delay and the navigation is updated.
     * If the distance to the stalking target is greater than the stalking stop distance squared, the navigation is set to move to the stalking target.
     * If the distance is less than or equal to the stalking stop distance squared, the navigation is stopped.
     */
    @Override
    public void tick()
    {
        if (this.hasReachedAttackStage())
        {
            if (this.mob.getTarget() == null && this.stalkingTarget != null)
            {
                this.mob.setTarget(this.stalkingTarget);
            }
            return;
        }

        final LivingEntity target = this.stalkingTarget;
        if (target == null || this.mob.isLeashed())
        {
            return;
        }

        this.mob.setTarget(null);
        this.mob.getLookControl().setLookAt(target, 10.0F, (float)this.mob.getMaxHeadXRot());

        if (--this.timeToRecalcPath > 0)
        {
            return;
        }

        this.timeToRecalcPath = this.adjustedTickDelay(10);
        if (this.mob.distanceToSqr(target) > STALKING_STOP_DISTANCE_SQR)
        {
            this.mob.getNavigation().moveTo(target, STALKING_SPEED_MODIFIER);
        }
        else
        {
            this.mob.getNavigation().stop();
        }
    }

    /**
     * Returns true if the current server level stage is greater than or equal to the corrupted entity aggro stage.
     * This is used to check if the mob has reached the attack stage.
     * @return true if the mob has reached the attack stage, false otherwise
     */
    private boolean hasReachedAttackStage()
    {
        final Level level = this.mob.level();
        if (!(level instanceof ServerLevel serverLevel))
        {
            return false;
        }

        return SalvationManager.stageForLevel(serverLevel).ordinal() >= Config.corruptedEntityAggroStage.get();
    }
}
