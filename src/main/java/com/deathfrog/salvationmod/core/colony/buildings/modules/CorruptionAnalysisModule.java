package com.deathfrog.salvationmod.core.colony.buildings.modules;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler.MitigationSnapshot;
import com.deathfrog.salvationmod.core.engine.CorruptionAnalysisSnapshot;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Synchronizes the level-gated Environmental Lab corruption report. */
public class CorruptionAnalysisModule extends AbstractBuildingModule
{
    /**
     * Serializes only the analysis fields unlocked by the building level and staffing state.
     *
     * @param buf destination view buffer
     */
    @SuppressWarnings("null")
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        final int buildingLevel = getBuilding().getBuildingLevel();
        final boolean hasLabTechnician = !getBuilding().getAllAssignedCitizen().isEmpty();
        buf.writeVarInt(buildingLevel);
        buf.writeBoolean(hasLabTechnician);

        final Level level = getBuilding().getColony().getWorld();
        if (buildingLevel < 2 || !hasLabTechnician || !(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        final CorruptionAnalysisSnapshot snapshot = CorruptionAnalysisSnapshot.create(serverLevel);
        buf.writeEnum(snapshot.stage());

        if (buildingLevel >= 3)
        {
            buf.writeLong(snapshot.progression());
            buf.writeLong(snapshot.currentThreshold());
            buf.writeLong(snapshot.nextThreshold());
            buf.writeDouble(snapshot.progressPercent());
        }

        if (buildingLevel >= 4)
        {
            buf.writeVarInt(snapshot.sourceTotals().size());
            for (final ProgressionSource source : ProgressionSource.values())
            {
                buf.writeEnum(source);
                buf.writeLong(snapshot.sourceTotals().getOrDefault(source, 0L));
            }
        }

        if (buildingLevel >= 5)
        {
            final MitigationSnapshot mitigation = SalvationColonyHandler
                .getHandler(serverLevel, getBuilding().getColony())
                .getMitigationSnapshot();
            buf.writeVarInt(mitigation.windowDays());
            buf.writeLong(mitigation.rollingPurification());
            buf.writeLong(mitigation.rollingCorruption());
            buf.writeLong(mitigation.rollingNet());
            buf.writeDouble(mitigation.mitigationRatio());
            buf.writeEnum(mitigation.trend());
            buf.writeLong(mitigation.lifetimePurification());
            buf.writeLong(mitigation.lifetimeCorruption());
        }
    }
}
