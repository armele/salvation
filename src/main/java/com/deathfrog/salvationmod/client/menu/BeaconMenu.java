package com.deathfrog.salvationmod.client.menu;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModMenus;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.core.blockentity.PurificationBeaconCoreBlockEntity;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BeaconMenu extends AbstractContainerMenu
{
    private static final int SLOT_Y = 20;
    private static final int PLAYER_INV_Y = 51;
    private static final int HOTBAR_Y = 109;
    private final Container beacon;

    public BeaconMenu(final int id, final Inventory playerInventory)
    {
        this(id, playerInventory, new FilteredBeaconContainer());
    }

    public BeaconMenu(final int id, final Inventory playerInventory, final @Nonnull Container container)
    {
        super(ModMenus.PURIFICATION_BEACON_MENU.get(), id);
        this.beacon = container;
        checkContainerSize(container, PurificationBeaconCoreBlockEntity.SLOT_COUNT);

        Player player = playerInventory.player;

        if (player == null)
        {
            throw new IllegalStateException("Player is null while attempting to construct a BeaconMenu.");
        }

        container.startOpen(player);

        for (int slot = 0; slot < PurificationBeaconCoreBlockEntity.SLOT_COUNT; slot++)
        {
            this.addSlot(new Slot(container, slot, 44 + slot * 18, SLOT_Y)
            {
                @Override
                public boolean mayPlace(final @Nonnull ItemStack stack)
                {
                    return stack.is(ModTags.Items.BEACON_MODULES);
                }

                @Override
                public int getMaxStackSize()
                {
                    return 1;
                }

                @Override
                public int getMaxStackSize(final @Nonnull ItemStack stack)
                {
                    return 1;
                }
            });
        }

        for (int row = 0; row < 3; row++)
        {
            for (int col = 0; col < 9; col++)
            {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, row * 18 + PLAYER_INV_Y));
            }
        }

        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++)
        {
            this.addSlot(new Slot(playerInventory, hotbarSlot, 8 + hotbarSlot * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(final @Nonnull Player player)
    {
        return this.beacon.stillValid(player);
    }

    /**
     * Quickly moves the item in the given slot to the player's inventory or the beacon's container.
     * If the item is moved to the beacon's container, the beacon's contents are updated.
     *
     * @param player The player performing the action
     * @param index The index of the slot to move the item from
     * @return The item that was moved, or an empty ItemStack if the move failed
     */
    @Override
    public @Nonnull ItemStack quickMoveStack(final @Nonnull Player player, final int index)
    {
        ItemStack copied = ItemStack.EMPTY;
        final Slot slot = this.slots.get(index);

        if (slot.hasItem())
        {
            final ItemStack slotStack = slot.getItem();
            copied = slotStack.copy();

            if (index < this.beacon.getContainerSize())
            {
                if (!this.moveItemStackTo(slotStack, this.beacon.getContainerSize(), this.slots.size(), true))
                {
                    return NullnessBridge.assumeNonnull(ItemStack.EMPTY);
                }
            }
            else if (!this.moveItemStackTo(slotStack, 0, this.beacon.getContainerSize(), false))
            {
                return NullnessBridge.assumeNonnull(ItemStack.EMPTY);
            }

            if (slotStack.isEmpty())
            {
                slot.setByPlayer(NullnessBridge.assumeNonnull(ItemStack.EMPTY));
            }
            else
            {
                slot.setChanged();
            }
        }

        return copied == null ? NullnessBridge.assumeNonnull(ItemStack.EMPTY) : copied;
    }

    @Override
    public void removed(final @Nonnull Player player)
    {
        super.removed(player);
        this.beacon.stopOpen(player);
    }

    private static final class FilteredBeaconContainer extends SimpleContainer
    {
        private FilteredBeaconContainer()
        {
            super(PurificationBeaconCoreBlockEntity.SLOT_COUNT);
        }

        @Override
        public boolean canPlaceItem(final int slot, final @Nonnull ItemStack stack)
        {
            return slot >= 0
                && slot < PurificationBeaconCoreBlockEntity.SLOT_COUNT
                && stack.is(ModTags.Items.BEACON_MODULES);
        }

        @Override
        public int getMaxStackSize()
        {
            return 1;
        }
    }
}
