package com.deathfrog.salvationmod.core.colony;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.IRecyclingListener;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.MessageUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.Set;

public class SalvationColonyHandler implements IRecyclingListener
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    // 20 ticks = 1 second; so this sets colony processing to about once every 40 seconds.
    protected final static int COLONY_PROCESS_FREQUENCY = 20 * 40; 
    
    // Percent chance that a notification will be sent
    protected final static int NOTIFICATION_CHANCE = 15;

    // Minimum time between notifications
    protected final static int NOTIFICATION_COOLDOWN = 20 * 60 * 10;

    protected final static String FLAVORMESSAGE_PREFIX = "com.salvation.flavormessage.";

    final protected BlockPos colonyCenter;
    final protected Level level;
    final protected SalvationSavedData data;
    final protected String colonyKey;
    final protected ColonyHandlerState state;
    final protected Set<BuildingRecycling> registeredRecyclers = new HashSet<>();

    final static Map<IColony, SalvationColonyHandler> colonyHandlers = new HashMap<>();

    /**
     * Gets the SalvationColonyHandler associated with the given colony.
     * If no handler exists, a new one is created and stored in the map.
     * @param level the level the colony is in
     * @param colony the colony to get the handler for
     * @return the handler associated with the colony, or a new one if none exists
     */
    public static SalvationColonyHandler getHandler(Level level, IColony colony)
    {
        return colonyHandlers.computeIfAbsent(colony, c -> new SalvationColonyHandler(level, c));
    } 

    protected SalvationColonyHandler(Level level, IColony colony) 
    {
        this.colonyCenter = colony.getCenter();
        this.level = level;
        this.data = SalvationSavedData.get((ServerLevel) level);
        this.colonyKey = SalvationSavedData.colonyKey(level, colony);
        this.state = data.getOrCreateColonyState(colonyKey);
    }    


    /**
     * Gets the colony associated with this handler.
     * This is a convenience wrapper around IColonyManager.getInstance().getIColony(level, colonyCenter)
     * @return the colony associated with this handler
     */
    public IColony getColony() 
    {
        return IColonyManager.getInstance().getIColony(level, colonyCenter);
    }

    /**
     * Returns the last evaluation game time for the colony.
     * This is the last time in game ticks that the colony-specific interactions with the Salvation lineup were evaluated.
     * @return the last evaluation game time for the colony
     */
    public long getLastEvaluationGameTime() 
    {
        return state.lastEvaluationGameTime;
    }

    /**
     * Returns the next game time when the colony logic should be processed.
     * This is used to control the speed at which the colony logic is processed.
     * A higher value means the colony logic will be processed more slowly.
     * The default implementation returns the value stored in the colony's state.
     * @return the next process tick for the colony
     */
    public long getNextProcessTick() 
    {
        return state.nextProcessTick;
    }

    /**
     * Processes the colony logic for the associated colony.
     * This method is responsible for advancing the colony logic of the associated colony.
     * The method updates the last evaluation game time and the next process tick, and then 
     * calls the logic loop to evaluate the colony-specific interactions with the Salvation storyline.
     */
    public void processColonyLogic() 
    {
        RandomSource random = level.getRandom();
        state.lastEvaluationGameTime = level.getGameTime();
        state.nextProcessTick = state.lastEvaluationGameTime + COLONY_PROCESS_FREQUENCY + random.nextInt(600);

        data.updateColonyState(colonyKey, state);

        IColony colony = getColony();

        if (colony == null) 
        {
            return;
        }

        LOGGER.info("Running salvation logic for colony: {}", colony.getName());

        processRecyclers(colony);
        processNotifications(colony);

        // TODO: Logic loop to evaluate colony-specific interactions with the Salvation storyline.
        // TODO: Corrupt herd animals
        // TODO: Advance corruption based on colony size.
    }

    private void processRecyclers(@Nonnull IColony colony)
    {
        for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) 
        {
            if (building instanceof BuildingRecycling recyclingBuilding && !registeredRecyclers.contains(recyclingBuilding)) 
            {
                recyclingBuilding.registerRecyclingListener(this);
                registeredRecyclers.add(recyclingBuilding);
            }
        }
    }

    /**
     * Called when a BuildingRecycling module has finished recycling a list of blocks.
     * This method is responsible for applying the negative progress to the corruption progression measure.
     * It will only run on the server side.
     * 
     * @param blocks The list of blocks that were recycled.
     * @param building The building that triggered the recycling completion.
     */
    public void onFinishedRecycling(List<ItemStorage> blocks, IBuilding building)
    {
        Level level = building.getColony().getWorld();

        if (level == null || level.isClientSide())
        {
            return;
        }

        LOGGER.info("Recycling completion notification: {}", building.getBuildingDisplayName());
        int purificationCredits = blocks.size();

        addPurificationCredits(purificationCredits);
        SalvationManager.progress((ServerLevel) level, -purificationCredits);
    }

    /**
     * Adds a given number of purification credits to the colony's state.
     * Purification credits are used to measure the progress of the colony in purifying the world.
     * They are used to determine when the colony can progress to the next stage of the Salvation storyline.
     * @param credits the number of purification credits to add
     */
    public void addPurificationCredits(int credits)
    {
        state.purificationCredits += credits;
    }

    private void processNotifications(@Nonnull IColony colony) 
    {
        RandomSource random = level.getRandom();
        long gameTime = level.getGameTime();

        if (gameTime < state.lastNotificationGameTime + NOTIFICATION_COOLDOWN) return;

        if (random.nextInt(100) <= NOTIFICATION_CHANCE) 
        {
            int notificationLevels = maxBuildingLevel();

            if (notificationLevels > 1) 
            {
                state.lastNotificationGameTime = gameTime;

                // TODO: get a random message to share
                MessageUtils.format(FLAVORMESSAGE_PREFIX + "1").sendTo(getColony()).forAllPlayers();
            }
        }
    }

    /**
     * Returns the maximum building level of all buildings in the colony.
     * If the colony is null, returns -1.
     * 
     * @return the maximum building level of all buildings in the colony
     */
    public int maxBuildingLevel()
    {
        int maxbuildingLevel = -1;
        IColony colony = getColony();

        if (colony == null) 
        {
            return maxbuildingLevel;
        }

        Map<BlockPos,IBuilding> buildings = colony.getServerBuildingManager().getBuildings();

        for (Map.Entry<BlockPos,IBuilding> entry : buildings.entrySet()) 
        {
            IBuilding building = entry.getValue();

            if (building.getBuildingLevel() > maxbuildingLevel) 
            {
                maxbuildingLevel = building.getBuildingLevel();
            }
        }

        return maxbuildingLevel;
    }
}
