package com.deathfrog.salvationmod.core.blocks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntities;
import com.deathfrog.salvationmod.core.blockentity.PurificationBeaconCoreBlockEntity;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PurificationBeaconCoreBlock extends BaseEntityBlock
{
    public static final MapCodec<PurificationBeaconCoreBlock> CODEC = simpleCodec(PurificationBeaconCoreBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    @SuppressWarnings("null")
    public PurificationBeaconCoreBlock(Properties props)
    {
        super(props);

        BlockState lit = this.stateDefinition.any().setValue(LIT, Boolean.FALSE);

        this.registerDefaultState(lit);
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

    /**
     * Adds the LIT property to the block state definition.
     * This property is a boolean, and is used to determine whether the block is lit.
     * @param builder The block state definition builder.
     */
    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(LIT);
    }

    /**
     * Creates a new block entity for this block at the given position and block state.
     * This method is called when the block is first loaded into the world.
     * The block entity will be responsible for ticking and updating the block's state.
     * @param pos the position of the block
     * @param state the block state of the block
     * @return a new block entity for this block
     */
    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state)
    {
        return new PurificationBeaconCoreBlockEntity(pos, state);
    }

    /**
     * Gets the block entity ticker for the given block state and block entity type.
     * This method is called on the server, and is used to create a block entity ticker for the given block entity type.
     * The block entity ticker is responsible for ticking and updating the block entity's state.
     * <p>
     * If the level is a client level, this method will return null.
     * <p>
     * If the block entity type is not a Purification Beacon Core block entity type, this method will return null.
     * <p>
     * Otherwise, this method will return a new block entity ticker for the Purification Beacon Core block entity type.
     *
     * @param level the level to get the block entity ticker for
     * @param state the block state of the block to get the block entity ticker for
     * @param type the block entity type to get the block entity ticker for
     * @return the block entity ticker for the given block state and block entity type, or null if the level is a client level or the block entity type is not a Purification Beacon Core block entity type
     */
    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        @Nonnull Level level,
        @Nonnull BlockState state,
        @Nonnull BlockEntityType<T> type)
    {
        if (level.isClientSide())
        {
            return null;
        }

        return type == SalvationTileEntities.PURIFICATION_BEACON_CORE.get()
            ? PurificationBeaconCoreBlockEntity.createTicker()
            : null;
    }

    @Override
    protected InteractionResult useWithoutItem(final @Nonnull BlockState state,
        final @Nonnull Level level,
        final @Nonnull BlockPos pos,
        final @Nonnull Player player,
        final @Nonnull BlockHitResult hit)
    {
        return openMenuIntent(level, pos, player);
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
        return toItemResult(openMenuIntent(level, pos, player));
    }

    private InteractionResult openMenuIntent(final @Nonnull Level level, final @Nonnull BlockPos pos, final @Nonnull Player player)
    {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MenuProvider provider)
        {
            player.openMenu(provider, pos);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    private static ItemInteractionResult toItemResult(final InteractionResult result)
    {
        return switch (result)
        {
            case CONSUME -> ItemInteractionResult.CONSUME;
            case SUCCESS -> ItemInteractionResult.SUCCESS;
            default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    /**
     * Called when the block is removed from the world.
     * 
     * @param state The block state of the block being removed
     * @param level The level accessor for the block
     * @param pos The position of the block
     * @param newState The block state of the block that is replacing this one
     * @param movedByPiston Whether the block was moved by a piston
     */
    @Override
    protected void onRemove(final @Nonnull BlockState state,
        final @Nonnull Level level,
        final @Nonnull BlockPos pos,
        final @Nonnull BlockState newState,
        final boolean movedByPiston)
    {
        Block block = newState.getBlock();

        if (block == null)
        {
            return;
        }

        if (!state.is(block))
        {
            final BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof PurificationBeaconCoreBlockEntity beacon)
            {
                beacon.dropContents();
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean propagatesSkylightDown(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return true;
    }

    @Override
    public boolean useShapeForLightOcclusion(@Nonnull BlockState state) {
        return true;
    }

    /**
     * Gets the collision shape for the given block state.
     * The collision shape is used to determine whether entities can collide with the block.
     * @param state the block state to get the collision shape for
     * @param level the block getter to get the collision shape for
     * @param pos the block position to get the collision shape for
     * @param context the collision context to get the collision shape for
     * @return the collision shape for the given block state
     */
    @Override
    public VoxelShape getShape(@Nonnull BlockState state,
        @Nonnull BlockGetter level,
        @Nonnull BlockPos pos,
        @Nonnull CollisionContext context)
    {
        VoxelShape BASE = Block.box(0, 0, 0, 16, 4, 16);
        VoxelShape TOP = Block.box(0, 12, 0, 16, 16, 16);
        VoxelShape LEG1 = Block.box(0, 0, 0, 2, 16, 2);
        VoxelShape LEG2 = Block.box(14, 0, 14, 16, 16, 16);
        VoxelShape LEG3 = Block.box(14, 0, 0, 16, 16, 2);
        VoxelShape LEG4 = Block.box(0, 0, 14, 2, 16, 16);
        
        return Shapes.or(NullnessBridge.assumeNonnull(BASE), TOP, LEG1, LEG2, LEG3, LEG4);
    }
}
