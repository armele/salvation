package com.deathfrog.salvationmod.core.portal;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModDimensions;
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
    private static final int BOSS_SPAWN_HEIGHT_OFFSET = 6;
    private static final long BOSS_SPAWN_RETRY_COOLDOWN_TICKS = 200L;

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

        if (locatorTarget == null)
        {
            SalvationMod.LOGGER.error("Unable to find suitable locator target for Exteritio boss structure {}", VORAXIAN_BASE_TEMPLATE);
            return;
        }

        data.setVoraxianBaseLocation(locatorTarget);
        SalvationMod.LOGGER.info("Placed Exteritio boss structure {} at {} (locator target: {})", VORAXIAN_BASE_TEMPLATE, origin, locatorTarget);
    }

    private static void ensureBossPresence(@Nonnull final ServerLevel level, @Nonnull final SalvationSavedData data)
    {
        final BlockPos center = data.getVoraxianBaseLocation();
        if (center == null || !isBossArenaEntityTicking(level, center))
        {
            return;
        }

        if (hasAliveBoss(level, data))
        {
            return;
        }

        final long gameTime = level.getGameTime();
        if (gameTime - data.getVoraxianOverlordLastSpawnGameTime() < BOSS_SPAWN_RETRY_COOLDOWN_TICKS)
        {
            return;
        }

        if (!data.hasVoraxianOverlordBeenSlain())
        {
            spawnOverlord(level, data);
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
     * Spawns a Voraxian Overlord entity at the Exteritio boss arena location.
     * The entity will be spawned at the highest solid block above the arena location, with an offset of
     * BOSS_SPAWN_HEIGHT_OFFSET blocks above the arena location's y-coordinate.
     * If the entity is unable to be spawned, an error is logged.
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
            return;
        }

        final int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
        final BlockPos spawnPos = new BlockPos(center.getX(), Math.max(center.getY() + BOSS_SPAWN_HEIGHT_OFFSET, surfaceY + 2), center.getZ());
        final VoraxianOverlordEntity overlord = ModEntityTypes.VORAXIAN_OVERLORD.get().create(level);
        if (overlord == null)
        {
            SalvationMod.LOGGER.error("Unable to create Voraxian Overlord entity in dimension {} for Exteritio boss arena at {}",
                getDimensionName(level), spawnPos);
            return;
        }

        overlord.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        overlord.setPersistenceRequired();
        overlord.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.STRUCTURE, null);

        if (level.addFreshEntity(overlord))
        {
            data.setVoraxianOverlordSlain(false);
            data.setVoraxianOverlordUuid(overlord.getUUID());
            data.setVoraxianOverlordLastSpawnGameTime(level.getGameTime());
            // SalvationMod.LOGGER.info("Spawned Voraxian Overlord in dimension {} at {}", getDimensionName(level), spawnPos);
        }
        else
        {
            data.setVoraxianOverlordLastSpawnGameTime(level.getGameTime());
            SalvationMod.LOGGER.error("Failed to add Voraxian Overlord entity in dimension {} to Exteritio boss arena at {}",
                getDimensionName(level), spawnPos);
        }
    }

    private static boolean isBossArenaEntityTicking(@Nonnull final ServerLevel level, @Nonnull final BlockPos center)
    {
        return level.isPositionEntityTicking(center);
    }

    private static String getDimensionName(@Nonnull final ServerLevel level)
    {
        return level.dimension().location().toString();
    }

    /**
     * Finds a suitable origin for spawning the Exteritio boss structure in the given level.
     * The origin is chosen to be at least {@link #MIN_RADIUS} blocks away from the nearest chunk border,
     * and at most {@link #EXTRA_RADIUS} blocks away from the nearest chunk border.
     * The origin is also aligned to the nearest chunk center.
     * The origin's Y-coordinate is chosen to be at the surface of the level at the origin's X and Z coordinates.
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
        final int originY = findSurfaceY(level, originX, originZ, size);

        return new BlockPos(originX, originY, originZ);
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
