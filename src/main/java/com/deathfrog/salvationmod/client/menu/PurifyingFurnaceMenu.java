package com.deathfrog.salvationmod.client.menu;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModMenus;
import com.deathfrog.salvationmod.core.blockentity.PurifyingFurnaceBlockEntity;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public class PurifyingFurnaceMenu extends AbstractContainerMenu
{
    private final Container container;
    private final ContainerData data;
    private final @Nonnull Level level;

    public PurifyingFurnaceMenu(final int id, final Inventory playerInv, final @Nonnull Container container, final @Nonnull ContainerData data)
    {
        super(ModMenus.PURIFYING_FURNACE_MENU.get(), id);
        this.container = container;
        this.data = data;

        Level pLevel = playerInv.player.level();

        if (pLevel == null) throw new IllegalStateException("Level is null while attempting to construct a PurifyingFurnaceMenu.");

        this.level = pLevel;

        // Container slots (positions are placeholders; you’ll lay them out in the client screen later)
        // Input
        this.addSlot(new Slot(container, PurifyingFurnaceBlockEntity.SLOT_INPUT, 56, 17));
        // Fuel
        this.addSlot(new Slot(container, PurifyingFurnaceBlockEntity.SLOT_FUEL, 56, 53));
        // Normal output (use FurnaceResultSlot so XP/recipe stuff behaves like vanilla)
        this.addSlot(new FurnaceResultSlot(NullnessBridge.assumeNonnull(playerInv.player), container, PurifyingFurnaceBlockEntity.SLOT_RESULT, 116, 35));
        // Bonus output (plain Slot; you can make it a result slot too if you want XP behavior)
        this.addSlot(new Slot(container, PurifyingFurnaceBlockEntity.SLOT_BONUS, 144, 35)
        {
            @Override
            public boolean mayPlace(final @Nonnull ItemStack stack)
            {
                return false;
            }
        });

        // Player inventory
        for (int row = 0; row < 3; row++)
        {
            for (int col = 0; col < 9; col++)
            {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++)
        {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
    }

    @Override
    public boolean stillValid(final @Nonnull Player player)
    {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(final @Nonnull Player player, final int index)
    {
        final Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        final ItemStack stackInSlot = slot.getItem();
        final ItemStack copy = stackInSlot.copy();

        final int containerSlots = 4;
        final int playerStart = containerSlots;
        final int playerEnd = this.slots.size();

        if (index < containerSlots)
        {
            // From block -> player
            if (!this.moveItemStackTo(stackInSlot, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        }
        else
        {
            // From player -> block
            if (isFuel(stackInSlot))
            {
                if (!this.moveItemStackTo(stackInSlot,
                    PurifyingFurnaceBlockEntity.SLOT_FUEL,
                    PurifyingFurnaceBlockEntity.SLOT_FUEL + 1,
                    false)) return ItemStack.EMPTY;
            }
            else if (isSmeltable(stackInSlot))
            {
                if (!this.moveItemStackTo(stackInSlot,
                    PurifyingFurnaceBlockEntity.SLOT_INPUT,
                    PurifyingFurnaceBlockEntity.SLOT_INPUT + 1,
                    false)) return ItemStack.EMPTY;
            }
            else
            {
                // Otherwise, shuffle within player inventory
                if (!this.moveItemStackTo(stackInSlot, playerStart, playerEnd, false)) return ItemStack.EMPTY;
            }
        }

        if (stackInSlot.isEmpty()) slot.set(NullnessBridge.assumeNonnull(ItemStack.EMPTY));
        else slot.setChanged();

        return copy;
    }

    private static boolean isFuel(final @Nonnull ItemStack stack)
    {
        // Vanilla furnace fuel logic is exposed on AbstractFurnaceBlockEntity; use it in your codebase.
        return net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.isFuel(stack);
    }

    private boolean isSmeltable(final ItemStack stack)
    {
        if (stack.isEmpty()) return false;

        return level.getRecipeManager()
            .getRecipeFor(NullnessBridge.assumeNonnull(RecipeType.SMELTING), new SingleRecipeInput(stack), level)
            .isPresent();
    }

    public boolean isLit()
    {
        return this.data.get(0) > 0; // index meanings depend on AbstractFurnaceBlockEntity's dataAccess
    }

    public int getCookProgressScaled(int pixels)
    {
        int cook = this.data.get(2);
        int cookTotal = this.data.get(3);
        return cookTotal != 0 && cook != 0 ? cook * pixels / cookTotal : 0;
    }

    public int getBurnLeftScaled(int pixels)
    {
        int litTime = this.data.get(0);
        int litDuration = this.data.get(1);
        if (litDuration == 0) litDuration = 200;
        return litTime * pixels / litDuration;
    }
}
