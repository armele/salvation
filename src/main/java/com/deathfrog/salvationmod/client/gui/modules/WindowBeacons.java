package com.deathfrog.salvationmod.client.gui.modules;

import java.util.List;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.blockentity.Beacon;
import com.deathfrog.salvationmod.core.blockentity.PurificationBeaconCoreBlockEntity;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.deathfrog.salvationmod.core.colony.buildings.moduleviews.LabBeaconModuleView;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.AbstractTextBuilder;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class WindowBeacons extends AbstractModuleWindow<LabBeaconModuleView> 
{
    private static final String LABBEACON_WINDOW = "gui/layouthuts/layoutlabbeaconmodule.xml";
    private static final String LABEL_BEACONLIST = "beaconlist";


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
        
        updateBeaconList();
    }

    private void updateBeaconList()
    {
        IColonyView colonyView = moduleView.getColony();
        
        int enabled = (int) colonyView.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_ENBABLE_BEACONS); 
        int range = (int) (1.0 + colonyView.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_BEACON_RANGE)); 
        double power = 1.0 +  colonyView.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_BEACON_POWER); 
        int frequency = (int) (PurificationBeaconCoreBlockEntity.DEFAULT_PULSES_PER_DAY + colonyView.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_BEACON_FREQUENCY)); 

        final Text title = findPaneOfTypeByID("title", Text.class);
        if (enabled > 0)
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

                final Image beaconIcon = rowPane.findPaneOfTypeByID("beaconicon", Image.class);
                beaconIcon.setImage(ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/gui/modules/beacondeco"), true);

                final Text position = rowPane.findPaneOfTypeByID("position", Text.class);
                String positionString = beacon.getPosition().toString();
                position.setText(Component.literal(positionString == null ? "Missing" : positionString));

                final Text status = rowPane.findPaneOfTypeByID("status", Text.class);
                String statusString = null;

                if (beacon.isValid())
                {
                    statusString = "Valid";

                    if (beacon.isLit())
                    {
                        statusString = statusString + " (Lit)";
                    }
                    else 
                    {
                        statusString = statusString + " (Unlit)";
                    }
                }
                else
                {
                    statusString = "Invalid";
                }

                status.setText(Component.literal(statusString == null ? "Unknown" : statusString));

                final Text fuelText = rowPane.findPaneOfTypeByID("fuel", Text.class);

                String fuelString = beacon.getFuel() + "";

                fuelText.setText(Component.literal(fuelString == null ? "Unknown" : fuelString));
            }

        });
    }

}
