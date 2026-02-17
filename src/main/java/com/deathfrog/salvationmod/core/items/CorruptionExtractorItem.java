package com.deathfrog.salvationmod.core.items;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.item.AbstractCraftCountItem;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.core.engine.EntityConversion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CorruptionExtractorItem extends AbstractCraftCountItem
{
    public CorruptionExtractorItem(Properties properties)
    {
        super(properties);
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
