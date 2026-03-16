package com.deathfrog.salvationmod.core.blocks;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;

public class BlightwoodSaplingBlock extends SaplingBlock
{

    private static final int LEAF_SCAN_RADIUS_XZ = 6;
    private static final int LEAF_SCAN_HEIGHT_UP = 12;
    private static final int GROUND_SEARCH_DEPTH = 16;

    public BlightwoodSaplingBlock(TreeGrower grower, Properties props)
    {
        super(grower, props);
    }

    /**
     * Advances the tree growing process at the given position.
     *
     * If the tree is at stage 0, it will immediately advance to the next stage.
     * If the tree is at a higher stage, it will attempt to grow the tree using the tree grower.
     * If the tree grows, the soil underneath the leaves will be blighted.
     *
     * @param level the level to modify
     * @param pos the position of the tree to advance
     * @param state the block state of the tree
     * @param random the random source to use for the growth attempt
     */
    @Override
    public void advanceTree(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull RandomSource random)
    {
        if (state.getValue(STAGE) == 0)
        {
            level.setBlock(pos, state.cycle(STAGE), 4);
            return;
        }

        boolean grew = this.treeGrower.growTree(level, level.getChunkSource().getGenerator(), pos, state, random);
        
        if (grew)
        {
            blightSoilUnderLeaves(level, pos);
        }
    }

    /**
     * Blights the soil around a trunk by replacing any dirt blocks with Blighted Grass.
     * This is used to create a visually appealing area around the trunk, and to prevent
     * the trunk from being surrounded by a large amount of dirt.
     *
     * @param level the level to modify
     * @param trunkPos the position of the trunk to blight around
     */
    public static void blightSoilAroundTrunk(@Nonnull ServerLevel level, @Nonnull BlockPos trunkPos)
    {
        BlockState blighted = ModBlocks.BLIGHTED_GRASS.get().defaultBlockState();

        if (blighted == null) return;

        BlockPos soilCenter = trunkPos.below();
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                BlockPos target = soilCenter.offset(dx, 0, dz);

                if (target == null) continue;

                BlockState targetState = level.getBlockState(target);
                if (targetState.is(NullnessBridge.assumeNonnull(Blocks.DIRT)))
                {
                    level.setBlock(target, blighted, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * Blights the soil around a trunk by replacing any dirt blocks with Blighted Grass.
     * This is used to create a visually appealing area around the trunk, and to prevent
     * the trunk from being surrounded by a large amount of dirt.
     *
     * @param level the level to modify
     * @param origin the position of the trunk to blight around
     */
    private static void blightSoilUnderLeaves(@Nonnull ServerLevel level, @Nonnull BlockPos origin)
    {
        final BlockState blighted = ModBlocks.BLIGHTED_GRASS.get().defaultBlockState();
        if (blighted == null) return;

        final BlockPos min = origin.offset(-LEAF_SCAN_RADIUS_XZ, 0, -LEAF_SCAN_RADIUS_XZ);
        final BlockPos max = origin.offset(LEAF_SCAN_RADIUS_XZ, LEAF_SCAN_HEIGHT_UP, LEAF_SCAN_RADIUS_XZ);

        if (min == null || max == null)
        {
            return;
        }

        for (BlockPos leafPos : BlockPos.betweenClosed(min, max))
        {
            if (leafPos == null)
            {
                continue;
            }

            final BlockState leafState = level.getBlockState(leafPos);
            if (!leafState.is(NullnessBridge.assumeNonnull(ModBlocks.BLIGHTWOOD_LEAVES.get())))
            {
                continue;
            }

            blightFirstDirtBelow(level, NullnessBridge.assumeNonnull(leafPos.below()), blighted);
        }
    }


    /**
     * Blights the first dirt block found directly below the given position.
     * This is used to create a visually appealing area around the trunk, and to prevent
     * the trunk from being surrounded by a large amount of dirt.
     *
     * This function will descend downwards from the given position, and will blight the first
     * dirt block it finds. It will also stop descending if it finds a non-air block that
     * cannot be replaced.
     *
     * @param level the level to modify
     * @param start the position to start searching from
     * @param blighted the blighted block state to replace the dirt block with
     */
    private static void blightFirstDirtBelow(
        @Nonnull ServerLevel level,
        @Nonnull BlockPos start,
        @Nonnull BlockState blighted)
    {
        BlockPos.MutableBlockPos cursor = start.mutable();

        for (int i = 0; i < GROUND_SEARCH_DEPTH && cursor.getY() > level.getMinBuildHeight(); i++)
        {
            final BlockState state = level.getBlockState(cursor);

            if (state.is(NullnessBridge.assumeNonnull(Blocks.DIRT)) || state.is(NullnessBridge.assumeNonnull(Blocks.GRASS_BLOCK)))
            {
                level.setBlock(cursor, blighted, Block.UPDATE_CLIENTS);
                return;
            }

            if (state.is(NullnessBridge.assumeNonnull(ModBlocks.BLIGHTWOOD_LOG.get())))
            {
                cursor.move(0, -1, 0);
                continue;
            }

            if (!state.isAir() && !state.canBeReplaced())
            {
                return;
            }

            cursor.move(0, -1, 0);
        }
    }

}
