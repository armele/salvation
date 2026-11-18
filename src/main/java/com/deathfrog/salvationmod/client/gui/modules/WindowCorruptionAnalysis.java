package com.deathfrog.salvationmod.client.gui.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler.MitigationSnapshot;
import com.deathfrog.salvationmod.core.colony.buildings.moduleviews.CorruptionAnalysisModuleView;
import com.deathfrog.salvationmod.core.colony.buildings.moduleviews.CorruptionAnalysisModuleView.SourceTotal;
import com.deathfrog.salvationmod.core.engine.CorruptionStage;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Environmental Lab corruption analysis window. */
public class WindowCorruptionAnalysis extends AbstractModuleWindow<CorruptionAnalysisModuleView>
{
    private static final String LAYOUT = "gui/layouthuts/layoutcorruptionanalysismodule.xml";
    private static final String TRANSLATION_PREFIX = "com.salvation.coremod.gui.environmental_lab.analysis.";
    private static final int MAXIMUM_STAGE = CorruptionStage.values().length - 1;

    /**
     * Creates the analysis window.
     *
     * @param moduleView synchronized analysis module view
     */
    public WindowCorruptionAnalysis(final CorruptionAnalysisModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, LAYOUT));
    }

    /** {@inheritDoc} */
    @Override
    public void onOpened()
    {
        super.onOpened();
        updateReport();
    }

    private void updateReport()
    {
        final int level = moduleView.getAnalysisLevel();
        setVisible("future", level < 2);
        setVisible("technician", level >= 2 && !moduleView.hasLabTechnician());
        final boolean reportAvailable = level >= 2 && moduleView.hasLabTechnician();
        setVisible("report", reportAvailable);

        if (!reportAvailable)
        {
            return;
        }

        final Text stageName = findPaneOfTypeByID("stage_name", Text.class);
        stageName.setText(Component.translatable(TRANSLATION_PREFIX + moduleView.getStage().getSerializedName()));
        PaneBuilders.tooltipBuilder()
            .append(Component.translatable(
                TRANSLATION_PREFIX + "stage_tooltip",
                moduleView.getStage().ordinal(),
                MAXIMUM_STAGE))
            .hoverPane(stageName)
            .build();
        setVisible("progress_section", level >= 3);
        setVisible("sources_section", level >= 4);
        setVisible("mitigation_section", level >= 5);

        if (level >= 3)
        {
            if (moduleView.getNextThreshold() < 0L)
            {
                setText("progress", Component.translatable(TRANSLATION_PREFIX + "terminal"));
            }
            else
            {
                setText("progress", Component.translatable(
                    TRANSLATION_PREFIX + "progress",
                    String.format(Locale.ROOT, "%.1f%%", moduleView.getProgressPercent())));
            }
        }

        if (level >= 4)
        {
            setText("corruption_header", Component.translatable(TRANSLATION_PREFIX + "corruption_sources").withStyle(ChatFormatting.BOLD));
            updateSourceList();
        }
        if (level >= 5 && moduleView.getMitigation() != null)
        {
            updateMitigation(moduleView.getMitigation());
        }
    }

    private void updateSourceList()
    {
        final List<SourceTotal> displayed = new ArrayList<>();
        for (final SourceTotal total : moduleView.getSourceTotals())
        {
            if (total.amount() > 0L)
            {
                displayed.add(total);
            }
        }

        final ScrollingList list = findPaneOfTypeByID("corruption_sources", ScrollingList.class);
        list.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return displayed.size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                final SourceTotal total = displayed.get(index);
                final Text name = rowPane.findPaneOfTypeByID("source_name", Text.class);
                final Text amount = rowPane.findPaneOfTypeByID("source_amount", Text.class);
                name.setText(Component.translatable(TRANSLATION_PREFIX + "source." + total.source().name().toLowerCase(Locale.ROOT)));
                amount.setText(Component.literal(Long.toString(total.amount()) + ""));
                rowPane.setHoverPane(null);
                PaneBuilders.tooltipBuilder()
                    .append(Component.translatable(
                        TRANSLATION_PREFIX + "source_tooltip." + total.source().name().toLowerCase(Locale.ROOT)))
                    .hoverPane(rowPane)
                    .build();
            }
        });
    }

    private void updateMitigation(final MitigationSnapshot mitigation)
    {
        setText("mitigation_header", Component.translatable(TRANSLATION_PREFIX + "mitigation.header").withStyle(ChatFormatting.BOLD));
        if (moduleView.getNextThreshold() < 0L)
        {
            setText("mitigation_contribution", Component.translatable(TRANSLATION_PREFIX + "mitigation.contribution_terminal"));
        }
        else
        {
            final double progressionContribution = -(double) mitigation.rollingNet() / (double) moduleView.getNextThreshold();
            setText("mitigation_contribution", Component.translatable(
                TRANSLATION_PREFIX + "mitigation.contribution",
                String.format(Locale.ROOT, "%.1f%%", progressionContribution * 100.0D)));
        }
        setText("mitigation_ratio", Component.translatable(
            TRANSLATION_PREFIX + "mitigation.ratio",
            String.format(Locale.ROOT, "%.1f%%", mitigation.mitigationRatio() * 100.0D)));
        setText("mitigation_trend", Component.translatable(
            TRANSLATION_PREFIX + "mitigation.trend",
            Component.translatable(TRANSLATION_PREFIX + "trend." + mitigation.trend().name().toLowerCase(Locale.ROOT))));
        setTooltip(
            "mitigation_contribution",
            Component.translatable(TRANSLATION_PREFIX + "mitigation.contribution_tooltip", mitigation.windowDays()));
        setTooltip(
            "mitigation_ratio",
            Component.translatable(TRANSLATION_PREFIX + "mitigation.ratio_tooltip", mitigation.windowDays()));
    }

    private void setTooltip(final String id, final Component component)
    {
        final Pane pane = window.findPaneByID(id);
        pane.setHoverPane(null);
        PaneBuilders.tooltipBuilder().append(component).hoverPane(pane).build();
    }

    private void setText(final String id, final Component component)
    {
        findPaneOfTypeByID(id, Text.class).setText(component);
    }

    private void setVisible(final String id, final boolean visible)
    {
        final Pane pane = window.findPaneByID(id);
        if (visible)
        {
            pane.show();
            pane.enable();
        }
        else
        {
            pane.hide();
            pane.disable();
        }
    }
}
