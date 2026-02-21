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
import com.deathfrog.salvationmod.core.colony.buildings.BuildingEnvironmentalLab;
import com.deathfrog.salvationmod.core.colony.jobs.JobLabTech;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.FurnaceUserModule;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAIRequestSmelter;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

public class EntityAIWorkLabTech extends AbstractEntityAIRequestSmelter<JobLabTech, BuildingEnvironmentalLab>
{

    public static final Logger LOGGER = LogUtils.getLogger();

    static private final int REFUEL_LEVEL = 100;
    static private final int ESSENCE_RESTOCK_LEVEL = 8;
    static private final int BURSTS_PER_ESSENCE = 8;
    static private final float CHANCE_FOR_CUSTOM_ACTION = 0.33f;

    protected BlockPos purifyingFurnacePos = null;
    protected BlockPos curentBeaconMaintenancePos = null;

    public enum LabTechAIState implements IAIState
    {
        MAINTAIN_BEACONS, RETRIEVE_ESSENCE;

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
            new AITarget<IAIState>(START_WORKING, DECIDE, 2),
            new AITarget<IAIState>(CRAFT, this::craft, 50));
        worker.setCanPickUpLoot(true);
    }

    /**
     * Decides on the next AI state based on the current conditions.
     * @return The next AI state to transition to.
     */
    @Override
    protected IAIState decide()
    {
        // Custom LabTech actions.
        // This includes removing purification essence from PurifyingFurnaces, if any exist.
        // This includes doing "analysis" work, once implemented.
    
        IAIState state = null;

        if (worker.getRandom().nextFloat() <= CHANCE_FOR_CUSTOM_ACTION)
        {
            state = retrieveEssence();
            if (state != null) return state;
        }

        if (worker.getRandom().nextFloat() <= CHANCE_FOR_CUSTOM_ACTION)
        {
            state = maintainBeacons();
            if (state != null) return state;
        }

        return super.decide();
    }

    /**
     * Retrieves a purification essence from a PurifyingFurnace if one exists.
     * 
     * @return The next AI state to transition to, or null if no change is needed.
     */
    @Nullable public IAIState retrieveEssence()
    {
        if (!(building.getColony().getWorld() instanceof ServerLevel serverLevel)) return null;

        BlockPos localBlockPos = purifyingFurnacePos;

        if (localBlockPos != null && serverLevel.getBlockEntity(localBlockPos) instanceof PurifyingFurnaceBlockEntity furnace)
        {
            if (!this.walkToWorkPos(purifyingFurnacePos))
            {
                return getState();
            }
            
            ItemStack essence = furnace.removeItem(PurifyingFurnaceBlockEntity.SLOT_BONUS, 64);   
            if (!essence.isEmpty())
            {
                boolean success = InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(essence, this.worker.getInventoryCitizen());

                if (!success)
                {
                    MCTPInventoryUtils.dropItemsInWorld(serverLevel, localBlockPos, essence);
                }

                purifyingFurnacePos = null;
                return null;
            }
        }

        FurnaceUserModule module = (FurnaceUserModule)this.building.getModule(com.minecolonies.core.colony.buildings.modules.BuildingModules.FURNACE);
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
                    return LabTechAIState.RETRIEVE_ESSENCE;
                }
            }
        }

        return null;
    }


    @Nullable public IAIState maintainBeacons()
    {
        if (!(building.getColony().getWorld() instanceof ServerLevel serverLevel)) return null;

        BlockPos localBlockPos = curentBeaconMaintenancePos;

        ItemStorage essenceStack = new ItemStorage(ModItems.ESSENCE_OF_CORRUPTION.get());

        Predicate<ItemStack> predicate = (stack) -> 
        {
            return Objects.equals(new ItemStorage(stack), essenceStack);
        };

        int essenceCount = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), predicate);

        if (localBlockPos != null && serverLevel.getBlockEntity(localBlockPos) instanceof PurificationBeaconCoreBlockEntity beacon)
        {
            if (!this.walkToSafePos(localBlockPos))
            {
                return getState();
            }
            
            int fuelNeeded = (REFUEL_LEVEL * 2) - beacon.getBoostingFuel();
            int unitsToAdd = (int) Math.ceil(fuelNeeded / BURSTS_PER_ESSENCE);

            boolean didReduce = InventoryUtils.attemptReduceStackInItemHandler(getInventory(), null, unitsToAdd);

            if (didReduce)
            {
                beacon.addBoostingFuel(unitsToAdd * BURSTS_PER_ESSENCE);
            }

            if (beacon.getBoostingFuel() > REFUEL_LEVEL)
            {
                curentBeaconMaintenancePos = null;
                return null;
            }
        }

        if (essenceCount <= ESSENCE_RESTOCK_LEVEL)
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
                TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {}: No outstanding requests for essence of corruption - making one now.", building.getColony().getID()));

                // Make a seasoning request.
                worker.getCitizenData()
                    .createRequestAsync(new Stack(essenceStack.getItemStack(),
                        Constants.STACKSIZE,
                        1));
            }

            if (essenceCount <= 0) return null;

        }
        else
        {
                boolean stocked = InventoryUtils.transferXOfFirstSlotInItemHandlerWithIntoNextFreeSlotInItemHandler(
                    worker.getInventoryCitizen(),
                    predicate,
                    Constants.STACKSIZE, building.getItemHandlerCap()
                );

                if (!stocked)
                {
                    return null;
                }
        }

        for (Beacon beaconInfo : PurificationBeaconCoreBlockEntity.getBeacons(building.getColony()))
        {
            if (beaconInfo == null) continue;
            
            BlockPos beaconLocation = beaconInfo.getPosition();

            if (beaconLocation == null) continue;

            BlockEntity furnaceEntity = world.getBlockEntity(beaconLocation);

            if (furnaceEntity instanceof PurificationBeaconCoreBlockEntity beacon)
            {
                if (beacon.getBoostingFuel() <= REFUEL_LEVEL)
                {
                    this.curentBeaconMaintenancePos = beaconInfo.getPosition();
                    return LabTechAIState.MAINTAIN_BEACONS;
                }
            }
        }

        return null;
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
}
