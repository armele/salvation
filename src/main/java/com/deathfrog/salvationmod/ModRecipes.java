package com.deathfrog.salvationmod;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.core.recipe.PurifyingRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCookingSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes
{
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(NullnessBridge.assumeNonnull(Registries.RECIPE_SERIALIZER), SalvationMod.MODID);

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
        DeferredRegister.create(NullnessBridge.assumeNonnull(Registries.RECIPE_TYPE), SalvationMod.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<PurifyingRecipe>> PURIFYING_TYPE =
        RECIPE_TYPES.register("purifying", () -> new RecipeType<PurifyingRecipe>()
        {
            @Override
            public String toString()
            {
                return SalvationMod.MODID + ":purifying";
            }
        });

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PurifyingRecipe>> PURIFYING_SERIALIZER =
        RECIPE_SERIALIZERS.register("purifying", () -> new SimpleCookingSerializer<>(PurifyingRecipe::new, 200));

    private ModRecipes() {}
}
