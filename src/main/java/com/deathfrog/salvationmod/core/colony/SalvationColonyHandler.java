package com.deathfrog.salvationmod.core.colony;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.salvationmod.core.SalvationSavedData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.MessageUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

public class SalvationColonyHandler 
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


        processNotifications(colony);

        // TODO: Logic loop to evaluate colony-specific interactions with the Salvation storyline.
    }

    private void processNotifications(@Nonnull IColony colony) 
    {
        RandomSource random = level.getRandom();
        long gameTime = level.getGameTime();

        if (gameTime < state.lastNotificationGameTime + NOTIFICATION_COOLDOWN) return;

        if (random.nextInt(100) <= NOTIFICATION_CHANCE) 
        {
            int maxBuildingLevel = maxBuildingLevel();

            if (maxBuildingLevel > 1) 
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
    private int maxBuildingLevel()
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
