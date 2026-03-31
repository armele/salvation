package com.deathfrog.salvationmod.core.entity.ai.workers.minimal;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.deathfrog.salvationmod.ModAttachments;
import com.deathfrog.salvationmod.core.colony.buildings.BuildingEnvironmentalLab;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
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
    private static final Set<UUID> INSTALLED_VISITOR_IDS = ConcurrentHashMap.newKeySet();
    protected IVisitorData visitor = null;
    protected long lastProgressUpdate = 0L;

    protected static final int PROGRESS_COOLDOWN = 6000;
    protected static final double PROGRESS_CHANCE = .25D;

    public enum WanderState implements IState
    {
        WANDER_UNI, WANDER_LABTECH, WANDER_OTHER
    }

    public EntityAIRefugeeWanderTask(IVisitorData visitor)
    {
        this.visitor = visitor;
    }

    public static void enableFor(final IVisitorData visitor)
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

        if (!INSTALLED_VISITOR_IDS.add(citizenEntity.getUUID()))
        {
            return;
        }

        final ITickRateStateMachine<IState> stateMachine = visitorEntity.getEntityStateController();
        new EntityAIRefugeeWanderTask(visitor).init(stateMachine);
    }

    /**
     * Initializes the AI state machine transitions for the shopping task.
     * 
     * @param stateMachine the state machine to add the transitions to.
     */
    public void init(ITickRateStateMachine<IState> stateMachine)
    {
        stateMachine.addTransition(new TickingTransition<>(VisitorState.IDLE, this::checkingProgress, this::refugeeWander, 150));
        stateMachine.addTransition(new TickingTransition<>(VisitorState.WANDERING, this::checkingProgress, this::refugeeWander, 150));
    }

    /**
     * Checks if enough time has passed since the last progress update to warrant
     * another progress update.
     * 
     * @return true if enough time has passed, false otherwise
     */
    protected boolean checkingProgress()
    {
        long currentTime = visitor.getColony().getWorld().getGameTime();

        if (currentTime - lastProgressUpdate > PROGRESS_COOLDOWN && visitor.getColony().getWorld().random.nextDouble() < PROGRESS_CHANCE)
        {
            lastProgressUpdate = currentTime;
            return true;
        }

        return false;
    }

    /**
     * Makes the visitor wander towards a random university, environmental lab, or town hall, if one exists in the colony.
     * If no such building exists, the visitor will idle.
     * @return the next state of the AI, which is always IDLE.
     */
    private IState refugeeWander()
    {
        IColony colony = visitor.getColony();

        if (colony == null)
        {
            return VisitorState.IDLE;
        }

        BlockPos uniPos = colony.getServerBuildingManager().getRandomBuilding(b -> b instanceof BuildingUniversity || b instanceof BuildingEnvironmentalLab || b instanceof BuildingTownHall);

        if (uniPos != null && !BlockPos.ZERO.equals(uniPos))
        {
            AbstractEntityCitizen visitorEntity = visitor.getEntity().get();
            
            EntityNavigationUtils.walkToRandomPosAround(visitorEntity, uniPos, 10, 0.6D);
        }

        return VisitorState.IDLE;
    }
}
