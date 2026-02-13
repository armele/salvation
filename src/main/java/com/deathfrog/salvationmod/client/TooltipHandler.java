package com.deathfrog.salvationmod.client;

import java.util.List;
import com.deathfrog.salvationmod.ModEnchantments;
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
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        if (!hasCorruptionWard(level, stack)) return;

        List<Component> tooltip = event.getToolTip();

        tooltip.add(Component.empty()); // spacer line

        tooltip.add(Component.translatable("tooltip.salvation.corruption_ward.flavor")
            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    /**
     * Returns true if the given item stack has a corruption ward enchantment, either
     * applied to a tool or stored in a book.
     * @param level the current level
     * @param stack the item stack to check
     * @return true if the item stack has a corruption ward enchantment, false otherwise
     */
    @SuppressWarnings("null")
    private static boolean hasCorruptionWard(Level level, ItemStack stack)
    {
        Registry<Enchantment> reg = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> ward = reg.getHolderOrThrow(ModEnchantments.CORRUPTION_WARD);

        // 1) Check applied enchantments (tools)
        ItemEnchantments applied = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        if (applied.getLevel(ward) > 0) return true;

        // 2) Check stored enchantments (books)
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);

        return stored.getLevel(ward) > 0;
    }
}
