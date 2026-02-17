package com.deathfrog.salvationmod.core.blocks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntities;
import com.deathfrog.salvationmod.core.blockentity.PurificationBeaconCoreBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PurificationBeaconCoreBlock extends Block implements EntityBlock
{
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    @SuppressWarnings("null")
    public PurificationBeaconCoreBlock(Properties props)
    {
        super(props);

        BlockState lit = this.stateDefinition.any().setValue(LIT, Boolean.FALSE);

        this.registerDefaultState(lit);
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(LIT);
    }

    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state)
    {
        return new PurificationBeaconCoreBlockEntity(pos, state);
    }

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
    public boolean propagatesSkylightDown(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return true;
    }

    @Override
    public boolean useShapeForLightOcclusion(@Nonnull BlockState state) {
        return true;
    }

    @SuppressWarnings("null")
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
        
        return Shapes.or(BASE, TOP, LEG1, LEG2, LEG3, LEG4);
    }
}