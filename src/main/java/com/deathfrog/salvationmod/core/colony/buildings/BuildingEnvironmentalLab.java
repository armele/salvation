package com.deathfrog.salvationmod.core.colony.buildings;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.salvationmod.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;

import net.minecraft.core.BlockPos;

public class BuildingEnvironmentalLab extends AbstractBuilding
{

    public BuildingEnvironmentalLab(@NotNull IColony colony, BlockPos pos)
    {
        super(colony, pos);
    }

    @Override
    public String getSchematicName()
    {
        return ModBuildings.ENVIRONMENTAL_LAB;
    }
    
}
