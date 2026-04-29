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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

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
    private static final int RAID_DARTER_WATER_SEARCH_RADIUS = 12;
    private static final int RAID_DARTER_WATER_SEARCH_DEPTH = 10;
    private static final int RAID_DARTER_SPAWN_ATTEMPTS = 48;

    private static final String EXTERITIO_RAID_MESSAGE = "com.salvation.exteritioraid.spawned";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final SalvationColonyHandler handler;

    /**
     * Creates a raid manager bound to the given colony handler.
     *
     * @param handler the Salvation colony handler that owns raid state and colony context
     */
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

        BlockPos center = placement.center();

        if (center != null) 
        {
            signalRaidLocation(serverLevel, center);
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

    /**
     * Signal the raid location with a lightning strike.
     * 
     * @param serverLevel
     * @param center
     */
    private void signalRaidLocation(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos center)
    {
        final LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (lightningBolt == null)
        {
            return;
        }

        Vec3 vecCenter = Vec3.atBottomCenterOf(center);

        if (vecCenter != null)
        {
            lightningBolt.moveTo(vecCenter);
        }

        lightningBolt.setVisualOnly(true);
        serverLevel.addFreshEntity(lightningBolt);
    }

    /**
     * Converts the handler level game time into the rolling mitigation day index.
     *
     * @return the current mitigation day, clamped to zero or greater
     */
    private long currentRollingMitigationDay()
    {
        return Math.max(0L, handler.level.getGameTime() / Math.max(1L, PurificationBeaconCoreBlockEntity.DEFAULT_DAY_LENGTH));
    }

    /**
     * Activate the placed raid portal.
     * 
     * @param serverLevel the level containing the placed raid portal template
     * @param origin the lower corner of the placed template
     * @param size the placed template size after rotation
     * @return true if a valid portal frame was found and activated, otherwise false
     */
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

    /**
     * Spawns an initial set of creatures when a raid event happens.
     * Darters are added only when a nearby water column can support them.
     * 
     * @param serverLevel the level where the raid portal was placed
     * @param origin the lower corner of the placed portal template
     * @param size the placed template size after rotation
     */
    private void spawnRaidCreatures(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final RandomSource random = serverLevel.getRandom();
        final int stingerCount = 1 + random.nextInt(4);
        final int mawCount = 1 + random.nextInt(2);
        final int observerCount = 1 + random.nextInt(1);
        final int darterCount = 1 + random.nextInt(3);
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

        if (hasNearbyDarterWater(serverLevel, center))
        {
            for (int i = 0; i < darterCount; i++)
            {
                spawnRaidDarter(serverLevel, center);
            }
        }
    }

    /**
     * Spawns a raid mob around the given center point.
     * Ground mobs are placed at the surface height, while airborne mobs are placed slightly above it.
     *
     * @param serverLevel the level to spawn the mob in
     * @param entityType the mob type to spawn
     * @param center the center point used for radial spawn placement
     * @param airborne whether to offset the mob above the surface
     */
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

    /**
     * Spawns a Voraxian Darter in a nearby valid water column, if one can be found.
     *
     * @param serverLevel the level to spawn the Darter in
     * @param center the raid center used as the search origin
     */
    @SuppressWarnings({"deprecation", "null"})
    private void spawnRaidDarter(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos center)
    {
        final BlockPos spawnPos = findNearbyDarterWater(serverLevel, center);
        if (spawnPos == null)
        {
            return;
        }

        final Mob mob = ModEntityTypes.VORAXIAN_DARTER.get().create(serverLevel);
        if (mob == null)
        {
            return;
        }

        final RandomSource random = serverLevel.getRandom();
        final float yaw = random.nextFloat() * 360.0F;

        mob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, yaw, 0.0F);
        if (!serverLevel.noCollision(mob) || !serverLevel.getWorldBorder().isWithinBounds(spawnPos))
        {
            return;
        }

        mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null);
        serverLevel.addFreshEntity(mob);
    }

    /**
     * Checks whether there is a nearby water column suitable for raid Darter spawns.
     *
     * @param serverLevel the level to inspect
     * @param center the raid center used as the search origin
     * @return true if a suitable water column is found, otherwise false
     */
    private boolean hasNearbyDarterWater(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos center)
    {
        return findNearbyDarterWater(serverLevel, center) != null;
    }

    /**
     * Finds a nearby Darter spawn position.
     * The search starts with random samples for variation, then falls back to a full square scan.
     *
     * @param serverLevel the level to search
     * @param center the raid center used as the search origin
     * @return a valid Darter water position, or null if none is found
     */
    @SuppressWarnings("null")
    private BlockPos findNearbyDarterWater(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos center)
    {
        final RandomSource random = serverLevel.getRandom();

        for (int attempt = 0; attempt < RAID_DARTER_SPAWN_ATTEMPTS; attempt++)
        {
            final int dx = random.nextInt((RAID_DARTER_WATER_SEARCH_RADIUS * 2) + 1) - RAID_DARTER_WATER_SEARCH_RADIUS;
            final int dz = random.nextInt((RAID_DARTER_WATER_SEARCH_RADIUS * 2) + 1) - RAID_DARTER_WATER_SEARCH_RADIUS;
            final BlockPos spawnPos = findDarterWaterColumn(serverLevel, center.offset(dx, 0, dz));
            if (spawnPos != null)
            {
                return spawnPos;
            }
        }

        for (int dx = -RAID_DARTER_WATER_SEARCH_RADIUS; dx <= RAID_DARTER_WATER_SEARCH_RADIUS; dx++)
        {
            for (int dz = -RAID_DARTER_WATER_SEARCH_RADIUS; dz <= RAID_DARTER_WATER_SEARCH_RADIUS; dz++)
            {
                final BlockPos spawnPos = findDarterWaterColumn(serverLevel, center.offset(dx, 0, dz));
                if (spawnPos != null)
                {
                    return spawnPos;
                }
            }
        }

        return null;
    }

    /**
     * Searches downward from the surface at a sample column for a two-block tagged-water space.
     *
     * @param serverLevel the level to inspect
     * @param sample the x/z column to search
     * @return the lower block of a valid Darter water column, or null if none is found
     */
    private BlockPos findDarterWaterColumn(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos sample)
    {
        final int surfaceY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample.getX(), sample.getZ());
        final int minY = Math.max(serverLevel.getMinBuildHeight() + 1, surfaceY - RAID_DARTER_WATER_SEARCH_DEPTH);

        for (int y = surfaceY; y >= minY; y--)
        {
            final BlockPos pos = new BlockPos(sample.getX(), y, sample.getZ());
            if (isDarterWater(serverLevel, pos))
            {
                return pos;
            }
        }

        return null;
    }

    /**
     * Checks whether a position and the block above it both contain tagged water.
     *
     * @param serverLevel the level to inspect
     * @param pos the lower position of the potential water column
     * @return true when the Darter can be placed in a tagged-water column
     */
    @SuppressWarnings("null")
    private boolean isDarterWater(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos pos)
    {
        final FluidState fluid = serverLevel.getFluidState(pos);
        final FluidState aboveFluid = serverLevel.getFluidState(pos.above());
        return fluid.is(FluidTags.WATER) && aboveFluid.is(FluidTags.WATER);
    }

    /**
     * Searches for a valid raid portal placement around the colony.
     * Candidate locations are rotated to face the colony and rejected when they are inside the colony,
     * outside the world border, or too uneven for the template.
     *
     * @param colony the colony the raid targets
     * @param serverLevel the level where the raid portal will be placed
     * @param templateSize the unrotated size of the raid portal template
     * @return placement data for the first valid candidate, or null if none is found
     */
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

    /**
     * Estimates the colony radius from its center to its farthest known building.
     *
     * @param colony the colony to measure
     * @return a conservative radius used to start the raid portal search outside the colony
     */
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

    /**
     * Validates a candidate raid portal placement.
     * The footprint must stay outside the colony, stay inside the world border, and have limited height variation.
     *
     * @param colony the colony the raid targets
     * @param serverLevel the level containing the candidate footprint
     * @param origin the lower corner of the candidate placement
     * @param size the rotated template size for the candidate placement
     * @return true if the footprint can safely receive the raid portal template
     */
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

    /**
     * Checks whether all horizontal corners of a template footprint are inside the world border.
     *
     * @param serverLevel the level whose world border is checked
     * @param origin the lower corner of the candidate placement
     * @param size the rotated template size for the candidate placement
     * @return true if the footprint corners are within the world border
     */
    private boolean isWithinWorldBorder(@Nonnull final ServerLevel serverLevel, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final BlockPos maxCorner = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        if (maxCorner == null) return false;

        return serverLevel.getWorldBorder().isWithinBounds(origin)
            && serverLevel.getWorldBorder().isWithinBounds(maxCorner)
            && serverLevel.getWorldBorder().isWithinBounds(new BlockPos(origin.getX(), origin.getY(), maxCorner.getZ()))
            && serverLevel.getWorldBorder().isWithinBounds(new BlockPos(maxCorner.getX(), origin.getY(), origin.getZ()));
    }

    /**
     * Finds the template origin Y by sampling the terrain under the planned footprint.
     *
     * @param level the level to sample
     * @param originX the candidate footprint minimum x coordinate
     * @param originZ the candidate footprint minimum z coordinate
     * @param size the rotated template size for the candidate placement
     * @return the Y coordinate used as the template origin
     */
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

    /**
     * Loads every chunk touched by the raid portal template before placement.
     *
     * @param level the level where the template will be placed
     * @param origin the lower corner of the template placement
     * @param size the rotated template size for the placement
     */
    private void forceLoadTemplateChunks(@Nonnull final ServerLevel level, @Nonnull final BlockPos origin, @Nonnull final Vec3i size)
    {
        final BlockPos maxCorner = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

        if (maxCorner == null) return;

        final ChunkPos minChunk = new ChunkPos(origin);
        final ChunkPos maxChunk = new ChunkPos(maxCorner);

        ChunkPos.rangeClosed(minChunk, maxChunk).forEach(chunkPos -> level.getChunk(chunkPos.x, chunkPos.z));
    }

    /**
     * Chooses the template rotation that points the raid portal toward the colony center.
     *
     * @param deltaX the candidate center x offset from the colony center
     * @param deltaZ the candidate center z offset from the colony center
     * @return the rotation that faces the candidate placement back toward the colony
     */
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

    /**
     * Placement data for a raid portal template.
     *
     * @param origin the lower corner where the template should be placed
     * @param rotation the rotation applied during placement
     * @param size the template size after applying rotation
     */
    public record RaidPortalPlacement(BlockPos origin, Rotation rotation, Vec3i size)
    {
        /**
         * Calculates the horizontal center of the placed template.
         *
         * @return the center position at the template origin Y level
         */
        public BlockPos center()
        {
            return origin.offset(size.getX() / 2, 0, size.getZ() / 2);
        }
    }
}
