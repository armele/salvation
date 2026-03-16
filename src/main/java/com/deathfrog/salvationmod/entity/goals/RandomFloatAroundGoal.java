package com.deathfrog.salvationmod.entity.goals;

import java.util.EnumSet;

import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;

public class RandomFloatAroundGoal extends Goal
{
    private final Monster mob;

    public RandomFloatAroundGoal(Monster mob)
    {
        this.mob = mob;
        this.setFlags(NullnessBridge.assumeNonnull(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)));
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
        RandomSource randomsource = this.mob.getRandom();
        double d0 = this.mob.getX() + (double) ((randomsource.nextFloat() * 2.0F - 1.0F) * 16.0F);
        double d1 = this.mob.getY() + (double) ((randomsource.nextFloat() * 2.0F - 1.0F) * 16.0F);
        double d2 = this.mob.getZ() + (double) ((randomsource.nextFloat() * 2.0F - 1.0F) * 16.0F);
        this.mob.getMoveControl().setWantedPosition(d0, d1, d2, 1.0);
    }

    @Override
    public void tick()
    {
        final MoveControl movecontrol = this.mob.getMoveControl();
        if (!movecontrol.hasWanted())
        {
            return;
        }

        this.mob.getLookControl().setLookAt(movecontrol.getWantedX(), movecontrol.getWantedY(), movecontrol.getWantedZ(), 10.0F, (float) this.mob.getMaxHeadXRot());
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
}
