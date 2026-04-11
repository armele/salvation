package com.deathfrog.salvationmod.core.items;

import com.deathfrog.mctradepost.api.util.NullnessBridge;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class CorruptedItem extends Item
{
    // Anticipating custom corrupted item logic that will take place in this stub.

    private final String tooltipKey;

    public CorruptedItem(Properties properties)
    {
        this(properties, null);
    }

    public CorruptedItem(Properties properties, String tooltipKey)
    {
        super(NullnessBridge.assumeNonnull(properties));
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull  Item.TooltipContext context, @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag tooltipFlag)
    {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        if (tooltipKey != null)
        {
            tooltipComponents.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
    
}
