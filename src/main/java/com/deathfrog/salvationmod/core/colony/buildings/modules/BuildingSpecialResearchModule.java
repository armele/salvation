package com.deathfrog.salvationmod.core.colony.buildings.modules;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.util.MathUtils;
import com.minecolonies.core.colony.managers.StatisticsManager;
import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class BuildingSpecialResearchModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule
{
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    
    private IStatisticsManager statisticsManager = new StatisticsManager();
    public static final String RESEARCH_BALANCE = "research_balance";
    public static final String RESEARCH_GENERATED = "special_research.generated";

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
     * Helper method for incrementation of the stats.
     * @param s the stat id to increment.
     */
    public void increment(final String s)
    {
       statisticsManager.increment(s, building.getColony().getDay());
       if (MathUtils.RANDOM.nextInt(10) == 0)
       {
           markDirty();
       }
    }

    /**
     * Helper method for incrementation of the stats by a count.
     * @param s the stat id to increment.
     * @param count the count to increment it by.
     */
    public void incrementBy(final String s, final int count)
    {
        statisticsManager.incrementBy(s, count, building.getColony().getDay());
        if (MathUtils.RANDOM.nextInt(10) <= count)
        {
            markDirty();
        }
    }

    /**
     * Deposits a given count of coins into the building's economy, adding the corresponding amount of value to the economy.
     * @param count the count to deposit
     */
    public void deposit(final int count)
    {
        statisticsManager.incrementBy(RESEARCH_GENERATED, count, building.getColony().getDay());
        // Colony stats (the official research balance)
        building.getColony().getStatisticsManager().incrementBy(RESEARCH_BALANCE, count, building.getColony().getDay());
        markDirty();
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

        LOGGER.info("Special Research Module: Colony tick");

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

        LOGGER.info("Special Research Module has {} researchers and {} research in progress", countResearchers, countResearchInProgress);


        // Whenever we have more researchers than research in progress, generate a research credit
        if (countResearchers > countResearchInProgress) 
        {
            int depositAmount = countResearchers - countResearchInProgress;

             LOGGER.info("Special Research Module depositing {} research credits.", depositAmount);

            deposit(depositAmount);
        }

    }
}
