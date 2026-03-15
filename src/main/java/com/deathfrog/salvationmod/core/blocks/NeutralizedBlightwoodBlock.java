package com.deathfrog.salvationmod.core.blocks;

import java.util.Optional;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.portal.ExteritioPortalManager;
import com.deathfrog.salvationmod.core.portal.ExteritioPortalShape;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class NeutralizedBlightwoodBlock extends Block
{
    @SuppressWarnings("null")
    private static final TagKey<Item> PURIFIED_IRON_TOOLS = TagKey.create(
        Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "purified_iron_tools")
    );

    public NeutralizedBlightwoodBlock(final BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    public boolean isPortalFrame(final @Nonnull BlockState state, final @Nonnull BlockGetter level, final @Nonnull BlockPos pos)
    {
        return true;
    }

    @SuppressWarnings("null")
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
        if (!stack.is(PURIFIED_IRON_TOOLS))
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
