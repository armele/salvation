package com.deathfrog.salvationmod.core.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.IRecyclingListener;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.engine.SalvationManager.CorruptionStage;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.MessageUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.Set;

public class SalvationColonyHandler implements IRecyclingListener
{
    public static final ResourceLocation RESEARCH_SUSTAINABILITY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "effects/sustainability");
    public static final ResourceLocation RESEARCH_IMMUNITY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "effects/immunity");
    public static final ResourceLocation RESEARCH_CLEANFUEL = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "effects/clean_fuel");
    public static final ResourceLocation RESEARCH_LYCANTHROPIC_IMMUNIZATION = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "effects/lycanthropic_immunization");
    public static final ResourceLocation RESEARCH_GREEN_RECYCLER = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "effects/green_recycling");

    public static final Logger LOGGER = LogUtils.getLogger();
    
    // 20 ticks = 1 second; so this sets colony processing to about once every 40 seconds.
    protected final static int COLONY_PROCESS_FREQUENCY = 20 * 40; 
    
    // Percent chance that a notification will be sent
    protected final static int NOTIFICATION_CHANCE = 15;

    // Minimum time between notifications
    protected final static int NOTIFICATION_COOLDOWN = 20 * 60 * 10;

    protected final static String FLAVORMESSAGE_PREFIX = "com.salvation.flavormessage.stage";

    final protected BlockPos colonyCenter;
    final protected ServerLevel level;
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
    public static SalvationColonyHandler getHandler(@Nonnull ServerLevel level, IColony colony)
    {
        return colonyHandlers.computeIfAbsent(colony, c -> new SalvationColonyHandler(level, c));
    } 

    protected SalvationColonyHandler(@Nonnull ServerLevel level, IColony colony) 
    {
        this.colonyCenter = colony.getCenter();
        this.level = level;
        this.data = SalvationSavedData.get(level);
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

        // LOGGER.info("Running salvation logic for colony: {}", colony.getName());
        // This is the primary location for evaluating colony-specific interactions with the Salvation storyline.

        processRecyclers(colony);
        processNotifications(colony);
        processColonySize(colony);

        // IDEA: (PHASE2) Corrupt herd animals
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
     * @param blocks The list of blocks that were generated through recycling.
     * @param building The building that triggered the recycling completion.
     */
    public void onFinishedRecycling(List<ItemStorage> blocks, IBuilding building)
    {
        Level level = building.getColony().getWorld();

        if (level == null || level.isClientSide())
        {
            return;
        }

        double greenRecycling = building.getColony().getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_GREEN_RECYCLER);

        if (greenRecycling == 0)
        {
            return;
        }

        LOGGER.info("Recycling completion notification: {}", building.getBuildingDisplayName());
        int purificationCredits = blocks.size();

        SalvationManager.recordCorruption((ServerLevel) level, ProgressionSource.COLONY, building.getPosition(), -purificationCredits);
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

    /**
     * Process notifications for the colony. This method is responsible for sending out messages to all players in the colony
     * at a random interval to inform them of the current state of the Salvation line.
     * Messages are chosen randomly from a list of 10 for each stage of the Salvation line.
     * Notifications are only sent out if the current game time is greater than the last notification game time plus a coldown period.
     * @param colony the colony to process notifications for
     */
    private void processNotifications(@Nonnull IColony colony) 
    {
        Level level = colony.getWorld();

        if ((!(level instanceof ServerLevel serverLevel)) || level.isClientSide()) return;

        RandomSource random = level.getRandom();
        long gameTime = level.getGameTime();

        if (gameTime < state.lastNotificationGameTime + NOTIFICATION_COOLDOWN) return;

        if (random.nextInt(100) <= NOTIFICATION_CHANCE) 
        {   
            CorruptionStage stage = SalvationManager.stageForLevel(serverLevel);
            int notificationStage = stage.ordinal();
            int notificationNumber = random.nextInt(10);

            state.lastNotificationGameTime = gameTime;
            MessageUtils.format(FLAVORMESSAGE_PREFIX + notificationStage + "." + notificationNumber).sendTo(getColony()).forAllPlayers();
        }
    }

    /**
     * Processes the size of the colony and updates the progress of the Salvation line accordingly.
     * This method is responsible for calculating the gap between the maximum building level and the sustainability level
     * of the colony, and then uses the SalvationManager to credit the corruption or purification.
     * 
     * @param colony the colony to process the size for
     */
    private void processColonySize(@Nonnull IColony colony) 
    {
        Level level = colony.getWorld();

        if (level == null || level.isClientSide() || !(level instanceof ServerLevel serverlevel)) return;

        List<Integer> maxBuildingLevels = maxBuildingLevel();
        int maxBuildingLevel = maxBuildingLevels.get(0);

        double sustainabilityLevel = 1 + colony.getResearchManager().getResearchEffects().getEffectStrength(RESEARCH_SUSTAINABILITY);  

        // If the research has progressed beyond the maximum building level, that's a credit
        int gap = maxBuildingLevel - (int) sustainabilityLevel;
        int buildingViolations = 0;

        if (gap <= 0)
        {
            addPurificationCredits(gap);
            SalvationManager.recordCorruption(serverlevel, ProgressionSource.COLONY, null, gap);
        }
        else
        {
            for (Integer buildingLevel : maxBuildingLevels)
            {
                int thisGap = buildingLevel - (int) sustainabilityLevel;
                buildingViolations += thisGap;
            }

            final int totalViolations = buildingViolations;
            TraceUtils.dynamicTrace(ModCommands.TRACE_COLONYLOOP, () -> LOGGER.info("Colony {} processColonySize: max building {}, sustainability level {}, gap {}. BuildingViolations {}", 
                colony.getName(), maxBuildingLevel, sustainabilityLevel, gap, totalViolations));

            SalvationManager.recordCorruption(serverlevel, ProgressionSource.COLONY, null, totalViolations);
        }
        
        data.updateColonyState(colonyKey, state);
    }


    /**
     * Returns a list of the maximum building levels of all buildings in the colony.
     * If the colony is null, an empty list is returned.
     * The list will have the highest building level in the first position.
     * @return a list of the maximum building levels of all buildings in the colony.
     */
    protected List<Integer> maxBuildingLevel()
    {
        final IColony colony = getColony();
        if (colony == null) return List.of();

        final Map<BlockPos, IBuilding> buildings = colony.getServerBuildingManager().getBuildings();
        if (buildings == null || buildings.isEmpty()) return List.of();

        final List<Integer> levels = new ArrayList<>(buildings.size());
        for (final IBuilding b : buildings.values())
        {
            if (b != null) levels.add(b.getBuildingLevel());
        }

        // highest first
        levels.sort(java.util.Comparator.reverseOrder()); 
        return levels;
    }

    /**
     * Returns the current number of purification credits the colony has.
     * Purification credits are used to measure the progress of the colony in purifying the world.
     * They are used to determine when the colony can progress to the next stage of the Salvation line.
     * @return the current number of purification credits the colony has.
     */
    public long getPurificationCredits() 
    {
        return state.purificationCredits;
    }
}
