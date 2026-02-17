package com.deathfrog.salvationmod.core.items;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.core.engine.EntityConversion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CorruptionExtractorItem extends Item
{
    public CorruptionExtractorItem(Properties properties)
    {
        super(properties);
    }

    /**
     * Determines if the item should leave a remaining item in the crafting grid after crafting is completed. This is typically used
     * for items that are not fully consumed during crafting.
     *
     * @return true if the item leaves a remaining item, false otherwise
     */
    @Override
    public boolean hasCraftingRemainingItem()
    {
        return true;
    }

    /**
     * This is called when the player is done crafting with the item contained in the given stack. It is used to determine what item is
     * left in the crafting grid slot after the player is done crafting with the item.
     * <p>In this case, we damage the item until it is broken, then return the remainder (which is either an empty stack if the item
     * was broken, or the damaged stack if it wasn't). This is the behavior of the vanilla crafting grid, and is necessary for the
     * immersion blender to work as intended.
     * <p>Note that the player is null in this case, since this is being called from the crafting grid rather than from a player's
     * inventory.
     */
    @Override
    public ItemStack getCraftingRemainingItem(@Nonnull ItemStack stack) 
    {
        ItemStack remainder = stack.copy();
        
        // Directly set the damage instead of using hurtAndBreak
        int currentDamage = remainder.getDamageValue(); 
        int maxDamage = remainder.getMaxDamage(); 
        
        if (currentDamage < maxDamage) 
        {
            remainder.setDamageValue(currentDamage + 1); 
        }
        
        // Return an empty stack if the item breaks, otherwise return the damaged stack
        return remainder.getDamageValue() >= maxDamage ? ItemStack.EMPTY : remainder; 
    }

    @Override
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player, @Nonnull LivingEntity interactionTarget, @Nonnull InteractionHand usedHand) 
    {
        Level level = interactionTarget.level();

        if (level == null || level.isClientSide()) return InteractionResult.PASS;

        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        if (!(interactionTarget.getType().is(ModTags.Entities.CORRUPTED_ENTITY))) return InteractionResult.PASS;

        EntityConversion.startCleansing(serverLevel, interactionTarget);

        int currentDamage = stack.getDamageValue(); 
        int maxDamage = stack.getMaxDamage(); 
        
        if (currentDamage < maxDamage) 
        {
            stack.setDamageValue(currentDamage + 1); 
        }

        if (stack.getDamageValue() >= maxDamage) 
        {
            stack.shrink(maxDamage);
        }

        return InteractionResult.SUCCESS_NO_ITEM_USED;
    }
}
