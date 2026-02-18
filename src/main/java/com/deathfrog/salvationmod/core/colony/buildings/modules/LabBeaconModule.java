package com.deathfrog.salvationmod.core.colony.buildings.modules;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.core.blockentity.Beacon;
import com.deathfrog.salvationmod.core.blockentity.PurificationBeaconCoreBlockEntity;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class LabBeaconModule extends AbstractBuildingModule implements ITickingModule
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    @Override
    public void onColonyTick(@NotNull IColony colony) 
    {
        Level level = colony.getWorld();

        if (!(level instanceof ServerLevel serverlevel)) return;

        final Set<Beacon> beacons = PurificationBeaconCoreBlockEntity.getBeacons(colony);
        for (final Beacon beacon : beacons)
        {
            BlockPos pos = beacon.getPosition();
            if (pos == null)
            {
                continue;
            }

            final BlockState state = serverlevel.getBlockState(pos);

            boolean valid = state.is(NullnessBridge.assumeNonnull(ModBlocks.PURIFICATION_BEACON_CORE.get()));

            if (!valid)
            {
                TraceUtils.dynamicTrace(ModCommands.TRACE_BEACON, () -> LOGGER.info("Clearing invalid beacon at {}.", pos));
                PurificationBeaconCoreBlockEntity.clearBeacon(colony, pos);
            }
        }
        
        markDirty();
    }

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
