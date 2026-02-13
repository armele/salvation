package com.deathfrog.salvationmod;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

// ModEnchantments.java (or a Constants class)
public final class ModEnchantments
{
    @SuppressWarnings("null")
    public static final ResourceKey<Enchantment> CORRUPTION_WARD =
        ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_ward"));

    private ModEnchantments()
    {}

    /**
     * Returns the corruption ward reduction for the given stack in the given level.
     * This reduction is a value between 0.0 and 1.0, inclusive, and is used to
     * scale the corruption damage applied to entities in the level.
     * If the stack is empty or the ward enchantment is not present, this method returns 0.0.
     * The reduction is calculated as follows: 1->0.8, 2->0.6, 3->0.4, 4->0.2, 5->0.0.
     * @param level the level to get the reduction for
     * @param stack the stack to get the reduction for
     * @return the corruption ward reduction for the given stack in the given level
     */
    @SuppressWarnings("null")
    public static float getCorruptionWardReduction(Level level, ItemStack stack) {
        if (stack.isEmpty()) return 0.0f;

        final var reg = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        final Holder<Enchantment> ward = reg.getHolderOrThrow(ModEnchantments.CORRUPTION_WARD);

        if (ward == null) return 0.0f;

        final ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int enchantmentLevel = ench.getLevel(ward);

        if (enchantmentLevel <= 0) return 0.0f;

        // Reduction of 20 percent per enchantment level.
        float reduction = Math.min(1.0f, 0.20f * enchantmentLevel);
        return reduction;

    }
}
