package com.deathfrog.salvationmod.entity.goals;

import java.util.EnumSet;

import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

public class RandomFloatAroundGoal extends Goal
{
    private final Monster mob;

    public RandomFloatAroundGoal(Monster mob)
    {
        this.mob = mob;
        this.setFlags(NullnessBridge.assumeNonnull(EnumSet.of(Goal.Flag.MOVE)));
    }

    /**
     * Checks if the goal can be used by the mob.
     * If the mob's move control doesn't have a wanted position, the goal can be used.
     * If the mob's move control does have a wanted position, the goal can be used if the distance between the mob's current position and the wanted position is either less than 1 block or greater than 60 blocks (to prevent the mob from getting stuck).
     * @return true if the goal can be used, false otherwise
     */
    @Override
    public boolean canUse()
    {
        if (this.hasLiveTarget())
        {
            return false;
        }

        MoveControl movecontrol = this.mob.getMoveControl();
        if (!movecontrol.hasWanted())
        {
            return true;
        }
        else
        {
            double d0 = movecontrol.getWantedX() - this.mob.getX();
            double d1 = movecontrol.getWantedY() - this.mob.getY();
            double d2 = movecontrol.getWantedZ() - this.mob.getZ();
            double d3 = d0 * d0 + d1 * d1 + d2 * d2;
            return d3 < 1.0 || d3 > 3600.0;
        }
    }

    @Override
    public boolean canContinueToUse()
    {
        return !this.hasLiveTarget() && this.hasUsableWantedPosition();
    }

    /**
     * Start the goal by generating a random position within a 16-block radius
     * of the mob's current position and setting the mob's move control to that
     * position. The move control is set to move at a speed of 1.0 blocks per
     * tick.
     */
    @Override
    public void start()
    {
        final RandomSource random = this.mob.getRandom();
        final Vec3 heading = this.getIdleHeading();
        final float yawOffsetRadians = (random.nextFloat() - 0.5F) * 1.1F;
        final Vec3 driftDirection = heading.yRot(yawOffsetRadians).normalize();
        final double travelDistance = 12.0D + random.nextDouble() * 10.0D;
        final double verticalOffset = (random.nextDouble() - 0.5D) * 6.0D;

        final double targetX = this.mob.getX() + driftDirection.x * travelDistance;
        final double targetY = this.mob.getY() + verticalOffset;
        final double targetZ = this.mob.getZ() + driftDirection.z * travelDistance;
        this.mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, 0.8D);
    }

    @Override
    public void tick()
    {
        // Let the flying move controller steer the body toward travel naturally.
    }

    /**
     * Check if the mob has a living target.
     * @return true if the mob is an Enemy and has a non-null, living target, false otherwise
     */
    private boolean hasLiveTarget()
    {
        LivingEntity target = this.mob.getTarget();
        return this.mob instanceof Enemy && target != null && target.isAlive();
    }

    /**
     * Check if the mob's move control has a wanted position that is within
     * a distance of 1.0 to 60.0 blocks from the mob's current position.
     * 
     * @return true if the mob's move control has a usable wanted position, false otherwise
     */
    private boolean hasUsableWantedPosition()
    {
        final MoveControl movecontrol = this.mob.getMoveControl();
        if (!movecontrol.hasWanted())
        {
            return false;
        }

        final double d0 = movecontrol.getWantedX() - this.mob.getX();
        final double d1 = movecontrol.getWantedY() - this.mob.getY();
        final double d2 = movecontrol.getWantedZ() - this.mob.getZ();
        final double d3 = d0 * d0 + d1 * d1 + d2 * d2;
        return d3 >= 1.0D && d3 <= 3600.0D;
    }

    private Vec3 getIdleHeading()
    {
        final Vec3 deltaMovement = this.mob.getDeltaMovement();
        if (deltaMovement.horizontalDistanceSqr() > 0.0025D)
        {
            return new Vec3(deltaMovement.x, 0.0D, deltaMovement.z).normalize();
        }

        final float yawRadians = this.mob.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yawRadians), 0.0D, Mth.cos(yawRadians)).normalize();
    }
}
