package com.deathfrog.salvationmod.core.colony.jobs;

import com.deathfrog.salvationmod.core.entity.ai.workers.crafting.EntityAIWorkLabTech;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;

import net.minecraft.resources.ResourceLocation;

public class JobLabTech extends AbstractJobCrafter<EntityAIWorkLabTech, JobLabTech> 
{
    public static final int COUNTER_TRIGGER = 4;

    protected int missingFuelCounter = 0;
    protected int missingFurnaceCounter = 0;
    protected int nothingToPurifyCounter = 0;
    protected int enableBeaconsCounter = 0;

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

    /**
     * Check if the interaction is valid/should be triggered.
     *
     * @return true if the interaction is valid/should be triggered.
     */
    public boolean checkForMissingFuelInteraction()
    {
        return missingFuelCounter > COUNTER_TRIGGER;
    }

    /**
     * Tick the menu interaction counter to determine the time when the interaction gets triggered.
     */
    public int tickMissingFuelCounter()
    {
        // to prevent unnecessary high counter when ignored by player
        if (missingFuelCounter < 100) 
        {
            missingFuelCounter++;
        }

        return missingFuelCounter;
    }

    /**
     * Reset the interaction counter.
     */
    public void resetMissingFuelCounter()
    {
        missingFuelCounter = 0;
    }

    /**
     * Check if the interaction is valid/should be triggered.
     *
     * @return true if the interaction is valid/should be triggered.
     */
    public boolean checkForMissingFurnaceInteraction()
    {
        return missingFurnaceCounter > COUNTER_TRIGGER;
    }

    /**
     * Tick the menu interaction counter to determine the time when the interaction gets triggered.
     */
    public int tickMissingFurnaceCounter()
    {
        // to prevent unnecessary high counter when ignored by player
        if (missingFurnaceCounter < 100) 
        {
            missingFurnaceCounter++;
        }

        return missingFurnaceCounter;
    }

    /**
     * Reset the interaction counter.
     */
    public void resetMissingFurnaceCounter()
    {
        missingFurnaceCounter = 0;
    }

    /**
     * Check if the interaction is valid/should be triggered.
     *
     * @return true if the interaction is valid/should be triggered.
     */
    public boolean checkForNothingToPurifyInteraction()
    {
        return nothingToPurifyCounter > COUNTER_TRIGGER;
    }

    /**
     * Tick the menu interaction counter to determine the time when the interaction gets triggered.
     */
    public int tickNothingToPurifyCounter()
    {
        // to prevent unnecessary high counter when ignored by player
        if (nothingToPurifyCounter < 100) 
        {
            nothingToPurifyCounter++;
        }

        return nothingToPurifyCounter;
    }

    /**
     * Reset the interaction counter.
     */
    public void resetNothingToPurifyCounter()
    {
        nothingToPurifyCounter = 0;
    }

    /**
     * Check if the interaction is valid/should be triggered.
     *
     * @return true if the interaction is valid/should be triggered.
     */
    public boolean checkEnableBeaconsInteraction()
    {
        // This one is less frequent.
        return enableBeaconsCounter > (COUNTER_TRIGGER * COUNTER_TRIGGER);
    }

    /**
     * Tick the menu interaction counter to determine the time when the interaction gets triggered.
     */
    public int tickEnableBeaconsCounter()
    {
        // to prevent unnecessary high counter when ignored by player
        if (enableBeaconsCounter < 100) 
        {
            enableBeaconsCounter++;
        }

        return enableBeaconsCounter;
    }

    /**
     * Reset the interaction counter.
     */
    public void resetEnableBeaconsCounter()
    {
        enableBeaconsCounter = 0;
    }
}