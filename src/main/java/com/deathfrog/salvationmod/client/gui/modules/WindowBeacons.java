package com.deathfrog.salvationmod.client.gui.modules;

import java.util.List;

import com.deathfrog.salvationmod.client.BeaconHighlighter;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.blockentity.Beacon;
import com.deathfrog.salvationmod.core.blockentity.PurificationBeaconCoreBlockEntity;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.deathfrog.salvationmod.core.colony.buildings.moduleviews.LabBeaconModuleView;
import com.ldtteam.blockui.BOGuiGraphics;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.AbstractTextBuilder;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.blockui.views.View;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class WindowBeacons extends AbstractModuleWindow<LabBeaconModuleView> 
{
    private static final String LABBEACON_WINDOW = "gui/layouthuts/layoutlabbeaconmodule.xml";
    private static final String LABEL_BEACONLIST = "beaconlist";
    private static final String BEACON_HIGHLIGHT = "beaconhighlight";


    private final ScrollingList beaconList;

    public WindowBeacons(LabBeaconModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, LABBEACON_WINDOW));

        beaconList = this.window.findPaneOfTypeByID(LABEL_BEACONLIST, ScrollingList.class);
    }

    @Override
    public void onOpened()
    {
        super.onOpened();

        final Text howto = findPaneOfTypeByID("title", Text.class);
        final AbstractTextBuilder.TooltipBuilder howtoTipBuilder = PaneBuilders.tooltipBuilder().hoverPane(howto);
        howtoTipBuilder.append(Component.translatable("com.salvation.coremod.gui.environmental_lab.beacon.hovertip"));
        howtoTipBuilder.build();
        
        final Image howToPic = findPaneOfTypeByID("help", Image.class);
        final AbstractTextBuilder.TooltipBuilder howtoTipPicBuilder = PaneBuilders.tooltipBuilder().hoverPane(howToPic);
        howtoTipPicBuilder.append(Component.translatable("com.salvation.coremod.gui.environmental_lab.beacon.hovertip"));
        howtoTipPicBuilder.build();

        updateBeaconList();
    }

    private void updateBeaconList()
    {
        IColonyView colonyView = moduleView.getColony();
        
        boolean enabled = colonyView.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_ENABLE_BEACONS) > 0; 
        int range = (int) (1.0 + colonyView.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_BEACON_RANGE)); 
        double power = 1.0 +  colonyView.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_BEACON_POWER); 
        int frequency = (int) (PurificationBeaconCoreBlockEntity.DEFAULT_PULSES_PER_DAY + colonyView.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_BEACON_FREQUENCY)); 

        final Text title = findPaneOfTypeByID("title", Text.class);
        if (enabled)
        {
            title.setText(Component.translatable("com.salvation.coremod.gui.environmental_lab.beacon.enabled"));
        }
        else
        {
            title.setText(Component.translatable("com.salvation.coremod.gui.environmental_lab.beacon.disabled"));
        }

        final Text rangeLabel =     findPaneOfTypeByID("research_range", Text.class);
        final Text powerLabel =     findPaneOfTypeByID("research_power", Text.class);
        final Text frequencyLabel = findPaneOfTypeByID("research_frequency", Text.class);

        rangeLabel.setText(Component.literal(range + " chunks"));
        powerLabel.setText(Component.literal("" + String.format("%.0f%%", power * 100)));
        frequencyLabel.setText(Component.literal(frequency + ""));

        beaconList.enable();
        beaconList.show();
        beaconList.setDataProvider(new ScrollingList.DataProvider()
        {
            List<Beacon> beaconList = moduleView.getBeacons();

            /**
             * The number of rows of the list.
             * 
             * @return the number.
             */
            @Override
            public int getElementCount()
            {
                return beaconList.size();
            }

            /**
             * Inserts the elements into each row.
             * 
             * @param index   the index of the row/list element.
             * @param rowPane the parent Pane for the row, containing the elements to update.
             */
            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                ClientLevel level = Minecraft.getInstance().level;

                if (level == null)
                {
                    return;
                }
                Beacon beacon = beaconList.get(index);

                final Image beaconImg = rowPane.findPaneOfTypeByID("beaconicon", Image.class);
                beaconImg.setImage(ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/gui/modules/beacondeco.png"), true);
                final Button highlightButton = ensureBeaconHighlightButton(rowPane);
                if (highlightButton != null)
                {
                    highlightButton.setEnabled(beacon.getPosition() != null);
                    highlightButton.setHandler(button -> BeaconHighlighter.highlight(beacon.getPosition()));
                    addBeaconHighlightTooltip(highlightButton, beacon.getPosition());
                }

                final Text position = rowPane.findPaneOfTypeByID("position", Text.class);
                String positionString = beacon.getPosition() == null ? null : beacon.getPosition().toShortString();
                position.setText(Component.literal(positionString == null ? "Missing" : positionString));
                final AbstractTextBuilder.TooltipBuilder positionTipBuilder = PaneBuilders.tooltipBuilder().hoverPane(position);
                positionTipBuilder.append(buildUpgradeTooltip(beacon));
                positionTipBuilder.build();

                final Image statusImg = rowPane.findPaneOfTypeByID("status", Image.class);
                String statusPath = null;
                String statusTooltip = null;

                if (beacon.isValid())
                {
                    if (beacon.isLit())
                    {
                        statusPath = "textures/gui/modules/validlit.png";
                        statusTooltip = "Valid, Lit";
                    }
                    else 
                    {
                        statusPath = "textures/gui/modules/validunlit.png";
                        statusTooltip = "Valid, Unlit";
                    }
                }
                else
                {
                    statusPath = "textures/gui/modules/invalid.png";
                    statusTooltip = "Invalid";
                }

                final AbstractTextBuilder.TooltipBuilder hoverPaneBuilder = PaneBuilders.tooltipBuilder().hoverPane(statusImg);
                    hoverPaneBuilder.append(Component.literal(statusTooltip));
                    hoverPaneBuilder.build();

                statusImg.setImage(ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, statusPath), true);

                final Text fuelText = rowPane.findPaneOfTypeByID("fuel", Text.class);

                String fuelString = beacon.getFuel() + "";

                fuelText.setText(Component.literal(fuelString == null ? "Unknown" : fuelString));
            }

        });
    }

    /**
     * Adds or reuses an invisible clickable overlay for the beacon icon in a row.
     *
     * @param row beacon row pane
     * @return clickable overlay button, or null if the row cannot contain children
     */
    private static Button ensureBeaconHighlightButton(final Pane row)
    {
        final Pane existing = row.findPaneByID(BEACON_HIGHLIGHT);
        if (existing instanceof Button button)
        {
            return button;
        }

        if (!(row instanceof View view))
        {
            return null;
        }

        final Button button = new InvisibleButton();
        button.setID(BEACON_HIGHLIGHT);
        button.setPosition(1, 2);
        button.setSize(16, 16);
        view.addChild(button);
        return button;
    }

    /**
     * Add the icon tooltip describing the highlight action.
     *
     * @param pane icon overlay pane
     * @param beaconPos beacon position
     */
    private static void addBeaconHighlightTooltip(final Pane pane, final BlockPos beaconPos)
    {
        pane.setHoverPane(null);
        PaneBuilders.tooltipBuilder()
            .append(beaconPos == null
                ? Component.literal("Beacon position missing")
                : Component.literal("Highlight beacon at " + beaconPos.toShortString()))
            .hoverPane(pane)
            .build();
    }

    /**
     * Add the tooltip that indicates what upgrades are installed for a given beacon.
     * 
     * @param beacon
     * @return
     */
    @SuppressWarnings("null")
    private static Component buildUpgradeTooltip(final Beacon beacon)
    {
        if (beacon.getUpgrades().isEmpty())
        {
            return Component.translatable("com.salvation.coremod.gui.environmental_lab.beacon.upgrades.none");
        }

        final net.minecraft.network.chat.MutableComponent tooltip = Component.translatable("com.salvation.coremod.gui.environmental_lab.beacon.upgrades");

        for (final Beacon.Upgrade upgrade : beacon.getUpgrades())
        {
            tooltip.append(Component.literal("\n"));
            tooltip.append(Component.literal("- "));
            tooltip.append(Component.translatable(upgrade.descriptionId()));

            if (upgrade.count() > 1)
            {
                tooltip.append(Component.literal(" x" + upgrade.count()));
            }
        }

        return tooltip;
    }

    /**
     * Click target layered over the beacon image without adding any rendered chrome.
     */
    private static final class InvisibleButton extends ButtonImage
    {
        @Override
        public void drawSelf(final BOGuiGraphics graphics, final double mx, final double my)
        {
            // Intentionally invisible; the Image below provides the visuals.
        }
    }

}
