package com.deathfrog.salvationmod.core.colony.jobs;

import com.deathfrog.salvationmod.core.entity.ai.workers.crafting.EntityAIWorkLabTech;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;

import net.minecraft.resources.ResourceLocation;

public class JobLabTech extends AbstractJobCrafter<EntityAIWorkLabTech, JobLabTech> 
{

    public JobLabTech(ICitizenData entity) 
    {
        super(entity);
    }

    @Override
    public EntityAIWorkLabTech generateAI() 
    {
        return new EntityAIWorkLabTech(this);
    }
    
    @Override
    public ResourceLocation getModel()
    {
        return super.getModel();
    }
}