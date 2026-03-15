package com.deathfrog.salvationmod.core.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.ModItems;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class BlightwoodPurification
{
    private static final int MAX_CONNECTED_BLOCKS = 512;
    private static final int SEARCH_RADIUS_XZ = 12;
    private static final int SEARCH_UP = 24;
    private static final int SEARCH_DOWN = 6;
    private static final int GRASS_PURIFY_RADIUS_XZ = 12;
    private static final int GRASS_PURIFY_HEIGHT_UP = 6;
    private static final int GRASS_PURIFY_HEIGHT_DOWN = 4;
    private static final float ESSENCE_DROP_CHANCE_PER_BLOCK = 0.05F;

    private BlightwoodPurification()
    {
    }

    /**
     * Purify a tree by replacing all connected blocks with a certain type of log/leaves, 
     * and spawn a burst of light when the tree is fully purified.
     * 
     * @param level the server level to operate on
     * @param origin the origin of the tree
     * @return the number of connected blocks that were replaced
     */
    public static int purifyTree(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin)
    {
        final List<BlockPos> connectedBlocks = collectConnectedTreeBlocks(level, origin);
        if (connectedBlocks.isEmpty())
        {
            return 0;
        }

        final TreeReplacement replacement = chooseReplacement(level, origin);
        final BlockState purifiedLogState = replacement.logState();
        final BlockState purifiedLeavesState = replacement.leavesState();

        if (purifiedLogState == null || purifiedLeavesState == null)
        {
            return 0;
        }

        for (final BlockPos targetPos : connectedBlocks)
        {
            if (targetPos == null)
            {
                continue;
            }

            final BlockState sourceState = level.getBlockState(targetPos);

            if (sourceState == null)
            {
                continue;
            }

            final BlockState purifiedState;

            if (isBlightwoodLog(sourceState))
            {
                purifiedState = copySharedProperties(sourceState, purifiedLogState);
            }
            else if (isBlightwoodLeaves(sourceState))
            {
                purifiedState = copySharedProperties(sourceState, purifiedLeavesState);
            }
            else
            {
                continue;
            }

            if (purifiedState == null)
            {
                continue;
            }

            level.setBlock(targetPos, purifiedState, Block.UPDATE_ALL);
            SalvationManager.recordCorruption(level, ProgressionSource.EXTRACTION, targetPos, -1);
            spawnPurificationBurst(level, targetPos, sourceState, connectedBlocks.size() > 12);
        }

        dropCorruptionEssence(level, origin, connectedBlocks.size());
        spawnFinale(level, origin, connectedBlocks.size());
        return connectedBlocks.size();
    }

    /**
     * Purify a patch of Blighted Grass by replacing all connected blocks with a certain type of grass.
     * 
     * @param level the server level to operate on
     * @param origin the origin of the patch of Blighted Grass
     * @return the number of connected blocks that were replaced
     */
    public static int purifyBlightedGrass(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin)
    {
        final BlockState purifiedGrass = NullnessBridge.assumeNonnull(Blocks.GRASS_BLOCK.defaultBlockState());
        int convertedBlocks = 0;
        final List<BlockPos> connectedBlocks = collectConnectedBlightedGrass(level, origin);

        for (final BlockPos targetPos : connectedBlocks)
        {
            if (targetPos == null)
            {
                continue;
            }

            final BlockState sourceState = level.getBlockState(targetPos);
            if (sourceState == null || !sourceState.is(NullnessBridge.assumeNonnull(ModBlocks.BLIGHTED_GRASS.get())))
            {
                continue;
            }

            level.setBlock(targetPos, purifiedGrass, Block.UPDATE_ALL);
            SalvationManager.recordCorruption(level, ProgressionSource.EXTRACTION, targetPos, -1);
            spawnGrassPurificationBurst(level, targetPos);
            convertedBlocks++;
        }

        if (convertedBlocks <= 0)
        {
            return 0;
        }

        spawnGrassPurificationFinale(level, origin, convertedBlocks);
        dropCorruptionEssence(level, origin, convertedBlocks);
        return convertedBlocks;
    }

    private static List<BlockPos> collectConnectedBlightedGrass(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin)
    {
        final List<BlockPos> found = new ArrayList<>();
        final Deque<BlockPos> frontier = new ArrayDeque<>();
        final Set<BlockPos> visited = new HashSet<>();

        frontier.add(origin);
        visited.add(origin);

        while (!frontier.isEmpty() && found.size() < MAX_CONNECTED_BLOCKS)
        {
            final BlockPos current = frontier.removeFirst();

            if (current == null)
            {
                continue;
            }

            final BlockState state = level.getBlockState(current);
            if (state == null || !state.is(NullnessBridge.assumeNonnull(ModBlocks.BLIGHTED_GRASS.get())))
            {
                continue;
            }

            found.add(current.immutable());

            for (final BlockPos neighbor : neighbors(current))
            {
                if (neighbor == null)
                {
                    continue;
                }

                if (visited.contains(neighbor) || !isWithinGrassBounds(origin, neighbor))
                {
                    continue;
                }

                visited.add(neighbor);
                frontier.addLast(neighbor);
            }
        }

        return found;
    }

    /**
     * Drops a certain amount of essence of corruption at the given position in the given level.
     * The amount of essence dropped is proportional to the number of blocks converted.
     * 
     * @param level The server level in which the essence is to be dropped.
     * @param origin The position at which the essence is to be dropped.
     * @param convertedBlocks The number of blocks that were converted in the purification process.
     */
    private static void dropCorruptionEssence(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin, final int convertedBlocks)
    {
        if (convertedBlocks <= 0)
        {
            return;
        }

        final RandomSource random = level.getRandom();
        int essenceCount = 0;

        for (int i = 0; i < convertedBlocks; i++)
        {
            if (random.nextFloat() < ESSENCE_DROP_CHANCE_PER_BLOCK)
            {
                essenceCount++;
            }
        }

        if (essenceCount <= 0)
        {
            return;
        }

        final ItemStack stack = new ItemStack(NullnessBridge.assumeNonnull(ModItems.ESSENCE_OF_CORRUPTION.get()), essenceCount);
        final double x = origin.getX() + 0.5 + ((random.nextDouble() - 0.5) * 0.4);
        final double y = origin.getY() + 0.6;
        final double z = origin.getZ() + 0.5 + ((random.nextDouble() - 0.5) * 0.4);
        final ItemEntity entity = new ItemEntity(level, x, y, z, stack);

        entity.setDefaultPickUpDelay();
        entity.setDeltaMovement(
            (random.nextDouble() - 0.5) * 0.08,
            0.12 + (random.nextDouble() * 0.05),
            (random.nextDouble() - 0.5) * 0.08
        );
        level.addFreshEntity(entity);
    }

    /**
     * Chooses a random replacement tree configuration from the biome's generation settings.
     * The chosen replacement is randomly selected from all the tree configurations in the biome's generation settings.
     * If no suitable tree configurations are found, it falls back to using the oak tree configuration.
     *
     * @param level the server level to operate on
     * @param origin the origin of the tree
     * @return a randomly chosen tree replacement
     */
    private static TreeReplacement chooseReplacement(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin)
    {
        final List<TreeReplacement> candidates = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final Holder<Biome> biomeHolder = level.getBiome(origin);

        for (final HolderSet<PlacedFeature> placedFeatureSet : biomeHolder.value().getGenerationSettings().features())
        {
            for (final Holder<PlacedFeature> placedFeatureHolder : placedFeatureSet)
            {
                final PlacedFeature placedFeature = placedFeatureHolder.value();

                for (final ConfiguredFeature<?, ?> configuredFeature : placedFeature.getFeatures().toList())
                {
                    if (configuredFeature == null || configuredFeature.feature() != Feature.TREE)
                    {
                        continue;
                    }

                    final TreeReplacement candidate = toTreeReplacement(configuredFeature, NullnessBridge.assumeNonnull(level.getRandom()), origin);

                    if (candidate == null)
                    {
                        continue;
                    }

                    final String key = treeKey(candidate);
                    if (seen.add(key))
                    {
                        candidates.add(candidate);
                    }
                }
            }
        }

        if (candidates.isEmpty())
        {
            return oakFallback();
        }

        return candidates.get(level.getRandom().nextInt(candidates.size()));
    }

    /**
     * Attempts to convert a ConfiguredFeature into a TreeReplacement.
     *
     * This method will return null if the configured feature is not a tree, or if the tree config does not have a valid trunk or foliage provider.
     *
     * Additionally, if the trunk or foliage of the tree is already a blightwood log or leaves, this method will return null.
     *
     * @param configuredFeature the configured feature to convert
     * @param random the random source to use
     * @param origin the origin position to use when generating the tree
     * @return a TreeReplacement representing the tree, or null if the conversion failed
     */
    private static TreeReplacement toTreeReplacement(
        @Nonnull final ConfiguredFeature<?, ?> configuredFeature,
        @Nonnull final RandomSource random,
        @Nonnull final BlockPos origin)
    {
        if (configuredFeature.feature() != Feature.TREE)
        {
            return null;
        }

        if (!(configuredFeature.config() instanceof TreeConfiguration treeConfig))
        {
            return null;
        }

        final BlockState logState = treeConfig.trunkProvider.getState(random, origin);
        final BlockState leavesState = treeConfig.foliageProvider.getState(random, origin);

        if (logState == null || leavesState == null)
        {
            return null;
        }

        if (isBlightwoodLog(logState) || isBlightwoodLeaves(leavesState))
        {
            return null;
        }

        return new TreeReplacement(logState, leavesState);
    }

    /**
     * Returns a string key representing the given TreeReplacement.
     * 
     * The format of the key is "log_block_id|leaves_block_id", where
     * log_block_id is the block ID of the log block, and leaves_block_id
     * is the block ID of the leaves block.
     * 
     * This method is used to cache the results of the tree replacement
     * lookup, so that the same tree replacement is not looked up
     * multiple times.
     */
    private static String treeKey(@Nonnull final TreeReplacement replacement)
    {
        return String.valueOf(replacement.logState().getBlock()) + "|" + String.valueOf(replacement.leavesState().getBlock());
    }

    /**
     * Returns a TreeReplacement representing an oak tree, which is used as a fallback when no suitable tree replacements are found.
     *
     * This method returns a TreeReplacement with the log block set to an oak log, and the leaves block set to oak leaves.
     *
     * This method is used when the biome's generation settings do not contain any suitable tree replacements.
     */
    private static TreeReplacement oakFallback()
    {
        return new TreeReplacement(
            NullnessBridge.assumeNonnull(Blocks.OAK_LOG.defaultBlockState()),
            NullnessBridge.assumeNonnull(Blocks.OAK_LEAVES.defaultBlockState())
        );
    }

    /**
     * Collect all connected Bightwood Tree Blocks starting from the given origin.
     * 
     * @param level the level to search in
     * @param origin the origin block to start from
     * @return a list of all connected Bightwood Tree Blocks
     */
    private static List<BlockPos> collectConnectedTreeBlocks(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin)
    {
        final List<BlockPos> found = new ArrayList<>();
        final Deque<BlockPos> frontier = new ArrayDeque<>();
        final Set<BlockPos> visited = new HashSet<>();

        frontier.add(origin);
        visited.add(origin);

        while (!frontier.isEmpty() && found.size() < MAX_CONNECTED_BLOCKS)
        {
            final BlockPos current = frontier.removeFirst();

            if (current == null)
            {
                continue;
            }

            final BlockState state = level.getBlockState(current);

            if (state == null)
            {
                continue;
            }

            if (!isBlightwoodTreeBlock(state))
            {
                continue;
            }

            found.add(current.immutable());

            for (final BlockPos neighbor : neighbors(current))
            {
                if (neighbor == null)
                {
                    continue;
                }

                if (visited.contains(neighbor) || !isWithinBounds(origin, neighbor))
                {
                    continue;
                }

                visited.add(neighbor);
                frontier.addLast(neighbor);
            }
        }

        return found;
    }

    /**
     * Returns an iterable of all the {@link BlockPos} neighbors of the given position.
     * 
     * @param pos the position to get the neighbors of
     * @return an iterable of all the neighbors of the given position
     */
    private static Iterable<BlockPos> neighbors(@Nonnull final BlockPos pos)
    {
        final List<BlockPos> neighbors = new ArrayList<>(26);

        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx == 0 && dy == 0 && dz == 0)
                    {
                        continue;
                    }

                    neighbors.add(pos.offset(dx, dy, dz));
                }
            }
        }

        return neighbors;
    }

    /**
     * Checks if the given position is within the search bounds of the origin.
     * 
     * The search bounds are defined as a cube with a side length of
     * 2 * {@link #SEARCH_RADIUS_XZ} + 1, centered on the origin.
     * Additionally, the search bounds are extended upwards and downwards by
     * {@link #SEARCH_UP} and {@link #SEARCH_DOWN} blocks respectively.
     * 
     * @param origin the origin block to check against
     * @param pos the position to check
     * @return true if the position is within the search bounds, false otherwise
     */
    private static boolean isWithinBounds(@Nonnull final BlockPos origin, @Nonnull final BlockPos pos)
    {
        return Math.abs(pos.getX() - origin.getX()) <= SEARCH_RADIUS_XZ
            && Math.abs(pos.getZ() - origin.getZ()) <= SEARCH_RADIUS_XZ
            && pos.getY() >= origin.getY() - SEARCH_DOWN
            && pos.getY() <= origin.getY() + SEARCH_UP;
    }

    private static boolean isWithinGrassBounds(@Nonnull final BlockPos origin, @Nonnull final BlockPos pos)
    {
        return Math.abs(pos.getX() - origin.getX()) <= GRASS_PURIFY_RADIUS_XZ
            && Math.abs(pos.getZ() - origin.getZ()) <= GRASS_PURIFY_RADIUS_XZ
            && pos.getY() >= origin.getY() - GRASS_PURIFY_HEIGHT_DOWN
            && pos.getY() <= origin.getY() + GRASS_PURIFY_HEIGHT_UP;
    }

    /**
     * Checks if the given block state is a BLightwood Tree Block.
     *
     * A Blightwood Tree Block is defined as either a BLightwood Log or a BLightwood Leaves block.
     *
     * @param state the block state to check
     * @return true if the block state is a BLightwood Tree Block, false otherwise
     */    
    private static boolean isBlightwoodTreeBlock(@Nonnull final BlockState state)
    {
        return isBlightwoodLog(state) || isBlightwoodLeaves(state);
    }

    /**
     * Checks if the given block state is a BLightwood Log block.
     *
     * @param state the block state to check
     * @return true if the block state is a BLightwood Log block, false otherwise
     */
    private static boolean isBlightwoodLog(@Nonnull final BlockState state)
    {
        return state.is(NullnessBridge.assumeNonnull(ModBlocks.BLIGHTWOOD_LOG.get()));
    }

    /**
     * Checks if the given block state is a BLightwood Leaves block.
     *
     * @param state the block state to check
     * @return true if the block state is a BLightwood Leaves block, false otherwise
     */    
    private static boolean isBlightwoodLeaves(@Nonnull final BlockState state)
    {
        return state.is(NullnessBridge.assumeNonnull(ModBlocks.BLIGHTWOOD_LEAVES.get()));
    }

    /**
     * Copies all shared properties from the source block state to the target block state.
     * Shared properties are defined as any property that is present in both the source and target block states.
     * 
     * @param source the source block state
     * @param target the target block state
     * @return the target block state with all shared properties copied from the source block state
     */
    private static BlockState copySharedProperties(@Nonnull final BlockState source, @Nonnull final BlockState target)
    {
        BlockState result = target;

        for (final Property<?> property : source.getProperties())
        {
            if (property == null)
            {
                continue;
            }

            if (!result.hasProperty(property))
            {
                continue;
            }

            result = copyPropertyValue(source, result, property);
        }

        return result;
    }

    /**
     * Copies the value of the given property from the source block state to the target block state.
     * If the source block state does not have the given property, or if the value of the property is null,
     * the target block state is returned unchanged.
     * @param source the source block state
     * @param target the target block state
     * @param property the property to copy
     * @return the target block state with the value of the property copied from the source block state
     */
    private static <T extends Comparable<T>> BlockState copyPropertyValue(
        @Nonnull final BlockState source,
        @Nonnull final BlockState target,
        @Nonnull final Property<T> property)
    {

        if (!source.hasProperty(property))
        {
            return target;
        }

        final T value = source.getValue(property);

        if (value == null)
        {
            return target;
        }

        return target.setValue(property, value);
    }

    /**
     * Spawns a burst of particles at the given position to visually represent the purification of a BLightwood log or leaves.
     * The type and number of particles spawned depends on whether the given block state was a log or leaves, and whether the given large tree parameter is true.
     *
     * @param level the level to spawn the particles in
     * @param pos the position to spawn the particles at
     * @param priorState the block state of the block that was just purified
     * @param largeTree whether the given block state was part of a large tree
     */
    private static void spawnPurificationBurst(
        @Nonnull final ServerLevel level,
        @Nonnull final BlockPos pos,
        @Nonnull final BlockState priorState,
        final boolean largeTree)
    {
        final RandomSource random = level.getRandom();
        final double x = pos.getX() + 0.5;
        final double y = pos.getY() + 0.5;
        final double z = pos.getZ() + 0.5;
        final boolean wasLeaves = isBlightwoodLeaves(priorState);

        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER), x, y, z,
            wasLeaves ? 4 : 2, 0.28, 0.28, 0.28, 0.02);
        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.END_ROD), x, y, z,
            wasLeaves ? 2 : 1, 0.18, 0.25, 0.18, 0.01);

        if (wasLeaves)
        {
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SPORE_BLOSSOM_AIR), x, y, z,
                3, 0.20, 0.15, 0.20, 0.0);
        }
        else
        {
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.POOF), x, y, z,
                2, 0.18, 0.18, 0.18, 0.01);
        }

        if (largeTree && random.nextInt(4) == 0)
        {
            level.playSound(null, x, y, z,
                NullnessBridge.assumeNonnull(SoundEvents.BREWING_STAND_BREW),
                SoundSource.BLOCKS, 0.18F, 1.45F + (random.nextFloat() * 0.15F));
        }
    }

    private static void spawnGrassPurificationBurst(@Nonnull final ServerLevel level, @Nonnull final BlockPos pos)
    {
        final double x = pos.getX() + 0.5;
        final double y = pos.getY() + 0.7;
        final double z = pos.getZ() + 0.5;

        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER), x, y, z,
            3, 0.25, 0.15, 0.25, 0.02);
        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.END_ROD), x, y, z,
            1, 0.15, 0.10, 0.15, 0.01);
        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SPORE_BLOSSOM_AIR), x, y, z,
            2, 0.22, 0.08, 0.22, 0.0);
    }

    private static void spawnGrassPurificationFinale(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin, final int convertedCount)
    {
        final RandomSource random = level.getRandom();
        final double x = origin.getX() + 0.5;
        final double y = origin.getY() + 1.0;
        final double z = origin.getZ() + 0.5;
        final double spread = convertedCount > 32 ? 1.4 : 0.9;

        level.playSound(null, x, y, z,
            NullnessBridge.assumeNonnull(SoundEvents.ENCHANTMENT_TABLE_USE),
            SoundSource.BLOCKS, 0.55F, 1.35F + (random.nextFloat() * 0.1F));
        level.playSound(null, x, y, z,
            NullnessBridge.assumeNonnull(SoundEvents.BONE_MEAL_USE),
            SoundSource.BLOCKS, 0.75F, 0.95F + (random.nextFloat() * 0.1F));

        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER), x, y, z,
            Math.min(36, 8 + convertedCount), spread, 0.55, spread, 0.03);
        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SPORE_BLOSSOM_AIR), x, y, z,
            Math.min(24, 6 + (convertedCount / 2)), spread, 0.35, spread, 0.0);
        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.END_ROD), x, y, z,
            Math.min(14, 4 + (convertedCount / 4)), spread * 0.7, 0.25, spread * 0.7, 0.02);
    }

    /**
     * Plays a finale burst of particles and sounds centered on the given origin after a purification event.
     * The number of particles spawned is proportional to the number of blocks converted.
     * @param level the level to play the finale in
     * @param origin the position to center the finale around
     * @param convertedCount the number of blocks converted in the purification event
     */
    private static void spawnFinale(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin, final int convertedCount)
    {
        final RandomSource random = level.getRandom();
        final double x = origin.getX() + 0.5;
        final double y = origin.getY() + 1.0;
        final double z = origin.getZ() + 0.5;
        final double spread = convertedCount > 20 ? 1.15 : 0.75;

        level.playSound(null, x, y, z,
            NullnessBridge.assumeNonnull(SoundEvents.ENCHANTMENT_TABLE_USE),
            SoundSource.BLOCKS, 0.7F, 1.25F + (random.nextFloat() * 0.1F));
        level.playSound(null, x, y, z,
            NullnessBridge.assumeNonnull(SoundEvents.ZOMBIE_VILLAGER_CURE),
            SoundSource.BLOCKS, 0.55F, 1.15F + (random.nextFloat() * 0.08F));

        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER), x, y, z,
            Math.min(24, 6 + convertedCount), spread, 1.2, spread, 0.03);
        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.END_ROD), x, y, z,
            Math.min(18, 4 + (convertedCount / 2)), spread * 0.8, 0.85, spread * 0.8, 0.02);
        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SPORE_BLOSSOM_AIR), x, y, z,
            Math.min(16, 5 + (convertedCount / 3)), spread, 0.9, spread, 0.0);
        level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.POOF), x, y, z,
            Math.min(16, 4 + (convertedCount / 3)), spread * 0.7, 0.75, spread * 0.7, 0.01);
    }

    private record TreeReplacement(BlockState logState, BlockState leavesState)
    {
    }
}
