package com.deathfrog.salvationmod.core.items;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class PurifyingFurnaceItem extends BlockItem
{
    private static final String TOOLTIP_KEY = "tooltip.salvation.purifying_furnace";

    public PurifyingFurnaceItem(final Block block, final Item.Properties properties)
    {
        super(block, properties);
    }

    @Override
    public void appendHoverText(@Nonnull final ItemStack stack,
                                @Nonnull final Item.TooltipContext context,
                                @Nonnull final List<Component> tooltipComponents,
                                @Nonnull final TooltipFlag tooltipFlag)
    {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
