package com.deathfrog.salvationmod.core.colony.buildings.modules;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModCommands;
import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.IChatPriority;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Recruitment interaction used for Salvation refugees.
 * <p>
 * MineColonies replaces a visitor with a new citizen when recruitment succeeds, so the visitor id tracked by
 * {@link BuildingRefugeeModule} is not available from the later citizen-added event. This interaction marks the original
 * visitor as a pending refugee recruitment before delegating to MineColonies' normal recruitment logic. The actual reward
 * is applied later from the confirmed hired-citizen event.
 */
public class RefugeeRecruitmentInteraction extends RecruitmentInteraction
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("minecolonies", "refugee_recruitment");
    private static final String RECRUIT_RESPONSE = "com.minecolonies.coremod.gui.chat.recruit";

    /**
     * Constructor used by MineColonies interaction deserialization.
     *
     * @param data the citizen or visitor that owns this interaction.
     */
    public RefugeeRecruitmentInteraction(final ICitizen data)
    {
        super(data);
    }

    /**
     * Creates a refugee recruitment interaction with the normal MineColonies recruitment choices.
     *
     * @param inquiry the dialogue text shown to the player.
     * @param priority the interaction priority.
     */
    public RefugeeRecruitmentInteraction(Component inquiry, IChatPriority priority)
    {
        super(inquiry, priority);
    }

    /**
     * Marks the tracked refugee as pending before MineColonies converts the visitor into a citizen.
     *
     * @param responseId the selected response index.
     * @param player the player responding to the interaction.
     * @param data the visitor data associated with this interaction.
     */
    @Override
    public void onServerResponseTriggered(int responseId, Player player, ICitizenData data)
    {
        markRefugeeRecruitmentPending(responseId, data);
        super.onServerResponseTriggered(responseId, player, data);
    }

    /**
     * Returns the registered interaction type path used by MineColonies persistence.
     *
     * @return the Salvation refugee recruitment interaction type path.
     */
    @Override
    public String getType()
    {
        return ID.getPath();
    }

    /**
     * Marks the visitor as a pending Salvation refugee only when the player selected the recruit response.
     *
     * @param responseId the selected response index.
     * @param visitorData the visitor being recruited.
     */
    private void markRefugeeRecruitmentPending(final int responseId, final ICitizenData visitorData)
    {
        if (visitorData == null || !isRecruitResponse(responseId)) return;

        IColony colony = visitorData.getColony();

        if (colony != null)
        {
            if (!markTrackedRefugeePending(colony, visitorData))
            {
                TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES,
                    () -> LOGGER.info("Colony {}: Recruitment response was not for a tracked refugee.", colony.getID()));
                return;
            }

            TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES,
                () -> LOGGER.info("Colony {}: Marked refugee {} recruitment pending.", colony.getID(), visitorData.getId()));
        }
    }

    /**
     * Checks whether the selected response is MineColonies' recruit option.
     *
     * @param responseId the selected response index.
     * @return true when the response is the recruit choice.
     */
    private boolean isRecruitResponse(final int responseId)
    {
        if (responseId < 0 || responseId >= getPossibleResponses().size())
        {
            return false;
        }

        return getPossibleResponses().get(responseId).getContents() instanceof TranslatableContents contents
            && RECRUIT_RESPONSE.equals(contents.getKey());
    }

    /**
     * Checks to see if the given visitor belongs to a refugee, and marks recruitment pending if so.
     * 
     * @param colony the colony where the visitor is being recruited.
     * @param visitorData the visitor being recruited.
     * @return true when a refugee module accepted the pending marker.
     */
    private static boolean markTrackedRefugeePending(final IColony colony, final ICitizenData visitorData)
    {
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.hasModule(BuildingModules.REFUGEE_MODULE)
                && building.getModule(BuildingModules.REFUGEE_MODULE).markRefugeeRecruitmentPending(visitorData))
            {
                return true;
            }
        }

        return false;
    }
}
