package com.deathfrog.salvationmod.utils;

import javax.annotation.Nonnull;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ArmorUtils
{
    /**
     * Counts the number of armor pieces worn by the given entity that
     * have the given tag.
     * 
     * @param entity the entity to check.
     * @param tag the tag to check for.
     * @return the number of armor pieces with the given tag.
     */
    public static int countTaggedArmorPieces(final LivingEntity entity, final @Nonnull TagKey<Item> tag)
    {
        int count = 0;
        for (final ItemStack stack : entity.getArmorSlots())
        {
            if (stack.is(tag))
            {
                count++;
            }
        }

        return count;
    }
}
