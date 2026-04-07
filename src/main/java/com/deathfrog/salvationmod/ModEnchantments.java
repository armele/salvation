package com.deathfrog.salvationmod;

import com.deathfrog.mctradepost.api.util.NullnessBridge;

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

    @SuppressWarnings("null")
    public static final ResourceKey<Enchantment> CORRUPTION_DISRUPTION =
        ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_disruption"));

    @SuppressWarnings("null")
    public static final ResourceKey<Enchantment> CORRUPTION_SIGHT =
        ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption_sight"));

    private static final float CORRUPTION_WARD_REDUCTION_PER_LEVEL = 0.2f;
    private static final float CORRUPTION_DISRUPTION_DAMAGE_BONUS_PER_LEVEL = 0.2f;

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
    public static float getCorruptionWardReduction(Level level, ItemStack stack) 
    {
        final int enchantmentLevel = getAppliedEnchantmentLevel(level, stack, CORRUPTION_WARD);

        if (enchantmentLevel <= 0) return 0.0f;

        float enchantmentStrenghtPerLevel = CORRUPTION_WARD_REDUCTION_PER_LEVEL;

        // Boost of 10% strength on purified items
        if (stack.is(ModTags.Items.PURIFIED_ITEMS))
        {
            enchantmentStrenghtPerLevel *= 1.1f;
        }

        // Reduction of 20 percent per enchantment level.
        float reduction = Math.min(1.0f, enchantmentStrenghtPerLevel * enchantmentLevel);
        return reduction;

    }

    public static float getCorruptionDisruptionDamageMultiplier(Level level, ItemStack stack)
    {
        final int enchantmentLevel = getAppliedEnchantmentLevel(level, stack, CORRUPTION_DISRUPTION);
        if (enchantmentLevel <= 0) return 1.0f;

        return 1.0f + (CORRUPTION_DISRUPTION_DAMAGE_BONUS_PER_LEVEL * enchantmentLevel);
    }

    public static boolean hasCorruptionSight(Level level, ItemStack stack)
    {
        return getAppliedEnchantmentLevel(level, stack, CORRUPTION_SIGHT) > 0;
    }

    @SuppressWarnings("null")
    public static int getAppliedEnchantmentLevel(Level level, ItemStack stack, ResourceKey<Enchantment> enchantmentKey)
    {
        if (stack.isEmpty()) return 0;

        final var reg = level.registryAccess().registryOrThrow(NullnessBridge.assumeNonnull(Registries.ENCHANTMENT));
        final Holder<Enchantment> enchantment = reg.getHolderOrThrow(NullnessBridge.assumeNonnull(enchantmentKey));

        final ItemEnchantments ench = stack.getOrDefault(NullnessBridge.assumeNonnull(DataComponents.ENCHANTMENTS), NullnessBridge.assumeNonnull(ItemEnchantments.EMPTY));
        return ench.getLevel(enchantment);
    }
}
