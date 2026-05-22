package com.deathfrog.salvationmod.core.blocks;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class PurifyingSaplingBlock extends SaplingBlock
{
    public PurifyingSaplingBlock(TreeGrower grower, Properties props)
    {
        super(grower, props);
    }

    @Override
    public void advanceTree(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull RandomSource random)
    {
        if (state.getValue(NullnessBridge.assumeNonnull(STAGE)) == 0)
        {
            final BlockState stateCycle = state.cycle(NullnessBridge.assumeNonnull(STAGE));
            if (stateCycle != null)
            {
                level.setBlock(pos, stateCycle, 4);
            }
            return;
        }

        final ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator != null)
        {
            this.treeGrower.growTree(level, generator, pos, state, random);
        }
    }
}
