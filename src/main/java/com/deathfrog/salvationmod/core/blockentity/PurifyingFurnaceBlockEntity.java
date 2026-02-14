package com.deathfrog.salvationmod.core.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModItems;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntities;
import com.deathfrog.salvationmod.client.menu.PurifyingFurnaceMenu;
import com.deathfrog.salvationmod.core.items.CorruptedItem;

import java.util.Optional;

public class PurifyingFurnaceBlockEntity extends AbstractFurnaceBlockEntity
{
    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_FUEL   = 1;
    public static final int SLOT_RESULT = 2; // vanilla result slot
    public static final int SLOT_BONUS  = 3; // bonus slot
    public static final int SLOT_COUNT  = 4;

    public PurifyingFurnaceBlockEntity(final BlockPos pos, final BlockState state)
    {
        super(SalvationTileEntities.PURIFYING_FURNACE.get(), pos, state, RecipeType.SMELTING);

        // Expand the vanilla furnace inventory to 4 slots. Vanilla logic will still only write SLOT_RESULT (2).
        this.items = net.minecraft.core.NonNullList.withSize(SLOT_COUNT, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
    }

    /**
     * Main server tick: delegate normal smelting to vanilla, then do bonus logic if a craft occurred.
     */
    public static void serverTick(final @Nonnull ServerLevel level, final @Nonnull BlockPos pos, final @Nonnull BlockState state, final @Nonnull PurifyingFurnaceBlockEntity be)
    {
        final ItemStack inputBefore = be.getItem(SLOT_INPUT).copy();
        final ItemStack fuelBefore  = be.getItem(SLOT_FUEL).copy();

        AbstractFurnaceBlockEntity.serverTick(level, pos, state, be);

        final ItemStack inputAfter = be.getItem(SLOT_INPUT);
        final boolean crafted = !inputBefore.isEmpty()
            && (inputAfter.isEmpty() || inputAfter.getCount() < inputBefore.getCount());

        if (!crafted) return;

        final Optional<RecipeHolder<SmeltingRecipe>> recipeOpt = be.findSmeltingRecipe(level, inputBefore);
        if (recipeOpt.isEmpty()) return;

        final ItemStack bonus = be.getBonusOutput(level, recipeOpt.get(), inputBefore, fuelBefore);
        if (!bonus.isEmpty())
        {
            be.tryInsertBonus(bonus);
            be.setChanged();
        }
    }

    private Optional<RecipeHolder<SmeltingRecipe>> findSmeltingRecipe(final ServerLevel level, final ItemStack input)
    {
        if (input.isEmpty()) return Optional.empty();
        return level.getRecipeManager().getRecipeFor(NullnessBridge.assumeNonnull(RecipeType.SMELTING), new SingleRecipeInput(input), level);
    }

    protected ItemStack getBonusOutput(final ServerLevel level,
                                    final RecipeHolder<SmeltingRecipe> recipe,
                                    final ItemStack inputConsumed,
                                    final ItemStack fuelSnapshot)
    {
        if (inputConsumed.is(ModTags.Items.CORRUPTED_ITEMS))
        {
            CorruptedItem byproduct = ModItems.ESSENCE_OF_CORRUPTION.get();
            return new ItemStack(NullnessBridge.assumeNonnull(byproduct));
        }
        return ItemStack.EMPTY;
    }

    /**
     * Attempt to insert into SLOT_BONUS. If it won't fit, we simply skip (no duping, no drops).
     * You can change this behavior (drop to world, spill into normal output, etc.).
     */
    protected void tryInsertBonus(final ItemStack stack)
    {
        if (stack.isEmpty()) return;

        final ItemStack cur = this.getItem(SLOT_BONUS);

        if (cur.isEmpty())
        {
            final int move = Math.min(stack.getCount(), stack.getMaxStackSize());
            final ItemStack placed = stack.copy();
            placed.setCount(move);
            this.setItem(SLOT_BONUS, placed);
            stack.shrink(move);
            return;
        }

        if (!ItemStack.isSameItemSameComponents(cur, stack)) return;

        final int space = Math.min(cur.getMaxStackSize(), this.getMaxStackSize()) - cur.getCount();
        if (space <= 0) return;

        final int move = Math.min(space, stack.getCount());
        cur.grow(move);
        stack.shrink(move);
        this.setItem(SLOT_BONUS, cur);
    }

    /**
     * Allow hoppers/etc to see both outputs on the DOWN face.
     * We keep vanilla semantics: UP=input, SIDE=fuel, DOWN=outputs.
     */
    @Override
    public int[] getSlotsForFace(final @Nonnull Direction side)
    {
        if (side == Direction.UP)
            return new int[] { SLOT_INPUT };

        if (side == Direction.DOWN)
            return new int[] { SLOT_RESULT, SLOT_BONUS };

        return new int[] { SLOT_FUEL };
    }

    @Override
    public boolean canPlaceItem(final int slot, final @Nonnull ItemStack stack)
    {
        if (slot == SLOT_RESULT || slot == SLOT_BONUS) return false;
        return super.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(final int slot, final @Nonnull ItemStack stack, final @Nonnull Direction dir)
    {
        // Allow extraction of both outputs
        if (dir == Direction.DOWN && (slot == SLOT_RESULT || slot == SLOT_BONUS))
            return true;

        return super.canTakeItemThroughFace(slot, stack, dir);
    }

    @Override
    protected Component getDefaultName()
    {
        return Component.translatable("container.salvation.purifying_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(final int id, final @Nonnull Inventory playerInv)
    {
        ContainerData localData = this.dataAccess;

        if (localData == null) throw new IllegalStateException("Data access is null while attempting to construct a PurifyingFurnaceMenu.");
        
        return new PurifyingFurnaceMenu(id, playerInv, this, localData);
    }

    @Override
    public void loadAdditional(final @Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        // super.load handles items, but because we expanded to 4 slots, make sure it stays 4
        if (this.items.size() != SLOT_COUNT)
        {
            final var newList = net.minecraft.core.NonNullList.withSize(SLOT_COUNT, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
            for (int i = 0; i < Math.min(this.items.size(), SLOT_COUNT); i++)
                newList.set(i, this.items.get(i));
            this.items = newList;
        }
    }

    @Override
    protected void saveAdditional(final @Nonnull CompoundTag tag, final @Nonnull HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        // super.saveAdditional already writes inventory; no extra tag needed unless you add extra fields
    }

}