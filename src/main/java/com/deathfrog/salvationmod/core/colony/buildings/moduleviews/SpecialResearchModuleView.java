package com.deathfrog.salvationmod.core.colony.buildings.moduleviews;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.client.gui.modules.WindowSpecialResearch;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingStatisticsModuleView;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class SpecialResearchModuleView extends BuildingStatisticsModuleView
{
    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/gui/modules/environmentalresearch.png");
    }

    @Override
    public Component getDesc()
    {
        return Component.translatable("com.salvation.gui.modules.special_research");
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowSpecialResearch(this, this.getBuildingStatisticsManager());
    }
}
