package com.deathfrog.salvationmod.entity.goals;

import java.util.EnumSet;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Melee pursuit tuned for water hunters.
 * It uses direct move-control steering while either side is submerged so the mob can keep pressure on
 * swimmers without depending on a land-style path recalculation loop.
 */
public class AquaticMeleeAttackGoal extends Goal
{
    private final PathfinderMob mob;
    private final double speedModifier;
    private final boolean followTargetEvenIfNotSeen;
    private int ticksUntilNextAttack;

    public AquaticMeleeAttackGoal(final PathfinderMob mob, final double speedModifier, final boolean followTargetEvenIfNotSeen)
    {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.followTargetEvenIfNotSeen = followTargetEvenIfNotSeen;
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
        final LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive())
        {
            return false;
        }

        if (!this.followTargetEvenIfNotSeen && !this.mob.getSensing().hasLineOfSight(target))
        {
            return false;
        }

        return !(target instanceof Player player) || (!player.isSpectator() && !player.isCreative());
    }

    @Override
    public void start()
    {
        this.mob.setAggressive(true);
        this.ticksUntilNextAttack = 0;
    }

    @Override
    public void stop()
    {
        final LivingEntity target = this.mob.getTarget();
        if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target))
        {
            this.mob.setTarget(null);
        }

        this.mob.setAggressive(false);
        this.mob.getNavigation().stop();
        this.mob.getMoveControl().setWantedPosition(this.mob.getX(), this.mob.getY(), this.mob.getZ(), 0.0D);
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        final LivingEntity target = this.mob.getTarget();
        if (target == null)
        {
            return;
        }

        this.mob.getLookControl().setLookAt(target, 40.0F, 40.0F);

        if (this.isWaterEngagement(target))
        {
            this.mob.getNavigation().stop();
            this.mob.getMoveControl().setWantedPosition(target.getX(), target.getY(0.35D), target.getZ(), this.speedModifier);
        }
        else
        {
            this.mob.getNavigation().moveTo(target, this.speedModifier);
        }

        this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
        if (this.ticksUntilNextAttack <= 0
            && this.mob.isWithinMeleeAttackRange(target)
            && this.mob.getSensing().hasLineOfSight(target))
        {
            this.ticksUntilNextAttack = this.adjustedTickDelay(20);
            this.mob.swing(InteractionHand.MAIN_HAND);
            this.mob.doHurtTarget(target);
        }
    }

    private boolean isWaterEngagement(final @Nonnull LivingEntity target)
    {
        return this.mob.isInWaterOrBubble() || target.isInWaterOrBubble();
    }
}
