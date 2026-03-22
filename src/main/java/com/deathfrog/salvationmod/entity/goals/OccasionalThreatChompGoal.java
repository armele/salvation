package com.deathfrog.salvationmod.entity.goals;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.entity.VoraxianMawEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

public class OccasionalThreatChompGoal extends Goal
{
    private final VoraxianMawEntity mob;
    private final double searchRange;
    private final int randomInterval;
    private final int threatDuration;

    private LivingEntity threatTarget;
    private int remainingTicks;
    private int rechompCooldown;

    public OccasionalThreatChompGoal(
        final VoraxianMawEntity mob,
        final double searchRange,
        final int randomInterval,
        final int threatDuration)
    {
        this.mob = mob;
        this.searchRange = searchRange;
        this.randomInterval = Math.max(1, randomInterval);
        this.threatDuration = Math.max(10, threatDuration);
        this.setFlags(NullnessBridge.assumeNonnull(EnumSet.of(Goal.Flag.LOOK)));
    }

    @Override
    public boolean canUse()
    {
        if (this.mob.getTarget() != null || this.mob.getRandom().nextInt(this.randomInterval) != 0)
        {
            return false;
        }

        this.threatTarget = this.findThreatTarget();
        return this.threatTarget != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.remainingTicks > 0
            && this.mob.getTarget() == null
            && this.threatTarget != null
            && this.threatTarget.isAlive()
            && this.mob.distanceToSqr(this.threatTarget) <= this.searchRange * this.searchRange
            && this.mob.hasLineOfSight(this.threatTarget);
    }

    @Override
    public void start()
    {
        this.remainingTicks = this.threatDuration;
        this.rechompCooldown = 0;
        this.mob.triggerThreatChomp();
    }

    @Override
    public void stop()
    {
        this.threatTarget = null;
        this.remainingTicks = 0;
        this.rechompCooldown = 0;
    }

    @Override
    public void tick()
    {
        if (this.threatTarget == null)
        {
            return;
        }

        this.remainingTicks--;
        if (this.rechompCooldown > 0)
        {
            this.rechompCooldown--;
        }

        this.mob.getLookControl().setLookAt(this.threatTarget, 16.0F, 16.0F);

        if (this.rechompCooldown <= 0)
        {
            this.mob.triggerThreatChomp();
            this.rechompCooldown = 10 + this.mob.getRandom().nextInt(8);
        }
    }

    private LivingEntity findThreatTarget()
    {
        final AABB searchBox = this.mob.getBoundingBox().inflate(this.searchRange, this.searchRange * 0.6D, this.searchRange);
        final List<LivingEntity> nearbyEntities = this.mob.level().getEntitiesOfClass(
            LivingEntity.class,
            searchBox,
            this::isValidThreatTarget);

        return nearbyEntities.stream()
            .min(Comparator.comparingDouble(this.mob::distanceToSqr))
            .orElse(null);
    }

    private boolean isValidThreatTarget(final LivingEntity candidate)
    {
        return candidate != this.mob
            && candidate.isAlive()
            && !candidate.isSpectator()
            && !this.mob.isAlliedTo(candidate)
            && this.mob.hasLineOfSight(candidate);
    }
}
