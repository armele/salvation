package com.deathfrog.salvationmod.core.client.gui.modules;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.colony.buildings.modules.WithdrawMessage;
import com.deathfrog.salvationmod.ModItems;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.colony.buildings.modules.BuildingSpecialResearchModule;
import com.deathfrog.salvationmod.core.colony.buildings.moduleviews.SpecialResearchModuleView;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.DropDownList;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import java.text.NumberFormat;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import static com.minecolonies.api.util.constant.WindowConstants.*;

/**
 * BOWindow for the Marketplace hut's ECON module.
 */
public class WindowSpecialResearch extends AbstractModuleWindow<SpecialResearchModuleView>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private IStatisticsManager statsManager = null;
    /**
     * Drop down list for interval.
     */
    private DropDownList intervalDropdown;

    /**
     * Current selected interval.
     */
    public String selectedInterval = "com.mctradepost.coremod.gui.interval.yesterday";

    public static final String CREDITS_MINTED = "credits.minted";
    public static final String CURRENT_BALANCE = "current_balance";
    public static final String WITHDRAW_TOOLTIP = "com.salvation.gui.special_research.withdraw.tooltip";
    public static final String TAG_BUTTON_WITHDRAW_RESEARCH_CREDIT = "withdrawResearchCredit";

    /**
     * Util tags.
     */
    private static final String SPECIALRESEARCH_WINDOW = "gui/layouthuts/layoutspecialresearchmodule.xml";

    public WindowSpecialResearch(SpecialResearchModuleView specialResearchModuleView, IStatisticsManager statsManager)
    {
        super(specialResearchModuleView, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, SPECIALRESEARCH_WINDOW));
        this.statsManager = statsManager;

        Button withdraw = findPaneOfTypeByID(TAG_BUTTON_WITHDRAW_RESEARCH_CREDIT, Button.class);
        registerButton(TAG_BUTTON_WITHDRAW_RESEARCH_CREDIT, this::withdrawResearchCredit);
        PaneBuilders.tooltipBuilder().hoverPane(withdraw).build().setText(Component.translatable(WITHDRAW_TOOLTIP));
    }

    /**
     * Map of intervals.
     */
    private static final LinkedHashMap<String, Integer> INTERVAL = new LinkedHashMap<>();

    // Continue to use the same intervals from Trade Post
    static
    {
        INTERVAL.put("com.mctradepost.coremod.gui.interval.yesterday", 1);
        INTERVAL.put("com.mctradepost.coremod.gui.interval.lastweek", 7);
        INTERVAL.put("com.mctradepost.coremod.gui.interval.100days", 100);
        INTERVAL.put("com.mctradepost.coremod.gui.interval.alltime", -1);
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        updateStats();
    }

    /**
     * Get the stat value for the given id and interval.
     * If the interval is invalid, 0 is returned and a warning is logged.
     * If the interval is positive, the stat value is retrieved from the given interval.
     * If the interval is negative, the stat value is retrieved from the start of the colony.
     * @param statsMan the statistics manager to use.
     * @param id the id of the stat to retrieve.
     * @param intervalArg the interval to use, as defined in INTERVAL.
     * @return the stat value, or 0 if the interval is invalid.
     */
    private int getStatFor(IStatisticsManager statsMan, String id, String intervalArg)
    {
        int stat = statsMan.getStatTotal(id);

        if (!INTERVAL.containsKey(intervalArg))
        {
            LOGGER.warn("Invalid interval: " + intervalArg);
            return 0;
        }

        int interval = INTERVAL.get(intervalArg);
        if (interval > 0)
        {
            stat = statsMan.getStatsInPeriod(id, buildingView.getColony().getDay() - interval, buildingView.getColony().getDay());
        }

        return stat;
    }

    /**
     * Update the display for the stats.
     */
    private void updateStats()
    {
        int currentBalance = getStatFor(buildingView.getColony().getStatisticsManager(),
            BuildingSpecialResearchModule.RESEARCH_BALANCE,
            "com.mctradepost.coremod.gui.interval.alltime");
        final Text balanceLabel = findPaneOfTypeByID(CURRENT_BALANCE, Text.class);
        NumberFormat formatter = NumberFormat.getIntegerInstance(); // or getCurrencyInstance() if using symbols
        String formattedSales = "ƒ" + formatter.format(currentBalance);
        balanceLabel.setText(Component.literal(formattedSales));

        int researchGenerated = getStatFor(statsManager, BuildingSpecialResearchModule.RESEARCH_GENERATED, selectedInterval);
        final Text researchLabel = findPaneOfTypeByID("totalresearch", Text.class);
        formattedSales = "ƒ" + formatter.format(researchGenerated);
        researchLabel.setText(Component.literal(formattedSales));

        final ItemIcon coinIcon = findPaneOfTypeByID("researchicon", ItemIcon.class);
        BucketItem coinItem = ModItems.POLLUTED_WATER_BUCKET.get();
        coinIcon.setItem(new ItemStack(NullnessBridge.assumeNonnull(coinItem), 1));

        // TODO: Replace with special research symbol and value;
        int itemValue = MCTPConfig.tradeCoinValue.get();
        final Text valueLabel = findPaneOfTypeByID("researchvalue", Text.class);
        String formattedLabel = "= ƒ" + formatter.format(itemValue);
        valueLabel.setText(Component.literal(formattedLabel));

        // MCTradePostMod.LOGGER.info("Stats: {} items sold, {} cash generated (formatted as {})", itemCount, cashGenerated,
        // formattedSales);

        intervalDropdown = findPaneOfTypeByID(DROPDOWN_INTERVAL_ID, DropDownList.class);
        intervalDropdown.setHandler(this::onDropDownListChanged);

        intervalDropdown.setDataProvider(new DropDownList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return INTERVAL.size();
            }

            @Override
            public MutableComponent getLabel(final int index)
            {
                String label = (String) INTERVAL.keySet().toArray()[index];
                return Component.translatableEscape(label == null ? "" : label);
            }
        });
        intervalDropdown.setSelectedIndex(new ArrayList<>(INTERVAL.keySet()).indexOf(selectedInterval));
    }


    /**
     * Called when the dropdown list changes.
     * Updates the stats to reflect the new interval.
     * @param dropDownList the dropdown list that changed.
     */
    private void onDropDownListChanged(final DropDownList dropDownList)
    {
        final String temp = (String) INTERVAL.keySet().toArray()[dropDownList.getSelectedIndex()];
        if (!temp.equals(selectedInterval))
        {
            selectedInterval = temp;
            updateStats();
        }
    }

    /**
     * On click withdraw one trade coin.
     *
     * @param button the clicked button.
     */
    private void withdrawResearchCredit(@NotNull final Button button)
    {
        // TODO: Implement special research message
        WithdrawMessage withdrawal = new WithdrawMessage(buildingView);
        withdrawal.sendToServer();
        updateStats();
    }
}
