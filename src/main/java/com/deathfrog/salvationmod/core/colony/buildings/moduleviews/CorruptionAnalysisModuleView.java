package com.deathfrog.salvationmod.core.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.client.gui.modules.WindowCorruptionAnalysis;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler.MitigationSnapshot;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler.MitigationTrend;
import com.deathfrog.salvationmod.core.engine.CorruptionStage;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Client-side state for the Environmental Lab corruption report. */
public class CorruptionAnalysisModuleView extends AbstractBuildingModuleView
{
    private int analysisLevel;
    private boolean hasLabTechnician;
    private CorruptionStage stage = CorruptionStage.STAGE_0_UNTRIGGERED;
    private long progression;
    private long currentThreshold;
    private long nextThreshold = -1L;
    private double progressPercent;
    private final List<SourceTotal> sourceTotals = new ArrayList<>();
    private MitigationSnapshot mitigation;

    /** {@inheritDoc} */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        analysisLevel = buf.readVarInt();
        hasLabTechnician = buf.readBoolean();
        sourceTotals.clear();
        mitigation = null;

        if (analysisLevel < 2 || !hasLabTechnician)
        {
            return;
        }

        stage = buf.readEnum(CorruptionStage.class);
        if (analysisLevel >= 3)
        {
            progression = buf.readLong();
            currentThreshold = buf.readLong();
            nextThreshold = buf.readLong();
            progressPercent = buf.readDouble();
        }
        if (analysisLevel >= 4)
        {
            final int sourceCount = buf.readVarInt();
            for (int i = 0; i < sourceCount; i++)
            {
                sourceTotals.add(new SourceTotal(buf.readEnum(ProgressionSource.class), buf.readLong()));
            }
        }
        if (analysisLevel >= 5)
        {
            mitigation = new MitigationSnapshot(
                buf.readVarInt(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong(),
                buf.readDouble(),
                buf.readEnum(MitigationTrend.class),
                buf.readLong(),
                buf.readLong());
        }
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.salvation.coremod.gui.environmental_lab.analysis");
    }

    /** {@inheritDoc} */
    @Override
    public BOWindow getWindow()
    {
        return new WindowCorruptionAnalysis(this);
    }

    /** {@inheritDoc} */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/gui/modules/analysis.png");
    }

    /** @return synchronized building analysis level */
    public int getAnalysisLevel()
    {
        return analysisLevel;
    }

    /** @return whether the building has an assigned lab technician */
    public boolean hasLabTechnician()
    {
        return hasLabTechnician;
    }

    /** @return current corruption stage */
    public CorruptionStage getStage()
    {
        return stage;
    }

    /** @return current effective progression */
    public long getProgression()
    {
        return progression;
    }

    /** @return threshold at which the current stage begins */
    public long getCurrentThreshold()
    {
        return currentThreshold;
    }

    /** @return next threshold, or a negative value at the terminal stage */
    public long getNextThreshold()
    {
        return nextThreshold;
    }

    /** @return within-stage progress percentage */
    public double getProgressPercent()
    {
        return progressPercent;
    }

    /** @return synchronized lifetime source totals */
    public List<SourceTotal> getSourceTotals()
    {
        return List.copyOf(sourceTotals);
    }

    /** @return colony mitigation report, or null below level five */
    public @Nullable MitigationSnapshot getMitigation()
    {
        return mitigation;
    }

    /**
     * Signed lifetime total for a progression source.
     *
     * @param source progression source
     * @param amount signed lifetime amount
     */
    public record SourceTotal(ProgressionSource source, long amount)
    {
    }
}
