package com.deathfrog.salvationmod;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.client.menu.PurifyingFurnaceMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.deathfrog.salvationmod.core.blockentity.PurifyingFurnaceBlockEntity;

public final class ModMenus
{
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(NullnessBridge.assumeNonnull(Registries.MENU), SalvationMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<PurifyingFurnaceMenu>> PURIFYING_FURNACE_MENU =
        MENUS.register("purifying_furnace",
            () -> IMenuTypeExtension.create((windowId, playerInv, extraData) ->
            {
                final BlockPos pos = extraData.readBlockPos();

                if (pos == null) throw new IllegalStateException("PurifyingFurnaceBlockEntity missing pos");

                final Level level = playerInv.player.level();
                final BlockEntity be = level.getBlockEntity(pos);

                if (!(be instanceof PurifyingFurnaceBlockEntity furnace))
                    throw new IllegalStateException("PurifyingFurnaceBlockEntity missing at " + pos);

                // MenuProvider path: let the BE construct the menu (it has access to dataAccess)
                return (PurifyingFurnaceMenu) furnace.createMenu(windowId, playerInv, NullnessBridge.assumeNonnull(playerInv.player));
            }));

    private ModMenus() {}
}