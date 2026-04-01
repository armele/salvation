package com.deathfrog.salvationmod.entity.goals;

import java.util.EnumSet;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.entity.VoraxianOverlordEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class VoraxianOverlordCombatGoal extends Goal
{
    private static final double RANGED_MIN_DISTANCE = 8.0D;
    private static final double RANGED_IDEAL_DISTANCE = 13.0D;
    private static final double RANGED_MAX_DISTANCE = 22.0D;
    private static final double RANGED_SPEED = 1.05D;
    private static final double RANGED_HEIGHT = 3.0D;
    private static final double RANGED_ATTACK_RADIUS = 28.0D;
    private static final int RANGED_ATTACK_INTERVAL = 24;

    private static final double MELEE_ENGAGE_DISTANCE = 2.8D;
    private static final double MELEE_SPEED = 1.5D;
    private static final double MELEE_HEIGHT = 1.2D;
    private static final int MELEE_ATTACK_INTERVAL = 16;

    private final VoraxianOverlordEntity mob;

    private int repositionCooldown;
    private int attackCooldown;
    private int strafeDirectionCooldown;
    private boolean strafeLeft;

    public VoraxianOverlordCombatGoal(final VoraxianOverlordEntity mob)
    {
        this.mob = mob;
        this.setFlags(NullnessBridge.assumeNonnull(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)));
    }

    @Override
    public boolean canUse()
    {
        final LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.canUse();
    }

    @Override
    public void start()
    {
        this.repositionCooldown = 0;
        this.attackCooldown = 0;
        this.strafeLeft = this.mob.getRandom().nextBoolean();
        this.strafeDirectionCooldown = 60 + this.mob.getRandom().nextInt(30);
    }

    @Override
    public void stop()
    {
        this.mob.getMoveControl().setWantedPosition(this.mob.getX(), this.mob.getY(), this.mob.getZ(), 0.0D);
        this.attackCooldown = 0;
        this.repositionCooldown = 0;
    }

    @Override
    public void tick()
    {
        final LivingEntity target = this.mob.getTarget();
        if (target == null)
        {
            return;
        }

        this.mob.getLookControl().setLookAt(target, 360.0F, 360.0F);

        if (this.attackCooldown > 0)
        {
            this.attackCooldown--;
        }

        if (this.strafeDirectionCooldown > 0)
        {
            this.strafeDirectionCooldown--;
        }

        if (this.mob.isMeleePhase())
        {
            this.tickMeleePhase(target);
            return;
        }

        this.tickRangedPhase(target);
    }

    /**
     * Ticks the ranged combat phase once per tick.
     * Decrements the reposition cooldown and checks if it has reached 0.
     * If the reposition cooldown has reached 0, it sets a new target position for the mob to move to, and resets the reposition cooldown.
     * The target position is calculated based on the following behavior:
     * - If the horizontal distance is less than the minimum distance, move away from the target
     * - If the horizontal distance is greater than the maximum distance, move towards the target
     * - Otherwise, hover around the target at the ideal distance
     * The function also checks if the mob is within attack range of the target, and if so, performs a ranged attack.
     * The function finally checks if it is time to change the strafe direction, and if so, changes the strafe direction and resets the strafe direction cooldown.
     */
    private void tickRangedPhase(final LivingEntity target)
    {
        if (this.repositionCooldown > 0)
        {
            this.repositionCooldown--;
        }

        final double dx = target.getX() - this.mob.getX();
        final double dz = target.getZ() - this.mob.getZ();
        final double horizontalDistSq = dx * dx + dz * dz;
        final double horizontalDist = Math.sqrt(horizontalDistSq);

        if (horizontalDist < 0.0001D)
        {
            return;
        }

        final double nx = dx / horizontalDist;
        final double nz = dz / horizontalDist;
        final double px = -nz;
        final double pz = nx;

        if (this.repositionCooldown <= 0)
        {
            final double strafeMagnitude = this.strafeLeft ? 1.8D : -1.8D;
            final double anchorDistance;
            if (horizontalDist < RANGED_MIN_DISTANCE)
            {
                anchorDistance = RANGED_IDEAL_DISTANCE + 1.5D;
            }
            else if (horizontalDist > RANGED_MAX_DISTANCE)
            {
                anchorDistance = RANGED_IDEAL_DISTANCE - 1.0D;
            }
            else
            {
                anchorDistance = RANGED_IDEAL_DISTANCE;
            }

            final double targetX = target.getX() - (nx * anchorDistance) + (px * strafeMagnitude);
            final double targetY = target.getY() + RANGED_HEIGHT + this.randomVerticalOffset();
            final double targetZ = target.getZ() - (nz * anchorDistance) + (pz * strafeMagnitude);

            this.mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, RANGED_SPEED);
            this.repositionCooldown = 14 + this.mob.getRandom().nextInt(8);
        }

        final double distanceToTargetSqr = this.mob.distanceToSqr(target);
        if (distanceToTargetSqr <= RANGED_ATTACK_RADIUS * RANGED_ATTACK_RADIUS
            && this.mob.hasLineOfSight(target)
            && this.attackCooldown <= 0)
        {
            this.mob.performRangedAttack(target, 1.0F);
            this.attackCooldown = RANGED_ATTACK_INTERVAL;
        }

        if (this.strafeDirectionCooldown <= 0 && this.mob.getRandom().nextInt(5) == 0)
        {
            this.strafeLeft = !this.strafeLeft;
            this.strafeDirectionCooldown = 60 + this.mob.getRandom().nextInt(30);
        }
    }

    /**
     * Tick function for the melee phase of the combat goal.
     * This function is responsible for moving the mob towards its target while taking into account the ideal distance, min distance, max distance, speedmodifier, and preferred height above the target.
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
    private void tickMeleePhase(final LivingEntity target)
    {
        final double dx = target.getX() - this.mob.getX();
        final double dz = target.getZ() - this.mob.getZ();
        final double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDist >= 0.0001D)
        {
            final double nx = dx / horizontalDist;
            final double nz = dz / horizontalDist;
            final double targetX = target.getX() - (nx * MELEE_ENGAGE_DISTANCE);
            final double targetY = target.getY(0.55D) + MELEE_HEIGHT;
            final double targetZ = target.getZ() - (nz * MELEE_ENGAGE_DISTANCE);
            this.mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, MELEE_SPEED);
        }
        else
        {
            this.mob.getMoveControl().setWantedPosition(target.getX(), target.getY(0.55D) + MELEE_HEIGHT, target.getZ(), MELEE_SPEED);
        }

        final double attackReach = (this.mob.getBbWidth() * 2.3F) * (this.mob.getBbWidth() * 2.3F) + target.getBbWidth();
        if (this.mob.distanceToSqr(target) <= attackReach && this.mob.hasLineOfSight(target) && this.attackCooldown <= 0)
        {
            this.mob.doHurtTarget(NullnessBridge.assumeNonnull(target));
            this.attackCooldown = MELEE_ATTACK_INTERVAL;
        }
    }

    private double randomVerticalOffset()
    {
        return (this.mob.getRandom().nextDouble() - 0.5D) * 1.1D;
    }
}
