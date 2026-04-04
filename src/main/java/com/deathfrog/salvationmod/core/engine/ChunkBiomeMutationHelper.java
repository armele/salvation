package com.deathfrog.salvationmod.core.engine;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class ChunkBiomeMutationHelper
{
    private ChunkBiomeMutationHelper()
    {
    }


    /**
     * Replace all instances of sourceBiome with targetBiome in chunk
     * @param chunk
     * @param sourceBiome
     * @param targetBiome
     * @param level
     * @return
     */
    public static boolean replaceChunkBiome(final @Nonnull ChunkAccess chunk,
        final @Nonnull Holder<Biome> sourceBiome,
        final @Nonnull Holder<Biome> targetBiome,
        final @Nonnull net.minecraft.server.level.ServerLevel level)
    {
        final int[] replacedCount = new int[] { 0 };

        final BiomeResolver resolver = (quartX, quartY, quartZ, sampler) ->
        {
            final Holder<Biome> currentBiome = chunk.getNoiseBiome(quartX, quartY, quartZ);
            if (currentBiome.equals(sourceBiome))
            {
                replacedCount[0]++;
                return targetBiome;
            }

            return currentBiome;
        };

        Climate.Sampler climate = level.getChunkSource().randomState().sampler();

        if (climate == null)
        {
            return false;
        }

        chunk.fillBiomesFromNoise(resolver, climate);
        if (replacedCount[0] <= 0)
        {
            return false;
        }

        List<ChunkAccess> chunkList = List.of(chunk);

        if (chunkList == null)
        {
            return false;
        }

        chunk.setUnsaved(true);
        level.getChunkSource().chunkMap.resendBiomesForChunks(chunkList);
        return true;
    }
}
