package com.deathfrog.salvationmod.core.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.IRecyclingListener;
import com.deathfrog.salvationmod.ModEntityTypes;
import com.deathfrog.salvationmod.Config;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.apiimp.initializer.ModInteractionInitializer;
import com.deathfrog.salvationmod.core.blockentity.PurificationBeaconCoreBlockEntity;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.engine.CorruptionStage;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.deathfrog.salvationmod.core.portal.ExteritioPortalShape;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

import java.util.Set;
import java.util.Optional;

public class SalvationColonyHandler implements IRecyclingListener
{
    public static final ResourceLocation RESEARCH_SUSTAINABILITY =              ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "effects/sustainability");
    public static final ResourceLocation RESEARCH_IMMUNITY =                    ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "effects/immunity");
    public static final ResourceLocation RESEARCH_CLEANFUEL =                   ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "effects/clean_fuel");
    public static final ResourceLocation RESEARCH_LYCANTHROPIC_IMMUNIZATION =   ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "effects/lycanthropic_immunization");
    public static final ResourceLocation RESEARCH_GREEN_RECYCLER =              ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "effects/green_recycling");
    public static final ResourceLocation RESEARCH_ENABLE_BEACONS =              ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "effects/enable_beacons");
    public static final ResourceLocation RESEARCH_BEACON_RANGE =                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "effects/beacon_range");
    public static final ResourceLocation RESEARCH_BEACON_POWER =                ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "effects/beacon_power");
    public static final ResourceLocation RESEARCH_BEACON_FREQUENCY =            ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "effects/beacon_frequency");

    public static final Logger LOGGER = LogUtils.getLogger();
    
    // 20 ticks = 1 second; so this sets colony processing to about once every 40 seconds.
    protected final static int COLONY_PROCESS_FREQUENCY = 20 * 40; 
    
    // Percent chance that a notification will be sent
    protected final static int NOTIFICATION_CHANCE = 15;

    // Minimum time between notifications, extended by 50% to reduce overlap with world messaging.
    protected final static int NOTIFICATION_COOLDOWN = 20 * 60 * Config.colonyNotificationCooldown.get();

    // Days between exteritio raids
    protected final static int BASE_RAID_COOLDOWN_DAYS = Config.exteritioRaidCooldown.get();

    @SuppressWarnings("null")
    protected final static @Nonnull ResourceLocation RAID_PORTAL_TEMPLATE = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "raid/portal1");

    protected final static int RAID_PORTAL_OUTSIDE_PADDING = 8;
    protected final static int RAID_PORTAL_SEARCH_DEPTH = 64;
    protected final static int RAID_PORTAL_RADIUS_STEP = 8;
    protected final static int RAID_PORTAL_SEARCH_ANGLES = 24;
    protected final static int RAID_PORTAL_MAX_HEIGHT_VARIATION = 6;

    protected final static String EXTERITIO_RAID_MESSAGE = "com.salvation.exteritioraid.spawned";

    // Prefix for flavor messages
    protected final static String COLONY_FLAVORMESSAGE_PREFIX = "com.salvation.colony.flavormessage.stage";

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
        processCitizens(colony);
        processRaids(colony);
    }

    /**
     * Processes the recyclers in the given colony.
     * This method is responsible for checking if the colony has any recyclers and registering a listener for them.
     * The listener is used to trigger interactions with the recyclers when they are used to recycle items.
     * @param colony the colony to process the recyclers for
     */
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
     * Process citizens in the given colony.
     * This method is responsible for checking if the colony has any citizens and randomly selecting one to trigger an interaction with.
     * The interaction triggered is a standard "escape" interaction with a message that is dependent on the current corruption stage.
     * @param colony the colony to process citizens for.
     */
    private void processCitizens(@Nonnull IColony colony)
    {
        Level level = colony.getWorld();

        if ((!(level instanceof ServerLevel serverLevel)) || level.isClientSide()) return;

        List<ICitizenData> citizens = colony.getCitizenManager().getCitizens();

        CorruptionStage stage = SalvationManager.stageForLevel(serverLevel);
        int notificationStage = stage.ordinal();

        if (citizens.size() <= 0)
        {
            return;
        }

        int citizenNumber = serverLevel.getRandom().nextInt(citizens.size());

        ICitizenData citizen = citizens.get(citizenNumber);

        if (citizen != null)
        {   
            citizen.triggerInteraction(new StandardInteraction(Component.translatableEscape(ModInteractionInitializer.CITIZEN_MESSGE_BASE + notificationStage + "." + citizen.getRandom().nextInt(10)), ChatPriority.CHITCHAT));
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
        data.updateColonyState(colonyKey, state);
    }

    /**
     * Adds to the colony's cumulative corruption contribution from colony-sourced activity.
     * This is tracked separately from purification credits so UI and messaging can reason about
     * how much of the world's corruption this colony has directly caused.
     * @param amount the amount of corruption contribution to add
     */
    public void addCorruptionContribution(final int amount)
    {
        state.corruptionContribution += amount;
        data.updateColonyState(colonyKey, state);
    }

    /**
     * Computes the colony contribution rating used for notification localization.
     * The baseline reflects how much of the world's corruption is attributable to colonies as a whole,
     * and this colony then shifts slightly up or down depending on whether it is cleaner or dirtier
     * than the average colony in the dimension.
     * @param serverLevel the level the colony belongs to
     * @return a rating clamped to the 0..9 localization range
     */
    private int computeColonyContributionRating(@Nonnull final ServerLevel serverLevel)
    {
        final List<IColony> colonies = IColonyManager.getInstance().getColonies(serverLevel);
        final int colonyCount = Math.max(1, colonies.size());
        final long totalProgression = SalvationManager.getProgressionMeasure(serverLevel);
        final long totalColonyNet = colonies.stream()
            .mapToLong(colony -> SalvationColonyHandler.getHandler(serverLevel, colony).getNetColonyContribution())
            .sum();
        final long thisColonyNet = Math.max(0L, getNetColonyContribution());

        final int baselineRating;
        if (totalProgression > 0 && totalColonyNet > 0)
        {
            baselineRating = Mth.clamp((int) Math.floor(((double) totalColonyNet / (double) totalProgression) * 10.0D), 0, 9);
        }
        else
        {
            baselineRating = 0;
        }

        int adjustment = 0;
        final double averageColonyNet = (double) totalColonyNet / (double) colonyCount;
        if (averageColonyNet > 0.0D)
        {
            final double relativeToAverage = thisColonyNet / averageColonyNet;

            if (relativeToAverage <= 0.50D)
            {
                adjustment = -2;
            }
            else if (relativeToAverage <= 0.85D)
            {
                adjustment = -1;
            }
            else if (relativeToAverage >= 2.00D)
            {
                adjustment = 2;
            }
            else if (relativeToAverage >= 1.15D)
            {
                adjustment = 1;
            }
        }

        return Mth.clamp(baselineRating + adjustment, 0, 9);
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

        if (NOTIFICATION_COOLDOWN <= 0) return;

        if (gameTime < state.lastNotificationGameTime + NOTIFICATION_COOLDOWN) return;

        if (random.nextInt(100) <= NOTIFICATION_CHANCE) 
        {   
            CorruptionStage stage = SalvationManager.stageForLevel(serverLevel);
            int colonyContributionRating = computeColonyContributionRating(serverLevel);

            int notificationStage = stage.ordinal();
            int notificationNumber = random.nextInt(10);

            state.lastNotificationGameTime = gameTime;
            data.updateColonyState(colonyKey, state);
            MessageUtils.format(
                COLONY_FLAVORMESSAGE_PREFIX + notificationStage + "." + notificationNumber + "." + colonyContributionRating,
                colony.getName())
                .sendTo(getColony())
                .forAllPlayers();
        }
    }


    /**
     * Process the colony size for the given colony.
     * This method is responsible for calculating the colony's sustainability level and determining if the colony has
     * progressed beyond the maximum building level.
     * If the research has progressed beyond the maximum building level, that's a credit.
     * If the colony has not progressed beyond the maximum building level, each building violation is
     * counted towards the colony's total corruption contribution.
     * @param colony the colony to process the size for
     */
    private void processColonySize(@Nonnull IColony colony) 
    {
        Level level = colony.getWorld();

        if (level == null || level.isClientSide() || !(level instanceof ServerLevel serverlevel)) return;

        List<Integer> allBuildingLevels = buildingLevels();
        if (allBuildingLevels.isEmpty()) return;
        int maxBuildingLevel = allBuildingLevels.get(0);

        double sustainabilityLevel = getSustainabilityLevel();  

        // If the research has progressed beyond the maximum building level, that's a credit
        int gap = maxBuildingLevel - (int) sustainabilityLevel;
        int buildingViolations = 0;

        if (gap <= 0)
        {
            final int purificationCredits = -gap;
            addPurificationCredits(purificationCredits);
            SalvationManager.recordCorruption(serverlevel, ProgressionSource.COLONY, null, gap);
        }
        else
        {
            for (Integer buildingLevel : allBuildingLevels)
            {
                int thisGap = buildingLevel - (int) sustainabilityLevel;
                buildingViolations += thisGap;
            }

            final int totalViolations = buildingViolations;
            TraceUtils.dynamicTrace(ModCommands.TRACE_COLONYLOOP, () -> LOGGER.info("Colony {} processColonySize: max building {}, sustainability level {}, gap {}. BuildingViolations {}", 
                colony.getName(), maxBuildingLevel, sustainabilityLevel, gap, totalViolations));

            SalvationManager.recordCorruption(serverlevel, ProgressionSource.COLONY, null, totalViolations);
            addCorruptionContribution(totalViolations);
        }
        
        data.updateColonyState(colonyKey, state);
    }

    /**
     * Returns the colony's current sustainability level based on its research effects.
     * A value of 1.0 means no sustainability research has been completed yet.
     * @return the colony sustainability level
     */
    public double getSustainabilityLevel()
    {
        final IColony colony = getColony();
        if (colony == null)
        {
            return 0.0D;
        }

        return colony.getResearchManager().getResearchEffects().getEffectStrength(RESEARCH_SUSTAINABILITY);
    }


    /**
     * Returns a list of the building levels of all buildings in the colony.
     * If the colony is null, an empty list is returned.
     * The list will have the highest building level in the first position.
     * @return a list of the building levels of all buildings in the colony.
     */
    protected List<Integer> buildingLevels()
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

    public long getCorruptionContribution()
    {
        return state.corruptionContribution;
    }

    public long getNetColonyContribution()
    {
        return Math.max(0L, (long) state.corruptionContribution - state.purificationCredits);
    }


    /**
     * Process the raids for the given colony.
     * This will check if a raid is due today, and if so, will generate a raid near the colony.
     * The raid generation is based on the current corruption stage of the world.
     * The chance of a raid spawning is based on the corruption stage's dailyRaidSpawnChance.
     * The cooldown between raids is based on the corruption stage's ordinal.
     * The cooldown is calculated as BASE_RAID_COOLDOWN_DAYS - stage.ordinal().
     * This means that the higher the corruption stage, the shorter the cooldown.
     * The raid generation is done by randomly selecting a location near the colony.
     * The location is chosen to be outside the colony's boundaries.
     * @param colony the colony to process the raids for.
     */
    private void processRaids(@Nonnull IColony colony) 
    {
        Level level = colony.getWorld();

        if ((!(level instanceof ServerLevel serverLevel)) || level.isClientSide()) return;

        // Are raids disabled?
        if (BASE_RAID_COOLDOWN_DAYS < 0) return; 

        CorruptionStage stage = SalvationManager.stageForLevel(serverLevel);

        // Don't double-up on raids... mercifully, a standard minecolonies raid pre-empts the Exteritio event
        if (colony.getRaiderManager().willRaidTonight()) return;

        int day = colony.getDay();
        
        if (day <= state.getLastExteritioRaidDayCheck()) return;

        state.setLastExteritioRaidDayCheck(day);

        int cooldown = BASE_RAID_COOLDOWN_DAYS - stage.ordinal();

        if (serverLevel.getGameTime() <= state.getLastExteritioRaidTick() + (cooldown * PurificationBeaconCoreBlockEntity.DEFAULT_DAY_LENGTH)) return;

        float rand = serverLevel.random.nextFloat();

        if (rand > stage.getDailyRaidSpawnChance()) return;

        final RaidPortalPlacement placement = placeRaidPortal(colony, serverLevel);
        if (placement != null)
        {
            MessageUtils.format(
                EXTERITIO_RAID_MESSAGE,
                BlockPosUtil.calcDirection(colony.getCenter(), placement.center()).getLongText()
            ).sendTo(colony).forAllPlayers();
            state.setLastExteritioRaidTick(serverLevel.getGameTime());
        }

        data.updateColonyState(colonyKey, state);
    
    }

    /**
     * Tries to place the raid portal template near the given colony.
     * This will check if the template can be loaded, and if not, will log an error and return false.
     * This will also check if the template has an invalid size, and if so, will log an error and return false.
     * This will then try to find a valid placement for the template near the colony.
     * If no valid placement is found, this will log a warning and return false.
     * This will then try to force-load the chunks at the placement location.
     * This will then try to place the template at the given location.
     * If the placement fails, this will log an error and return false.
     * If the placement is successful, this will log a success message and return true.
     * @param colony the colony to place the raid portal near
     * @param serverLevel the level to place the raid portal in
     * @return true if the raid portal was successfully placed, false otherwise
     */
    public RaidPortalPlacement placeRaidPortal(@Nonnull final IColony colony, @Nonnull final ServerLevel serverLevel)
    {
        final Optional<StructureTemplate> templateOptional = serverLevel.getStructureManager().get(RAID_PORTAL_TEMPLATE);
        if (templateOptional.isEmpty())
        {
            LOGGER.error("Colony {} raid skipped because template {} could not be loaded.", colony.getID(), RAID_PORTAL_TEMPLATE);
            return null;
        }

        final StructureTemplate template = templateOptional.get();
        final Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0)
        {
            LOGGER.error("Colony {} raid skipped because template {} has invalid size {}.", colony.getID(), RAID_PORTAL_TEMPLATE, size);
            return null;
        }

        final RaidPortalPlacement placement = findRaidPortalPlacement(colony, serverLevel, size);
        if (placement == null)
        {
            LOGGER.warn("Colony {} raid skipped because no valid placement was found for {} near {}.", colony.getID(), RAID_PORTAL_TEMPLATE, colony.getCenter());
            return null;
        }

        BlockPos origin = placement.origin();

        if (origin == null)
        {
            LOGGER.warn("Colony {} raid skipped because no valid placement origin was found for {} near {}.", colony.getID(), RAID_PORTAL_TEMPLATE, colony.getCenter());
            return null;
        }

        Vec3i placementSize = placement.size();

        if (placementSize == null)
        {
            LOGGER.warn("Colony {} raid skipped because no valid placement size was found for {} near {}.", colony.getID(), RAID_PORTAL_TEMPLATE, colony.getCenter());
            return null;
        }

        forceLoadTemplateChunks(serverLevel, origin, placementSize);

        final StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(NullnessBridge.assumeNonnull(placement.rotation()))
            .addProcessor(NullnessBridge.assumeNonnull(JigsawReplacementProcessor.INSTANCE));

        final long placementSeed = serverLevel.getSeed() ^ origin.asLong() ^ colony.getCenter().asLong();
        final boolean placed = template.placeInWorld(
            serverLevel,
            origin,
            origin,
            NullnessBridge.assumeNonnull(settings),
            NullnessBridge.assumeNonnull(StructureBlockEntity.createRandom(placementSeed)),
            2
        );

        if (!placed)
        {
            LOGGER.error("Colony {} raid template {} failed to place at {}.", colony.getID(), RAID_PORTAL_TEMPLATE, origin);
            return null;
        }

        if (!activatePlacedRaidPortal(serverLevel, origin, placementSize))
        {
            LOGGER.warn("Colony {} raid portal structure {} was placed at {}, but no valid portal frame was found to activate.", colony.getID(), RAID_PORTAL_TEMPLATE, origin);
        }

        spawnRaidCreatures(serverLevel, origin, placementSize);

        LOGGER.info("Placed colony raid portal {} for colony {} at {} with rotation {}.", RAID_PORTAL_TEMPLATE, colony.getID(), origin, placement.rotation());
        return placement;
    }

    /**
     * Activates the placed raid portal structure by searching for a valid portal frame.
     * This will search all positions within the given bounding box for a valid portal frame.
     * If a valid portal frame is found, it will be activated by creating the portal blocks.
     * If no valid portal frame is found, this will return false.
     * 
     * @param serverLevel the level to search for a valid portal frame in
     * @param origin the position of the bottom-left corner of the bounding box
     * @param size the size of the bounding box
     * @return true if a valid portal frame was found and activated, false otherwise
     */
    private boolean activatePlacedRaidPortal(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final BlockPos maxCorner = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        if (maxCorner == null) return false;

        for (final BlockPos pos : BlockPos.betweenClosed(origin, maxCorner))
        {
            final Optional<ExteritioPortalShape> portalShape = ExteritioPortalShape.findPortalShape(
                serverLevel,
                pos.immutable(),
                ExteritioPortalShape::isValid,
                Direction.Axis.X
            );

            if (portalShape.isPresent())
            {
                portalShape.get().createPortalBlocks();
                return true;
            }
        }

        return false;
    }

    /**
     * Spawns the raid creatures for the given colony raid portal.
     * This will spawn a random number of Voraxian Stingers and Voraxian Observers within a circle
     * centered at the given position.
     *
     * @param serverLevel the server level to spawn the creatures in
     * @param origin the position of the bottom-left corner of the bounding box of the raid portal
     * @param size the size of the bounding box of the raid portal
     */
    private void spawnRaidCreatures(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final RandomSource random = serverLevel.getRandom();
        final int stingerCount = 1 + random.nextInt(4);
        final int mawCount = 1 + random.nextInt(2);
        final int observerCount = 1 + random.nextInt(1);
        final BlockPos center = origin.offset(size.getX() / 2, 0, size.getZ() / 2);

        if (center == null) return;

        for (int i = 0; i < stingerCount; i++)
        {
            spawnRaidMob(serverLevel, NullnessBridge.assumeNonnull(ModEntityTypes.VORAXIAN_STINGER.get()), center, false);
        }

        for (int i = 0; i < mawCount; i++)
        {
            spawnRaidMob(serverLevel, NullnessBridge.assumeNonnull(ModEntityTypes.VORAXIAN_MAW.get()), center, true);
        }

        for (int i = 0; i < observerCount; i++)
        {
            spawnRaidMob(serverLevel, NullnessBridge.assumeNonnull(ModEntityTypes.VORAXIAN_OBSERVER.get()), center, true);
        }
    }

    /**
     * Spawns a raid mob at a random position within a circle centered at the given position.
     * The raid mob will be spawned at the surface level of the world, or slightly above it if the
     * airborne parameter is true.
     *
     * @param serverLevel the server level to spawn the mob in
     * @param entityType the entity type of the mob to spawn
     * @param center the center of the circle to spawn the mob at
     * @param airborne whether or not to spawn the mob slightly above the surface level
     */
    @SuppressWarnings({"deprecation", "null"})
    private void spawnRaidMob(
        @Nonnull final ServerLevel serverLevel,
        @Nonnull final EntityType<? extends Mob> entityType,
        @Nonnull final BlockPos center,
        final boolean airborne)
    {
        final Mob mob = entityType.create(serverLevel);
        if (mob == null)
        {
            return;
        }

        final RandomSource random = serverLevel.getRandom();
        final double angle = random.nextDouble() * (Math.PI * 2.0D);
        final double radius = 2.0D + random.nextDouble() * 4.0D;
        final int spawnX = Mth.floor(center.getX() + 0.5D + Math.cos(angle) * radius);
        final int spawnZ = Mth.floor(center.getZ() + 0.5D + Math.sin(angle) * radius);
        final int surfaceY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnX, spawnZ);
        final double spawnY = airborne ? surfaceY + 1.5D + random.nextDouble() * 2.0D : surfaceY;
        final float yaw = random.nextFloat() * 360.0F;

        mob.moveTo(spawnX + 0.5D, spawnY, spawnZ + 0.5D, yaw, 0.0F);
        mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
        serverLevel.addFreshEntity(mob);
    }

    /**
     * Finds a valid placement for the raid portal template near the given colony.
     * The placement is chosen to be outside the colony's boundaries.
     * The template is searched for in a grid pattern, with a step size of at most half the size of the bounding box in both the X and Z directions.
     * The Y-coordinate is searched in a grid pattern, with a step size of at most half the size of the bounding box in the Y direction.
     * The highest Y-coordinate found is returned.
     * If no valid placement is found, this method returns null.
     *
     * @param colony the colony to place the raid portal near
     * @param serverLevel the level to place the raid portal in
     * @param templateSize the size of the raid portal template
     * @return the valid placement for the raid portal template, or null if no valid placement is found
     */
    private RaidPortalPlacement findRaidPortalPlacement(@Nonnull final IColony colony, @Nonnull final ServerLevel serverLevel, @Nonnull final Vec3i templateSize)
    {
        final RandomSource random = serverLevel.getRandom();
        final double angleOffset = random.nextDouble() * (Math.PI * 2.0D);
        final int baseRadius = estimateColonyRadius(colony);
        final int halfSpan = Math.max(templateSize.getX(), templateSize.getZ()) / 2;

        for (int radiusOffset = 0; radiusOffset <= RAID_PORTAL_SEARCH_DEPTH; radiusOffset += RAID_PORTAL_RADIUS_STEP)
        {
            final int radius = baseRadius + halfSpan + RAID_PORTAL_OUTSIDE_PADDING + radiusOffset;

            for (int angleIndex = 0; angleIndex < RAID_PORTAL_SEARCH_ANGLES; angleIndex++)
            {
                final double angle = angleOffset + ((Math.PI * 2.0D) * angleIndex / RAID_PORTAL_SEARCH_ANGLES);
                final int centerX = colonyCenter.getX() + Mth.floor(Math.cos(angle) * radius);
                final int centerZ = colonyCenter.getZ() + Mth.floor(Math.sin(angle) * radius);
                final Rotation rotation = rotationFacingColony(centerX - colonyCenter.getX(), centerZ - colonyCenter.getZ());
                final Vec3i rotatedSize = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                    ? new Vec3i(templateSize.getZ(), templateSize.getY(), templateSize.getX())
                    : templateSize;
                final int originX = centerX - (rotatedSize.getX() / 2);
                final int originZ = centerZ - (rotatedSize.getZ() / 2);
                final int originY = findSurfaceY(serverLevel, originX, originZ, rotatedSize);
                final BlockPos origin = new BlockPos(originX, originY, originZ);

                if (isValidRaidPortalPlacement(colony, serverLevel, origin, rotatedSize))
                {
                    return new RaidPortalPlacement(origin, rotation, rotatedSize);
                }
            }
        }

        return null;
    }


    /**
     * Estimates the radius of the given colony.
     * The radius is calculated as the ceiling of the square root of the maximum distance squared
     * between the colony's center and any of its buildings.
     * The maximum distance squared is calculated as the maximum of the current maximum distance squared
     * and the distance squared between the colony's center and each of its buildings.
     * @param colony the colony to estimate the radius of
     * @return the estimated radius of the colony
     */
    private int estimateColonyRadius(@Nonnull final IColony colony)
    {
        int maxDistanceSq = 32 * 32;

        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building != null)
            {
                maxDistanceSq = Math.max(maxDistanceSq, (int) colony.getDistanceSquared(building.getPosition()));
            }
        }

        return Mth.ceil(Math.sqrt(maxDistanceSq));
    }

    /**
     * Checks if the given raid portal placement is valid.
     * The placement is considered valid if it is within the world border,
     * and if the height variation within the placement is not greater than
     * {@link #RAID_PORTAL_MAX_HEIGHT_VARIATION}.
     *
     * @param colony the colony to check the placement against
     * @param serverLevel the level to check the placement in
     * @param origin the position of the raid portal
     * @param size the size of the raid portal
     * @return true if the placement is valid, false otherwise
     */
    private boolean isValidRaidPortalPlacement(@Nonnull final IColony colony, @Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        if (!isWithinWorldBorder(serverLevel, origin, size))
        {
            return false;
        }

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        final int stepX = Math.max(1, size.getX() / 3);
        final int stepZ = Math.max(1, size.getZ() / 3);

        for (int dx = 0; dx < size.getX(); dx += stepX)
        {
            for (int dz = 0; dz < size.getZ(); dz += stepZ)
            {
                final int sampleX = origin.getX() + Math.min(dx, size.getX() - 1);
                final int sampleZ = origin.getZ() + Math.min(dz, size.getZ() - 1);
                final int sampleY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);
                final BlockPos samplePos = new BlockPos(sampleX, sampleY, sampleZ);

                if (colony.isCoordInColony(serverLevel, samplePos))
                {
                    return false;
                }

                minY = Math.min(minY, sampleY);
                maxY = Math.max(maxY, sampleY);
            }
        }

        return maxY - minY <= RAID_PORTAL_MAX_HEIGHT_VARIATION;
    }

    /**
     * Checks if the given position is within the world border.
     * The position is considered to be within the world border if it and the opposite corner of the given size
     * are both within the world border.
     * @param serverLevel the level to check the position in
     * @param origin the position to check
     * @param size the size of the bounding box
     * @return true if the position is within the world border, false otherwise
     */
    private boolean isWithinWorldBorder(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final BlockPos maxCorner = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        if (maxCorner == null) return false;

        return serverLevel.getWorldBorder().isWithinBounds(origin)
            && serverLevel.getWorldBorder().isWithinBounds(maxCorner)
            && serverLevel.getWorldBorder().isWithinBounds(new BlockPos(origin.getX(), origin.getY(), maxCorner.getZ()))
            && serverLevel.getWorldBorder().isWithinBounds(new BlockPos(maxCorner.getX(), origin.getY(), origin.getZ()));
    }

    /**
     * Finds the highest Y-coordinate of the surface of the given level within the given bounding box.
     * The bounding box is centered at the given origin, with a size of the given Vec3i.
     * The Y-coordinate is searched in a grid pattern, with a step size of at most half the size of the bounding box in both the X and Z directions.
     * The highest Y-coordinate found is returned.
     *
     * @param level the level to search in
     * @param originX the X-coordinate of the origin of the bounding box
     * @param originZ the Z-coordinate of the origin of the bounding box
     * @param size the size of the bounding box
     * @return the highest Y-coordinate of the surface of the given level within the given bounding box
     */
    private int findSurfaceY(@Nonnull final ServerLevel level, final int originX, final int originZ, @Nonnull final Vec3i size)
    {
        int highestY = level.getMinBuildHeight() + 1;
        final int stepX = Math.max(1, size.getX() / 4);
        final int stepZ = Math.max(1, size.getZ() / 4);

        for (int dx = 0; dx <= size.getX(); dx += stepX)
        {
            for (int dz = 0; dz <= size.getZ(); dz += stepZ)
            {
                highestY = Math.max(
                    highestY,
                    level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        originX + Math.min(dx, size.getX() - 1),
                        originZ + Math.min(dz, size.getZ() - 1)
                    )
                );
            }
        }

        // Heightmap queries return the first open block above the surface; structure origins need the supporting surface block.
        return Math.max(level.getMinBuildHeight(), highestY - 1);
    }

    /**
     * Forces the loading of all chunks within the given bounding box.
     * This is used to ensure that the chunks are loaded before the Exteritio boss structure is placed.
     *
     * @param level the level to force the loading of chunks in
     * @param origin the origin of the bounding box
     * @param size the size of the bounding box
     */
    private void forceLoadTemplateChunks(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final BlockPos maxCorner = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        if (maxCorner == null) return;

        final ChunkPos minChunk = new ChunkPos(origin);
        final ChunkPos maxChunk = new ChunkPos(maxCorner);

        ChunkPos.rangeClosed(minChunk, maxChunk).forEach(chunkPos -> level.getChunk(chunkPos.x, chunkPos.z));
    }

    /**
     * Computes the rotation (in degrees) needed to face the colony when standing at the given position (deltaX, deltaZ)
     * relative to the colony center.
     * The rotation is computed by taking the direction from the given position to the colony center and then
     * converting it to the corresponding rotation.
     * The direction is computed using the getNearest method of the Direction class.
     * The rotation is computed using a switch statement on the direction.
     * If the direction is SOUTH, the rotation is CLOCKWISE_180 (180 degrees).
     * If the direction is WEST, the rotation is CLOCKWISE_90 (90 degrees).
     * If the direction is EAST, the rotation is COUNTERCLOCKWISE_90 (-90 degrees).
     * In all other cases, the rotation is NONE (0 degrees).
     * @param deltaX the change in X coordinate relative to the colony center
     * @param deltaZ the change in Z coordinate relative to the colony center
     * @return the rotation needed to face the colony when standing at the given position
     */
    private Rotation rotationFacingColony(final int deltaX, final int deltaZ)
    {
        final Direction directionToColony = Direction.getNearest(-deltaX, 0, -deltaZ);
        return switch (directionToColony)
        {
            case WEST -> Rotation.CLOCKWISE_180;
            case SOUTH -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.COUNTERCLOCKWISE_90;
            case EAST -> Rotation.NONE;
            default -> Rotation.NONE;
        };
    }

    public record RaidPortalPlacement(BlockPos origin, Rotation rotation, Vec3i size)
    {
        public BlockPos center()
        {
            return origin.offset(size.getX() / 2, 0, size.getZ() / 2);
        }
    }
}
