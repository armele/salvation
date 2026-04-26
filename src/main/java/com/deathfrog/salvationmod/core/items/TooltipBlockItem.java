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

public class TooltipBlockItem extends BlockItem
{
    private final String tooltipKey;

    public TooltipBlockItem(@Nonnull final Block block, @Nonnull final Item.Properties properties, @Nonnull final String tooltipKey)
    {
        super(block, properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(@Nonnull final ItemStack stack,
        @Nonnull final Item.TooltipContext context,
        @Nonnull final List<Component> tooltipComponents,
        @Nonnull final TooltipFlag tooltipFlag)
    {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable(tooltipKey + "").withStyle(ChatFormatting.GRAY));
    }
}
