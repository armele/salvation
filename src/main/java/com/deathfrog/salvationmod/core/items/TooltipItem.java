package com.deathfrog.salvationmod.core.items;

import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class TooltipItem extends Item
{
    private final String tooltipKey;

    public TooltipItem(final Properties properties, final String tooltipKey)
    {
        super(NullnessBridge.assumeNonnull(properties));
        this.tooltipKey = tooltipKey;
    }

    @SuppressWarnings("null")
    @Override
    public void appendHoverText(@Nonnull final ItemStack stack,
                                @Nonnull final Item.TooltipContext context,
                                @Nonnull final List<Component> tooltipComponents,
                                @Nonnull final TooltipFlag tooltipFlag)
    {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
