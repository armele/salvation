package com.deathfrog.salvationmod.entity.goals;

import java.util.EnumSet;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;

public class AggressiveFloatTowardsTargetGoal<T extends Mob & RangedAttackMob> extends Goal
{
    private final T mob;

    private final double minDistance;
    private final double idealDistance;
    private final double maxDistance;
    private final double speedModifier;
    private final double preferredHeightAboveTarget;
    private final int attackInterval;
    private final double attackRadiusSqr;

    private int repositionCooldown = 0;
    private int attackCooldown = 0;
    private int strafeDirectionCooldown = 0;
    private boolean strafeLeft = false;

    public AggressiveFloatTowardsTargetGoal(final T mob)
    {
        this(mob, 5.0D, 10.0D, 18.0D, 1.0D, 2.5D, 40, 16.0D);
    }

    public AggressiveFloatTowardsTargetGoal(final T mob,
                                 final double minDistance,
                                 final double idealDistance,
                                 final double maxDistance,
                                 final double speedModifier,
                                 final double preferredHeightAboveTarget,
                                 final int attackInterval,
                                 final double attackRadius)
    {
        this.mob = mob;
        this.minDistance = minDistance;
        this.idealDistance = idealDistance;
        this.maxDistance = maxDistance;
        this.speedModifier = speedModifier;
        this.preferredHeightAboveTarget = preferredHeightAboveTarget;
        this.attackInterval = attackInterval;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.setFlags(NullnessBridge.assumeNonnull(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)));
    }

    /**
     * Check if the goal can be used by the mob.
     * The goal can be used if the mob has a target that is not null and is alive.
     * @return true if the goal can be used, false otherwise
     */
    @Override
    public boolean canUse()
    {
        LivingEntity target = this.mob.getTarget();
        
        return target != null && target.isAlive();
    }

    /**
     * Checks if the goal can continue to be used by the mob.
     * The goal can continue to be used if the mob has a target that is not null and is alive.
     * @return true if the goal can continue to be used, false otherwise
     */
    @Override
    public boolean canContinueToUse()
    {
        return this.canUse();
    }

    /**
     * Starts the goal by resetting the reposition cooldown and randomly deciding whether to strafe left or right.
     */
    @Override
    public void start()
    {
        this.repositionCooldown = 0;
        this.attackCooldown = 0;
        this.strafeLeft = this.mob.getRandom().nextBoolean();
        this.strafeDirectionCooldown = 80 + this.mob.getRandom().nextInt(40);
    }

    /**
     * Resets the move control to the mob's current position and sets the speed modifier to 0.0.
     * This is called when the goal is stopped, to prevent the mob from continuing to move towards the target.
     */
    @Override
    public void stop()
    {
        this.mob.getMoveControl().setWantedPosition(this.mob.getX(), this.mob.getY(), this.mob.getZ(), 0.0D);
        this.attackCooldown = 0;
    }

    /**
     * Tick function for the FloatTowardsTargetGoal.
     * This function is responsible for moving the mob towards its target while taking into account the ideal distance, min distance, max distance, speed modifier, and preferred height above the target.
     * It also adds some randomness to the movement to make it feel less robotic.
     * The function checks if the mob has a target, and if the target is null it returns immediately.
     * It then sets the mob's look control to look at the target.
     * If the reposition cooldown is greater than 0, it decrements the cooldown and returns.
     * The function then calculates the horizontal distance from the mob to the target, and checks if the distance is less than 0.0001D.
     * If the distance is less than 0.0001D, it returns immediately.
     * The function then calculates the horizontal perpendicular for strafing, and the target position based on the horizontal distance.
     * The target position is calculated based on the following behavior:
     * - Too close: back away and strafe
     * - Too far: close distance and strafe lightly
     * - In ideal band: mostly circle / hover around the target
     * The function then sets the mob's move control to the target position, and occasionally switches the strafe direction to make it feel less robotic.
     */
    @Override
    public void tick()
    {
        final var target = this.mob.getTarget();
        if (target == null)
        {
            return;
        }

        this.mob.getLookControl().setLookAt(target, 8.0F, 8.0F);

        if (this.attackCooldown > 0)
        {
            this.attackCooldown--;
        }

        if (this.strafeDirectionCooldown > 0)
        {
            this.strafeDirectionCooldown--;
        }

        if (this.repositionCooldown > 0)
        {
            this.repositionCooldown--;
            return;
        }

        final double dx = target.getX() - this.mob.getX();
        // final double dy = (target.getY() + this.preferredHeightAboveTarget) - this.mob.getY();
        final double dz = target.getZ() - this.mob.getZ();

        final double horizontalDistSq = dx * dx + dz * dz;
        final double horizontalDist = Math.sqrt(horizontalDistSq);

        // Degenerate case protection
        if (horizontalDist < 0.0001D)
        {
            return;
        }

        final double nx = dx / horizontalDist;
        final double nz = dz / horizontalDist;

        // Horizontal perpendicular for strafing
        final double px = -nz;
        final double pz = nx;

        double targetX;
        double targetY;
        double targetZ;

        /*
         * Behavior:
         * - Too close: back away and strafe
         * - Too far: close distance and strafe lightly
         * - In ideal band: mostly circle / hover around the target
         */
        if (horizontalDist < this.minDistance)
        {
            final double backOff = this.idealDistance + 1.0D;
            final double strafe = this.strafeLeft ? 1.25D : -1.25D;

            targetX = target.getX() - (nx * backOff) + (px * strafe);
            targetY = target.getY() + this.preferredHeightAboveTarget + this.randomVerticalOffset();
            targetZ = target.getZ() - (nz * backOff) + (pz * strafe);
        }
        else if (horizontalDist > this.maxDistance)
        {
            final double approach = this.idealDistance;
            final double strafe = this.strafeLeft ? 0.75D : -0.75D;

            targetX = target.getX() - (nx * approach) + (px * strafe);
            targetY = target.getY() + this.preferredHeightAboveTarget + this.randomVerticalOffset();
            targetZ = target.getZ() - (nz * approach) + (pz * strafe);
        }
        else
        {
            final double strafe = this.strafeLeft ? 1.5D : -1.5D;

            targetX = target.getX() - (nx * this.idealDistance) + (px * strafe);
            targetY = target.getY() + this.preferredHeightAboveTarget + this.randomVerticalOffset();
            targetZ = target.getZ() - (nz * this.idealDistance) + (pz * strafe);
        }

        targetY = this.adjustTargetY(targetX, targetY, targetZ, target);
        this.mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, this.speedModifier);

        final double distanceToTargetSqr = this.mob.distanceToSqr(target);
        if (distanceToTargetSqr <= this.attackRadiusSqr && this.mob.hasLineOfSight(target) && this.attackCooldown <= 0)
        {
            final float distanceFactor = (float) Math.sqrt(distanceToTargetSqr) / (float) Math.sqrt(this.attackRadiusSqr);
            this.mob.performRangedAttack(target, Math.max(0.1F, Math.min(1.0F, distanceFactor)));
            this.attackCooldown = this.attackInterval;
        }

        // Keep orbit direction stable for a while so the mob doesn't thrash its yaw while circling.
        if (this.strafeDirectionCooldown <= 0 && this.mob.getRandom().nextInt(6) == 0)
        {
            this.strafeLeft = !this.strafeLeft;
            this.strafeDirectionCooldown = 80 + this.mob.getRandom().nextInt(40);
        }

        this.repositionCooldown = 26 + this.mob.getRandom().nextInt(10);
    }

    private double randomVerticalOffset()
    {
        return (this.mob.getRandom().nextDouble() - 0.5D) * 0.75D;
    }

    private double adjustTargetY(final double targetX, final double targetY, final double targetZ, final LivingEntity target)
    {
        double adjustedY = Math.max(targetY, target.getY() + this.preferredHeightAboveTarget);

        if (this.isPositionInFluid(this.mob.getX(), this.mob.getY(), this.mob.getZ()))
        {
            adjustedY = Math.max(adjustedY, this.findFluidSurfaceY(this.mob.blockPosition()) + 1.5D);
        }

        final BlockPos desiredPos = BlockPos.containing(targetX, adjustedY, targetZ);
        if (this.mob.level().getFluidState(desiredPos).isEmpty())
        {
            return adjustedY;
        }

        return Math.max(adjustedY, this.findFluidSurfaceY(desiredPos) + 1.5D);
    }

    private boolean isPositionInFluid(final double x, final double y, final double z)
    {
        return !this.mob.level().getFluidState(BlockPos.containing(x, y, z)).isEmpty();
    }

    private double findFluidSurfaceY(final BlockPos startPos)
    {
        BlockPos.MutableBlockPos cursor = startPos.mutable();

        for (int i = 0; i < 16 && !this.mob.level().getFluidState(cursor).isEmpty(); i++)
        {
            cursor.move(0, 1, 0);
        }

        return cursor.getY();
    }
}
