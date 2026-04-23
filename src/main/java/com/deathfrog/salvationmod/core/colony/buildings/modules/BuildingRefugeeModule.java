package com.deathfrog.salvationmod.core.colony.buildings.modules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.ModItems;
import com.deathfrog.salvationmod.core.colony.SalvationHappinessFactorTypeInitializer;
import com.deathfrog.salvationmod.core.engine.SalvationEventListener;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.entity.ai.workers.minimal.EntityAIRefugeeWanderTask;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IBuildingEventsModule;
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
import com.minecolonies.api.util.EntityUtils;
import com.minecolonies.core.colony.buildings.DefaultBuildingInstance;
import com.minecolonies.core.colony.eventhooks.citizenEvents.VisitorSpawnedEvent;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class BuildingRefugeeModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule, IBuildingEventsModule
{
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    
    private static final String TAG_NEXTREFUGEE_TIME = "nextRefugeeTime";
    private static final String TAG_REFUGEES = "refugees";
    private static final String TAG_REFUGEE_ID = "refugee";

    /**
     * Eligible time for spawning more refugees
     */
    private long nextRefugeeTime = 10000;

    /**
     * Visitor ids of refugees spawned and owned by this module.
     */
    private final List<Integer> refugees = new ArrayList<>();

    boolean listeningForRecruits = false;


    /**
     * Deserializes the module's state from the given compound tag.
     * <p>
     * This method is responsible for deserializing the module's state from the given compound tag.
     * <p>
     * It will read the next refugnee time from the compound tag and store it in the module's state.
     * <p>
     * The compound tag should contain a single long value named {@link #TAG_NEXTREFUGEE_TIME}.
     * <p>
     * This method is called by the building when it is deserializing its state from a compound tag.
     * <p>
     * The building will pass in the compound tag that it is deserializing from, and this method will read the module's state from the tag.
     * <p>
     * The module is responsible for deserializing its state from the tag and storing it in its instance variables.
     * <p>
     * The module should throw a {@link RuntimeException} if the tag is invalid or if the module is unable to deserialize its state from the tag.
     * <p>
     * The building will catch any exceptions that are thrown by this method and log them to the console.
     * <p>
     * The building will then continue deserializing its state from the tag.
     * @param provider the provider of the compound tag.
     * @param compound the compound tag to deserialize from.
     */
    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        nextRefugeeTime = compound.getLong(TAG_NEXTREFUGEE_TIME);

        refugees.clear();
        final ListTag refugeelist = compound.getList(TAG_REFUGEES, Tag.TAG_COMPOUND);
        for (final Tag data : refugeelist)
        {
            final int id = ((CompoundTag) data).getInt(TAG_REFUGEE_ID);
            final IVisitorData visitorData = building.getColony().getVisitorManager().getVisitor(id);
            if (visitorData != null)
            {
                refugees.add(id);
            }
        }
    }

    /**
     * Serializes the module's state to the given compound tag.
     * <p>
     * This method is responsible for serializing the module's state to the given compound tag.
     * <p>
     * It will write the next refugnee time to the compound tag.
     * <p>
     * The compound tag should contain a single long value named {@link #TAG_NEXTREFUGEE_TIME}.
     * <p>
     * This method is called by the building when it is serializing its state to a compound tag.
     * <p>
     * The building will pass in the compound tag that it is serializing to, and this method will write the module's state to the tag.
     * <p>
     * The module is responsible for serializing its state to the tag and storing it in its instance variables.
     * <p>
     * The module should throw a {@link RuntimeException} if the tag is invalid or if the module is unable to serialize its state to the tag.
     * <p>
     * The building will catch any exceptions that are thrown by this method and log them to the console.
     * <p>
     * The building will then continue serializing its state to the tag.
     * @param provider the provider of the compound tag.
     * @param compound the compound tag to serialize to.
     */
    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        compound.putLong(TAG_NEXTREFUGEE_TIME, nextRefugeeTime);

        final ListTag refugeelist = new ListTag();
        for (final Integer id : refugees)
        {
            final CompoundTag refugeeCompound = new CompoundTag();
            refugeeCompound.putInt(TAG_REFUGEE_ID, id);
            refugeelist.add(refugeeCompound);
        }

        compound.put(TAG_REFUGEES, refugeelist);
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

                // citizenData.markDirty(0);
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
        final int effectiveTownHallLevel = getEffectiveTownHallLevel();

        long gameTime = level.getGameTime();

        if (!(level instanceof ServerLevel serverLevel)) return;

        int refugeeLevel = SalvationManager.stageForLevel(serverLevel).ordinal();

        if (refugeeLevel > SalvationManager.finalStage().ordinal()) 
        {
            refugeeLevel = SalvationManager.finalStage().ordinal();
        }

        rehydrateRefugees(colony);

        final int refugeeCount = refugees.size();
        final int maxRefugees = Math.max(refugeeLevel, effectiveTownHallLevel) * 2; 

        if (refugeeCount >= maxRefugees)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES, () -> LOGGER.info("Colony {} has no room for refugess {} refugees present of {} max.", colony.getID(), refugeeCount, maxRefugees));

            return;
        }

        int localRefugeeLevel = refugeeLevel;
        TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES,
            () -> LOGGER.info("Checking for refugees at gametime {}, with a refugee level of {} and a next refugee eligibility time of {}.", localRefugeeLevel, gameTime, nextRefugeeTime));

        if (gameTime >= nextRefugeeTime)
        {

            final IVisitorData visitorData = spawnVisitor(refugeeLevel);

            if (visitorData != null)
            {
                refugees.add(visitorData.getId());
                visitorData
                    .triggerInteraction(new RecruitmentInteraction(
                        Component.translatable("com.salvation.coremod.gui.chat.recruitstory" +
                            (level.random.nextInt(10) + "." + refugeeLevel), visitorData.getName().split(" ")[0]),
                        ChatPriority.IMPORTANT));
            }

            nextRefugeeTime = gameTime + level.getRandom().nextInt(3000) +
                (6000 / effectiveTownHallLevel) * colony.getCitizenManager().getCurrentCitizenCount() /
                    colony.getCitizenManager().getMaxCitizens();
        }
    }

    /**
     * Prunes removed visitor ids and restores refugee-specific behavior for active refugee entities.
     */
    private void rehydrateRefugees(final IColony colony)
    {
        refugees.removeIf(id -> colony.getVisitorManager().getVisitor(id) == null);

        TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES,
            () -> LOGGER.info("Rehydrating {} refugees in colony {}.", refugees.size(), colony.getID()));

        for (final Integer id : refugees)
        {
            final IVisitorData visitorData = colony.getVisitorManager().getVisitor(id);
            EntityAIRefugeeWanderTask.enableFor(visitorData);
        }
    }

    /**
     * Spawns a refugee citizen that can be recruited.
     */
    @Nullable
    protected IVisitorData spawnVisitor(int refugeeLevel)
    {
        final int townHallLevel = getEffectiveTownHallLevel();
        final int recruitLevel = building.getColony().getWorld().random.nextInt((refugeeLevel + 1) * townHallLevel) + 15;

        final IVisitorData newCitizen = (IVisitorData) building.getColony().getVisitorManager().createAndRegisterCivilianData();
        

        TraceUtils.dynamicTrace(ModCommands.TRACE_REFUGEES, () -> LOGGER.info("Colony {} Refugee {} has been spawned.", newCitizen.getColony().getID(), newCitizen.getUUID()));


        BlockPos tavernPos = building.getColony().getServerBuildingManager().getBestBuilding(building.getPosition(), DefaultBuildingInstance.class);

        if (tavernPos != null && !BlockPos.ZERO.equals(tavernPos))
        {
            IBuilding tavern = building.getColony().getServerBuildingManager().getBuilding(tavernPos);

            if (tavern != null)
            {
                newCitizen.setHomeBuilding(tavern);
                newCitizen.setBedPos(building.getPosition());
            }
            else
            {
                newCitizen.setHomeBuilding(building);
                newCitizen.setBedPos(building.getPosition());
            }
        }        
        else
        {
            newCitizen.setHomeBuilding(building);
            newCitizen.setBedPos(building.getPosition());
        }

        newCitizen.getCitizenSkillHandler().init(recruitLevel);
        newCitizen.setRecruitCosts(new ItemStack(BuildingSpecialResearchModule.researchCreditItem(), recruitLevel));

        final Level world = building.getColony().getWorld();
        final LinkedHashSet<BlockPos> spawnPositions = new LinkedHashSet<>();
        BlockPos spawnPos = EntityUtils.getSpawnPoint(world, building.getPosition());

        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            final BlockPos candidateSpawnPos = EntityUtils.getSpawnPoint(
                world,
                building.getPosition().relative(NullnessBridge.assumeNonnull(direction)));
            if (candidateSpawnPos != null)
            {
                spawnPositions.add(candidateSpawnPos);
            }
        }

        if (spawnPos != null)
        {
            spawnPositions.add(spawnPos);
        }

        if (spawnPositions.isEmpty())
        {
            spawnPos = building.getPosition();
        }
        else if (spawnPos == null)
        {
            spawnPos = spawnPositions.iterator().next();
        }

        building.getColony().getVisitorManager().spawnOrCreateCivilian(newCitizen, world, new ArrayList<>(spawnPositions), true);
        if (newCitizen.getEntity().isPresent())
        {
            AbstractEntityCitizen citizenEntity = newCitizen.getEntity().get();
            citizenEntity.setHealth(citizenEntity.getMaxHealth() - refugeeLevel);
            citizenEntity.setItemSlot(EquipmentSlot.CHEST, getVests(townHallLevel));
            EntityAIRefugeeWanderTask.installForNewRefugee(newCitizen);
        }
        building.getColony().getEventDescriptionManager().addEventDescription(new VisitorSpawnedEvent(spawnPos, newCitizen.getName()));

        return newCitizen;
    }

    /**
     * Called when the building is destroyed.
     * Removes all refugees spawned by the building and clears the internal refugee list.
     */
    @Override
    public void onDestroyed()
    {
        for (final Integer id : refugees)
        {
            final IVisitorData visitorData = building.getColony().getVisitorManager().getVisitor(id);
            if (visitorData != null)
            {
                building.getColony().getVisitorManager().removeCivilian(visitorData);
            }
        }
        refugees.clear();
    }

    /**
     * Refugee spawning scales with town hall progression, but level 0 should still behave like the
     * lowest normal tier rather than producing divide-by-zero or empty random ranges.
     */
    private int getEffectiveTownHallLevel()
    {
        return Math.max(1, Math.min(5, building.getBuildingLevel()));
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
            case 5:
                newVest = new ItemStack(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_CHESTPLATE.get()));
                break;
            default:
                newVest = ItemStack.EMPTY;
                break;
        }

        return newVest;
    }
}
