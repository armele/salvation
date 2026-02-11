package com.deathfrog.salvationmod.core.colony.buildings.modules;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModItems;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.util.MathUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.managers.StatisticsManager;
import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import static com.deathfrog.salvationmod.ModCommands.TRACE_RESEARCHCREDIT;

public class BuildingSpecialResearchModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule
{
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    private IStatisticsManager statisticsManager = new StatisticsManager();
    public static final String RESEARCH_BALANCE = "research_balance";
    public static final String RESEARCH_SPENT = "research_spent";
    public static final String RESEARCH_GENERATED = "special_research.generated";
    public static final String CREDITS_WITHDRAWN = "credits_withdrawn";


    public static @Nonnull Item researchCreditItem()
    {
        return NullnessBridge.assumeNonnull(ModItems.RESEARCH_CREDIT.get());
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        statisticsManager.readFromNBT(compound);
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        statisticsManager.writeToNBT(compound);
    }

    @Override
    public void serializeToView(final RegistryFriendlyByteBuf buf, final boolean fullSync)
    {
        statisticsManager.serialize(buf, fullSync);
    }

    /**
     * Get the statistic manager of the building.
     * @return the manager.
     */
    public IStatisticsManager getBuildingStatisticsManager()
    {
        return statisticsManager;
    }

    /**
     * Deposits a given amount of research credits into the research module. 
     * adding the corresponding research balance.
     * @param amount the count to deposit
     */
    public void deposit(final int amount)
    {
        TraceUtils.dynamicTrace(TRACE_RESEARCHCREDIT, () -> LOGGER.info("Deposited {} research credits.", amount));

        statisticsManager.incrementBy(RESEARCH_GENERATED, amount, building.getColony().getDay());
        // Colony stats (the official research balance)
        building.getColony().getStatisticsManager().incrementBy(RESEARCH_BALANCE, amount, building.getColony().getDay());

        if (MathUtils.RANDOM.nextInt(10) <= amount)
        {
            markDirty();
        }
    }


    public void spend(final int count)
    {
        statisticsManager.incrementBy(RESEARCH_SPENT, count, building.getColony().getDay());

        // Colony stats (the official research balance)
        building.getColony().getStatisticsManager().incrementBy(RESEARCH_SPENT, count, building.getColony().getDay());

        if (MathUtils.RANDOM.nextInt(10) <= count)
        {
            markDirty();
        }
    }

    /**
     * Returns the total balance for the building.
     * @return the total balance.
     */
    public int getTotalBalance()
    {
        IStatisticsManager statsManager = building.getColony().getStatisticsManager();
        return statsManager.getStatTotal(RESEARCH_BALANCE);
    }

    /**
     * Called every colony tick.
     * This method is responsible for generating research credits whenever there are more researchers than research in progress.
     * It will deposit the difference between the number of researchers and the number of research in progress into the building's 
     * research balance.
     * @param colony the colony to generate research credits for.
     */
    @Override
    public void onColonyTick(@NotNull IColony colony) 
    {

        // LOGGER.info("Special Research Module: Colony tick");

        final Set<ICitizenData> citizens = building.getAllAssignedCitizen();
        final List<ILocalResearch> inProgress = colony.getResearchManager().getResearchTree().getResearchInProgress();
        
        int countResearchers = 0;
        int countResearchInProgress = 0;

        if (citizens == null || citizens.isEmpty()) 
        {
            return;
        }

        countResearchers = citizens.size();

        if (inProgress != null) 
        {
            countResearchInProgress = inProgress.size();
        }

        // LOGGER.info("Special Research Module has {} researchers and {} research in progress", countResearchers, countResearchInProgress);


        // Whenever we have more researchers than research in progress, generate a research credit
        if (countResearchers > countResearchInProgress) 
        {
            final int depositAmount = countResearchers - countResearchInProgress;

            TraceUtils.dynamicTrace(TRACE_RESEARCHCREDIT, () -> LOGGER.info("Special Research Module depositing {} research credits.", depositAmount));

            deposit(depositAmount);
        }

    }

    /**
     * Mints a given number of trade coins, removing the corresponding amount of value from the building's economy.
     * 
     * @param player      the player using the minting function (not used, but required for later potential functionality)
     * @param coinsToMint the number of coins to mint
     * @return a stack of the minted coins
     */
    public ItemStack mintSpecialResearchCredit(Player player, int creditsToMint)
    {
        int creditValue = MCTPConfig.tradeCoinValue.get();
        ItemStack coinStack = ItemStack.EMPTY;

        if (creditsToMint > 0)
        {
            int valueToRemove = creditsToMint * creditValue;

            if (valueToRemove < getTotalBalance())
            {
                Item creditItem = researchCreditItem();

                coinStack = new ItemStack(creditItem, creditsToMint);

                StatsUtil.trackStat(building, CREDITS_WITHDRAWN, creditsToMint);
                deposit(-valueToRemove);
                markDirty();
            }
            else
            {
                if (player != null)
                {
                    MessageUtils.format("salvation.special_research.nsf").sendTo(player);
                }
            }
        }

        return coinStack;
    }

    /**
     * Deposits a given stack of research credits into the building's supply, adding the corresponding amount of value to the balance.
     * 
     * @param player         the player using the depositing function (not used, but required for later potential functionality)
     * @param creditsToDeposit the stack of coins to deposit
     */
    public void depositCredits(Player player, ItemStack creditsToDeposit)
    {
        int creditValue = MCTPConfig.tradeCoinValue.get();

        Item creditItem = researchCreditItem();

        if (!creditsToDeposit.is(creditItem))
        {
            return;
        }

        if (creditsToDeposit.getCount() > 0)
        {
            int valueToAdd = creditsToDeposit.getCount() * creditValue;

            deposit(valueToAdd);

            // Now remove the coins from the player's inventory
            creditsToDeposit.setCount(0);
            player.getInventory().setChanged();
        }
    }

}
