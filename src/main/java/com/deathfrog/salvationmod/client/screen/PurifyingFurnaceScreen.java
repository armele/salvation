package com.deathfrog.salvationmod.client.screen;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.client.menu.PurifyingFurnaceMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class PurifyingFurnaceScreen extends AbstractContainerScreen<PurifyingFurnaceMenu>
{
    @SuppressWarnings("null")
    private static final @Nonnull ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/gui/container/purifying_furnace.png");

    public PurifyingFurnaceScreen(final PurifyingFurnaceMenu menu, final Inventory inventory, final Component title)
    {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        final int left = this.leftPos;
        final int top = this.topPos;

        // Base furnace texture
        graphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight);

        // Flame (same coords/style as vanilla furnace)
        if (this.menu.isLit())
        {
            final int flame = this.menu.getBurnLeftScaled(13);
            graphics.blit(TEXTURE, left + 56, top + 36 + 12 - flame, 176, 12 - flame, 14, flame + 1);
        }

        // Arrow progress
        final int progress = this.menu.getCookProgressScaled(24);
        graphics.blit(TEXTURE, left + 79, top + 34, 176, 14, progress + 1, 16);
    }
}
