package com.deathfrog.salvationmod.core.blocks;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.Config;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.ModItems;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.deathfrog.salvationmod.core.engine.ChunkCorruptionSystem;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public class PurifyingLeavesBlock extends LeavesBlock
{
    public PurifyingLeavesBlock(Properties properties)
    {
        super(properties);
    }

    @SuppressWarnings("null")
    @Override
    protected boolean isRandomlyTicking(@Nonnull BlockState state)
    {
        return !state.getValue(PERSISTENT);
    }

    /**
     * Purifying leaves will remove corruption (leaving behind essence of corruption) before eventually decaying.
     */
    @Override
    protected void randomTick(@Nonnull BlockState state, @Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull RandomSource random)
    {
        super.randomTick(state, level, pos, random);

        if (!level.getBlockState(pos).is(this))
        {
            return;
        }

        final int corruptionPerLeaf = Config.purifyingTreeCorruptionPerLeaf.get();
        final int before = ChunkCorruptionSystem.getChunkCorruption(level, pos);
        if (before < corruptionPerLeaf)
        {
            return;
        }

        SalvationManager.recordCorruption(level, ProgressionSource.TREE, pos, -corruptionPerLeaf);

        final int after = ChunkCorruptionSystem.getChunkCorruption(level, pos);
        if (before - after < corruptionPerLeaf)
        {
            return;
        }

        popResource(level, pos, new ItemStack(NullnessBridge.assumeNonnull(ModItems.ESSENCE_OF_CORRUPTION.get())));
        maybeDropSapling(level, pos, random);

        int chanceOfLeafDecay = Config.chanceOfLeafDecay.get();

        if (random.nextIntBetweenInclusive(1, 100) <= chanceOfLeafDecay)
        {
            level.setBlock(pos, NullnessBridge.assumeNonnull(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * If the leaf block is inside a colony that has done the research that allows it to reproduce, it may 
     * drop a sapling.
     * 
     * @param level
     * @param pos
     * @param random
     */
    @SuppressWarnings("null")
    private void maybeDropSapling(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull RandomSource random)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null)
        {
            return;
        }

        final double fruitingChance = colony.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_FRUITING);
        if (fruitingChance <= 0.0D)
        {
            return;
        }

        if (random.nextDouble() * 100.0D < fruitingChance)
        {
            popResource(level, pos, new ItemStack(ModBlocks.PURIFYING_SAPLING.get().asItem()));
        }
    }
}
