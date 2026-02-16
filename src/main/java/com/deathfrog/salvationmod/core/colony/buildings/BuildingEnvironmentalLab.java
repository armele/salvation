package com.deathfrog.salvationmod.core.colony.buildings;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.api.colony.buildings.ModBuildings;
import com.deathfrog.salvationmod.api.colony.buildings.jobs.ModJobs;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.util.CraftingUtils;
import com.minecolonies.api.util.OptionalPredicate;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public class BuildingEnvironmentalLab extends AbstractBuilding
{

    public BuildingEnvironmentalLab(@NotNull IColony colony, BlockPos pos)
    {
        super(colony, pos);
    }

    @Override
    public String getSchematicName()
    {
        return ModBuildings.ENVIRONMENTAL_LAB;
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
            return CraftingUtils.getIngredientValidatorBasedOnTags(ModJobs.LABTECH_ID.getPath())
                    .combine(super.getIngredientValidator());
        }

        @Override
        public boolean isRecipeCompatible(@NotNull final IGenericRecipe recipe)
        {
            boolean iscompat = super.isRecipeCompatible(recipe);

            SalvationMod.LOGGER.debug("isRecipeCompatible - checking {}: {}", recipe, iscompat);

            if (!iscompat) return false;

            final Optional<Boolean> isRecipeAllowed = CraftingUtils.isRecipeCompatibleBasedOnTags(recipe, ModJobs.LABTECH_TAG);
            if (isRecipeAllowed.isPresent()) return isRecipeAllowed.get();

            return false;
        }
    }
    
}
