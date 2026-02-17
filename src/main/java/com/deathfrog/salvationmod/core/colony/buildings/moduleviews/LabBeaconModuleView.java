package com.deathfrog.salvationmod.core.colony.buildings.moduleviews;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.client.gui.modules.WindowBeacons;
import com.deathfrog.salvationmod.core.blockentity.Beacon;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class LabBeaconModuleView extends AbstractBuildingModuleView
{   
    final List<Beacon> beacons = new java.util.ArrayList<>();

    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        this.beacons.clear();

        final int count = buf.readVarInt();

        for (int i = 0; i < count; i++)
        {
            final boolean hasPos = buf.readBoolean();
            final BlockPos pos = hasPos ? BlockPos.of(buf.readLong()) : null;

            final boolean valid = buf.readBoolean();
            final boolean lit = buf.readBoolean();
            final int fuel = buf.readVarInt();

            this.beacons.add(new Beacon(pos, valid, lit, fuel));
        }
    }

    @Override
    public @Nullable Component getDesc()
    {
        return(Component.translatable("com.salvation.coremod.gui.environmental_lab.beacon"));
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowBeacons(this);
    }
    
   @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/gui/modules/beacon.png");
    }

    public List<Beacon> getBeacons()
    {
        return this.beacons;
    }

}
