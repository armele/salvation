package com.deathfrog.salvationmod.entity;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.Config;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.visitor.VisitorCitizen;

import net.minecraft.world.entity.LivingEntity;

public final class CorruptedCreatureTargeting
{
    private CorruptedCreatureTargeting()
    {
    }

    public static boolean canAttackCivilian(final @Nonnull LivingEntity target)
    {
        if (!(target instanceof AbstractEntityCitizen))
        {
            return false;
        }

        if (target instanceof VisitorCitizen)
        {
            return Config.corruptedCreaturesAttackVisitors.get();
        }

        return Config.corruptedCreaturesAttackCitizens.get();
    }
}
