package com.deathfrog.salvationmod.core.colony.buildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.api.colony.buildings.ModBuildings;
import com.deathfrog.salvationmod.api.colony.buildings.jobs.ModJobs;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.compatibility.ICompatibilityManager;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.CraftingUtils;
import com.minecolonies.api.util.OptionalPredicate;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public class BuildingEnvironmentalLab extends AbstractBuilding
{
    public final static List<ItemStorage> allowedItems = new ArrayList<>();
    
    public BuildingEnvironmentalLab(@NotNull IColony colony, BlockPos pos)
    {
        super(colony, pos);
    }

    @Override
    public String getSchematicName()
    {
        return ModBuildings.ENVIRONMENTAL_LAB;
    }

    /**
     * Returns a list of all items that are allowed to be used in the building's crafting module.
     * The list is populated lazily, so the first call to this method will take longer than subsequent calls.
     *
     * @return A list of all allowed items.
     */
    public static List<ItemStorage> getAllowedItems()
    {
        if (allowedItems.size() == 0)
        {
            final ICompatibilityManager compatibility = IColonyManager.getInstance().getCompatibilityManager();
            for (final ItemStack stack : compatibility.getListOfAllItems())
            {
                if (stack.is(ModTags.Items.CORRUPTED_ITEMS))
                {
                    final ItemStack output = IColonyManager.getInstance().getCompatibilityManager().getFurnaceRecipes().getSmeltingResult(stack);
                    if (!output.isEmpty())
                    {
                        allowedItems.add(new ItemStorage(stack));
                    }
                }
            }
        }


        return allowedItems;
    }

    public static class CraftingModule extends AbstractCraftingBuildingModule.Crafting
    {
        /**
         * Create a new module.
         *
         * @param jobEntry the entry of the job.
         */
        public CraftingModule(final JobEntry jobEntry)
        {
            super(jobEntry);
        }

        @NotNull
        @Override
        public OptionalPredicate<ItemStack> getIngredientValidator()
        {
            return CraftingUtils.getIngredientValidatorBasedOnTags(ModJobs.LABTECH_TAG)
                    .combine(super.getIngredientValidator());
        }

        @Override
        public boolean isRecipeCompatible(@NotNull final IGenericRecipe recipe)
        {
            boolean iscompat = super.isRecipeCompatible(recipe);

            // SalvationMod.LOGGER.info("isRecipeCompatible - checking {}: {}", recipe, iscompat);

            if (!iscompat) return false;

            final Optional<Boolean> isRecipeAllowed = CraftingUtils.isRecipeCompatibleBasedOnTags(recipe, ModJobs.LABTECH_TAG);
            if (isRecipeAllowed.isPresent()) return isRecipeAllowed.get();

            return false;
        }
    }
    
    public static class SmeltingModule extends AbstractCraftingBuildingModule.Smelting
    {
        /**
         * Create a new module.
         *
         * @param jobEntry the entry of the job.
         */
        public SmeltingModule(final JobEntry jobEntry)
        {
            super(jobEntry);
        }

        @NotNull
        @Override
        public OptionalPredicate<ItemStack> getIngredientValidator()
        {
            return CraftingUtils.getIngredientValidatorBasedOnTags(ModJobs.LABTECH_TAG)
                    .combine(super.getIngredientValidator());
        }

        @Override
        public boolean isRecipeCompatible(@NotNull final IGenericRecipe recipe)
        {
            if (!super.isRecipeCompatible(recipe))
            {
                return false;
            }
            return CraftingUtils.isRecipeCompatibleBasedOnTags(recipe, ModJobs.LABTECH_TAG).orElse(false);
        }
    }
}
