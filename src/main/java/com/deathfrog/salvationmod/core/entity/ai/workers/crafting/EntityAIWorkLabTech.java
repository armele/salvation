package com.deathfrog.salvationmod.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.MCTPInventoryUtils;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.ModItems;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.blockentity.Beacon;
import com.deathfrog.salvationmod.core.blockentity.PurificationBeaconCoreBlockEntity;
import com.deathfrog.salvationmod.core.blockentity.PurifyingFurnaceBlockEntity;
import com.deathfrog.salvationmod.core.blocks.PurifyingFurnace;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.deathfrog.salvationmod.core.colony.buildings.BuildingEnvironmentalLab;
import com.deathfrog.salvationmod.core.colony.buildings.modules.BuildingModules;
import com.deathfrog.salvationmod.core.colony.buildings.modules.PurifyingFurnaceModule;
import com.deathfrog.salvationmod.core.colony.jobs.JobLabTech;
import com.deathfrog.salvationmod.core.colony.requestable.CorruptedItemDeliverable;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.constant.BuildingConstants;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.translation.RequestSystemTranslationConstants;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.GATHERING_REQUIRED_MATERIALS;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.FILL_UP_FURNACES;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static com.minecolonies.api.util.constant.TranslationConstants.FURNACE_USER_NO_FUEL;

public class EntityAIWorkLabTech extends AbstractEntityAICrafting<JobLabTech, BuildingEnvironmentalLab>
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String STAT_ITEMS_PURIFIED = "items_purified";
    public static final String STAT_BEACONS_FUELED = "beacons_fueled";

    public static final String LABTECH_NO_FURNACES = "com.salvation.labtech.no_furnaces";
    public static final String LABTECH_NOTHING_TO_PURIFY = "com.salvation.labtech.nothing_to_purify";
    public static final String LABTECH_ENABLE_BEACONS = "com.salvation.labtech.enable_beacons";
    public static final String PURIFIABLE_REQUESTS = "com.salvation.corrupted_items";
    /**
     * How many times the AI should attempt to find an allegedly delivered item before giving up on it.
     */
    protected int deliverAcceptanceCounter = 0;
    protected static final int SOFT_DELIVERY_ACCEPTANCE_COUNTER = 10;
    protected static final int HARD_DELIVERY_ACCEPTANCE_COUNTER = 20;
    protected static final int RETRIEVE_SMELTABLE_IF_MORE_THAN = 10;

    static private final int REFUEL_LEVEL = 100;
    static private final int ESSENCE_RESTOCK_LEVEL = 8;
    static private final int BURSTS_PER_ESSENCE = 8;
    static private final float CHANCE_FOR_CUSTOM_ACTION = 0.33f;

    private static final int STORAGE_BUFFER = 3;

    protected BlockPos purifyingFurnacePos = null;
    protected BlockPos currentBeaconMaintenancePos = null;
    protected boolean backupFurnaceScan = false;

    public enum LabTechAIState implements IAIState
    {
        MAINTAIN_BEACONS, RETRIEVE_PRODUCTS, PURIFY_ITEMS, ADD_FUEL;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }
    
    /**
     * Crafting icon
     */
    private final static VisibleCitizenStatus CRAFTING =
        new VisibleCitizenStatus(ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/icons/work/labtech_crafting.png"),
            "com.salvation.gui.visiblestatus.labtech.crafting");

    @SuppressWarnings("unchecked")
    public EntityAIWorkLabTech(@NotNull JobLabTech job)
    {
        super(job);
        super.registerTargets(new AITarget<IAIState>(IDLE, START_WORKING, 2),
            new AITarget<IAIState>(LabTechAIState.MAINTAIN_BEACONS, this::maintainBeacons, 50),
            new AITarget<IAIState>(LabTechAIState.RETRIEVE_PRODUCTS, this::retrieveProducts, 50),
            new AITarget<IAIState>(LabTechAIState.PURIFY_ITEMS, this::purifyItems, 50),
            new AITarget<IAIState>(FILL_UP_FURNACES, this::fillUpFurnace, 20),
            new AITarget<IAIState>(DECIDE, this::decide, 50)
        );

        worker.setCanPickUpLoot(true);
    }

    /**
     * Decides on the next AI state based on the current conditions.
     * @return The next AI state to transition to.
     */
    @Override
    public IAIState decide()
    {
        // Custom LabTech actions.

        if (worker.getRandom().nextFloat() <= CHANCE_FOR_CUSTOM_ACTION)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech decide() triggering RETRIEVE_PRODUCTS", building.getColony().getID()));

            return LabTechAIState.RETRIEVE_PRODUCTS;
        }

        if (worker.getRandom().nextFloat() <= CHANCE_FOR_CUSTOM_ACTION)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech decide() triggering MAINTAIN_BEACONS", building.getColony().getID()));

            return LabTechAIState.MAINTAIN_BEACONS;
        }

        IAIState superState = super.decide();

        if (currentRecipeStorage == null && worker.getRandom().nextFloat() <= CHANCE_FOR_CUSTOM_ACTION)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech decide() triggering PURIFY_ITEMS", building.getColony().getID()));

            return LabTechAIState.PURIFY_ITEMS;
        }

        return superState;
    }


    /**
     * Waits for the AI to receive a request to craft an item.
     * If the AI receives a request, it will transition to the GET_MATERIALS state.
     * @return The next AI state to transition to.
     */
    @Override
    protected @NotNull IAIState waitForRequests() 
    {
        IAIState state = super.waitForRequests();

        TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech waitForRequests() has open sync request? {} Has completed reqeusts to pick up? {} state: {}. deliverAcceptanceCounter: {}", 
            building.getColony().getID(), building.hasOpenSyncRequest(worker.getCitizenData()), building.hasCitizenCompletedRequestsToPickup(worker.getCitizenData()), state, deliverAcceptanceCounter));

        if (state != AIWorkerState.NEEDS_ITEM) 
        {
            deliverAcceptanceCounter = 0;
            return state;
        }

        if (deliverAcceptanceCounter++ < SOFT_DELIVERY_ACCEPTANCE_COUNTER || building.hasOpenSyncRequest(worker.getCitizenData())) 
        {
            return state;
        }

        boolean clearedSomething = cleanStuckRequests(deliverAcceptanceCounter);

        if (clearedSomething)
        {
            deliverAcceptanceCounter = 0;
        }

        // If we didn't clear anything, staying in NEEDS_ITEM is more honest than DECIDE.
        return clearedSomething ? AIWorkerState.DECIDE : AIWorkerState.NEEDS_ITEM;
    }

    /**
     * Retrieves the final smelted product and a purification essence from a PurifyingFurnace if one exists.
     * 
     * @return The next AI state to transition to, or null if no change is needed.
     */
    @Nullable public IAIState retrieveProducts()
    {
        if (!(building.getColony().getWorld() instanceof ServerLevel serverLevel)) 
        {
            Log.getLogger().error("Colony {} - LabTech retrieveEssence() has no server level.", building.getColony().getID());
            return DECIDE;
        }

        BlockPos localBlockPos = purifyingFurnacePos;

        if (localBlockPos != null && serverLevel.getBlockEntity(localBlockPos) instanceof PurifyingFurnaceBlockEntity furnace)
        {

            TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech retrieveProducts() targeting furnace at {} for product retrieval.", 
                building.getColony().getID(), localBlockPos.toShortString()));

            if (!this.walkToWorkPos(purifyingFurnacePos))
            {
                return getState();
            }
            
            ItemStack essence = furnace.removeItem(PurifyingFurnaceBlockEntity.SLOT_BONUS, 64);   
            if (!essence.isEmpty())
            {
                int essenceCount = essence.getCount();
                StatsUtil.trackStat(building, STAT_ITEMS_PURIFIED, essenceCount);
                
                boolean success = InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(essence, this.worker.getInventoryCitizen());

                if (!success)
                {
                    MCTPInventoryUtils.dropItemsInWorld(serverLevel, localBlockPos, essence);
                }
            }

            ItemStack product = furnace.removeItem(PurifyingFurnaceBlockEntity.SLOT_RESULT, 64);   
            if (!product.isEmpty())
            {
                boolean success = InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(product, this.worker.getInventoryCitizen());

                if (!success)
                {
                    MCTPInventoryUtils.dropItemsInWorld(serverLevel, localBlockPos, product);
                }

                purifyingFurnacePos = null;
            }

            TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech retrieveProducts() furnace at {}; labtech retrieved {} and {}.", 
                building.getColony().getID(), localBlockPos.toShortString(), essence, product));

            return DECIDE;
        }

        PurifyingFurnaceModule module = this.building.getModule(PurifyingFurnaceModule.class);
        for (BlockPos furnacePos : module.getFurnaces())
        {
            if (furnacePos == null) continue;
            
            BlockEntity furnaceEntity = world.getBlockEntity(furnacePos);

            if (furnaceEntity instanceof PurifyingFurnaceBlockEntity furnace)
            {
                ItemStack resultSlot = furnace.getItem(PurifyingFurnaceBlockEntity.SLOT_BONUS);
                if (!resultSlot.isEmpty())
                {
                    this.purifyingFurnacePos = furnacePos;
                    return LabTechAIState.RETRIEVE_PRODUCTS;
                }
            }
        }

        return DECIDE;
    }

    /**
     * A state that is responsible for maintaining the purification beacons in the colony.
     * This state will check to see if any of the beacons need their fuel replenished.
     * If a beacon needs fuel, this state will attempt to restock it from the colony's inventory.
     * If the colony does not have enough essence of corruption to restock the beacon, this state will request more from the colony.
     * If the colony has enough essence of corruption, this state will give the job to the worker to walk to the beacon and restock it.
     * @return The next state to transition to, or null if no transition is needed.
     */
    @Nullable public IAIState maintainBeacons()
    {
        if (!(building.getColony().getWorld() instanceof ServerLevel serverLevel)) return DECIDE;

        boolean enabled = building.getColony().getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_ENABLE_BEACONS) > 0;

        TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech maintainBeacons() research status: {}", building.getColony().getID(), enabled));

        if (!enabled)
        {
            job.tickEnableBeaconsCounter();

            if (worker.getCitizenData() != null)
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatableEscape(LABTECH_ENABLE_BEACONS), ChatPriority.BLOCKING));
            }

            return DECIDE;
        }

        job.resetEnableBeaconsCounter();

        BlockPos localBlockPos = currentBeaconMaintenancePos;

        ItemStorage essenceStack = new ItemStorage(ModItems.ESSENCE_OF_CORRUPTION.get());

        Predicate<ItemStack> predicate = (stack) -> 
        {
            return Objects.equals(new ItemStorage(stack), essenceStack);
        };

        int workerEssenceCount = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), predicate);

        TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech maintainBeacons() labtech essence count: {} at {} ", 
            building.getColony().getID(), workerEssenceCount, localBlockPos == null ? "null" : localBlockPos.toShortString()));

        if (localBlockPos != null && serverLevel.getBlockEntity(localBlockPos) instanceof PurificationBeaconCoreBlockEntity beacon && workerEssenceCount > 0)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech maintainBeacons() going to fuel beacon at {}.", 
              building.getColony().getID(), localBlockPos.toShortString()));

            if (!this.walkToSafePos(localBlockPos))
            {
                return getState();
            }
            
            int fuelNeeded = (REFUEL_LEVEL * 2) - beacon.getBoostingFuel();
            int unitsToAdd = (int) Math.ceil(fuelNeeded / BURSTS_PER_ESSENCE);

            boolean didReduce = InventoryUtils.attemptReduceStackInItemHandler(getInventory(), null, unitsToAdd);

            TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech maintainBeacons() fueling beacon. fuelNeeded: {}, unitsToAdd: {}, didReduce: {}", 
              building.getColony().getID(), fuelNeeded, unitsToAdd, didReduce));

            if (didReduce)
            {
                StatsUtil.trackStat(building, STAT_BEACONS_FUELED, 1);
                beacon.addBoostingFuel(unitsToAdd * BURSTS_PER_ESSENCE);
            }

            if (beacon.getBoostingFuel() > REFUEL_LEVEL)
            {
                currentBeaconMaintenancePos = null;
                return DECIDE;
            }
        }

        int buildingEssenceCount = InventoryUtils.getItemCountInItemHandler(building.getItemHandlerCap(), predicate);

        if (buildingEssenceCount + workerEssenceCount <= ESSENCE_RESTOCK_LEVEL)
        {
            // Check to see if we've already asked for more essence of corruption. If not, request more.
            final ImmutableList<IRequest<? extends Stack>> openRequests = building.getOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(Stack.class));
            boolean outstandingRequest = false;

            for (IRequest<? extends Stack> request : openRequests)
            {
                if (request.getRequest().getStack().is(NullnessBridge.assumeNonnull(essenceStack.getItem())))
                {
                    outstandingRequest = true;
                }
            }

            if (!outstandingRequest)
            {
                TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {}: maintainBeacons() Building Essence Count: {}, No outstanding requests for {} - making one now.", building.getColony().getID(), buildingEssenceCount, essenceStack));

                // Make a new request.
                worker.getCitizenData()
                    .createRequestAsync(new Stack(essenceStack.getItemStack(),
                        Constants.STACKSIZE,
                        1));
            }

            if (buildingEssenceCount <= 0) return DECIDE;

        }
        else
        {
            boolean stocked = InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoNextFreeSlotInItemHandler(
                building.getItemHandlerCap(),
                predicate,
                Constants.STACKSIZE, worker.getInventoryCitizen()
            );

            if (!stocked)
            {
                return DECIDE;
            }
        }

        Set<Beacon> beacons = PurificationBeaconCoreBlockEntity.getBeacons(building.getColony());
        
        TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech maintainBeacons() checking beacon fuel need for {} beacons.", building.getColony().getID(), beacons.size()));

        for (Beacon beaconInfo : beacons)
        {
            if (beaconInfo == null) continue;
            
            BlockPos beaconLocation = beaconInfo.getPosition();

            if (beaconLocation == null) continue;

            BlockEntity furnaceEntity = world.getBlockEntity(beaconLocation);

            if (furnaceEntity instanceof PurificationBeaconCoreBlockEntity beacon)
            {
                if (beacon.getBoostingFuel() <= REFUEL_LEVEL)
                {
                    this.currentBeaconMaintenancePos = beaconInfo.getPosition();
                    TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech maintainBeacons() identified beacon with fuel need at {}.", 
                        building.getColony().getID(), currentBeaconMaintenancePos.toShortString()));

                    return LabTechAIState.MAINTAIN_BEACONS;
                }
            }
        }

        return DECIDE;
    }


    /**
     * Returns the expected class of the building that this AI is bound to.
     * In this case, it is a {@link BuildingEnvironmentalLab}.
     * @return The expected class of the building.
     */
    @Override
    public Class<BuildingEnvironmentalLab> getExpectedBuildingClass()
    {   
        return BuildingEnvironmentalLab.class;
    }

    /**
     * Starts the crafting process and sets the worker's visible status to CRAFTING.
     * @return The next AI state to transition to.
     */
    @Override
    protected IAIState craft()
    {
        worker.getCitizenData().setVisibleStatus(CRAFTING);
        return super.craft();
    }


    /**
     * Cleans stuck requests from the building's request queue that are not deliverable anymore (for example, if a request is async, but the
     * citizen is not available to pick it up anymore).
     * 
     * @return true if any requests were cleared, false otherwise.
     */
    protected boolean cleanStuckRequests(int tryCounter)
    {
        ICitizenData citizen = worker.getCitizenData();
        Collection<IRequest<?>> completed = building.getCompletedRequestsOfCitizenOrBuilding(citizen);

        boolean cleared = false;

        // Copy IDs to avoid concurrent modification surprises.
        List<IRequest<?>> snapshot = new ArrayList<>(completed);

        for (IRequest<?> request : snapshot)
        {
            IToken<?> id = request.getId();
            if (!request.canBeDelivered() || citizen.isRequestAsync(id) || tryCounter > HARD_DELIVERY_ACCEPTANCE_COUNTER)
            {

                TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - Guest Services cleanStuckRequests() clearing stuck request: {}", 
                    building.getColony().getID(), request.getLongDisplayString()));

                building.markRequestAsAccepted(citizen, id);
                cleared = true;
            }
        }

        return cleared;
    }

    /**
     * If the AI has a building, it will attempt to purify items in the building.
     * It will first check if it can walk to the building.
     * If the AI can walk to the building, it will check if there are any items to purify.
     * If there are no items to purify, it will notify the worker that there are no items to purify.
     * If there are items to purify, it will check if there are any furnaces available to retrieve fuel from.
     * If there are furnaces available, it will walk to the furnace and retrieve the fuel.
     * If there are no furnaces available, it will check if there are any items to retrieve from the furnaces.
     * If there are items to retrieve, it will walk to the furnace and retrieve the items.
     * If there are no items to retrieve, it will check if the AI has enough smeltable to craft the items to purify.
     * If the AI does not have enough smeltable, it will request smeltable from the request system.
     * If the AI has enough smeltable, it will check if the AI has enough fuel to craft the items to purify.
     * If the AI does not have enough fuel, it will request fuel from the request system.
     * If the AI has enough fuel, it will attempt to craft the items to purify.
     * @return The next AI state to transition to.
     */
    public IAIState purifyItems()
    {
        if (!walkToBuilding())
        {
            return getState();
        }

        final PurifyingFurnaceModule furnaceModule = building.getModule(PurifyingFurnaceModule.class);
        final ItemListModule fuelListModule = building.getModule(ItemListModule.class, m -> m.getId().equals(BuildingConstants.FUEL_LIST));

        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);

        List<ItemStorage> itemsToPurify = BuildingEnvironmentalLab.getAllowedItems();

        if (fuelListModule.getList().isEmpty())
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech purifyItems() no fuel items set.", building.getColony().getID()));
            job.tickMissingFuelCounter();

            if (worker.getCitizenData() != null)
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatableEscape(FURNACE_USER_NO_FUEL), ChatPriority.BLOCKING));
            }
            return getState();
        }
        else
        {
            job.resetMissingFuelCounter();
        }

        if (furnaceModule.getFurnaces().isEmpty() && !backupFurnaceScan)
        {
            furnaceModule.scanBuildingForPurifyingFurnaces();
            backupFurnaceScan = true;
        }

        if (furnaceModule.getFurnaces().isEmpty())
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech purifyItems() has no furnaces.", building.getColony().getID()));
            job.tickMissingFurnaceCounter();

            if (worker.getCitizenData() != null)
            {
                worker.getCitizenData()
                  .triggerInteraction(new StandardInteraction(Component.translatableEscape(LABTECH_NO_FURNACES), ChatPriority.BLOCKING));
            }
            return getState();
        }
        else
        {
            job.resetMissingFurnaceCounter();
        }

        final int amountOfSmeltableInBuilding = InventoryUtils.getCountFromBuilding(building, this::isSmeltable);
        final int amountOfSmeltableInInv = InventoryUtils.getItemCountInItemHandler((worker.getInventoryCitizen()), this::isSmeltable);

        final int amountOfFuelInBuilding = InventoryUtils.getCountFromBuilding(building, itemsToPurify);
        final int amountOfFuelInInv = InventoryUtils.getItemCountInItemHandler((worker.getInventoryCitizen()), stack -> fuelListModule.isItemInList(new ItemStorage(stack)));

        boolean maxToKeep = reachedMaxToKeep();
        if (amountOfSmeltableInBuilding + amountOfSmeltableInInv <= 0 && !maxToKeep)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech purifyItems() needs to request smeltable items. Building: {}, Inventory: {}, MaxToKeep: {}", 
              building.getColony().getID(), amountOfSmeltableInBuilding, amountOfSmeltableInInv, maxToKeep));
            requestSmeltable();
        }

        if (amountOfFuelInBuilding + amountOfFuelInInv <= 0 && !building.hasWorkerOpenRequestsFiltered(worker.getCitizenData().getId(),
          req -> req.getShortDisplayString().getSiblings().contains(Component.translatableEscape(RequestSystemTranslationConstants.REQUESTS_TYPE_BURNABLE))))
        {
            worker.getCitizenData()
              .createRequestAsync(new StackList(getAllowedFuel(), RequestSystemTranslationConstants.REQUESTS_TYPE_BURNABLE, Constants.STACKSIZE * furnaceModule.getFurnaces().size(), 1));
        }

        if (amountOfSmeltableInBuilding > 0 && amountOfSmeltableInInv == 0)
        {
            needsCurrently = new Tuple<>(this::isSmeltable, Constants.STACKSIZE);
            return GATHERING_REQUIRED_MATERIALS;
        }
        else if (amountOfFuelInBuilding > 0 && amountOfFuelInInv == 0)
        {
            needsCurrently = new Tuple<>(stack -> fuelListModule.isItemInList(new ItemStorage(stack)), Constants.STACKSIZE);
            return GATHERING_REQUIRED_MATERIALS;
        }

        return checkIfAbleToSmelt(amountOfFuelInBuilding + amountOfFuelInInv, amountOfSmeltableInBuilding + amountOfSmeltableInInv);
    }

    /**
     * If the worker reached his max amount.
     *
     * @return true if so.
     */
    protected boolean reachedMaxToKeep()
    {
        final int count = InventoryUtils.countEmptySlotsInBuilding(building);
        return count <= STORAGE_BUFFER;
    }
    
    /**
     * Checks if the given ItemStack is smeltable in the building.
     *
     * @param stack the ItemStack to check
     * @return true if the stack is smeltable, false otherwise
     */
    protected boolean isSmeltable(final ItemStack stack)
    {
        if (ItemStackUtils.isEmpty(stack))
        {
            return false;
        }

        if (ItemStackUtils.IS_SMELTABLE.test(stack) &&  BuildingEnvironmentalLab.getAllowedItems().contains(new ItemStorage(stack)))
        {
            return true;
        }

        return false;
    }


    /**
     * Requests smeltable to be delivered to the building. If the building does not have enough purifiable items, 
     * it will request them from the request system. If the building has enough purifiable, it will not request anything.
     * If the building has no allowed items to request, it will notify the worker that there are no allowed items to request.
     * If the building has allowed items to request, it will request the items from the request system.
     */
    public void requestSmeltable()
    {
        if (InventoryUtils.hasBuildingEnoughElseCount(building, s -> new CorruptedItemDeliverable(1).matches(s), 1) <= 0
            && !building.hasWorkerOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(CorruptedItemDeliverable.class))
            && !building.hasWorkerOpenRequestsFiltered(worker.getCitizenData().getId(),
                req -> req.getShortDisplayString().getSiblings().contains(Component.translatable(PURIFIABLE_REQUESTS))))
        {
            final List<ItemStorage> allowedItems = BuildingEnvironmentalLab.getAllowedItems();

            if (allowedItems.isEmpty())
            {
                worker.getCitizenData().createRequestAsync(new CorruptedItemDeliverable(Constants.STACKSIZE * building.getModule(PurifyingFurnaceModule.class).getFurnaces().size()));
            }
            else
            {
                final List<ItemStack> requests = BuildingEnvironmentalLab.getAllowedItems().stream().map(ItemStorage::getItemStack).toList();

                if (requests.isEmpty())
                {
                    job.tickNothingToPurifyCounter();
                    if (worker.getCitizenData() != null)
                    {
                        worker.getCitizenData()
                          .triggerInteraction(new StandardInteraction(Component.translatableEscape(LABTECH_NOTHING_TO_PURIFY), ChatPriority.IMPORTANT));
                    }
                }
                else
                {
                    worker.getCitizenData()
                      .createRequestAsync(new StackList(requests,
                        PURIFIABLE_REQUESTS,
                        Constants.STACKSIZE * building.getModule(PurifyingFurnaceModule.class).getFurnaces().size(),
                        1));
                }
            }
        }
    }

    /**
     * Get a copy of the list of allowed fuel.
     *
     * @return the list.
     */
    private List<ItemStack> getAllowedFuel()
    {
        final List<ItemStack> list = new ArrayList<>();
        for (final ItemStorage storage : building.getModule(ItemListModule.class, m -> m.getId().equals(BuildingConstants.FUEL_LIST)).getList())
        {
            final ItemStack stack = storage.getItemStack().copy();
            stack.setCount(stack.getMaxStackSize());
            list.add(stack);
        }
        return list;
    }

    /**
     * Checks if the worker has enough fuel and/or smeltable to start smelting.
     *
     * @param amountOfFuel      the total amount of fuel.
     * @param amountOfSmeltable the total amount of smeltables.
     * @return START_USING_FURNACE if enough, else check for additional worker specific jobs.
     */
    private IAIState checkIfAbleToSmelt(final int amountOfFuel, final int amountOfSmeltable)
    {

        if (amountOfSmeltable > 0)
        {
            job.resetNothingToPurifyCounter();
        }

        final PurifyingFurnaceModule module = building.getModule(PurifyingFurnaceModule.class);
        for (final BlockPos pos : module.getFurnaces())
        {
            if (pos == null) continue;

            final BlockEntity entity = world.getBlockEntity(pos);

            if (entity instanceof PurifyingFurnaceBlockEntity furnace)
            {
                if ((amountOfFuel > 0 && furnace.hasSmeltableInFurnaceAndNoFuel())
                      || (amountOfSmeltable > 0 && furnace.hasFuelInFurnaceAndNoSmeltable())
                      || (amountOfFuel > 0 && amountOfSmeltable > 0 && furnace.hasNeitherFuelNorSmeltAble()))
                {
                    purifyingFurnacePos = pos;

                    TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - LabTech can smelt - Furnace {}.", building.getColony().getID(), pos.toShortString()));

                    return FILL_UP_FURNACES;
                }
            }
            else
            {
                if (!(world.getBlockState(pos).getBlock() instanceof PurifyingFurnace))
                {
                    module.removeFromFurnaces(pos);
                }
            }
        }

        return DECIDE;
    }

    /**
     * Smelt the smeltable after the required items are in the inv.
     *
     * @return the next state to go to.
     */
    private IAIState fillUpFurnace()
    {
        if (building.getModule(BuildingModules.PURIFYING_FURNACE).getFurnaces().isEmpty())
        {
            if (worker.getCitizenData() != null)
            {
                worker.getCitizenData()
                  .triggerInteraction(new StandardInteraction(Component.translatableEscape(LABTECH_NO_FURNACES), ChatPriority.BLOCKING));
            }
            return START_WORKING;
        }

        BlockPos localFurnacePos = purifyingFurnacePos;
        if (localFurnacePos == null)
        {
            return DECIDE;
        }

        if (!walkToWorkPos(localFurnacePos))
        {
            return getState();
        }

        final BlockEntity entity = world.getBlockEntity(localFurnacePos);
        if (entity instanceof PurifyingFurnaceBlockEntity furnace)
        {

            if (InventoryUtils.hasItemInItemHandler((worker.getInventoryCitizen()), this::isSmeltable)
                  && (furnace.hasFuelInFurnaceAndNoSmeltable() || furnace.hasNeitherFuelNorSmeltAble()))
            {
                InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoInItemHandler(
                  (worker.getInventoryCitizen()), this::isSmeltable, Constants.STACKSIZE,
                  new InvWrapper(furnace), PurifyingFurnaceBlockEntity.SLOT_INPUT);
            }

            final ItemListModule module = building.getModule(ItemListModule.class, m -> m.getId().equals(BuildingConstants.FUEL_LIST));
            if (InventoryUtils.hasItemInItemHandler((worker.getInventoryCitizen()), stack -> module.isItemInList(new ItemStorage(stack)))
                  && (furnace.hasSmeltableInFurnaceAndNoFuel() || furnace.hasNeitherFuelNorSmeltAble()))
            {
                InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoInItemHandler(
                  (worker.getInventoryCitizen()), stack -> module.isItemInList(new ItemStorage(stack)), Constants.STACKSIZE,
                  new InvWrapper(furnace), PurifyingFurnaceBlockEntity.SLOT_FUEL);
            }
        }
        
        purifyingFurnacePos = null;
        return DECIDE;
    }
}
