package com.deathfrog.salvationmod.core.portal;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModDimensions;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.ModEntityTypes;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData;
import com.deathfrog.salvationmod.entity.VoraxianOverlordEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

public final class ExteritioBossStructureManager
{
    public enum BossArenaRegenerationResult
    {
        WRONG_DIMENSION,
        NO_SAVED_ARENA,
        SAVED_SPAWN_ALREADY_PRESENT,
        ANCHOR_FOUND_AND_RECORDED,
        CLEARED_MISSING_ANCHOR
    }

    @SuppressWarnings("null")
    private static final @Nonnull ResourceLocation VORAXIAN_BASE_TEMPLATE = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "voraxian_structures/voraxian_base");

    private static final long POSITION_SEED_SALT = 0x56A2F31DL;
    private static final int MIN_RADIUS = 1536;
    private static final int EXTRA_RADIUS = 1024;
    private static final int CHUNK_CENTER_OFFSET = 8;
    private static final long MINECRAFT_DAY_TICKS = 24000L;
    private static final double RESPAWN_CHANCE_PER_DAY = 0.10D;
    private static final int BOSS_SEARCH_HORIZONTAL_RADIUS = 96;
    private static final int BOSS_SEARCH_VERTICAL_RADIUS = 64;
    private static final long BOSS_SPAWN_RETRY_COOLDOWN_TICKS = 200L;
    private static final int LEGACY_ANCHOR_SEARCH_HORIZONTAL_RADIUS = 128;
    private static final int LEGACY_ANCHOR_SEARCH_VERTICAL_RADIUS = 96;
    private static final double SURFACE_ARENA_CHANCE = 0.20D;
    private static final int MIN_UNDERGROUND_DEPTH = 12;
    private static final int EXTRA_UNDERGROUND_DEPTH = 84;
    private static final int VOID_FALLBACK_MIN_OFFSET = 32;
    private static final int VOID_FALLBACK_EXTRA_OFFSET = 128;

    private ExteritioBossStructureManager()
    {
    }

    /**
     * Ensures that the Exteritio boss structure is spawned in the given level.
     * This method checks if the structure is already spawned, and if not, it tries to spawn it.
     * If the spawning fails, an error is logged, and the method returns.
     *
     * @param level the level to check in
     */
    public static void ensureSpawned(@Nonnull final ServerLevel level)
    {
        if (level.dimension() != ModDimensions.EXTERITIO)
        {
            return;
        }

        final SalvationSavedData data = SalvationSavedData.get(level);
        if (!data.hasVoraxianBaseLocation())
        {
            ensureStructureSpawned(level, data);
        }

        if (!data.hasVoraxianBaseLocation())
        {
            return;
        }

        ensureBossPresence(level, data);
    }

    /**
     * Places the Voraxian base structure if this Exteritio save has not already recorded one.
     * <p>
     * The placed structure's center is stored as the locator target. If the structure contains a
     * {@link ModBlocks#VORAXIAN_OVERLORD_ANCHOR} block, that block is consumed and its position is
     * stored as the boss spawn point.
     *
     * @param level the Exteritio level to place the structure in
     * @param data the saved data that tracks the arena and boss state
     */
    private static void ensureStructureSpawned(@Nonnull final ServerLevel level, @Nonnull final SalvationSavedData data)
    {
        if (data.hasVoraxianBaseLocation())
        {
            return;
        }

        final Optional<StructureTemplate> templateOptional = level.getStructureManager().get(VORAXIAN_BASE_TEMPLATE);
        if (templateOptional.isEmpty())
        {
            SalvationMod.LOGGER.error("Unable to load Exteritio boss structure template {}", VORAXIAN_BASE_TEMPLATE);
            return;
        }

        final StructureTemplate template = templateOptional.get();
        final Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0)
        {
            SalvationMod.LOGGER.error("Structure template {} has invalid size {}", VORAXIAN_BASE_TEMPLATE, size);
            return;
        }

        final BlockPos origin = findPlacementOrigin(level, size);

        if (origin == null)
        {
            SalvationMod.LOGGER.error("Unable to find suitable origin for Exteritio boss structure {}", VORAXIAN_BASE_TEMPLATE);
            return;
        }

        forceLoadTemplateChunks(level, origin, size);

        final StructurePlaceSettings placement = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .addProcessor(NullnessBridge.assumeNonnull(JigsawReplacementProcessor.INSTANCE));

        if (placement == null)
        {
            SalvationMod.LOGGER.error("Unable to find suitable placement for Exteritio boss structure {}", VORAXIAN_BASE_TEMPLATE);
            return;
        }

        final long placementSeed = level.getSeed() ^ origin.asLong() ^ POSITION_SEED_SALT;
        final boolean placed = template.placeInWorld(
            level,
            origin,
            origin,
            placement,
            NullnessBridge.assumeNonnull(StructureBlockEntity.createRandom(placementSeed)),
            2
        );

        if (!placed)
        {
            SalvationMod.LOGGER.error("Failed placing Exteritio boss structure {} at {}", VORAXIAN_BASE_TEMPLATE, origin);
            return;
        }

        final BlockPos locatorTarget = origin.offset(size.getX() / 2, 0, size.getZ() / 2).immutable();
        final BlockPos bossSpawnLocation = findAndConsumeOverlordAnchor(level, origin, size);

        if (locatorTarget == null)
        {
            SalvationMod.LOGGER.error("Unable to find suitable locator target for Exteritio boss structure {}", VORAXIAN_BASE_TEMPLATE);
            return;
        }

        data.setVoraxianBaseLocation(locatorTarget);
        if (bossSpawnLocation != null)
        {
            data.setVoraxianOverlordSpawnLocation(bossSpawnLocation);
        }
        else
        {
            SalvationMod.LOGGER.warn("Placed Exteritio boss structure {} at {}, but no {} block was found. The Voraxian Overlord will not spawn until an anchor is present.",
                VORAXIAN_BASE_TEMPLATE, origin, ModBlocks.VORAXIAN_OVERLORD_ANCHOR.getId());
        }

        TraceUtils.dynamicTrace(ModCommands.TRACE_OVERLORD, () -> SalvationMod.LOGGER.info("Placed Exteritio boss structure {} at {} (locator target: {}, boss spawn: {})",
            VORAXIAN_BASE_TEMPLATE, origin, locatorTarget, bossSpawnLocation));
    }

    /**
     * Ensures that the Voraxian Overlord exists when the arena is loaded and eligible for spawning.
     * <p>
     * The first spawn is immediate once the arena is entity-ticking and no living boss is found. After
     * the boss has been slain, respawns are checked once per Minecraft day using
     * {@link #RESPAWN_CHANCE_PER_DAY}.
     *
     * @param level the Exteritio level to check
     * @param data the saved data that tracks the arena and boss state
     */
    private static void ensureBossPresence(@Nonnull final ServerLevel level, @Nonnull final SalvationSavedData data)
    {
        final BlockPos center = data.getVoraxianBaseLocation();
        if (center == null || !isBossArenaEntityTicking(level, center))
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_OVERLORD, () -> SalvationMod.LOGGER.warn("Unable to spawn Voraxian Overlord for Exteritio boss arena at {} because base location is not ticking.", center));
            return;
        }

        if (hasAliveBoss(level, data))
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_OVERLORD, () -> SalvationMod.LOGGER.warn("Unable to spawn Voraxian Overlord for Exteritio boss arena at {} because boss is already alive.", center));
            return;
        }

        if (!data.hasVoraxianOverlordBeenSlain())
        {
            spawnOverlord(level, data);
            return;
        }

        final long gameTime = level.getGameTime();
        if (gameTime - data.getVoraxianOverlordLastSpawnGameTime() < BOSS_SPAWN_RETRY_COOLDOWN_TICKS)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_OVERLORD, () -> SalvationMod.LOGGER.warn("Unable to respawn Voraxian Overlord for Exteritio boss arena at {} because cooldown has not expired.", center));
            return;
        }

        final long day = level.getDayTime() / MINECRAFT_DAY_TICKS;
        if (day <= data.getVoraxianOverlordLastRespawnDayCheck())
        {
            return;
        }

        data.setVoraxianOverlordLastRespawnDayCheck(day);
        if (level.random.nextDouble() < RESPAWN_CHANCE_PER_DAY)
        {
            spawnOverlord(level, data);
        }
    }

    /**
     * Records that the Voraxian Overlord has been slain in Exteritio.
     * <p>
     * This clears the tracked boss UUID through saved data and starts the daily respawn check from the
     * current Minecraft day.
     *
     * @param level the level where the death occurred
     */
    public static void onOverlordSlain(@Nonnull final ServerLevel level)
    {
        if (level.dimension() != ModDimensions.EXTERITIO)
        {
            return;
        }

        final SalvationSavedData data = SalvationSavedData.get(level);
        data.setVoraxianOverlordSlain(true);
        data.setVoraxianOverlordLastRespawnDayCheck(level.getDayTime() / MINECRAFT_DAY_TICKS);
    }

    /**
     * Checks the saved boss arena for an Overlord anchor and clears the saved arena when none exists.
     * <p>
     * This is intended for manually upgrading legacy worlds. If the saved data already has a boss spawn
     * location, the arena is treated as upgraded because new arenas consume the visible anchor block after
     * recording it. If no spawn location is saved, this scans around the saved arena target for an anchor.
     * A found anchor is consumed and recorded; a missing anchor clears the saved base and spawn locations
     * so the next {@link #ensureSpawned(ServerLevel)} call can place a fresh arena from the updated template.
     *
     * @param level the level whose saved Exteritio boss arena should be checked
     * @return the result of the regeneration check
     */
    public static BossArenaRegenerationResult regenerateSavedArenaIfMissingAnchor(@Nonnull final ServerLevel level)
    {
        if (level.dimension() != ModDimensions.EXTERITIO)
        {
            return BossArenaRegenerationResult.WRONG_DIMENSION;
        }

        final SalvationSavedData data = SalvationSavedData.get(level);
        if (!data.hasVoraxianBaseLocation())
        {
            return BossArenaRegenerationResult.NO_SAVED_ARENA;
        }

        if (data.hasVoraxianOverlordSpawnLocation())
        {
            return BossArenaRegenerationResult.SAVED_SPAWN_ALREADY_PRESENT;
        }

        final BlockPos found = resolveOverlordSpawnLocation(level, data);
        if (found != null)
        {
            return BossArenaRegenerationResult.ANCHOR_FOUND_AND_RECORDED;
        }

        data.clearVoraxianBaseLocation();
        data.clearVoraxianOverlordSpawnLocation();
        data.setVoraxianOverlordUuid(null);
        return BossArenaRegenerationResult.CLEARED_MISSING_ANCHOR;
    }

    /**
     * Checks if there is an alive VoraxianOverlordEntity instance in the given level,
     * within a certain radius of the Voraxian base location.
     *
     * @param level the level to check in
     * @param data the SalvationSavedData instance for the level
     * @return true if an alive VoraxianOverlordEntity is found, false otherwise
     */
    private static boolean hasAliveBoss(@Nonnull final ServerLevel level, @Nonnull final SalvationSavedData data)
    {
        final BlockPos center = data.getVoraxianBaseLocation();
        if (center == null || !isBossArenaEntityTicking(level, center))
        {
            return false;
        }

        final UUID trackedUuid = data.getVoraxianOverlordUuid();
        if (trackedUuid != null)
        {
            final Entity trackedEntity = level.getEntity(trackedUuid);
            if (trackedEntity instanceof VoraxianOverlordEntity trackedOverlord && trackedOverlord.isAlive())
            {
                return true;
            }
        }

        final AABB searchBox = new AABB(center).inflate(BOSS_SEARCH_HORIZONTAL_RADIUS, BOSS_SEARCH_VERTICAL_RADIUS, BOSS_SEARCH_HORIZONTAL_RADIUS);

        if (searchBox == null)
        {
            return false;
        }

        final Optional<VoraxianOverlordEntity> overlord = level.getEntitiesOfClass(
            VoraxianOverlordEntity.class,
            searchBox,
            VoraxianOverlordEntity::isAlive
        ).stream().findFirst();

        overlord.ifPresent(found -> data.setVoraxianOverlordUuid(found.getUUID()));
        return overlord.isPresent();
    }

    /**
     * Spawns a Voraxian Overlord entity at the structure-authored anchor location.
     * If no anchor has been recorded or found in the legacy arena, the spawn is skipped.
     *
     * @param level the level to spawn the entity in
     * @param data the SalvationSavedData instance for the level
     */
    @SuppressWarnings("null")
    private static void spawnOverlord(@Nonnull final ServerLevel level, @Nonnull final SalvationSavedData data)
    {
        final BlockPos center = data.getVoraxianBaseLocation();
        if (center == null || !isBossArenaEntityTicking(level, center))
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_OVERLORD, () -> SalvationMod.LOGGER.warn("Unable to spawn Voraxian Overlord for Exteritio boss arena at {} because base location is not ticking.", center));
            return;
        }

        final BlockPos spawnPos = resolveOverlordSpawnLocation(level, data);
        if (spawnPos == null)
        {
            data.setVoraxianOverlordLastSpawnGameTime(level.getGameTime());
            TraceUtils.dynamicTrace(ModCommands.TRACE_OVERLORD, () -> SalvationMod.LOGGER.warn("Unable to spawn Voraxian Overlord for Exteritio boss arena at {} because no {} block has been recorded or found.",
                center, ModBlocks.VORAXIAN_OVERLORD_ANCHOR.getId()));
            return;
        }

        final VoraxianOverlordEntity overlord = ModEntityTypes.VORAXIAN_OVERLORD.get().create(level);
        if (overlord == null)
        {
            TraceUtils.dynamicTrace(ModCommands.TRACE_OVERLORD, () -> SalvationMod.LOGGER.error("Unable to create Voraxian Overlord entity in dimension {} for Exteritio boss arena at {}",
                getDimensionName(level), spawnPos));
            return;
        }

        overlord.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        overlord.setPersistenceRequired();
        overlord.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.STRUCTURE, null);

        if (!level.noCollision(overlord))
        {
            data.setVoraxianOverlordLastSpawnGameTime(level.getGameTime());
            TraceUtils.dynamicTrace(ModCommands.TRACE_OVERLORD, () -> SalvationMod.LOGGER.error("Unable to spawn Voraxian Overlord at {} because the anchor does not provide enough open space.",
                spawnPos));
            return;
        }

        if (level.addFreshEntity(overlord))
        {
            data.setVoraxianOverlordSlain(false);
            data.setVoraxianOverlordUuid(overlord.getUUID());
            data.setVoraxianOverlordLastSpawnGameTime(level.getGameTime());
            TraceUtils.dynamicTrace(ModCommands.TRACE_OVERLORD, () -> SalvationMod.LOGGER.info("Spawned Voraxian Overlord in dimension {} at {}", getDimensionName(level), spawnPos));
        }
        else
        {
            data.setVoraxianOverlordLastSpawnGameTime(level.getGameTime());
            TraceUtils.dynamicTrace(ModCommands.TRACE_OVERLORD, () -> SalvationMod.LOGGER.error("Failed to add Voraxian Overlord entity in dimension {} to Exteritio boss arena at {}",
                getDimensionName(level), spawnPos));
        }
    }

    /**
     * Checks whether the saved boss arena position is loaded strongly enough for entities to tick.
     *
     * @param level the level containing the arena
     * @param center the saved arena locator target
     * @return true if entity logic can run at the arena position
     */
    private static boolean isBossArenaEntityTicking(@Nonnull final ServerLevel level, @Nonnull final BlockPos center)
    {
        return level.isPositionEntityTicking(center);
    }

    /**
     * Returns the resource location string for the level's dimension.
     *
     * @param level the level to describe
     * @return the dimension id, for logging
     */
    private static String getDimensionName(@Nonnull final ServerLevel level)
    {
        return level.dimension().location().toString();
    }

    /**
     * Resolves the position where the Voraxian Overlord should spawn.
     * <p>
     * New arenas persist this position when the structure is first placed. Legacy arenas that do not
     * have a saved spawn location are scanned near the saved locator target for a
     * {@link ModBlocks#VORAXIAN_OVERLORD_ANCHOR}; if found, the anchor is consumed and persisted.
     *
     * @param level the Exteritio level containing the arena
     * @param data the saved data that may already contain the boss spawn position
     * @return the boss spawn position, or null if no anchor has been recorded or found
     */
    @SuppressWarnings("null")
    private static BlockPos resolveOverlordSpawnLocation(@Nonnull final ServerLevel level, @Nonnull final SalvationSavedData data)
    {
        final BlockPos savedSpawn = data.getVoraxianOverlordSpawnLocation();
        if (savedSpawn != null)
        {
            return savedSpawn;
        }

        final BlockPos center = data.getVoraxianBaseLocation();
        if (center == null)
        {
            return null;
        }

        final BlockPos min = center.offset(
            -LEGACY_ANCHOR_SEARCH_HORIZONTAL_RADIUS,
            -LEGACY_ANCHOR_SEARCH_VERTICAL_RADIUS,
            -LEGACY_ANCHOR_SEARCH_HORIZONTAL_RADIUS);
            
        final BlockPos max = center.offset(
            LEGACY_ANCHOR_SEARCH_HORIZONTAL_RADIUS,
            LEGACY_ANCHOR_SEARCH_VERTICAL_RADIUS,
            LEGACY_ANCHOR_SEARCH_HORIZONTAL_RADIUS);

        final BlockPos found = findAndConsumeOverlordAnchor(level, min, max);
        if (found != null)
        {
            data.setVoraxianOverlordSpawnLocation(found);
        }

        return found;
    }

    /**
     * Searches a placed structure volume for the Overlord anchor block.
     *
     * @param level the level containing the placed structure
     * @param origin the minimum corner of the placed structure
     * @param size the template size used to compute the maximum corner
     * @return the anchor position, or null if no anchor was found
     */
    private static BlockPos findAndConsumeOverlordAnchor(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final BlockPos max = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        if (max == null)
        {
            return null;
        }

        return findAndConsumeOverlordAnchor(level, origin, max);
    }

    /**
     * Searches a block volume for the Overlord anchor block and replaces the first match with air.
     * <p>
     * The consumed block's position is used as the stable boss spawn point, so the marker does not
     * remain visible in the finished arena.
     *
     * @param level the level to scan and modify
     * @param min the minimum scan corner, inclusive
     * @param max the maximum scan corner, inclusive
     * @return the consumed anchor position, or null if no anchor was found
     */
    @SuppressWarnings("null")
    private static BlockPos findAndConsumeOverlordAnchor(@Nonnull final ServerLevel level, @Nonnull final BlockPos min, @Nonnull final BlockPos max)
    {
        for (final BlockPos pos : BlockPos.betweenClosed(min, max))
        {
            if (level.getBlockState(pos).is(ModBlocks.VORAXIAN_OVERLORD_ANCHOR.get()))
            {
                final BlockPos spawnPos = pos.immutable();
                level.setBlock(pos, NullnessBridge.assumeNonnull(Blocks.AIR.defaultBlockState()), Block.UPDATE_ALL);
                return spawnPos;
            }
        }

        return null;
    }

    /**
     * Finds a suitable origin for spawning the Exteritio boss structure in the given level.
     * The origin is chosen to be at least {@link #MIN_RADIUS} blocks away from the nearest chunk border,
     * and at most {@link #EXTRA_RADIUS} blocks away from the nearest chunk border.
     * The origin is also aligned to the nearest chunk center.
     * The origin's Y-coordinate is chosen from the sampled surface or a deterministic underground fallback.
     *
     * @param level the level to find the origin in
     * @param size the size of the Exteritio boss structure
     * @return the suitable origin for spawning the Exteritio boss structure
     */
    private static BlockPos findPlacementOrigin(@Nonnull final ServerLevel level, @Nonnull final Vec3i size)
    {
        final RandomSource random = RandomSource.create(level.getSeed() ^ POSITION_SEED_SALT);
        final double angle = random.nextDouble() * (Math.PI * 2.0D);
        final int radius = MIN_RADIUS + random.nextInt(EXTRA_RADIUS + 1);

        final int centerX = alignToChunkCenter(Mth.floor(Math.cos(angle) * radius));
        final int centerZ = alignToChunkCenter(Mth.floor(Math.sin(angle) * radius));
        final int originX = centerX - (size.getX() / 2);
        final int originZ = centerZ - (size.getZ() / 2);
        final int originY = choosePlacementY(level, originX, originZ, size, random);

        return new BlockPos(originX, originY, originZ);
    }

    /**
     * Chooses the template origin Y. Most arenas are buried at a variable depth below the sampled
     * terrain, while some remain at the surface. If the sampled footprint has no useful heightmap
     * surface, a deterministic mid-depth fallback is used instead of pinning the arena to world bottom.
     *
     * @param level the level to search in
     * @param originX the X-coordinate of the origin of the bounding box
     * @param originZ the Z-coordinate of the origin of the bounding box
     * @param size the size of the bounding box
     * @param random the deterministic arena placement random
     * @return the Y-coordinate used as the template origin
     */
    private static int choosePlacementY(
        @Nonnull final ServerLevel level,
        final int originX,
        final int originZ,
        @Nonnull final Vec3i size,
        @Nonnull final RandomSource random)
    {
        final int minY = level.getMinBuildHeight() + 1;
        final int maxY = Math.max(minY, level.getMaxBuildHeight() - size.getY() - 1);
        final int surfaceY = findSurfaceY(level, originX, originZ, size);

        if (surfaceY > minY)
        {
            if (random.nextDouble() < SURFACE_ARENA_CHANCE)
            {
                return Mth.clamp(surfaceY, minY, maxY);
            }

            final int depth = MIN_UNDERGROUND_DEPTH + random.nextInt(EXTRA_UNDERGROUND_DEPTH + 1);
            return Mth.clamp(surfaceY - depth, minY, maxY);
        }

        final int fallbackY = minY + VOID_FALLBACK_MIN_OFFSET + random.nextInt(VOID_FALLBACK_EXTRA_OFFSET + 1);
        return Mth.clamp(fallbackY, minY, maxY);
    }

    /**
     * Finds the highest Y-coordinate of the surface of the given level within the given bounding box.
     * The bounding box is centered at the given origin, with a size of the given Vec3i.
     * The Y-coordinate is searched in a grid pattern, with a step size of at most half the size of the bounding box in both the X and Z directions.
     * The highest Y-coordinate found is returned.
     *
     * @param level the level to search in
     * @param originX the X-coordinate of the origin of the bounding box
     * @param originZ the Z-coordinate of the origin of the bounding box
     * @param size the size of the bounding box
     * @return the highest Y-coordinate of the surface of the given level within the given bounding box
     */
    private static int findSurfaceY(@Nonnull final ServerLevel level, final int originX, final int originZ, @Nonnull final Vec3i size)
    {
        int highestY = level.getMinBuildHeight() + 1;
        final int stepX = Math.max(1, size.getX() / 4);
        final int stepZ = Math.max(1, size.getZ() / 4);

        for (int dx = 0; dx <= size.getX(); dx += stepX)
        {
            for (int dz = 0; dz <= size.getZ(); dz += stepZ)
            {
                highestY = Math.max(
                    highestY,
                    level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        originX + Math.min(dx, size.getX() - 1),
                        originZ + Math.min(dz, size.getZ() - 1)
                    )
                );
            }
        }

        return highestY;
    }

    /**
     * Forces the loading of all chunks within the given bounding box.
     * This is used to ensure that the chunks are loaded before the Exteritio boss structure is placed.
     *
     * @param level the level to force the loading of chunks in
     * @param origin the origin of the bounding box
     * @param size the size of the bounding box
     */
    private static void forceLoadTemplateChunks(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final BlockPos maxCorner = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        if (maxCorner == null) return;

        final ChunkPos minChunk = new ChunkPos(origin);
        final ChunkPos maxChunk = new ChunkPos(maxCorner);

        ChunkPos.rangeClosed(minChunk, maxChunk).forEach(chunkPos -> level.getChunk(chunkPos.x, chunkPos.z));
    }

    /**
     * Aligns a coordinate to the center of a chunk.
     * A chunk is 16x16 blocks, so the center is at the 8th block.
     * This method takes a coordinate, rounds it down to the nearest multiple of 16,
     * and then adds 8 to get the center of the chunk.
     *
     * @param coordinate the coordinate to align
     * @return the aligned coordinate
     */
    private static int alignToChunkCenter(final int coordinate)
    {
        return ((coordinate >> 4) << 4) + CHUNK_CENTER_OFFSET;
    }
}
