package com.deathfrog.salvationmod.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.colony.buildings.BuildingEnvironmentalLab;
import com.deathfrog.salvationmod.core.colony.jobs.JobLabTech;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAIRequestSmelter;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

public class EntityAIWorkLabTech extends AbstractEntityAIRequestSmelter<JobLabTech, BuildingEnvironmentalLab>
{

    public static final Logger LOGGER = LogUtils.getLogger();

    public enum BartenderAIState implements IAIState
    {
        MAINTAIN_BEACONS;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }
    
    /**
     * Crafting icon
     */
    private final static VisibleCitizenStatus CRAFTING =
        new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/icons/work/labtech_crafting.png"),
            "com.salvation.gui.visiblestatus.labtech.crafting");

    @SuppressWarnings("unchecked")
    public EntityAIWorkLabTech(@NotNull JobLabTech job)
    {
        super(job);
        super.registerTargets(new AITarget<IAIState>(IDLE, START_WORKING, 2),
            new AITarget<IAIState>(START_WORKING, DECIDE, 2),
            new AITarget<IAIState>(CRAFT, this::craft, 50));
        worker.setCanPickUpLoot(true);
    }

    @Override
    protected IAIState decide()
    {
        // TODO: implement custom LabTech decisions here.
        // This includes removing purification essence from PurifyingFurnaces, if any exist.
        // This includes doing "analysis" work, once implemented.

        return super.decide();
    }

    @Override
    public Class<BuildingEnvironmentalLab> getExpectedBuildingClass()
    {   
        return BuildingEnvironmentalLab.class;
    }

    @Override
    protected IAIState craft()
    {
        worker.getCitizenData().setVisibleStatus(CRAFTING);
        return super.craft();
    }
}
