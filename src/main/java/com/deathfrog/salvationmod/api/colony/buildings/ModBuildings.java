package com.deathfrog.salvationmod.api.colony.buildings;

import com.deathfrog.salvationmod.ModBlocks;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import org.jetbrains.annotations.NotNull;

public class ModBuildings
{
    public static final String ENVIRONMENTAL_LAB   = "environmental_lab";

     public static BuildingEntry environmentalLab;

    private ModBuildings()
    {
        throw new IllegalStateException("Tried to initialize: ModBuildings but this is a Utility class.");
    }

    @NotNull
    public static AbstractBlockHut<?>[] getHuts()
    {
        return new AbstractBlockHut[] 
        {
            ModBlocks.blockHutEnvironmentalLab.get(),
        };
    }
}
