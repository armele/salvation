package com.deathfrog.salvationmod.core.colony.buildings.moduleviews;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.client.gui.modules.WindowDiscoveries;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class LabDiscoveryModuleView extends AbstractBuildingModuleView
{

    @Override
    public void deserialize(@NotNull RegistryFriendlyByteBuf arg0)
    {
        // No-op
    }

    @Override
    public @Nullable Component getDesc()
    {
        return(Component.translatable("com.salvation.coremod.gui.environmental_lab.discovery"));
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowDiscoveries(this);
    }
    
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/gui/modules/discoveries.png");
    }
}
