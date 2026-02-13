package com.deathfrog.salvationmod.core.blocks.huts;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import net.minecraft.resources.ResourceLocation;

public class BlockHutEnvironmentalLab extends SalvationBaseBlockHut
{
    public static final String HUT_NAME = "blockhutenvironmentallab";

    public BlockHutEnvironmentalLab()
    {
        super();
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.environmentalLab;
    }

    @Override
    public String getHutName()
    {
        return HUT_NAME;
    }


    /**
     * Returns the resource location of this block hut in the Minecraft registry.
     * The location is of the form "mctradepost:<hut_name>" where <hut_name> is the name
     * returned by {@link #getHutName()}.
     *
     * @return the resource location of this block hut in the Minecraft registry.
     * @throws IllegalStateException if the block hut has no name.
     */
    @Nonnull
    public ResourceLocation getRegistryName()
    {
        String name = this.getHutName();
        if (name == null)
        {
            throw new IllegalStateException("Block hut has no name");
        }
        else
        {
            ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, name);
            return (ResourceLocation) NullnessBridge.assumeNonnull(resLoc);
        }
    }
}
