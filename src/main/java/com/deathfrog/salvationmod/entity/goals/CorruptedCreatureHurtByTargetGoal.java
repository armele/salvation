package com.deathfrog.salvationmod.entity.goals;

import javax.annotation.Nullable;

import com.deathfrog.salvationmod.entity.CorruptedCreatureTargeting;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;

public class CorruptedCreatureHurtByTargetGoal extends HurtByTargetGoal
{
    public CorruptedCreatureHurtByTargetGoal(final PathfinderMob mob, final Class<?>... toIgnoreDamage)
    {
        super(mob, toIgnoreDamage);
    }

    @Override
    public boolean canUse()
    {
        return this.canAttackConfiguredTarget(this.mob.getLastHurtByMob()) && super.canUse();
    }

    @Override
    public boolean canContinueToUse()
    {
        if (!this.canAttackConfiguredTarget(this.mob.getTarget()))
        {
            this.mob.setTarget(null);
            return false;
        }

        return super.canContinueToUse();
    }

    private boolean canAttackConfiguredTarget(final @Nullable LivingEntity target)
    {
        return !(target instanceof AbstractEntityCitizen) || CorruptedCreatureTargeting.canAttackCivilian(target);
    }
}
