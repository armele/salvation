package com.deathfrog.salvationmod.core.items;

import java.util.List;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.item.AbstractCraftCountItem;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.core.engine.BlightwoodPurification;
import com.deathfrog.salvationmod.core.engine.EntityConversion;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CorruptionExtractorItem extends AbstractCraftCountItem
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    public CorruptionExtractorItem(Properties properties)
    {
        super(properties);
    }

    /**
     * Attempt to use the corruption extractor on the given {@link LivingEntity}.
     */
    @Override
    public InteractionResult interactLivingEntity(@Nonnull ItemStack stack, @Nonnull Player player, @Nonnull LivingEntity interactionTarget, @Nonnull InteractionHand usedHand) 
    {
        Level level = interactionTarget.level();

        if (level == null || level.isClientSide()) return InteractionResult.PASS;

        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        if (!(SalvationManager.isCorruptedEntity(interactionTarget.getType()))) return InteractionResult.PASS;

        EntityConversion.startConversion(serverLevel, interactionTarget, true, player instanceof ServerPlayer serverPlayer ? serverPlayer : null);

        consumeDurability(player, usedHand, stack, 1);

        return InteractionResult.SUCCESS_NO_ITEM_USED;
    }


    /**
     * Attempts to use the corruption extractor on the given block.
     * If the block is a Blightwood Log or a BLighted Grass, it will attempt to purify the block.
     * If the block is not one of the above, it will fall back to the superclass's implementation.
     *
     * The amount of durability consumed is proportional to the number of blocks that were purified.
     * The minimum amount of durability consumed is 1.
     *
     * @param ctx the use on context
     * @return the result of the use on action
     */
    @Override
    public InteractionResult useOn(final @Nonnull UseOnContext ctx)
    {
        final Level level = ctx.getLevel();
        final @Nonnull BlockPos clickedPos = NullnessBridge.assumeNonnull(ctx.getClickedPos());
        final BlockState clickedState = level.getBlockState(clickedPos);

        if (level.isClientSide())
        {
            if (clickedState.is(NullnessBridge.assumeNonnull(ModBlocks.BLIGHTWOOD_LOG.get()))
                || clickedState.is(NullnessBridge.assumeNonnull(ModBlocks.BLIGHTED_GRASS.get())))
            {
                return InteractionResult.SUCCESS;
            }

            return super.useOn(ctx);
        }

        if (!(level instanceof ServerLevel serverLevel))
        {
            return InteractionResult.PASS;
        }

        final ItemStack stack = ctx.getItemInHand();
        final int convertedBlocks;

        if (clickedState.is(NullnessBridge.assumeNonnull(ModBlocks.BLIGHTWOOD_LOG.get())))
        {
            convertedBlocks = BlightwoodPurification.purifyTree(serverLevel, clickedPos);
        }
        else if (clickedState.is(NullnessBridge.assumeNonnull(ModBlocks.BLIGHTED_GRASS.get())))
        {
            convertedBlocks = BlightwoodPurification.purifyBlightedGrass(serverLevel, clickedPos);
        }
        else
        {
            return super.useOn(ctx);
        }

        if (convertedBlocks <= 0 || stack == null || stack.isEmpty())
        {
            return InteractionResult.PASS;
        }

        consumeDurability(ctx.getPlayer(), ctx.getHand(), stack, Math.max(1, (convertedBlocks / 10)));
        return InteractionResult.SUCCESS;
    }


    /**
     * Consume durability of the item held by a player in a given hand.
     * If the stack is broken (i.e. its durability is 0 or less), it will be removed from the player's inventory.
     *
     * @param player the player holding the item
     * @param usedHand the hand used to hold the item
     * @param stack the item stack to consume durability on
     * @param damageToAdd the amount of damage to add to the item
     */
    private static void consumeDurability(Player player, InteractionHand usedHand, @Nonnull final ItemStack stack, int damageToAdd)
    {
        if (damageToAdd <= 0 || player == null || usedHand == null || stack.isEmpty() || !stack.isDamageableItem())
        {
            return;
        }

        final EquipmentSlot slot = LivingEntity.getSlotForHand(usedHand);
        if (slot == null)
        {
            return;
        }

        final int currentDamage = stack.getDamageValue();
        final int maxDamage = stack.getMaxDamage();

        // Defensive cleanup: if the stack is already at or past its break threshold,
        // remove it explicitly instead of relying on hurtAndBreak to recover it.
        if (currentDamage >= maxDamage)
        {
            player.setItemInHand(usedHand, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
            player.onEquippedItemBroken(NullnessBridge.assumeNonnull(stack.getItem()), slot);
            return;
        }

        // If this use would break the item, remove it explicitly.
        if (currentDamage + damageToAdd >= maxDamage)
        {
            player.setItemInHand(usedHand, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
            player.onEquippedItemBroken(NullnessBridge.assumeNonnull(stack.getItem()), slot);
            return;
        }

        stack.setDamageValue(currentDamage + damageToAdd);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull  Item.TooltipContext context, @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag tooltipFlag)
    {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("tooltip.salvation.corruption_extractor.flavor").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

}
