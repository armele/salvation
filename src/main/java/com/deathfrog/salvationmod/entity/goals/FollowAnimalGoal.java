package com.deathfrog.salvationmod.entity.goals;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.PathType;

/**
 * Generalized "follow a nearby animal/mob" goal. Vanilla-like, but restricted to a target class.
 *
 * @param <T> the class of mob to follow (e.g., Sheep, Cow, Pig)
 */
public class FollowAnimalGoal<T extends Mob> extends Goal
{
    public enum TargetSelection
    {
        NEAREST,
        RANDOM
    }

    private final Mob follower;
    @Nonnull private final Class<T> targetClass;
    @Nonnull private final Predicate<T> targetPredicate;

    @Nullable
    private T target;

    private final double speedModifier;
    private final PathNavigation navigation;
    private int timeToRecalcPath;

    private final float stopDistance;
    private float oldWaterCost;
    private final float areaSize;

    // Optional behavior knobs
    private final float startChance;              // 1.0 = always try; <1.0 = occasional
    private final boolean onlyWhenNotAggressive;  // don't follow if follower has a combat target
    private final TargetSelection selection;

    public FollowAnimalGoal(
        final Mob follower,
        @Nonnull final Class<T> targetClass,
        final double speedModifier,
        final float stopDistance,
        final float areaSize,
        final float startChance,
        final boolean onlyWhenNotAggressive,
        final TargetSelection selection,
        @Nullable final Predicate<T> extraPredicate)
    {
        this.follower = follower;
        this.targetClass = targetClass;
        this.speedModifier = speedModifier;
        this.navigation = follower.getNavigation();
        this.stopDistance = stopDistance;
        this.areaSize = areaSize;

        this.startChance = startChance;
        this.onlyWhenNotAggressive = onlyWhenNotAggressive;
        this.selection = selection;

        // Base predicate: visible + not null.
        Predicate<T> base = t -> t != null && !t.isInvisible();

        // extra predicate provided by caller.
        if (extraPredicate != null)
        {
            Predicate<T> adjusted = base.and(extraPredicate);

            if (adjusted != null)
            {
                this.targetPredicate = adjusted;
            }
            else
            {
                this.targetPredicate = base;
            }
        }
        else
        {
            this.targetPredicate = base;
        }

        this.setFlags(NullnessBridge.assumeNonnull(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)));

        if (!(this.navigation instanceof GroundPathNavigation) && !(this.navigation instanceof FlyingPathNavigation))
        {
            throw new IllegalArgumentException("Unsupported mob type for FollowAnimalGoal");
        }
    }

    /**
     * Convenience constructor for typical usage.
     */
    public FollowAnimalGoal(
        final Mob follower,
        @Nonnull final Class<T> targetClass,
        final double speedModifier,
        final float stopDistance,
        final float areaSize)
    {
        this(follower, targetClass, speedModifier, stopDistance, areaSize,
            1.0F, true, TargetSelection.RANDOM, null);
    }

    /**
     * Check if the goal can be used by the follower mob.
     * If a target was selected, the goal can be used.
     */
    @Override
    public boolean canUse()
    {
        if (onlyWhenNotAggressive && follower.getTarget() != null)
            return false;

        if (startChance < 1.0F && follower.getRandom().nextFloat() >= startChance)
            return false;

        final List<T> list = follower.level().getEntitiesOfClass(
            targetClass,
            NullnessBridge.assumeNonnull(follower.getBoundingBox().inflate((double)areaSize)),
            targetPredicate
        );

        if (list.isEmpty())
            return false;

        this.target = (selection == TargetSelection.NEAREST)
            ? pickNearest(list)
            : pickRandom(list);

        return this.target != null;
    }

    /**
     * Check if the goal can continue to be used by the follower mob.
     * If the navigation is done or the mob is too close to the target, the goal can't be used.
     * If the follower has a combat target and onlyWhenNotAggressive is true, the goal can't be used.
     * @return true if the goal can continue to be used, false otherwise
     */
    @Override
    public boolean canContinueToUse()
    {
        T localTarget = this.target;

        if (localTarget == null)
            return false;

        return !this.navigation.isDone()
            && this.follower.distanceToSqr(localTarget) > (double)(this.stopDistance * this.stopDistance)
            && ( !onlyWhenNotAggressive || this.follower.getTarget() == null );
    }

    /**
     * Starts the goal by setting the time to recalculate the path to zero and
     * setting the water pathfinding malus to zero. The old water malus is
     * stored for later use.
     */
    @Override
    public void start()
    {
        this.timeToRecalcPath = 0;
        this.oldWaterCost = this.follower.getPathfindingMalus(PathType.WATER);
        this.follower.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    /**
     * Resets the goal to its default state.
     * The target is set to null, the navigation is stopped, and the water pathfinding malus is reset to its old value.
     */
    @Override
    public void stop()
    {
        this.target = null;
        this.navigation.stop();
        this.follower.setPathfindingMalus(PathType.WATER, this.oldWaterCost);
    }

    /**
     * Called every tick to update the goal's state.
     * If the target is not null and the follower is not leashed, the follower's look control is set to look at the target.
     * If the time to recalculate the path has expired, the time is reset to the adjusted tick delay and the navigation is updated.
     * If the distance to the target is greater than the stop distance squared, the navigation is set to move to the target.
     * If the distance is less than or equal to the stop distance squared, the navigation is stopped.
     * If the target's look control wants to look at the follower's position, the navigation is set to move to the target's position minus the offset from the follower to the target.
     * If the target's look control does not want to look at the follower's position, the navigation is set to move to the follower's position minus the offset from the follower to the target.
     */
    @Override
    public void tick()
    {
        T localTarget = this.target;

        if (localTarget != null && !this.follower.isLeashed())
        {
            this.follower.getLookControl().setLookAt(localTarget, 10.0F, (float)this.follower.getMaxHeadXRot());

            if (--this.timeToRecalcPath <= 0)
            {
                this.timeToRecalcPath = this.adjustedTickDelay(10);

                double dx = this.follower.getX() - localTarget.getX();
                double dy = this.follower.getY() - localTarget.getY();
                double dz = this.follower.getZ() - localTarget.getZ();
                double d2 = dx * dx + dy * dy + dz * dz;

                if (d2 > (double)(this.stopDistance * this.stopDistance))
                {
                    this.navigation.moveTo(localTarget, this.speedModifier);
                }
                else
                {
                    this.navigation.stop();

                    LookControl lookcontrol = localTarget.getLookControl();
                    if (d2 <= (double)this.stopDistance
                        || (lookcontrol.getWantedX() == this.follower.getX()
                            && lookcontrol.getWantedY() == this.follower.getY()
                            && lookcontrol.getWantedZ() == this.follower.getZ()))
                    {
                        double ox = localTarget.getX() - this.follower.getX();
                        double oz = localTarget.getZ() - this.follower.getZ();
                        this.navigation.moveTo(
                            this.follower.getX() - ox,
                            this.follower.getY(),
                            this.follower.getZ() - oz,
                            this.speedModifier
                        );
                    }
                }
            }
        }
    }

    /**
     * Returns a random target from the given list.
     * @param list the list of targets to choose from
     * @return a random target from the list, or null if the list is empty
     */
    private T pickRandom(final List<T> list)
    {
        return list.get(this.follower.getRandom().nextInt(list.size()));
    }

    /**
     * Returns the nearest target to the follower in the given list.
     * @param list the list of targets to choose from
     * @return the nearest target to the follower, or null if the list is empty
     */
    private T pickNearest(final List<T> list)
    {
        T best = null;
        double bestD2 = Double.MAX_VALUE;

        for (T candidate : list)
        {
            if (candidate == null) continue;

            final double d2 = this.follower.distanceToSqr(candidate);
            if (d2 < bestD2)
            {
                bestD2 = d2;
                best = candidate;
            }
        }

        return best;
    }
}