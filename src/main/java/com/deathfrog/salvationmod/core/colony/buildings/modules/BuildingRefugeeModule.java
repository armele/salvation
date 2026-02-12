package com.deathfrog.salvationmod.core.colony.buildings.modules;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.core.colony.SalvationHappinessFactorTypeInitializer;
import com.deathfrog.salvationmod.core.engine.SalvationEventListener;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenHappinessHandler;
import com.minecolonies.api.entity.citizen.happiness.DynamicHappinessSupplier;
import com.minecolonies.api.entity.citizen.happiness.ExpirationBasedHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import com.minecolonies.api.eventbus.EventBus;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.eventhooks.citizenEvents.VisitorSpawnedEvent;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class BuildingRefugeeModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule
{
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    
    private static final String TAG_NEXTREFUGEE_TIME = "nextRefugeeTime";

    /**
     * Eligible time for spawning more refugees
     */
    private long nextRefugeeTime = 10000;

    boolean listeningForRecruits = false;

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        nextRefugeeTime = compound.getLong(TAG_NEXTREFUGEE_TIME);
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        compound.putLong(TAG_NEXTREFUGEE_TIME, nextRefugeeTime);
    }

    @Override
    public void serializeToView(final RegistryFriendlyByteBuf buf, final boolean fullSync)
    {

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
        if (!listeningForRecruits)
        {
            listeningForRecruits = true;
            EventBus bus = IMinecoloniesAPI.getInstance().getEventBus();
            bus.subscribe(CitizenAddedModEvent.class, SalvationEventListener::onCitizenAdded);
        }

        handleHappiness();

        handleRefugees();
    }

    /**
     * Sets up the happiness modifiers for all citizens in the colony, to understand Salvation happiness.
     * If a citizen does not already have a happiness modifier for Salvation's purification happiness factor, it adds one.
     * This is necessary to ensure that the happiness of citizens is affected by the presence of a BuildingRefugeeModule.
     */
    private void handleHappiness()
    {
        // LOGGER.info("BuildngRefugeeModule.handleHappiness()");

        IColony colony = building.getColony();
        for (ICitizenData citizenData : colony.getCitizenManager().getCitizens())
        {
            ICitizenHappinessHandler happinessHandler = citizenData.getCitizenHappinessHandler();
            IHappinessModifier happinessModifier = happinessHandler.getModifier(SalvationHappinessFactorTypeInitializer.PURIFICATION_HAPPINESS_MODIFIER);

            // Set our citizens up to understand Salvation happiness.
            if (happinessModifier == null)
            {
                happinessHandler.addModifier(new ExpirationBasedHappinessModifier(SalvationHappinessFactorTypeInitializer.PURIFICATION_HAPPINESS_MODIFIER, 3.0, new DynamicHappinessSupplier(SalvationHappinessFactorTypeInitializer.PURIFICATION_HAPPINESS_MODIFIER_LOCATION), 2));
                
                // IDEA: Figure out how to get custom happiness modifier to show on citizen, or adjust "social"
                // happinessHandler.processDailyHappiness(citizenData);
                // LOGGER.info("Added purification happiness modifier to citizen: {}, {}", citizenData.getName(), happinessHandler.getModifiers());

                citizenData.markDirty(0);
            }
        }

    }


    /**
     * Handles the spawning of refugees at the building hosting this module, based on the current corruption stage.
     */
    private void handleRefugees()
    {
        IColony colony = building.getColony();
        Level level = colony.getWorld();

        long gameTime = level.getGameTime();

        if (!(level instanceof ServerLevel serverLevel)) return;

        int refugeeLevel = SalvationManager.stageForLevel(serverLevel).ordinal();
        if (refugeeLevel > SalvationManager.finalStage().ordinal()) 
        {
            refugeeLevel = SalvationManager.finalStage().ordinal();
        }

        int numVisitors = building.getColony().getVisitorManager().getCivilianDataMap().size();

        if (numVisitors >= Math.max(refugeeLevel, building.getBuildingLevel()) * 2)
        {
            return;
        }

        int localRefugeeLevel = refugeeLevel;
        TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES,
            () -> LOGGER.info("Checking for refugees at gametime {}, with a refugee level of {} and a next refugee eligibility time of {}.", localRefugeeLevel, gameTime, nextRefugeeTime));

        if (refugeeLevel > 0 && gameTime >= nextRefugeeTime)
        {

            final IVisitorData visitorData = spawnVisitor(refugeeLevel);

            if (visitorData != null)
            {
                visitorData
                    .triggerInteraction(new RecruitmentInteraction(
                        Component.translatable("com.salvation.coremod.gui.chat.recruitstory" +
                            (level.random.nextInt(10) + "." + refugeeLevel), visitorData.getName().split(" ")[0]),
                        ChatPriority.IMPORTANT));
            }

            nextRefugeeTime = gameTime + level.getRandom().nextInt(3000) +
                (6000 / building.getBuildingLevel()) * colony.getCitizenManager().getCurrentCitizenCount() /
                    colony.getCitizenManager().getMaxCitizens();
        }
    }

    /**
     * Spawns a refugee citizen that can be recruited.
     */
    @Nullable
    protected IVisitorData spawnVisitor(int refugeeLevel)
    {
        final int recruitLevel = building.getColony().getWorld().random.nextInt(refugeeLevel * building.getBuildingLevel()) + 15;

        final IVisitorData newCitizen = (IVisitorData) building.getColony().getVisitorManager().createAndRegisterCivilianData();
        newCitizen.setBedPos(building.getPosition());
        newCitizen.setHomeBuilding(building);
        newCitizen.getCitizenSkillHandler().init(recruitLevel);
        newCitizen.setRecruitCosts(new ItemStack(BuildingSpecialResearchModule.researchCreditItem(), recruitLevel));

        BlockPos spawnPos = BlockPosUtil.findSpawnPosAround(building.getColony().getWorld(), building.getPosition());
        if (spawnPos == null)
        {
            spawnPos = building.getPosition();
        }

        building.getColony().getVisitorManager().spawnOrCreateCivilian(newCitizen, building.getColony().getWorld(), spawnPos, true);
        if (newCitizen.getEntity().isPresent())
        {
            AbstractEntityCitizen citizenEntity = newCitizen.getEntity().get();
            citizenEntity.setHealth(citizenEntity.getMaxHealth() - refugeeLevel);
            citizenEntity.setItemSlot(EquipmentSlot.CHEST, getVests(recruitLevel));
        }
        building.getColony().getEventDescriptionManager().addEventDescription(new VisitorSpawnedEvent(spawnPos, newCitizen.getName()));

        return newCitizen;
    }

    /**
     * Get the hat for the given recruit level.
     *
     * @param recruitLevel the input recruit level.
     * @return the itemstack for the boots.
     */
    private ItemStack getVests(final int recruitLevel)
    {
        ItemStack newVest = null;

        switch (recruitLevel)
        {
            case 1:
                newVest = new ItemStack(NullnessBridge.assumeNonnull(Items.LEATHER_CHESTPLATE));
                break;
            case 2:
                newVest = new ItemStack(NullnessBridge.assumeNonnull(Items.IRON_CHESTPLATE));
                break;
            case 3:
                newVest = new ItemStack(NullnessBridge.assumeNonnull(Items.GOLDEN_CHESTPLATE));
                break;
            case 4:
                newVest = new ItemStack(NullnessBridge.assumeNonnull(Items.DIAMOND_CHESTPLATE));
                break;
            default:
                newVest = ItemStack.EMPTY;
                break;
        }

        return newVest;
    }
}
