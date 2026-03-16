package com.deathfrog.salvationmod.core.blocks;

import java.util.Optional;

import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.core.portal.ExteritioPortalManager;
import com.deathfrog.salvationmod.core.portal.ExteritioPortalShape;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class NeutralizedBlightwoodBlock extends Block
{
    public NeutralizedBlightwoodBlock(final BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    public boolean isPortalFrame(final @Nonnull BlockState state, final @Nonnull BlockGetter level, final @Nonnull BlockPos pos)
    {
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(
        final @Nonnull ItemStack stack,
        final @Nonnull BlockState state,
        final @Nonnull Level level,
        final @Nonnull BlockPos pos,
        final @Nonnull Player player,
        final @Nonnull InteractionHand hand,
        final @Nonnull BlockHitResult hit)
    {
        if (!stack.is(ModTags.Items.PURIFIED_IRON_TOOLS))
        {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide())
        {
            return ItemInteractionResult.SUCCESS;
        }

        final Direction.Axis axis = hit.getDirection().getAxis() == Direction.Axis.Y ? Direction.Axis.X : hit.getDirection().getAxis();

        final Optional<ExteritioPortalShape> shape = ExteritioPortalManager.trySpawnPortal(level, pos.relative(hit.getDirection()), axis);
        if (shape.isEmpty())
        {
            final Optional<ExteritioPortalShape> fallbackShape = ExteritioPortalShape.findEmptyPortalShape(level, pos, axis);
            if (fallbackShape.isEmpty())
            {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }

            fallbackShape.get().createPortalBlocks();
        }

        level.playSound(null, pos, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.4F + 0.8F);
        return ItemInteractionResult.CONSUME;
    }
}
