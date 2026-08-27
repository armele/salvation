package com.deathfrog.salvationmod.core.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.blocks.ExtendedTimberFrameBlock;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.entity.block.MateriallyTexturedBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/** Creates and restores Salvation-owned materially textured corruption blocks. */
public final class ArchitecturalCorruption
{
    private static final int MAX_CONNECTED_BLOCKS = 512;
    private static final int PATCH_RADIUS_XZ = 12;
    private static final int PATCH_HEIGHT_UP = 6;
    private static final int PATCH_HEIGHT_DOWN = 4;

    /**
     * Prevents construction of this stateless utility class.
     */
    private ArchitecturalCorruption() {}

    /**
     * Attempts to corrupt the struck block according to the current datapack stage rules.
     * The operation respects the {@code mobGriefing} game rule, verifies that the block is
     * a valid Domum Ornamentum glazed-center material, replaces it with a materialized glazed
     * block, and records both its original state and its corruption contribution for restoration.
     *
     * @param level the server level containing the struck block
     * @param pos the position of the struck block
     * @param hitFace the face of the block struck by the corruption projectile
     * @param source the entity responsible for the corruption attack, or {@code null} when unavailable
     * @return {@code true} when the block was successfully replaced; otherwise {@code false}
     */
    @SuppressWarnings("null")
    public static boolean tryCorrupt(@Nonnull final ServerLevel level, @Nonnull final BlockPos pos,
        @Nonnull final Direction hitFace, final Entity source)
    {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))
        {
            return false;
        }

        final CorruptionStage stage = SalvationManager.stageForLevel(level);
        final float chance = stage.getBlockCorruptionChance();
        final int amount = stage.getBlockCorruptionAmount();
        if (amount <= 0 || chance <= 0.0F || level.random.nextFloat() >= chance)
        {
            return false;
        }

        final BlockState originalState = level.getBlockState(pos);
        if (originalState == null || !isEligible(level, pos, originalState))
        {
            return false;
        }

        final List<IMateriallyTexturedBlockComponent> components =
            new ArrayList<>(MCTradePostMod.GLAZED.get().getComponents());
        if (components.size() < 2)
        {
            return false;
        }

        final IMateriallyTexturedBlockComponent frame = components.get(0);
        final IMateriallyTexturedBlockComponent center = components.get(1);
        if (!originalState.getBlock().defaultBlockState().is(center.getValidSkins())
            || !ModBlocks.CORRUPTION_GLAZE.get().defaultBlockState().is(frame.getValidSkins()))
        {
            return false;
        }

        final BlockState glazedState = MCTradePostMod.GLAZED.get().defaultBlockState()
            .setValue(ExtendedTimberFrameBlock.FACING, hitFace);
        if (!level.setBlock(pos, glazedState, Block.UPDATE_ALL))
        {
            return false;
        }

        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof MateriallyTexturedBlockEntity texturedBlockEntity))
        {
            level.setBlock(pos, originalState, Block.UPDATE_ALL);
            return false;
        }

        final MaterialTextureData textureData = MaterialTextureData.builder()
            .setComponent(frame.getId(), ModBlocks.CORRUPTION_GLAZE.get())
            .setComponent(center.getId(), originalState.getBlock())
            .build();
        texturedBlockEntity.updateTextureDataWith(textureData);
        texturedBlockEntity.setChanged();
        level.sendBlockUpdated(pos, glazedState, glazedState, Block.UPDATE_ALL);

        CorruptedBlockData.get(level).put(pos, originalState, amount);
        SalvationManager.recordCorruption(level, ProgressionSource.BLOCK_CORRUPTION, pos, amount);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 0.65F);
        return true;
    }

    /**
     * Restores a Salvation-created glazed corruption block to its exact original block state.
     * A successful restoration removes the saved restoration record and reverses the world and
     * chunk corruption credited when the block was corrupted.
     *
     * @param level the server level containing the corrupted block
     * @param pos the position of the corrupted block
     * @return {@code true} when a tracked block was successfully restored; otherwise {@code false}
     */
    @SuppressWarnings("null")
    public static boolean tryRestore(@Nonnull final ServerLevel level, @Nonnull final BlockPos pos)
    {
        final CorruptedBlockData data = CorruptedBlockData.get(level);
        if (!restoreTrackedBlock(level, pos, data))
        {
            return false;
        }

        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.35F);
        return true;
    }

    /**
     * Restores the bounded, connected patch of tracked architectural corruption containing the
     * supplied origin. Connections include faces, edges, and corners, matching blighted-grass
     * patch extraction. Every block is restored to its own saved state and reverses its own saved
     * corruption contribution. Successful blocks also contribute independently to the standard
     * essence-of-corruption reward chance.
     *
     * @param level the server level containing the corrupted architecture
     * @param origin a tracked corrupted block in the patch to restore
     * @return the number of blocks successfully restored
     */
    public static int purifyConnectedPatch(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin)
    {
        final CorruptedBlockData data = CorruptedBlockData.get(level);

        if (data == null) return 0;

        final List<BlockPos> connectedBlocks = collectConnectedBlocks(level, origin, data);
        int restoredBlocks = 0;

        for (final BlockPos targetPos : connectedBlocks)
        {
            if (targetPos == null) continue;

            if (restoreTrackedBlock(level, targetPos, data))
            {
                BlightwoodPurification.spawnGrassPurificationBurst(level, targetPos);
                restoredBlocks++;
            }
        }

        if (restoredBlocks > 0)
        {
            BlightwoodPurification.spawnGrassPurificationFinale(level, origin, restoredBlocks);
            BlightwoodPurification.dropCorruptionEssence(level, origin, restoredBlocks);
        }

        return restoredBlocks;
    }

    /**
     * Determines whether the block at a position is a tracked architectural corruption block
     * that can be restored by a corruption extractor.
     *
     * @param level the server level containing the block
     * @param pos the position to inspect
     * @return {@code true} when the position contains a glazed block with saved restoration data
     */
    @SuppressWarnings("null")
    public static boolean isRestorable(@Nonnull final ServerLevel level, @Nonnull final BlockPos pos)
    {
        return level.getBlockState(pos).is(MCTradePostMod.GLAZED.get()) && CorruptedBlockData.get(level).get(pos) != null;
    }

    /**
     * Finds tracked, restorable glazed blocks connected to an extraction origin within the same
     * safety cap and spatial bounds used for blighted-grass patch extraction.
     *
     * @param level the server level containing the patch
     * @param origin the starting position
     * @param data the restoration records for the level
     * @return the connected tracked positions, capped at {@value #MAX_CONNECTED_BLOCKS}
     */
    private static List<BlockPos> collectConnectedBlocks(@Nonnull final ServerLevel level,
        @Nonnull final BlockPos origin, @Nonnull final CorruptedBlockData data)
    {
        final List<BlockPos> found = new ArrayList<>();
        final Deque<BlockPos> frontier = new ArrayDeque<>();
        final Set<BlockPos> visited = new HashSet<>();
        frontier.add(origin);
        visited.add(origin);

        while (!frontier.isEmpty() && found.size() < MAX_CONNECTED_BLOCKS)
        {
            final BlockPos current = frontier.removeFirst();

            if (current == null) continue;

            if (!isTrackedGlazedBlock(level, current, data))
            {
                continue;
            }

            found.add(current.immutable());
            for (int x = -1; x <= 1; x++)
            {
                for (int y = -1; y <= 1; y++)
                {
                    for (int z = -1; z <= 1; z++)
                    {
                        if (x == 0 && y == 0 && z == 0)
                        {
                            continue;
                        }

                        final BlockPos neighbor = current.offset(x, y, z);

                        if (neighbor == null) continue;

                        if (isWithinPatchBounds(origin, neighbor) && visited.add(neighbor))
                        {
                            frontier.addLast(neighbor);
                        }
                    }
                }
            }
        }

        return found;
    }

    /**
     * Restores one tracked glazed block without producing patch-level sounds or rewards.
     * Stale records whose glazed block has been removed are discarded without reducing corruption,
     * preserving the rule that mining corrupted blocks does not count as purification.
     *
     * @param level the server level containing the block
     * @param pos the position to restore
     * @param data the restoration records for the level
     * @return {@code true} if the saved original state was restored
     */
    @SuppressWarnings("null")
    private static boolean restoreTrackedBlock(@Nonnull final ServerLevel level, @Nonnull final BlockPos pos,
        @Nonnull final CorruptedBlockData data)
    {
        final CorruptedBlockEntry entry = data.get(pos);
        if (entry == null)
        {
            return false;
        }

        if (!level.getBlockState(pos).is(MCTradePostMod.GLAZED.get()))
        {
            data.remove(pos);
            return false;
        }

        if (!level.setBlock(pos, entry.originalState(), Block.UPDATE_ALL))
        {
            return false;
        }

        data.remove(pos);
        SalvationManager.recordCorruption(level, ProgressionSource.BLOCK_CORRUPTION, pos, -entry.corruptionAmount());
        return true;
    }

    /**
     * Tests whether a position has both the glazed block and Salvation restoration metadata.
     *
     * @param level the server level containing the position
     * @param pos the position to test
     * @param data the restoration records for the level
     * @return {@code true} when the position belongs to a restorable architectural patch
     */
    @SuppressWarnings("null")
    private static boolean isTrackedGlazedBlock(@Nonnull final ServerLevel level, @Nonnull final BlockPos pos,
        @Nonnull final CorruptedBlockData data)
    {
        return data.get(pos) != null && level.getBlockState(pos).is(MCTradePostMod.GLAZED.get());
    }

    /**
     * Tests whether a candidate remains inside the bounded architectural extraction volume.
     *
     * @param origin the extraction origin
     * @param pos the candidate position
     * @return {@code true} when the candidate is within the configured bounds
     */
    private static boolean isWithinPatchBounds(@Nonnull final BlockPos origin, @Nonnull final BlockPos pos)
    {
        return Math.abs(pos.getX() - origin.getX()) <= PATCH_RADIUS_XZ
            && Math.abs(pos.getZ() - origin.getZ()) <= PATCH_RADIUS_XZ
            && pos.getY() >= origin.getY() - PATCH_HEIGHT_DOWN
            && pos.getY() <= origin.getY() + PATCH_HEIGHT_UP;
    }

    /**
     * Applies the non-material safety checks required before a world block may be replaced.
     * Material compatibility with the glazed frame is checked separately using the Domum
     * Ornamentum component tags.
     *
     * @param level the server level containing the candidate block
     * @param pos the candidate block position
     * @param state the current state at the candidate position
     * @return {@code true} when the block is safe to consider for architectural corruption
     */
    @SuppressWarnings("null")
    private static boolean isEligible(@Nonnull final ServerLevel level, @Nonnull final BlockPos pos,
        @Nonnull final BlockState state)
    {
        if (state.isAir() || state.is(ModTags.Blocks.CORRUPTION_ATTACK_IMMUNE)
            || state.is(MCTradePostMod.GLAZED.get()) || state.is(ModBlocks.CORRUPTION_GLAZE.get()))
        {
            return false;
        }

        if (!(state.getBlock().asItem() instanceof BlockItem) || state.getDestroySpeed(level, pos) < 0.0F)
        {
            return false;
        }

        return level.getBlockEntity(pos) == null;
    }

    /**
     * Restoration information for one architectural corruption block.
     *
     * @param originalState the exact block state replaced by corruption
     * @param corruptionAmount the progression and chunk corruption credited for the replacement
     */
    private record CorruptedBlockEntry(BlockState originalState, int corruptionAmount) {}

    private static final class CorruptedBlockData extends SavedData
    {
        private static final String DATA_NAME = "salvation_architectural_corruption";
        private final Map<Long, CorruptedBlockEntry> entries = new HashMap<>();

        /**
         * Creates an empty architectural-corruption data store.
         */
        private CorruptedBlockData() {}

        /**
         * Retrieves the dimension-specific architectural-corruption data, loading or creating it
         * through the level's SavedData storage as necessary.
         *
         * @param level the server level whose data should be retrieved
         * @return the persistent architectural-corruption data for the level
         */
        static CorruptedBlockData get(@Nonnull final ServerLevel level)
        {
            return level.getDataStorage().computeIfAbsent(
                new Factory<>(CorruptedBlockData::new, CorruptedBlockData::load), DATA_NAME);
        }

        /**
         * Looks up restoration information for a block position.
         *
         * @param pos the position to look up
         * @return the saved entry, or {@code null} when the position is not tracked
         */
        CorruptedBlockEntry get(@Nonnull final BlockPos pos)
        {
            return entries.get(pos.asLong());
        }

        /**
         * Adds or replaces the restoration information for a corrupted block and marks the data dirty.
         *
         * @param pos the position of the corrupted glazed block
         * @param originalState the exact block state that was replaced
         * @param corruptionAmount the corruption contribution credited for the replacement
         */
        void put(@Nonnull final BlockPos pos, @Nonnull final BlockState originalState, final int corruptionAmount)
        {
            entries.put(pos.asLong(), new CorruptedBlockEntry(originalState, corruptionAmount));
            setDirty();
        }

        /**
         * Removes the restoration information for a position, marking the data dirty only when an
         * entry was present.
         *
         * @param pos the position whose restoration information should be removed
         */
        void remove(@Nonnull final BlockPos pos)
        {
            if (entries.remove(pos.asLong()) != null)
            {
                setDirty();
            }
        }

        /**
         * Serializes all tracked positions, original block states, and corruption contributions.
         *
         * @param tag the compound tag into which the data is written
         * @param registries the registry lookup provider supplied by the SavedData system
         * @return the populated compound tag
         */
        @SuppressWarnings("null")
        @Override
        public CompoundTag save(@Nonnull final CompoundTag tag, @Nonnull final net.minecraft.core.HolderLookup.Provider registries)
        {
            final ListTag list = new ListTag();
            for (Map.Entry<Long, CorruptedBlockEntry> mapEntry : entries.entrySet())
            {
                final CompoundTag entryTag = new CompoundTag();
                entryTag.putLong("pos", mapEntry.getKey());
                entryTag.put("state", NbtUtils.writeBlockState(mapEntry.getValue().originalState()));
                entryTag.putInt("amount", mapEntry.getValue().corruptionAmount());
                list.add(entryTag);
            }
            tag.put("entries", list);
            return tag;
        }

        /**
         * Deserializes dimension-specific architectural-corruption data from persistent storage.
         * Saved corruption amounts are clamped to non-negative values to tolerate malformed data.
         *
         * @param tag the compound tag containing serialized entries
         * @param registries the registry lookup provider supplied by the SavedData system
         * @return the reconstructed architectural-corruption data
         */
        @SuppressWarnings("null")
        static CorruptedBlockData load(@Nonnull final CompoundTag tag, final net.minecraft.core.HolderLookup.Provider registries)
        {
            final CorruptedBlockData data = new CorruptedBlockData();
            final ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++)
            {
                final CompoundTag entryTag = list.getCompound(i);
                final BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entryTag.getCompound("state"));
                data.entries.put(entryTag.getLong("pos"),
                    new CorruptedBlockEntry(state, Math.max(0, entryTag.getInt("amount"))));
            }
            return data;
        }
    }
}
