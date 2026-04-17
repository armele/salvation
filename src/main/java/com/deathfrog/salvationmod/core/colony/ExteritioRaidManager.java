package com.deathfrog.salvationmod.core.colony;

import java.util.Optional;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.Config;
import com.deathfrog.salvationmod.ModEntityTypes;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.blockentity.PurificationBeaconCoreBlockEntity;
import com.deathfrog.salvationmod.core.engine.CorruptionStage;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.portal.ExteritioPortalShape;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.MessageUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class ExteritioRaidManager
{
    /**
     * Corruption weights used throughout Salvation's progression systems.
     * We reuse the same ladder here so the raid bias is scaled against familiar "trivial/minor/major/extreme"
     * contribution sizes instead of an arbitrary raw number.
     */
    private static final int RAID_CORRUPTION_WEIGHT_MAJOR = 5;
    private static final int RAID_CORRUPTION_WEIGHT_EXTREME = 13;
    private static final float MIN_RAID_CHANCE_MULTIPLIER = 0.8F;
    private static final float MAX_RAID_CHANCE_MULTIPLIER = 1.2F;

    // Days between exteritio raids
    private static final int BASE_RAID_COOLDOWN_DAYS = Config.exteritioRaidCooldown.get();

    @SuppressWarnings("null")
    private static final @Nonnull ResourceLocation RAID_PORTAL_TEMPLATE = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "raid/portal1");

    private static final int RAID_PORTAL_OUTSIDE_PADDING = 8;
    private static final int RAID_PORTAL_SEARCH_DEPTH = 64;
    private static final int RAID_PORTAL_RADIUS_STEP = 8;
    private static final int RAID_PORTAL_SEARCH_ANGLES = 24;
    private static final int RAID_PORTAL_MAX_HEIGHT_VARIATION = 6;

    private static final String EXTERITIO_RAID_MESSAGE = "com.salvation.exteritioraid.spawned";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final SalvationColonyHandler handler;

    public ExteritioRaidManager(@Nonnull final SalvationColonyHandler handler)
    {
        this.handler = handler;
    }

    /**
     * Process the raids for the given colony.
     * This will check if a raid is due today, and if so, will generate a raid near the colony.
     * The raid generation is based on the highest corruption stage the world has reached.
     * The chance of a raid spawning is based on that stage's dailyRaidSpawnChance, then adjusted slightly
     * by the colony's recent net corruption over the rolling mitigation window.
     * The cooldown between raids is based on that stage's ordinal.
     * The cooldown is calculated as BASE_RAID_COOLDOWN_DAYS - stage.ordinal().
     * This means that the higher the corruption stage, the shorter the cooldown.
     * The raid generation is done by randomly selecting a location near the colony.
     * The location is chosen to be outside the colony's boundaries.
     *
     * @param colony the colony to process the raids for
     */
    public void processRaids(@Nonnull final IColony colony)
    {
        final Level level = colony.getWorld();

        if ((!(level instanceof ServerLevel serverLevel)) || level.isClientSide()) return;

        if (BASE_RAID_COOLDOWN_DAYS < 0) return;

        final CorruptionStage stage = SalvationManager.maxStageForLevel(serverLevel);

        if (colony.getRaiderManager().willRaidTonight()) return;

        final int day = colony.getDay();

        if (day <= handler.state.getLastExteritioRaidDayCheck()) return;

        handler.state.setLastExteritioRaidDayCheck(day);

        final int cooldown = BASE_RAID_COOLDOWN_DAYS - stage.ordinal();

        if (serverLevel.getGameTime() <= handler.state.getLastExteritioRaidTick() + (cooldown * PurificationBeaconCoreBlockEntity.DEFAULT_DAY_LENGTH)) return;

        final float rand = serverLevel.random.nextFloat();
        final float raidChance = Mth.clamp(stage.getDailyRaidSpawnChance() * computeRaidChanceMultiplier(), 0.0F, 1.0F);

        if (rand > raidChance) return;

        final RaidPortalPlacement placement = placeRaidPortal(colony, serverLevel);
        if (placement != null)
        {
            MessageUtils.format(
                EXTERITIO_RAID_MESSAGE,
                BlockPosUtil.calcDirection(colony.getCenter(), placement.center()).getLongText()
            ).sendTo(colony).forAllPlayers();
            handler.state.setLastExteritioRaidTick(serverLevel.getGameTime());
        }

        handler.data.updateColonyState(handler.colonyKey, handler.state);
    }

    /**
     * Tries to place the raid portal template near the given colony.
     *
     * @param colony the colony to place the raid portal near
     * @param serverLevel the level to place the raid portal in
     * @return the placement information if the raid portal was successfully placed, otherwise null
     */
    public RaidPortalPlacement placeRaidPortal(@Nonnull final IColony colony, @Nonnull final ServerLevel serverLevel)
    {
        final Optional<StructureTemplate> templateOptional = serverLevel.getStructureManager().get(RAID_PORTAL_TEMPLATE);
        if (templateOptional.isEmpty())
        {
            LOGGER.error("Colony {} raid skipped because template {} could not be loaded.", colony.getID(), RAID_PORTAL_TEMPLATE);
            return null;
        }

        final StructureTemplate template = templateOptional.get();
        final Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0)
        {
            LOGGER.error("Colony {} raid skipped because template {} has invalid size {}.", colony.getID(), RAID_PORTAL_TEMPLATE, size);
            return null;
        }

        final RaidPortalPlacement placement = findRaidPortalPlacement(colony, serverLevel, size);
        if (placement == null)
        {
            LOGGER.warn("Colony {} raid skipped because no valid placement was found for {} near {}.", colony.getID(), RAID_PORTAL_TEMPLATE, colony.getCenter());
            return null;
        }

        final BlockPos origin = placement.origin();
        if (origin == null)
        {
            LOGGER.warn("Colony {} raid skipped because no valid placement origin was found for {} near {}.", colony.getID(), RAID_PORTAL_TEMPLATE, colony.getCenter());
            return null;
        }

        final Vec3i placementSize = placement.size();
        if (placementSize == null)
        {
            LOGGER.warn("Colony {} raid skipped because no valid placement size was found for {} near {}.", colony.getID(), RAID_PORTAL_TEMPLATE, colony.getCenter());
            return null;
        }

        forceLoadTemplateChunks(serverLevel, origin, placementSize);

        final StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(NullnessBridge.assumeNonnull(placement.rotation()))
            .addProcessor(NullnessBridge.assumeNonnull(JigsawReplacementProcessor.INSTANCE));

        final long placementSeed = serverLevel.getSeed() ^ origin.asLong() ^ colony.getCenter().asLong();
        final boolean placed = template.placeInWorld(
            serverLevel,
            origin,
            origin,
            NullnessBridge.assumeNonnull(settings),
            NullnessBridge.assumeNonnull(StructureBlockEntity.createRandom(placementSeed)),
            2
        );

        if (!placed)
        {
            LOGGER.error("Colony {} raid template {} failed to place at {}.", colony.getID(), RAID_PORTAL_TEMPLATE, origin);
            return null;
        }

        if (!activatePlacedRaidPortal(serverLevel, origin, placementSize))
        {
            LOGGER.warn("Colony {} raid portal structure {} was placed at {}, but no valid portal frame was found to activate.", colony.getID(), RAID_PORTAL_TEMPLATE, origin);
        }

        spawnRaidCreatures(serverLevel, origin, placementSize);

        LOGGER.info("Placed colony raid portal {} for colony {} at {} with rotation {}.", RAID_PORTAL_TEMPLATE, colony.getID(), origin, placement.rotation());
        return placement;
    }

    /**
     * Computes a modest per-colony raid chance modifier from recent net corruption.
     * Colonies that have been net-neutral or net-purifying over the rolling window bottom out at 0.8x,
     * while colonies with sustained net corruption trend up to 1.2x.
     * <p>
     * "Significant" net corruption is intentionally anchored to Salvation's existing 1/2/5/13 weight ladder:
     * roughly one extreme-weight net contribution per rolling-window day, with a floor of two extreme events
     * or one major event per day for very short windows.
     *
     * @return a clamped multiplier in the range [0.8, 1.2]
     */
    private float computeRaidChanceMultiplier()
    {
        final int rollingWindowDays = Math.max(1, Config.colonyMitigationRollingDays.get());
        final long currentDay = currentRollingMitigationDay();
        final long rollingCorruption = handler.state.getRollingCorruptionContribution(currentDay, rollingWindowDays);
        final long rollingPurification = handler.state.getRollingPurificationCredits(currentDay, rollingWindowDays);
        final long rollingNetCorruption = Math.max(0L, rollingCorruption - rollingPurification);

        if (rollingNetCorruption <= 0L)
        {
            return MIN_RAID_CHANCE_MULTIPLIER;
        }

        final long significantNetCorruption = Math.max(
            RAID_CORRUPTION_WEIGHT_EXTREME * 2L,
            Math.max(
                RAID_CORRUPTION_WEIGHT_MAJOR * (long) rollingWindowDays,
                RAID_CORRUPTION_WEIGHT_EXTREME * (long) rollingWindowDays));

        final float normalized = Mth.clamp((float) rollingNetCorruption / (float) significantNetCorruption, 0.0F, 1.0F);
        return Mth.lerp(normalized, 1.0F, MAX_RAID_CHANCE_MULTIPLIER);
    }

    private long currentRollingMitigationDay()
    {
        return Math.max(0L, handler.level.getGameTime() / Math.max(1L, PurificationBeaconCoreBlockEntity.DEFAULT_DAY_LENGTH));
    }

    private boolean activatePlacedRaidPortal(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final BlockPos maxCorner = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        if (maxCorner == null) return false;

        for (final BlockPos pos : BlockPos.betweenClosed(origin, maxCorner))
        {
            final Optional<ExteritioPortalShape> portalShape = ExteritioPortalShape.findPortalShape(
                serverLevel,
                pos.immutable(),
                ExteritioPortalShape::isValid,
                Direction.Axis.X
            );

            if (portalShape.isPresent())
            {
                portalShape.get().createPortalBlocks();
                return true;
            }
        }

        return false;
    }

    private void spawnRaidCreatures(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final RandomSource random = serverLevel.getRandom();
        final int stingerCount = 1 + random.nextInt(4);
        final int mawCount = 1 + random.nextInt(2);
        final int observerCount = 1 + random.nextInt(1);
        final BlockPos center = origin.offset(size.getX() / 2, 0, size.getZ() / 2);

        if (center == null) return;

        for (int i = 0; i < stingerCount; i++)
        {
            spawnRaidMob(serverLevel, NullnessBridge.assumeNonnull(ModEntityTypes.VORAXIAN_STINGER.get()), center, false);
        }

        for (int i = 0; i < mawCount; i++)
        {
            spawnRaidMob(serverLevel, NullnessBridge.assumeNonnull(ModEntityTypes.VORAXIAN_MAW.get()), center, true);
        }

        for (int i = 0; i < observerCount; i++)
        {
            spawnRaidMob(serverLevel, NullnessBridge.assumeNonnull(ModEntityTypes.VORAXIAN_OBSERVER.get()), center, true);
        }
    }

    @SuppressWarnings({"deprecation", "null"})
    private void spawnRaidMob(
        @Nonnull final ServerLevel serverLevel,
        @Nonnull final EntityType<? extends Mob> entityType,
        @Nonnull final BlockPos center,
        final boolean airborne)
    {
        final Mob mob = entityType.create(serverLevel);
        if (mob == null)
        {
            return;
        }

        final RandomSource random = serverLevel.getRandom();
        final double angle = random.nextDouble() * (Math.PI * 2.0D);
        final double radius = 2.0D + random.nextDouble() * 4.0D;
        final int spawnX = Mth.floor(center.getX() + 0.5D + Math.cos(angle) * radius);
        final int spawnZ = Mth.floor(center.getZ() + 0.5D + Math.sin(angle) * radius);
        final int surfaceY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnX, spawnZ);
        final double spawnY = airborne ? surfaceY + 1.5D + random.nextDouble() * 2.0D : surfaceY;
        final float yaw = random.nextFloat() * 360.0F;

        mob.moveTo(spawnX + 0.5D, spawnY, spawnZ + 0.5D, yaw, 0.0F);
        mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
        serverLevel.addFreshEntity(mob);
    }

    private RaidPortalPlacement findRaidPortalPlacement(@Nonnull final IColony colony, @Nonnull final ServerLevel serverLevel, @Nonnull final Vec3i templateSize)
    {
        final RandomSource random = serverLevel.getRandom();
        final double angleOffset = random.nextDouble() * (Math.PI * 2.0D);
        final int baseRadius = estimateColonyRadius(colony);
        final int halfSpan = Math.max(templateSize.getX(), templateSize.getZ()) / 2;

        for (int radiusOffset = 0; radiusOffset <= RAID_PORTAL_SEARCH_DEPTH; radiusOffset += RAID_PORTAL_RADIUS_STEP)
        {
            final int radius = baseRadius + halfSpan + RAID_PORTAL_OUTSIDE_PADDING + radiusOffset;

            for (int angleIndex = 0; angleIndex < RAID_PORTAL_SEARCH_ANGLES; angleIndex++)
            {
                final double angle = angleOffset + ((Math.PI * 2.0D) * angleIndex / RAID_PORTAL_SEARCH_ANGLES);
                final int centerX = handler.colonyCenter.getX() + Mth.floor(Math.cos(angle) * radius);
                final int centerZ = handler.colonyCenter.getZ() + Mth.floor(Math.sin(angle) * radius);
                final Rotation rotation = rotationFacingColony(centerX - handler.colonyCenter.getX(), centerZ - handler.colonyCenter.getZ());
                final Vec3i rotatedSize = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                    ? new Vec3i(templateSize.getZ(), templateSize.getY(), templateSize.getX())
                    : templateSize;
                final int originX = centerX - (rotatedSize.getX() / 2);
                final int originZ = centerZ - (rotatedSize.getZ() / 2);
                final int originY = findSurfaceY(serverLevel, originX, originZ, rotatedSize);
                final BlockPos origin = new BlockPos(originX, originY, originZ);

                if (isValidRaidPortalPlacement(colony, serverLevel, origin, rotatedSize))
                {
                    return new RaidPortalPlacement(origin, rotation, rotatedSize);
                }
            }
        }

        return null;
    }

    private int estimateColonyRadius(@Nonnull final IColony colony)
    {
        int maxDistanceSq = 32 * 32;

        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building != null)
            {
                maxDistanceSq = Math.max(maxDistanceSq, (int) colony.getDistanceSquared(building.getPosition()));
            }
        }

        return Mth.ceil(Math.sqrt(maxDistanceSq));
    }

    private boolean isValidRaidPortalPlacement(@Nonnull final IColony colony, @Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        if (!isWithinWorldBorder(serverLevel, origin, size))
        {
            return false;
        }

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        final int stepX = Math.max(1, size.getX() / 3);
        final int stepZ = Math.max(1, size.getZ() / 3);

        for (int dx = 0; dx < size.getX(); dx += stepX)
        {
            for (int dz = 0; dz < size.getZ(); dz += stepZ)
            {
                final int sampleX = origin.getX() + Math.min(dx, size.getX() - 1);
                final int sampleZ = origin.getZ() + Math.min(dz, size.getZ() - 1);
                final int sampleY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);
                final BlockPos samplePos = new BlockPos(sampleX, sampleY, sampleZ);

                if (colony.isCoordInColony(serverLevel, samplePos))
                {
                    return false;
                }

                minY = Math.min(minY, sampleY);
                maxY = Math.max(maxY, sampleY);
            }
        }

        return maxY - minY <= RAID_PORTAL_MAX_HEIGHT_VARIATION;
    }

    private boolean isWithinWorldBorder(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final BlockPos maxCorner = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        if (maxCorner == null) return false;

        return serverLevel.getWorldBorder().isWithinBounds(origin)
            && serverLevel.getWorldBorder().isWithinBounds(maxCorner)
            && serverLevel.getWorldBorder().isWithinBounds(new BlockPos(origin.getX(), origin.getY(), maxCorner.getZ()))
            && serverLevel.getWorldBorder().isWithinBounds(new BlockPos(maxCorner.getX(), origin.getY(), origin.getZ()));
    }

    private int findSurfaceY(@Nonnull final ServerLevel level, final int originX, final int originZ, @Nonnull final Vec3i size)
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

        return Math.max(level.getMinBuildHeight(), highestY - 1);
    }

    private void forceLoadTemplateChunks(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final BlockPos maxCorner = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        if (maxCorner == null) return;

        final ChunkPos minChunk = new ChunkPos(origin);
        final ChunkPos maxChunk = new ChunkPos(maxCorner);

        ChunkPos.rangeClosed(minChunk, maxChunk).forEach(chunkPos -> level.getChunk(chunkPos.x, chunkPos.z));
    }

    private Rotation rotationFacingColony(final int deltaX, final int deltaZ)
    {
        final Direction directionToColony = Direction.getNearest(-deltaX, 0, -deltaZ);
        return switch (directionToColony)
        {
            case WEST -> Rotation.CLOCKWISE_180;
            case SOUTH -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.COUNTERCLOCKWISE_90;
            case EAST -> Rotation.NONE;
            default -> Rotation.NONE;
        };
    }

    public record RaidPortalPlacement(BlockPos origin, Rotation rotation, Vec3i size)
    {
        public BlockPos center()
        {
            return origin.offset(size.getX() / 2, 0, size.getZ() / 2);
        }
    }
}
