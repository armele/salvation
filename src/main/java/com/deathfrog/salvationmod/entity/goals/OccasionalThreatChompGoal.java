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
    private final int responseCooldownDuration;
    private final int threatDuration;

    private LivingEntity threatTarget;
    private int remainingTicks;
    private int rechompCooldown;
    private int responseCooldown;

    public OccasionalThreatChompGoal(
        final VoraxianMawEntity mob,
        final double searchRange,
        final int responseCooldownDuration,
        final int threatDuration)
    {
        this.mob = mob;
        this.searchRange = searchRange;
        this.responseCooldownDuration = Math.max(1, responseCooldownDuration);
        this.threatDuration = Math.max(10, threatDuration);
        this.setFlags(NullnessBridge.assumeNonnull(EnumSet.of(Goal.Flag.LOOK)));
    }

    /**
     * Checks if the goal can be used by the mob.
     * The goal can be used if the mob doesn't have a target, is not cooling down
     * from a previous display, and has a visible threat within the search range.
     * @return true if the goal can be used, false otherwise
     */
    @Override
    public boolean canUse()
    {
        if (this.mob.getTarget() != null)
        {
            return false;
        }

        if (this.responseCooldown > 0)
        {
            this.responseCooldown--;
            return false;
        }

        this.threatTarget = this.findThreatTarget();
        return this.threatTarget != null;
    }

    /**
     * Checks if the goal can continue to be used by the mob.
     * The goal can continue to be used if the remaining ticks are greater than 0,
     * the mob doesn't have a target, the threat target is not null and is alive,
     * the mob is within the search range of the threat target, and
     * the mob has line of sight to the threat target.
     * @return true if the goal can continue to be used, false otherwise
     */
    @Override
    public boolean canContinueToUse()
    {
        LivingEntity target = this.threatTarget;

        if (target == null)
        {
            return false;
        }

        return this.remainingTicks > 0
            && this.mob.getTarget() == null
            && target.isAlive()
            && this.mob.distanceToSqr(target) <= this.searchRange * this.searchRange
            && this.mob.hasLineOfSight(target);
    }

    /**
     * Starts the goal by resetting the remaining ticks to the threat duration and the rechomp cooldown to 0.
     * The mob is then told to trigger a threat chomp.
     */
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
        this.responseCooldown = this.responseCooldownDuration;
    }

    /**
     * Ticks the goal once per tick.
     * Decrements the remaining ticks and the rechomp cooldown.
     * If the rechomp cooldown reaches 0, the mob is told to trigger a threat chomp and the rechomp cooldown is reset to a random value between 10 and 17.
     * The mob is also told to look at the threat target.
     */
    @Override
    public void tick()
    {
        LivingEntity localThreatTarget = this.threatTarget;

        if (localThreatTarget == null)
        {
            return;
        }

        this.remainingTicks--;
        if (this.rechompCooldown > 0)
        {
            this.rechompCooldown--;
        }

        this.mob.getLookControl().setLookAt(localThreatTarget, 16.0F, 16.0F);

        if (this.rechompCooldown <= 0)
        {
            this.mob.triggerThreatChomp();
            this.rechompCooldown = 10 + this.mob.getRandom().nextInt(8);
        }
    }

    /**
     * Finds the closest living entity to the mob within the search range.
     * The search range is inflated by 0.6 times the search range in the Y direction.
     * The entity is filtered by the isValidThreatTarget predicate.
     * If no entities are found, null is returned.
     * @return the closest living entity to the mob, or null if none are found
     */
    private LivingEntity findThreatTarget()
    {
        final AABB searchBox = this.mob.getBoundingBox().inflate(this.searchRange, this.searchRange * 0.6D, this.searchRange);

        if (searchBox == null)
        {
            return null;
        }

        final List<LivingEntity> nearbyEntities = this.mob.level().getEntitiesOfClass(
            LivingEntity.class,
            searchBox,
            this::isValidThreatTarget);

        return nearbyEntities.stream()
            .min(Comparator.comparingDouble(this.mob::distanceToSqr))
            .orElse(null);
    }

    /**
     * Checks if a living entity is a valid threat target.
     * The entity is a valid threat target if it is not the mob itself,
     * is alive, is not a spectator, is not allied to the mob, and
     * the mob has line of sight to the entity.
     * @param candidate the entity to check
     * @return true if the entity is a valid threat target, false otherwise
     */
    private boolean isValidThreatTarget(final LivingEntity candidate)
    {
        return candidate != this.mob
            && candidate.isAlive()
            && !candidate.isSpectator()
            && !this.mob.isAlliedTo(candidate)
            && this.mob.hasLineOfSight(candidate);
    }
}
