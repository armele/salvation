package com.deathfrog.salvationmod.client.screen;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.client.menu.BeaconMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class BeaconScreen extends AbstractContainerScreen<BeaconMenu>
{
    @SuppressWarnings("null")
    private static final @Nonnull ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/gui/container/hopper.png");

    public BeaconScreen(final BeaconMenu menu, final Inventory playerInventory, final Component title)
    {
        super(menu, playerInventory, title);
        this.imageHeight = 133;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics guiGraphics, final float partialTick, final int mouseX, final int mouseY)
    {
        final int left = (this.width - this.imageWidth) / 2;
        final int top = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight);
    }
}
