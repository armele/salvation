package com.deathfrog.salvationmod.core.entity.ai.workers.minimal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModAttachments;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.core.colony.buildings.BuildingEnvironmentalLab;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingLibrary;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingSchool;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity;
import com.minecolonies.core.entity.ai.visitor.EntityAIVisitor.VisitorState;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.visitor.VisitorCitizen;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.attachment.AttachmentType;

public class EntityAIRefugeeWanderTask
{
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ConcurrentMap<UUID, Integer> INSTALLED_VISITOR_ENTITY_IDS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<IState> REFUGEE_WANDER_STATES = List.of(VisitorState.IDLE, VisitorState.WANDERING);

    protected IVisitorData visitor = null;
    protected long lastWanderAttempt = 0L;
    protected BlockPos activeTargetCenter = null;
    protected int activeTargetRange = DEFAULT_TARGET_RANGE;

    protected static final int WANDER_CHECK_INTERVAL = 80;
    protected static final int WANDER_COOLDOWN = 1200;
    protected static final double WANDER_CHANCE = 0.7D;
    protected static final double TARGETED_WANDER_SPEED = 0.72D;
    protected static final double LOCAL_WANDER_SPEED = 0.68D;
    protected static final int DEFAULT_TARGET_RANGE = 12;
    protected static final int LOCAL_WANDER_RANGE = 14;
    protected static final int MIN_DIST_SQR_FOR_NEW_TARGET = 64;

    public enum WanderState implements IState
    {
        WANDER_UNI, WANDER_LABTECH, WANDER_OTHER
    }

    public EntityAIRefugeeWanderTask(IVisitorData visitor)
    {
        this.visitor = visitor;
    }

    /**
     * Marks a freshly spawned refugee so its wander behavior can survive future reloads.
     */
    public static void installForNewRefugee(final IVisitorData visitor)
    {
        if (visitor == null || visitor.getEntity().isEmpty())
        {
            return;
        }

        final AttachmentType<ModAttachments.RefugeeWanderData> attachmentType = ModAttachments.REFUGEE_WANDER.get();
        if (attachmentType == null)
        {
            return;
        }

        visitor.getEntity().get().setData(attachmentType, new ModAttachments.RefugeeWanderData(true));
        ensureInstalled(visitor);
    }

    /**
     * Rehydrates refugee wandering for persisted refugees. Refugees missing the persisted attachment
     * are treated as stale pre-attachment entities and dismissed instead of being silently revived.
     */
    public static void enableFor(final IVisitorData visitor)
    {
        if (visitor == null || visitor.getEntity().isEmpty())
        {
            return;
        }
        
        final IColony colony = visitor.getColony();

        final AttachmentType<ModAttachments.RefugeeWanderData> attachmentType = ModAttachments.REFUGEE_WANDER.get();
        if (attachmentType == null)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES,
                    () -> LOGGER.info("Dismissing stale refugee {} from colony {} because refugee_wander was never attached.", visitor.getUUID(), colony.getID()));
                colony.getVisitorManager().removeCivilian(visitor);
            return;
        }

        final ModAttachments.RefugeeWanderData data = visitor.getEntity().get().getData(attachmentType);
        if (data == null || !data.enabled())
        {
            if (colony != null)
            {
                TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES,
                    () -> LOGGER.info("Dismissing stale refugee {} from colony {} because refugee_wander was never enabled.", visitor.getUUID(), colony.getID()));
                    colony.getVisitorManager().removeCivilian(visitor);
            }
            return;
        }

        ensureInstalled(visitor);
    }

    /**
     * Tries to rehydrate the given citizen entity as a refugee visitor by checking if it has the necessary data and enabling the refugee wander task if so.
     * 
     * @param citizen the citizen entity to be rehydrated
     */
    public static void tryRehydrate(final AbstractEntityCitizen citizen)
    {
        if (!(citizen instanceof VisitorCitizen))
        {
            return;
        }

        final AttachmentType<ModAttachments.RefugeeWanderData> attachmentType = ModAttachments.REFUGEE_WANDER.get();
        if (attachmentType == null)
        {
            return;
        }

        final ModAttachments.RefugeeWanderData data = citizen.getData(attachmentType);
        if (data == null || !data.enabled())
        {
            return;
        }

        final ICitizenData citizenData = citizen.getCitizenData();
        if (!(citizenData instanceof IVisitorData visitorData))
        {
            return;
        }

        ensureInstalled(visitorData);
    }

    /**
     * Ensures that the provided visitor entity has the necessary data to be a refugee visitor, and that its state machine is initialized with the refugee wander task.
     *
     * @param visitor the visitor entity to be installed
     */
    private static void ensureInstalled(final IVisitorData visitor)
    {
        if (visitor == null || visitor.getEntity().isEmpty())
        {
            return;
        }

        final AbstractEntityCitizen citizenEntity = visitor.getEntity().get();
        if (!(citizenEntity instanceof VisitorCitizen visitorEntity))
        {
            return;
        }

        final AttachmentType<ModAttachments.RefugeeWanderData> attachmentType = ModAttachments.REFUGEE_WANDER.get();
        if (attachmentType == null)
        {
            return;
        }

        final ModAttachments.RefugeeWanderData data = citizenEntity.getData(attachmentType);
        if (data == null || !data.enabled())
        {
            return;
        }

        final Integer previousEntityId = INSTALLED_VISITOR_ENTITY_IDS.put(citizenEntity.getUUID(), citizenEntity.getId());
        if (previousEntityId != null && previousEntityId.intValue() == citizenEntity.getId())
        {
            return;
        }

        final ITickRateStateMachine<IState> stateMachine = visitorEntity.getEntityStateController();


        TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES,
            () -> LOGGER.info("Colony {} Initializing Refugee {} with their wander task.", visitor.getColony().getID(), visitor.getUUID()));

        new EntityAIRefugeeWanderTask(visitor).init(stateMachine);
    }

    /**
     * Initializes the refugee wander transitions ahead of the stock visitor transitions.
     * 
     * @param stateMachine the state machine to add the transitions to.
     */
    public void init(ITickRateStateMachine<IState> stateMachine)
    {
        stateMachine.addTransitionGroup(REFUGEE_WANDER_STATES, new TickingTransition<>(this::checkingProgress, this::refugeeWander, WANDER_CHECK_INTERVAL));
    }

    /**
     * Runs whenever a refugee already has a destination to continue following, or
     * enough time has passed to start a new science-themed wander.
     * 
     * @return true if the refugee task should drive navigation this tick.
     */
    protected boolean checkingProgress()
    {
        if (visitor == null || visitor.getEntity().isEmpty())
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES, () -> LOGGER.info("No visitor associated with Refugee Wander task."));

            return false;
        }

        final IColony colony = visitor.getColony();

        if (colony == null)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES, () -> LOGGER.info("No colony associated with Refugee {} Wander task.", visitor.getUUID()));

            return false;
        }

        if (activeTargetCenter != null)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES, () -> LOGGER.info("Colony {} Refugee {} has an active target of {}.", colony.getID(), visitor.getUUID(), activeTargetCenter.toShortString()));

            return true;
        }

        final long currentTime = colony.getWorld().getGameTime();
        if (currentTime - lastWanderAttempt < WANDER_COOLDOWN)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES, () -> LOGGER.info("Colony {} Refugee {} has not exceeded their cooldown.", colony.getID(), visitor.getUUID()));

            return false;
        }

        if (colony.getWorld().random.nextDouble() >= WANDER_CHANCE)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES, () -> LOGGER.info("Colony {} Refugee {} did not pass their wander chance.", colony.getID(), visitor.getUUID()));

            return false;
        }

        lastWanderAttempt = currentTime;

        TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES, () -> LOGGER.info("Colony {} Refugee {} is about to wander at time {}.", colony.getID(), visitor.getUUID(), currentTime));

        return true;
    }

    /**
     * Pushes refugees toward the colony's science district while still allowing local
     * wandering when they are already nearby.
     */
    private IState refugeeWander()
    {
        if (visitor == null || visitor.getEntity().isEmpty())
        {
            clearActiveTarget();
            return VisitorState.IDLE;
        }

        final IColony colony = visitor.getColony();

        if (colony == null)
        {
            clearActiveTarget();
            return VisitorState.IDLE;
        }

        final AbstractEntityCitizen visitorEntity = visitor.getEntity().get();

        BlockPos localActiveTargetCenter = activeTargetCenter;

        if (localActiveTargetCenter == null || visitorEntity.blockPosition().distSqr(localActiveTargetCenter) <= MIN_DIST_SQR_FOR_NEW_TARGET)
        {
            final WanderTarget target = chooseWanderTarget(colony, visitorEntity);
            if (target != null)
            {
                activeTargetCenter = target.center();
                activeTargetRange = target.range();
            }
            else
            {
                activeTargetCenter = fallbackCenter(colony, visitorEntity);
                activeTargetRange = LOCAL_WANDER_RANGE;
            }
        }

        if (activeTargetCenter == null || BlockPos.ZERO.equals(activeTargetCenter))
        {
            clearActiveTarget();
            return VisitorState.IDLE;
        }

        final boolean arrived = EntityNavigationUtils.walkToRandomPosAround(
            visitorEntity,
            activeTargetCenter,
            activeTargetRange,
            activeTargetRange == LOCAL_WANDER_RANGE ? LOCAL_WANDER_SPEED : TARGETED_WANDER_SPEED);

        if (arrived)
        {
            clearActiveTarget();
            return VisitorState.IDLE;
        }

        return VisitorState.WANDERING;
    }

    /**
     * Chooses a weighted target from the list of available science-related buildings in the colony.
     * The weights are as follows:
     * - Environmental Lab: 4, range 10
     * - University: 3, range 12
     * - Library: 3, range 10
     * - School: 2, range 9
     * - Town Hall: 1, range 12
     * 
     * @param colony the colony to search for targets in
     * @param visitorEntity the entity that is doing the wandering
     * @return a weighted target, or null if no targets are available
     */
    private WanderTarget chooseWanderTarget(final IColony colony, final AbstractEntityCitizen visitorEntity)
    {
        final List<WanderTarget> weightedTargets = new ArrayList<>();

        addWeightedTarget(weightedTargets, colony.getServerBuildingManager().getBestBuilding(visitorEntity.blockPosition(), BuildingEnvironmentalLab.class), 4, 10);
        addWeightedTarget(weightedTargets, colony.getServerBuildingManager().getBestBuilding(visitorEntity.blockPosition(), BuildingUniversity.class), 3, 12);
        addWeightedTarget(weightedTargets, colony.getServerBuildingManager().getBestBuilding(visitorEntity.blockPosition(), BuildingLibrary.class), 3, 10);
        addWeightedTarget(weightedTargets, colony.getServerBuildingManager().getBestBuilding(visitorEntity.blockPosition(), BuildingSchool.class), 2, 9);
        addWeightedTarget(weightedTargets, colony.getServerBuildingManager().getBestBuilding(visitorEntity.blockPosition(), BuildingTownHall.class), 1, 12);

        if (weightedTargets.isEmpty())
        {
            return null;
        }

        return weightedTargets.get(colony.getWorld().random.nextInt(weightedTargets.size()));
    }

    private void addWeightedTarget(final List<WanderTarget> targets, final BlockPos pos, final int weight, final int range)
    {
        if (pos == null || BlockPos.ZERO.equals(pos))
        {
            return;
        }

        final WanderTarget target = new WanderTarget(pos, range);
        for (int i = 0; i < weight; i++)
        {
            targets.add(target);
        }
    }

    /**
     * Finds a suitable "center" position for the given visitor entity to wander around.
     * 
     * The order of preference is as follows:
     * - The visitor's home building (if it exists)
     * - The colony's town hall (if it exists)
     * - The visitor's current position
     * 
     * @param colony the colony to search for buildings in
     * @param visitorEntity the entity to find a center for
     * @return a suitable center position, or the visitor's current position if no other options are available
     */
    private BlockPos fallbackCenter(final IColony colony, final AbstractEntityCitizen visitorEntity)
    {
        final BlockPos homePos = visitor.getHomeBuilding() != null ? visitor.getHomeBuilding().getPosition() : null;
        if (homePos != null && !BlockPos.ZERO.equals(homePos))
        {
            return homePos;
        }

        final BlockPos townHallPos = colony.getServerBuildingManager().getBestBuilding(visitorEntity.blockPosition(), BuildingTownHall.class);
        if (townHallPos != null && !BlockPos.ZERO.equals(townHallPos))
        {
            return townHallPos;
        }

        return visitorEntity.blockPosition();
    }

    private void clearActiveTarget()
    {
        activeTargetCenter = null;
        activeTargetRange = DEFAULT_TARGET_RANGE;
    }

    private record WanderTarget(BlockPos center, int range)
    {
    }
}
