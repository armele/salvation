package com.deathfrog.salvationmod.client.gui.modules;

import java.util.ArrayList;
import java.util.List;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.colony.buildings.moduleviews.LabDiscoveryModuleView;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class WindowDiscoveries extends AbstractModuleWindow<LabDiscoveryModuleView>
{
    private static final String LABDISCOVERY_WINDOW = "gui/layouthuts/layoutdiscoverymodule.xml";

    private static String TAG_BUTTON_VIEWDETAIL = "viewDetail";
    private static String TAG_LISTBOX = "discoverylist";
    private static String TAG_SHORTDESC = "shortdesc";
    private static String TAG_DETAIL = "detail";
    private static String DISCOVERY_PREFIX = "com.salvation.coremod.gui.environmental_lab.discovery";

    private final ScrollingList discoveryList;
    private final List<Discovery> allDiscoveries = List.of(
        new Discovery("signs", 1),
        new Discovery("refugees", 1),
        new Discovery("impact", 1),
        new Discovery("corruption", 1),
        new Discovery("essence", 2),
        new Discovery("extractor", 2),
        new Discovery("inverter", 2),
        new Discovery("beacon_fuel", 2),
        new Discovery("exteritio", 3),
        new Discovery("voraxium", 3),
        new Discovery("locator", 3),
        new Discovery("upgrades", 3),
        new Discovery("unstable_materials", 4),
        new Discovery("voraxian", 4),
        new Discovery("difficulty", 4),
        new Discovery("enchantments", 4),
        new Discovery("exploration", 5),
        new Discovery("portal", 5),
        new Discovery("overlord", 5)
    );

    private final List<String> knownDiscoveries = new ArrayList<>();

    public WindowDiscoveries(LabDiscoveryModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, LABDISCOVERY_WINDOW));
        registerButton(TAG_BUTTON_VIEWDETAIL, this::viewDetail);

        discoveryList = this.window.findPaneOfTypeByID(TAG_LISTBOX, ScrollingList.class);
        buildDiscoveries();
    }
    
    /**
     * Build the list of all discoveries that the player has access to.
     * This is done by iterating over all the discoveries in the map and adding
     * them to the list if the building level is greater than or equal to
     * the required level for the discovery.
     */
    private void buildDiscoveries()
    {
        for (Discovery discovery : allDiscoveries)
        {
            if (buildingView.getBuildingLevel() >= discovery.requiredLevel())
            {
                knownDiscoveries.add(discovery.key());
            }
        }
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        updateDiscoveryList();
    }

    private void updateDiscoveryList()
    {
        discoveryList.setDataProvider(new ScrollingList.DataProvider()
        {

            @Override
            public int getElementCount()
            {    
                return knownDiscoveries.size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane)
            {   
                final Text shortDesc = rowPane.findPaneOfTypeByID(TAG_SHORTDESC, Text.class);
                String discoveryKey = knownDiscoveries.get(index);
                shortDesc.setText(Component.translatable(DISCOVERY_PREFIX + "." + discoveryKey + "." + TAG_SHORTDESC));
            }

        });
    }

    /**
     * View the detail for the discovery associated with the given button.
     * This is done by getting the index of the button in the list and
     * retrieving the associated discovery key. The detail text is then
     * set to a translatable component with the discovery key.
     * 
     * @param button the button that was clicked.
     */
    private void viewDetail(final Button button)
    {
        final int row = discoveryList.getListElementIndexByPane(button);

        final Text detail = findPaneOfTypeByID(TAG_DETAIL, Text.class);

        String discoveryKey = knownDiscoveries.get(row);
        detail.setText(Component.translatable(DISCOVERY_PREFIX + "." + discoveryKey + "." + TAG_DETAIL));
    }

    private record Discovery(String key, int requiredLevel)
    {
    }
}
