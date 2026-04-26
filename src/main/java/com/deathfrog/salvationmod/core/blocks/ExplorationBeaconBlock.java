package com.deathfrog.salvationmod.core.blocks;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class ExplorationBeaconBlock extends PurificationBeaconCoreBlock
{
    public ExplorationBeaconBlock(final Properties props)
    {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(final @Nonnull BlockState state,
        final @Nonnull Level level,
        final @Nonnull BlockPos pos,
        final @Nonnull Player player,
        final @Nonnull BlockHitResult hit)
    {
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(final @Nonnull ItemStack stack,
        final @Nonnull BlockState state,
        final @Nonnull Level level,
        final @Nonnull BlockPos pos,
        final @Nonnull Player player,
        final @Nonnull InteractionHand hand,
        final @Nonnull BlockHitResult hit)
    {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
