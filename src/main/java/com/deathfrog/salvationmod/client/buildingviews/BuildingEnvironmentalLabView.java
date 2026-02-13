package com.deathfrog.salvationmod.client.buildingviews;

import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;

import net.minecraft.core.BlockPos;

public class BuildingEnvironmentalLabView extends AbstractBuildingView
{

    public BuildingEnvironmentalLabView(IColonyView colonyView, @NotNull BlockPos blockPos)
    {
        super(colonyView, blockPos);
    }
    
}
