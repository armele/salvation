package com.deathfrog.salvationmod.core.colony.buildings.modules;

import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.salvationmod.core.blockentity.Beacon;
import com.deathfrog.salvationmod.core.blockentity.PurificationBeaconCoreBlockEntity;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class LabBeaconModule extends AbstractBuildingModule
{

    /**
     * Gather beacon information and serialize it to the view. 
     *
     * @param buf the buffer to serialize the trade list to.
     */
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        // Get the beacons for this colony
        final Set<Beacon> beacons = PurificationBeaconCoreBlockEntity.getBeacons(this.getBuilding().getColony());

        // Deterministic ordering so the UI doesn’t “shuffle” each sync
        final java.util.List<Beacon> ordered = new java.util.ArrayList<>(beacons);
        ordered.sort(java.util.Comparator.comparingLong(b -> b.getPosition() == null ? Long.MIN_VALUE : b.getPosition().asLong()));

        // If you never expect null positions, you can omit the per-entry null flag and just write the long.
        buf.writeVarInt(ordered.size());

        for (final Beacon beacon : ordered)
        {
            final BlockPos pos = beacon.getPosition();

            // Handle null safely (in case a partially-built beacon exists)
            buf.writeBoolean(pos != null);
            if (pos != null)
            {
                buf.writeLong(pos.asLong()); // compact BlockPos encoding
            }

            buf.writeBoolean(beacon.isValid());
            buf.writeBoolean(beacon.isLit());
            buf.writeVarInt(beacon.getFuel());
        }
    }
}
