package com.deathfrog.salvationmod.core.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
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
    private static final float SPEED_MULTIPLIER = 0.8F;
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

        serverTickPurifying(level, pos, state, be);

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

    /**
     * Server tick for Purifying Furnace block entity.
     * Delegates smelting logic to vanilla, then performs bonus logic if a craft occurred.
     * This method is responsible for updating the block state and notifying the level of changes.
     *
     * @param level The current level
     * @param pos The position of the block entity
     * @param state The current block state
     * @param be The block entity being ticked
     */
    @SuppressWarnings("null")
    private static void serverTickPurifying(final @Nonnull Level level, final @Nonnull BlockPos pos, final @Nonnull BlockState state, final @Nonnull PurifyingFurnaceBlockEntity be)
    {
        final boolean wasLit = be.isLit();
        boolean changed = false;

        if (be.isLit())
        {
            be.dataAccess.set(DATA_LIT_TIME, be.dataAccess.get(DATA_LIT_TIME) - 1);
        }

        final ItemStack fuelStack = be.items.get(SLOT_FUEL);
        final ItemStack inputStack = be.items.get(SLOT_INPUT);
        final boolean hasInput = !inputStack.isEmpty();
        final boolean hasFuel = !fuelStack.isEmpty();

        if (be.isLit() || hasFuel && hasInput)
        {
            final RecipeHolder<SmeltingRecipe> recipe = hasInput ? be.findSmeltingRecipe((ServerLevel) level, inputStack).orElse(null) : null;
            final int maxStackSize = be.getMaxStackSize();

            if (!be.isLit() && canBurn(level.registryAccess(), recipe, be.items, maxStackSize, be))
            {
                final int burnDuration = be.getBurnDuration(fuelStack);
                be.dataAccess.set(DATA_LIT_TIME, burnDuration);
                be.dataAccess.set(DATA_LIT_DURATION, burnDuration);

                if (be.isLit())
                {
                    changed = true;
                    if (fuelStack.hasCraftingRemainingItem())
                    {
                        be.items.set(SLOT_FUEL, fuelStack.getCraftingRemainingItem());
                    }
                    else if (hasFuel)
                    {
                        fuelStack.shrink(1);
                        if (fuelStack.isEmpty())
                        {
                            be.items.set(SLOT_FUEL, fuelStack.getCraftingRemainingItem());
                        }
                    }
                }
            }

            if (be.isLit() && canBurn(level.registryAccess(), recipe, be.items, maxStackSize, be))
            {
                final int adjustedCookTime = be.getAdjustedCookTime(recipe);
                be.dataAccess.set(DATA_COOKING_TOTAL_TIME, adjustedCookTime);

                final int nextProgress = be.dataAccess.get(DATA_COOKING_PROGRESS) + 1;
                be.dataAccess.set(DATA_COOKING_PROGRESS, nextProgress);
                if (nextProgress >= adjustedCookTime)
                {
                    be.dataAccess.set(DATA_COOKING_PROGRESS, 0);
                    be.dataAccess.set(DATA_COOKING_TOTAL_TIME, adjustedCookTime);
                    if (burn(level.registryAccess(), recipe, be.items, maxStackSize, be))
                    {
                        be.setRecipeUsed(recipe);
                    }

                    changed = true;
                }
            }
            else
            {
                be.dataAccess.set(DATA_COOKING_PROGRESS, 0);
            }
        }
        else if (!be.isLit() && be.dataAccess.get(DATA_COOKING_PROGRESS) > 0)
        {
            be.dataAccess.set(DATA_COOKING_PROGRESS,
                Mth.clamp(be.dataAccess.get(DATA_COOKING_PROGRESS) - BURN_COOL_SPEED, 0, be.dataAccess.get(DATA_COOKING_TOTAL_TIME)));
        }

        if (wasLit != be.isLit())
        {
            changed = true;
            level.setBlock(pos, state.setValue(NullnessBridge.assumeNonnull(AbstractFurnaceBlock.LIT), be.isLit()), 3);
        }

        if (changed)
        {
            setChanged(level, pos, state);
        }
    }

    /**
     * Get the adjusted cook time for a recipe, applying a speed multiplier to the base cook time.
     * If the recipe is null, a default base cook time of 200 is used.
     * The result is clamped to a minimum of 1.
     *
     * @param recipe The recipe to get the adjusted cook time for
     * @return The adjusted cook time
     */
    private int getAdjustedCookTime(final RecipeHolder<? extends AbstractCookingRecipe> recipe)
    {
        final int baseCookTime = recipe != null ? recipe.value().getCookingTime() : 200;
        return Math.max(1, Math.round(baseCookTime * SPEED_MULTIPLIER));
    }

    /**
     * Sets the item in the given slot to the given stack.
     * If the slot is the input slot (SLOT_INPUT), updates the DATA_COOKING_TOTAL_TIME
     * data accessor based on the recipe for the given input item.
     *
     * @param slot The slot to set the item in
     * @param stack The item to set in the slot
     */
    @Override
    public void setItem(final int slot, final @Nonnull ItemStack stack)
    {
        super.setItem(slot, stack);

        if (slot == SLOT_INPUT)
        {
            final RecipeHolder<SmeltingRecipe> recipe =
                this.level instanceof ServerLevel serverLevel ? findSmeltingRecipe(serverLevel, this.getItem(SLOT_INPUT)).orElse(null) : null;
            this.dataAccess.set(DATA_COOKING_TOTAL_TIME, getAdjustedCookTime(recipe));
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

    protected boolean isEmpty(ItemStack stack)
    {
        return stack == null || stack.isEmpty();
    }

    public boolean hasSmeltableInFurnaceAndNoFuel()
    {
        return !isEmpty(this.getItem(PurifyingFurnaceBlockEntity.SLOT_INPUT)) && isEmpty(this.getItem(PurifyingFurnaceBlockEntity.SLOT_FUEL));
    }

    public boolean hasNeitherFuelNorSmeltAble()
    {
        return isEmpty(this.getItem(PurifyingFurnaceBlockEntity.SLOT_INPUT)) && isEmpty(this.getItem(PurifyingFurnaceBlockEntity.SLOT_FUEL));
    }

    public boolean hasFuelInFurnaceAndNoSmeltable()
    {
        return isEmpty(this.getItem(PurifyingFurnaceBlockEntity.SLOT_INPUT)) && !isEmpty(this.getItem(PurifyingFurnaceBlockEntity.SLOT_FUEL));
    }

    /**
     * Keep furnace "lit" state tied to burn time, not blockstate.
     * Abstract furnace ticking relies on this semantic to advance cooking.
     */
    public boolean isLit()
    {
        return this.dataAccess.get(0) > 0;
    }

    /**
     * Check if the given recipe can be burned with the given inventory and furnace.
     * A recipe can be burned if the input slot is not empty and the recipe is not null.
     * A recipe can also be burned if the result slot is empty, or if the result slot contains the same item as the assembled recipe and the total count of the items does not exceed the max stack size of the item.
     * @param registryAccess the registry access
     * @param recipe the recipe to check
     * @param inventory the inventory to check
     * @param maxStackSize the max stack size of the item
     * @param furnace the furnace to check
     * @return true if the recipe can be burned, false otherwise
     */
    @SuppressWarnings("null")
    private static boolean canBurn(final RegistryAccess registryAccess,
                                   final RecipeHolder<? extends AbstractCookingRecipe> recipe,
                                   final NonNullList<ItemStack> inventory,
                                   final int maxStackSize,
                                   final PurifyingFurnaceBlockEntity furnace)
    {
        if (inventory.get(SLOT_INPUT).isEmpty() || recipe == null)
        {
            return false;
        }

        final ItemStack result = recipe.value().assemble(new SingleRecipeInput(furnace.getItem(SLOT_INPUT)), registryAccess);
        if (result.isEmpty())
        {
            return false;
        }

        final ItemStack currentResult = inventory.get(SLOT_RESULT);
        if (currentResult.isEmpty())
        {
            return true;
        }

        if (!ItemStack.isSameItemSameComponents(currentResult, result))
        {
            return false;
        }

        return currentResult.getCount() + result.getCount() <= maxStackSize
            && currentResult.getCount() + result.getCount() <= currentResult.getMaxStackSize();
    }

    /**
     * Burn the given recipe with the given inventory and furnace.
     * The burn process checks if the given recipe can be burned with the given inventory and furnace,
     * and if so, it will consume the input item and add the result item to the result slot.
     * If the input item is a wet sponge and the fuel slot is not empty and contains a bucket, it will replace the fuel slot with a water bucket.
     * @param registryAccess the registry access
     * @param recipe the recipe to burn
     * @param inventory the inventory to burn with
     * @param maxStackSize the max stack size of the item
     * @param furnace the furnace to burn with
     * @return true if the recipe was burned, false otherwise
     */
    @SuppressWarnings("null")
    private static boolean burn(final RegistryAccess registryAccess,
                                final RecipeHolder<? extends AbstractCookingRecipe> recipe,
                                final NonNullList<ItemStack> inventory,
                                final int maxStackSize,
                                final PurifyingFurnaceBlockEntity furnace)
    {
        if (!canBurn(registryAccess, recipe, inventory, maxStackSize, furnace))
        {
            return false;
        }

        final ItemStack input = inventory.get(SLOT_INPUT);
        final ItemStack result = recipe.value().assemble(new SingleRecipeInput(furnace.getItem(SLOT_INPUT)), registryAccess);
        final ItemStack currentResult = inventory.get(SLOT_RESULT);
        if (currentResult.isEmpty())
        {
            inventory.set(SLOT_RESULT, result.copy());
        }
        else if (ItemStack.isSameItemSameComponents(currentResult, result))
        {
            currentResult.grow(result.getCount());
        }

        if (input.is(Blocks.WET_SPONGE.asItem()) && !inventory.get(SLOT_FUEL).isEmpty() && inventory.get(SLOT_FUEL).is(Items.BUCKET))
        {
            inventory.set(SLOT_FUEL, new ItemStack(Items.WATER_BUCKET));
        }

        input.shrink(1);
        return true;
    }

}
