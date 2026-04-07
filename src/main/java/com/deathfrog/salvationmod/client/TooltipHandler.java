package com.deathfrog.salvationmod.client;

import java.util.List;
import com.deathfrog.salvationmod.ModEnchantments;
import com.deathfrog.salvationmod.ModTags;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = "salvation", value = Dist.CLIENT)
public final class TooltipHandler
{
    private TooltipHandler()
    {}

    /**
     * Called when an item's tooltip is being rendered. If the item stack has a corruption ward, this method adds a spacer line and a
     * flavor text to the item's tooltip.
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event)
    {
        final ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        final Level level = Minecraft.getInstance().level;
        if (level == null) return;

        final List<Component> tooltip = event.getToolTip();

        if (stack.is(ModTags.Items.PURIFIED_ITEMS))
        { 
            tooltip.add(Component.translatable("tooltip.salvation.purified_items.flavor").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        boolean hasCustomTooltip = false;
        if (hasEnchantment(level, stack, ModEnchantments.CORRUPTION_WARD))
        {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.salvation.corruption_ward.flavor")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            hasCustomTooltip = true;
        }

        if (hasEnchantment(level, stack, ModEnchantments.CORRUPTION_DISRUPTION))
        {
            if (!hasCustomTooltip)
            {
                tooltip.add(Component.empty());
            }

            tooltip.add(Component.translatable("tooltip.salvation.corruption_disruption.flavor")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            hasCustomTooltip = true;
        }

        if (hasEnchantment(level, stack, ModEnchantments.CORRUPTION_SIGHT))
        {
            if (!hasCustomTooltip)
            {
                tooltip.add(Component.empty());
            }

            tooltip.add(Component.translatable("tooltip.salvation.corruption_sight.flavor")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
        }
    }

    /**
     * Returns true if the given item stack has a corruption ward enchantment, either
     * applied to a tool or stored in a book.
     * @param level the current level
     * @param stack the item stack to check
     * @return true if the item stack has a corruption ward enchantment, false otherwise
     */
    @SuppressWarnings("null")
    private static boolean hasEnchantment(Level level, ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> enchantmentKey)
    {
        final Registry<Enchantment> reg = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        final Holder<Enchantment> enchantment = reg.getHolderOrThrow(enchantmentKey);

        // 1) Check applied enchantments (tools)
        final ItemEnchantments applied = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        if (applied.getLevel(enchantment) > 0) return true;

        // 2) Check stored enchantments (books)
        final ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);

        return stored.getLevel(enchantment) > 0;
    }
}
