package com.deathfrog.salvationmod.core.recipe;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.ModRecipes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

public class PurifyingRecipe extends AbstractCookingRecipe
{
    public PurifyingRecipe(final String group,
                           final CookingBookCategory category,
                           final Ingredient ingredient,
                           final ItemStack result,
                           final float experience,
                           final int cookingTime)
    {
        super(ModRecipes.PURIFYING_TYPE.get(), group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    public ItemStack getToastSymbol()
    {
        return new ItemStack(NullnessBridge.assumeNonnull(ModBlocks.PURIFYING_FURNACE.get()));
    }

    @Override
    public RecipeSerializer<?> getSerializer()
    {
        return ModRecipes.PURIFYING_SERIALIZER.get();
    }
}
