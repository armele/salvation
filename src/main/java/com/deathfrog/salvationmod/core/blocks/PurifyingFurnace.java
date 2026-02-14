package com.deathfrog.salvationmod.core.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.salvationmod.core.blockentity.PurifyingFurnaceBlockEntity;
import com.mojang.serialization.MapCodec;

public class PurifyingFurnace extends BaseEntityBlock
{
    // REQUIRED in 1.21.x for BaseEntityBlock descendants
    public static final MapCodec<PurifyingFurnace> CODEC = simpleCodec(PurifyingFurnace::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    @SuppressWarnings("null")
    public PurifyingFurnace(final Properties props)
    {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec()
    {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(final @Nonnull BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(final @Nonnull BlockPos pos, final @Nonnull BlockState state)
    {
        return new PurifyingFurnaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final @Nonnull Level level, final @Nonnull BlockState state, final @Nonnull BlockEntityType<T> type)
    {
        if (level.isClientSide()) return null;

        return (lvl, pos, st, be) ->
        {
            if (be instanceof PurifyingFurnaceBlockEntity smelter)
            {
                PurifyingFurnaceBlockEntity.serverTick((ServerLevel) lvl, pos, st, smelter);
            }
        };
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder)
    {
        builder.add(FACING, LIT);
    }

    @Override
    protected InteractionResult useWithoutItem(final @Nonnull BlockState state, final @Nonnull Level level, final @Nonnull BlockPos pos,
                                            final @Nonnull Player player, final @Nonnull BlockHitResult hit)
    {
        return openMenuIntent(level, pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(final @Nonnull ItemStack stack, final @Nonnull BlockState state, final @Nonnull Level level, final @Nonnull BlockPos pos,
                                            final @Nonnull Player player, final @Nonnull InteractionHand hand, final @Nonnull BlockHitResult hit)
    {
        // Optional: allow shift-right-click to use the held item normally
        // if (player.isShiftKeyDown()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        return toItemResult(openMenuIntent(level, pos, player));
    }

    private InteractionResult openMenuIntent(final @Nonnull Level level, final @Nonnull BlockPos pos, final @Nonnull Player player)
    {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MenuProvider provider)
        {
            player.openMenu(provider, pos);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    private static ItemInteractionResult toItemResult(final InteractionResult r)
    {
        // In 1.21, ItemInteractionResult has the common “pass / success / consume” variants.
        return switch (r)
        {
            case CONSUME -> ItemInteractionResult.CONSUME;
            case SUCCESS -> ItemInteractionResult.SUCCESS;
            default      -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    @SuppressWarnings("null")
    @Override
    public void animateTick(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull RandomSource random)
    {
        if (!state.getValue(LIT)) return;

        // Occasional fire crackle (like vanilla)
        if (random.nextInt(10) == 0)
        {
            level.playLocalSound(
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                SoundEvents.FURNACE_FIRE_CRACKLE,
                SoundSource.BLOCKS,
                1.0F,
                1.0F,
                false
            );
        }

        final Direction facing = state.getValue(FACING);
        final double x = pos.getX() + 0.5;
        final double y = pos.getY() + 0.5;
        final double z = pos.getZ() + 0.5;

        // Offset particles toward the “front” face
        final double forward = 0.52;
        final double sideways = random.nextDouble() * 0.6 - 0.3;

        double px = x;
        double pz = z;

        switch (facing)
        {
            case WEST  -> { px = x - forward; pz = z + sideways; }
            case EAST  -> { px = x + forward; pz = z + sideways; }
            case NORTH -> { px = x + sideways; pz = z - forward; }
            case SOUTH -> { px = x + sideways; pz = z + forward; }
            default    -> {}
        }

        level.addParticle(ParticleTypes.SMOKE, px, y + 0.1, pz, 0.0, 0.0, 0.0);
        level.addParticle(ParticleTypes.FLAME, px, y + 0.1, pz, 0.0, 0.0, 0.0);
    }

    @SuppressWarnings("null")
    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext ctx)
    {
        return this.defaultBlockState()
            .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
            .setValue(LIT, false);
    }

}