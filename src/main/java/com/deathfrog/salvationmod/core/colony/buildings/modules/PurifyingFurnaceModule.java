package com.deathfrog.salvationmod.core.colony.buildings.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.core.blockentity.PurifyingFurnaceBlockEntity;
import com.deathfrog.salvationmod.core.blocks.PurifyingFurnace;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IAltersRequiredItems;
import com.minecolonies.api.colony.buildings.modules.IModuleWithExternalBlocks;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.NBTUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.BuildingConstants;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.mojang.logging.LogUtils;

import org.apache.logging.log4j.util.TriConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PurifyingFurnaceModule extends AbstractBuildingModule implements IPersistentModule, IModuleWithExternalBlocks, IAltersRequiredItems
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * Tag to store the furnace list.
     */
    private static final String TAG_FURNACES = "furnaces";

    /**
     * List of registered furnaces.
     */
    private final List<BlockPos> furnaces = new ArrayList<>();

    /**
     * Construct a new furnace user module.
     */
    public PurifyingFurnaceModule()
    {
        super();
    }

    /**
     * Deserialize the furnace list from NBT.
     * 
     * <p>
     * This method deserializes the list of furnaces stored in the NBT compound.
     * If the list is empty after deserialization, it will scan the building for
     * purifying furnaces.
     * 
     * @param provider the holder lookup provider
     * @param compound the compound tag to deserialize from
     */
    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        furnaces.clear();
        
        final ListTag furnaceTagList = compound.getList(TAG_FURNACES, Tag.TAG_INT_ARRAY);
        for (int i = 0; i < furnaceTagList.size(); ++i)
        {
            furnaces.add(NBTUtils.readBlockPos(furnaceTagList.get(i)));
        }

        if (furnaces.isEmpty())
        {
            scanBuildingForPurifyingFurnaces();
        }
    }

    /**
     * Serialize the list of furnaces into NBT.
     * @param provider the holder lookup provider
     * @param compound the compound tag to serialize into
     */
    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        @NotNull final ListTag furnacesTagList = new ListTag();
        for (@NotNull final BlockPos entry : furnaces)
        {
            furnacesTagList.add(NBTUtils.writeBlockPos(entry));
        }
        compound.put(TAG_FURNACES, furnacesTagList);
    }

    /**
     * Rebuild the furnace list by scanning the building contents.
     * 
     * This is intended as a recovery path for old saves or cases where the
     * furnace list was not yet populated when the building was serialized.
     */
    public void scanBuildingForPurifyingFurnaces()
    {
        if (building == null || building.getColony() == null)
        {
            return;
        }

        final Level world = building.getColony().getWorld();
        if (world == null)
        {
            return;
        }

        final var corners = building.getCorners();
        if (corners == null || corners.getA() == null || corners.getB() == null)
        {
            return;
        }

        final BlockPos cornerA = corners.getA();
        final BlockPos cornerB = corners.getB();

        final int minX = Math.min(cornerA.getX(), cornerB.getX());
        final int minY = Math.min(cornerA.getY(), cornerB.getY());
        final int minZ = Math.min(cornerA.getZ(), cornerB.getZ());

        final int maxX = Math.max(cornerA.getX(), cornerB.getX());
        final int maxY = Math.max(cornerA.getY(), cornerB.getY());
        final int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        for (int x = minX; x <= maxX; x++)
        {
            for (int y = minY; y <= maxY; y++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    final BlockPos pos = new BlockPos(x, y, z);

                    if (!WorldUtil.isBlockLoaded(world, pos))
                    {
                        continue;
                    }

                    final BlockState state = world.getBlockState(pos);
                    if (state.getBlock() instanceof PurifyingFurnace && !furnaces.contains(pos))
                    {
                        TraceUtils.dynamicTrace(ModCommands.TRACE_LABTECH, () -> LOGGER.info("Colony {} - backup furnace scan adding purifying furnace at position {}.", building.getColony().getID(), pos.toShortString()));
                        furnaces.add(pos.immutable());
                    }
                }
            }
        }
    }

    /**
     * Remove a furnace from the building.
     *
     * @param pos the position of it.
     */
    public void removeFromFurnaces(final BlockPos pos)
    {
        furnaces.remove(pos);
    }

    /**
     * Check if an ItemStack is one of the accepted fuel items.
     *
     * @param stack the itemStack to check.
     * @return true if so.
     */
    public boolean isAllowedFuel(final ItemStack stack)
    {
        if (ItemStackUtils.isEmpty(stack))
        {
            return false;
        }
        return building.getModule(ItemListModule.class, m -> m.getId().equals(BuildingConstants.FUEL_LIST)).isItemInList(new ItemStorage(stack));
    }

    /**
     * Return a list of furnaces assigned to this hut.
     *
     * @return copy of the list
     */
    public List<BlockPos> getFurnaces()
    {
        return new ArrayList<>(furnaces);
    }

    /**
     * Called when a block is placed in the building.
     * If the block is a furnace or a purifying furnace, add it to the list of furnaces.
     * This is called on the server and on the client.
     *
     * @param blockState the block state of the block placed
     * @param pos the position of the block placed
     * @param world the world the block is being placed in
     */
    @Override
    public void onBlockPlacedInBuilding(@NotNull final BlockState blockState, @NotNull final BlockPos pos, @NotNull final Level world)
    {
        if ((blockState.getBlock() instanceof FurnaceBlock || blockState.getBlock() instanceof PurifyingFurnace) && !furnaces.contains(pos))
        {
            furnaces.add(pos);
        }
    }


    /**
     * Returns a list of all block positions registered to this building.
     * This list is used to determine which blocks the building should attempt to keep items in.
     * In this case, it will return all furnaces registered to the building.
     * @return a list of block positions registered to this building.
     */
    @Override
    public List<BlockPos> getRegisteredBlocks()
    {
        return new ArrayList<>(furnaces);
    }

    /**
     * Alter the items to be kept in the building inventory.
     *
     * @param consumer a tri-consumer that takes a predicate to filter items, the quantity to keep, and a boolean indicating whether to keep the item in the inventory.
     * The predicate is used to filter items to keep/discard.
     * The quantity is the number of items to keep.
     * The boolean indicates whether to keep the item in the inventory (true) or discard it (false).
     *
     * In this implementation, it will filter items by checking if they are fuel items allowed by the building, and it will keep them if the quantity is less than or equal to the stack size times the building level.
     */
    @Override
    public void alterItemsToBeKept(TriConsumer<Predicate<ItemStack>, Integer, Boolean> consumer)
    {
        consumer.accept(this::isAllowedFuel, Constants.STACKSIZE * building.getBuildingLevel(), false);
    }


    /**
     * Check to see how many furnaces are still processing
     *
     * @return the count.
     */
    public int countOfBurningFurnaces()
    {
        int count = 0;
        final Level world = building.getColony().getWorld();
        for (final BlockPos pos : getFurnaces())
        {
            if (WorldUtil.isBlockLoaded(world, pos))
            {
                if (pos == null) continue;

                final BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof PurifyingFurnaceBlockEntity furnace)
                {
                    if (furnace.isLit() && !furnace.getItem(PurifyingFurnaceBlockEntity.SLOT_INPUT).isEmpty())
                    {
                        count += 1;
                    }
                }
            }
        }
        return count;
    }
}
