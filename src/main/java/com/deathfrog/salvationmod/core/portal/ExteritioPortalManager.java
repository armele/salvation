package com.deathfrog.salvationmod.core.portal;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.ModDimensions;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.blocks.ExteritioPortalBlock;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;

public final class ExteritioPortalManager
{
    private static final int SEARCH_RADIUS = 64;
    private static final int PLACEMENT_RADIUS = 16;
    private static final int PORTAL_SURFACE_SCAN_DEPTH = 6;

    private ExteritioPortalManager()
    {
    }

    /**
     * Attempts to spawn a portal shape at the given position on the given axis.
     * If the level is client-side, an empty optional is returned.
     * Otherwise, this method will search for an empty portal shape at the given position on the given axis, and
     * if an empty portal shape is found, it will be created and returned.
     * If no empty portal shape is found, an empty optional is returned.
     *
     * @param level the level accessor to use for block lookups
     * @param clickedPos the position to search for an empty portal shape
     * @param axis the axis to construct the primary portal shape on
     * @return an optional containing the created portal shape, or an empty optional if unable to create portal
     */
    @SuppressWarnings("null")
    public static Optional<ExteritioPortalShape> trySpawnPortal(final Level level, final BlockPos clickedPos, final Direction.Axis axis)
    {
        if (level.isClientSide())
        {
            return Optional.empty();
        }

        final Optional<ExteritioPortalShape> shape = ExteritioPortalShape.findEmptyPortalShape(level, clickedPos, axis);
        shape.ifPresent(ExteritioPortalShape::createPortalBlocks);
        return shape;
    }

    /**
     * Gets the dimension transition for the given entity at the given portal position.
     * If there is an existing portal near the entity's position in the target level, the entity is transitioned to that portal.
     * If there is not an existing portal near the entity's position in the target level, a new portal is spawned near the entity's position.
     * The entity is transitioned to the spawned portal.
     *
     * @param sourceLevel the source level
     * @param entity the entity
     * @param portalPos the position of the portal block
     * @return the dimension transition for the given entity at the given portal position, or null if unable to create portal
     */
    @Nullable
    public static DimensionTransition getPortalDestination(final ServerLevel sourceLevel, final Entity entity, final @Nonnull BlockPos portalPos)
    {
        final ResourceKey<Level> targetKey = sourceLevel.dimension() == ModDimensions.EXTERITIO ? Level.OVERWORLD : ModDimensions.EXTERITIO;

        if (targetKey == null)
        {
            return null;
        }

        final ServerLevel targetLevel = sourceLevel.getServer().getLevel(targetKey);
        if (targetLevel == null)
        {
            return null;
        }

        final WorldBorder worldBorder = targetLevel.getWorldBorder();
        final BlockPos idealTargetPos = worldBorder.clampToBounds(entity.getX(), entity.getY(), entity.getZ());
        final Direction.Axis preferredAxis = sourceLevel.getBlockState(portalPos)
            .getOptionalValue(ExteritioPortalBlock.AXIS)
            .orElse(Direction.Axis.X);

        if (idealTargetPos == null || preferredAxis == null) return null;

        final Optional<LocatedPortal> existingPortal = findClosestPortal(targetLevel, idealTargetPos, SEARCH_RADIUS, preferredAxis);

        final BlockUtil.FoundRectangle destinationRectangle;
        final DimensionTransition.PostDimensionTransition postTransition;
        if (existingPortal.isPresent())
        {
            final LocatedPortal foundPortal = existingPortal.get();
            final BlockPos foundPos = foundPortal.ticketPos();

            destinationRectangle = foundPortal.rectangle();
            postTransition = DimensionTransition.PLAY_PORTAL_SOUND.then(entityInTarget -> entityInTarget.placePortalTicket(foundPos));
        }
        else
        {
            final PortalCreationDiagnostics diagnostics = new PortalCreationDiagnostics();
            final Optional<BlockUtil.FoundRectangle> createdPortal = createPortalNear(targetLevel, idealTargetPos, preferredAxis, diagnostics);

            if (createdPortal.isEmpty())
            {
                SalvationMod.LOGGER.error(
                    "Unable to create Exteritio portal near {} in {} with axis {}. {}",
                    idealTargetPos,
                    targetLevel.dimension().location(),
                    preferredAxis,
                    diagnostics.describe()
                );
                return null;
            }

            destinationRectangle = createdPortal.get();
            postTransition = DimensionTransition.PLAY_PORTAL_SOUND.then(NullnessBridge.assumeNonnull(DimensionTransition.PLACE_PORTAL_TICKET));
        }

        if (postTransition == null) 
        {
            SalvationMod.LOGGER.error("Unable to create Exteritio portal near {} - post transition is null.", idealTargetPos);
            return null;
        }

        return createDimensionTransition(entity, portalPos, destinationRectangle, targetLevel, postTransition);
    }

    /**
     * Gets the existing portal rectangle at the given position.
     * The portal rectangle is the largest rectangle of portal blocks centered at the given position, aligned with the axis of the portal block at the given position.
     * If the portal block at the given position has no axis, or if there is no portal block at the given position, null is returned.
     *
     * @param level the level accessor to use for block lookups
     * @param pos the position of the portal block
     * @return the existing portal rectangle at the given position, or null if unable to find portal rectangle
     */
    @Nullable
    private static BlockUtil.FoundRectangle getExistingPortalRectangle(final ServerLevel level, final @Nonnull BlockPos pos)
    {
        final BlockState state = level.getBlockState(pos);
        final Direction.Axis axis = state.getOptionalValue(NullnessBridge.assumeNonnull(BlockStateProperties.HORIZONTAL_AXIS)).orElse(Direction.Axis.X);

        if (axis == null)
        {
            return null;
        }

        return BlockUtil.getLargestRectangleAround(
            pos,
            axis,
            ExteritioPortalShape.MAX_WIDTH,
            Direction.Axis.Y,
            ExteritioPortalShape.MAX_HEIGHT,
            checkPos -> checkPos != null && level.getBlockState(checkPos).is(NullnessBridge.assumeNonnull(ModBlocks.EXTERITIO_PORTAL.get()))
        );
    }

    /**
     * Finds the closest portal position to the given ideal position within the given radius.
     * The closest portal position is the position of a portal block that is closest to the ideal position.
     * If there is no portal block within the given radius, null is returned.
     *
     * @param level the level accessor to use for block lookups
     * @param idealPos the ideal position to search for a portal
     * @param radius the radius to search for a portal
     * @return the closest portal position to the given ideal position within the given radius, or null if unable to find portal
     */
    @SuppressWarnings("null")
    private static Optional<LocatedPortal> findClosestPortal(
        final ServerLevel level,
        final BlockPos idealPos,
        final int radius,
        final Direction.Axis preferredAxis)
    {
        final Optional<LocatedPortal> nearbyPortalShape = findNearbyPortalShape(level, idealPos, radius, preferredAxis);
        if (nearbyPortalShape.isPresent())
        {
            return nearbyPortalShape;
        }

        return findClosestPortalPosition(level, idealPos, radius)
            .flatMap(pos -> Optional.ofNullable(getExistingPortalRectangle(level, pos))
                .map(rectangle -> new LocatedPortal(rectangle, pos)));
    }

    /**
     * Finds the closest portal block to the given ideal position within the given radius.
     * The closest portal block is the block that is closest to the ideal position.
     * If there is no portal block within the given radius, an empty optional is returned.
     *
     * @param level the level accessor to use for block lookups
     * @param idealPos the ideal position to search for a portal block
     * @param radius the radius to search for a portal block
     * @return the closest portal block to the given ideal position within the given radius, or an empty optional if unable to find portal block
     */
    private static Optional<BlockPos> findClosestPortalPosition(final ServerLevel level, final BlockPos idealPos, final int radius)
    {
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                final int x = idealPos.getX() + dx;
                final int z = idealPos.getZ() + dz;
                for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--)
                {
                    final BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).is(NullnessBridge.assumeNonnull(ModBlocks.EXTERITIO_PORTAL.get())))
                    {
                        continue;
                    }

                    final double distance = pos.distSqr(idealPos);
                    if (distance < bestDistance)
                    {
                        bestDistance = distance;
                        bestPos = pos.immutable();
                    }
                }
            }
        }

        return Optional.ofNullable(bestPos);
    }

    /**
     * Finds a portal shape that is nearby to the given ideal position.
     * The search is performed in a square area of the given radius around the ideal position.
     * The search is performed in a vertical column, starting from the surface of the world and going down.
     * The search will terminate as soon as a portal shape is found, or when the entire column has been searched.
     * If a portal shape is found, it is returned as a LocatedPortal, along with its position.
     * If no portal shape is found, an empty optional is returned.
     *
     * @param level the level accessor to use for block lookups
     * @param idealPos the ideal position to search for a portal shape
     * @param radius the radius to search for a portal shape
     * @param preferredAxis the axis to construct the primary portal shape on
     * @return an optional containing a portal shape that is nearby to the given ideal position, or an empty optional if unable to find portal shape
     */
    @SuppressWarnings("null")
    private static Optional<LocatedPortal> findNearbyPortalShape(
        final ServerLevel level,
        final BlockPos idealPos,
        final int radius,
        final Direction.Axis preferredAxis)
    {
        final int minY = level.getMinBuildHeight() + 1;
        final int maxY = level.getMaxBuildHeight() - 1;

        BlockPos idealY = idealPos.atY(0);

        if (idealY == null) return Optional.empty();

        for (final BlockPos columnPos : BlockPos.withinManhattanStream(idealY, radius, 0, radius)
            .map(BlockPos::immutable)
            .sorted(Comparator.comparingDouble(pos -> {
                final long dx = pos.getX() - idealPos.getX();
                final long dz = pos.getZ() - idealPos.getZ();
                return (double) (dx * dx + dz * dz);
            }))
            .toList())
        {
            final int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, columnPos.getX(), columnPos.getZ()) - 1;
            final int startY = Math.max(minY, surfaceY - PORTAL_SURFACE_SCAN_DEPTH);
            final int endY = Math.min(maxY, surfaceY + PORTAL_SURFACE_SCAN_DEPTH);

            for (int y = startY; y <= endY; y++)
            {
                final Optional<ExteritioPortalShape> shape = ExteritioPortalShape.findPortalShape(
                    level,
                    new BlockPos(columnPos.getX(), y, columnPos.getZ()),
                    ExteritioPortalShape::isValid,
                    preferredAxis
                );
                if (shape.isEmpty())
                {
                    continue;
                }

                final ExteritioPortalShape portalShape = shape.get();
                if (!portalShape.isComplete())
                {
                    portalShape.createPortalBlocks();
                }

                final BlockUtil.FoundRectangle rectangle = portalShape.asRectangle();
                final BlockPos minCorner = rectangle.minCorner;
                if (minCorner == null)
                {
                    continue;
                }

                return Optional.of(new LocatedPortal(rectangle, minCorner));
            }
        }

        return Optional.empty();
    }

    /**
     * Creates an Exteritio portal near the requested target using the same broad strategy as vanilla's portal forcer.
     * A nearby natural cavity with solid footing is preferred. If no suitable cavity is found, a small fallback
     * platform and air pocket are manufactured near the clamped target position before the portal frame is placed.
     *
     * @param level the target level where the destination portal should be created
     * @param idealPos the preferred destination position
     * @param axis the horizontal axis for the portal blocks
     * @param diagnostics counters and sample reasons collected while searching
     * @return the created portal rectangle, or an empty optional if both natural and fallback placement fail
     */
    private static Optional<BlockUtil.FoundRectangle> createPortalNear(
        final ServerLevel level,
        final @Nonnull BlockPos idealPos,
        final @Nonnull Direction.Axis axis,
        final PortalCreationDiagnostics diagnostics)
    {
        final Direction widthDir = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        if (widthDir == null)
        {
            return Optional.empty();
        }

        final Direction depthDir = widthDir.getClockWise();

        if (depthDir == null) return Optional.empty();

        final Optional<BlockPos> naturalPlacement = findNaturalPortalPlacement(level, idealPos, widthDir, depthDir, diagnostics);
        final BlockPos placement = naturalPlacement.orElseGet(() -> createFallbackPortalPlacement(level, idealPos, widthDir, depthDir, diagnostics).orElse(null));

        if (placement == null)
        {
            return Optional.empty();
        }

        placeExteritioPortal(level, placement, widthDir, axis);
        return Optional.of(new BlockUtil.FoundRectangle(placement, 2, 3));
    }

    /**
     * Searches nearby columns for a naturally valid portal placement.
     * The search prefers the closest fully clear placement with side clearance, but will remember the closest placement
     * that can host the frame itself if no side-clear candidate exists.
     *
     * @param level the target level to search
     * @param idealPos the preferred destination position
     * @param widthDir the horizontal direction along the two portal interior columns
     * @param depthDir the horizontal direction perpendicular to the portal plane
     * @param diagnostics counters and sample rejection reasons collected while scanning
     * @return a bottom-left portal interior position, or an empty optional if no natural placement is found
     */
    @SuppressWarnings("null")
    private static Optional<BlockPos> findNaturalPortalPlacement(
        final ServerLevel level,
        final @Nonnull BlockPos idealPos,
        final @Nonnull Direction widthDir,
        final @Nonnull Direction depthDir,
        final PortalCreationDiagnostics diagnostics)
    {
        final WorldBorder worldBorder = level.getWorldBorder();
        final int maxY = Math.min(level.getMaxBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight()) - 1;
        final BlockPos idealY = idealPos.atY(0);
        BlockPos bestWithSideClearance = null;
        BlockPos bestWithoutSideClearance = null;
        double bestWithSideClearanceDistance = Double.MAX_VALUE;
        double bestWithoutSideClearanceDistance = Double.MAX_VALUE;

        if (idealY == null)
        {
            return Optional.empty();
        }

        for (final BlockPos columnPos : BlockPos.withinManhattanStream(idealY, PLACEMENT_RADIUS, 0, PLACEMENT_RADIUS)
            .map(BlockPos::immutable)
            .sorted(Comparator.comparingDouble(pos -> {
                final long dx = pos.getX() - idealPos.getX();
                final long dz = pos.getZ() - idealPos.getZ();
                return (double) (dx * dx + dz * dz);
            }))
            .toList())
        {
            final int surfaceY = Math.min(maxY, level.getHeight(Heightmap.Types.MOTION_BLOCKING, columnPos.getX(), columnPos.getZ()));
            final BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos(columnPos.getX(), surfaceY, columnPos.getZ());
            if (!worldBorder.isWithinBounds(candidate) || !worldBorder.isWithinBounds(candidate.relative(widthDir)))
            {
                diagnostics.recordFailure(PortalCreationFailureReason.OUTSIDE_WORLD_BORDER, candidate.immutable(), level.getBlockState(candidate));
                continue;
            }

            for (int y = surfaceY; y >= level.getMinBuildHeight(); y--)
            {
                candidate.setY(y);
                diagnostics.recordNaturalCandidate();
                if (!canPortalReplaceBlock(level, candidate))
                {
                    continue;
                }

                int topClearY = y;
                while (y > level.getMinBuildHeight() && canPortalReplaceBlock(level, candidate.move(Direction.DOWN)))
                {
                    y--;
                }
                candidate.setY(y);

                if (y + 4 > maxY)
                {
                    diagnostics.recordFailure(PortalCreationFailureReason.INSUFFICIENT_HEIGHT, candidate.immutable(), level.getBlockState(candidate));
                    continue;
                }

                final int clearHeight = topClearY - y;
                if (clearHeight > 0 && clearHeight < 3)
                {
                    diagnostics.recordFailure(PortalCreationFailureReason.INSUFFICIENT_CLEARANCE, candidate.immutable(), level.getBlockState(candidate));
                    continue;
                }

                final BlockPos bottomLeft = candidate.immutable();
                final double distance = bottomLeft.distSqr(idealPos);
                if (canHostPortalFrame(level, bottomLeft, widthDir, depthDir, 0, diagnostics))
                {
                    if (canHostPortalFrame(level, bottomLeft, widthDir, depthDir, -1, diagnostics)
                        && canHostPortalFrame(level, bottomLeft, widthDir, depthDir, 1, diagnostics)
                        && distance < bestWithSideClearanceDistance)
                    {
                        bestWithSideClearanceDistance = distance;
                        bestWithSideClearance = bottomLeft;
                    }

                    if (bestWithSideClearance == null && distance < bestWithoutSideClearanceDistance)
                    {
                        bestWithoutSideClearanceDistance = distance;
                        bestWithoutSideClearance = bottomLeft;
                    }
                }
            }
        }

        return Optional.ofNullable(bestWithSideClearance == null ? bestWithoutSideClearance : bestWithSideClearance);
    }

    /**
     * Manufactures a small safe portal pocket near the requested position when no natural cavity can host a portal.
     * This mirrors vanilla's last-resort portal behavior while refusing to overwrite block entities or unbreakable blocks.
     *
     * @param level the target level where the fallback pocket should be created
     * @param idealPos the preferred destination position
     * @param widthDir the horizontal direction along the two portal interior columns
     * @param depthDir the horizontal direction perpendicular to the portal plane
     * @param diagnostics counters and sample failure reasons collected while preparing the fallback
     * @return the bottom-left portal interior position, or an empty optional if a fallback cannot be created
     */
    @SuppressWarnings("null")
    private static Optional<BlockPos> createFallbackPortalPlacement(
        final ServerLevel level,
        final @Nonnull BlockPos idealPos,
        final @Nonnull Direction widthDir,
        final @Nonnull Direction depthDir,
        final PortalCreationDiagnostics diagnostics)
    {
        final WorldBorder worldBorder = level.getWorldBorder();
        final int maxY = Math.min(level.getMaxBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight()) - 1;
        final int minFallbackY = Math.max(level.getMinBuildHeight() + 1, 70);
        final int maxFallbackY = maxY - 9;
        if (maxFallbackY < minFallbackY)
        {
            diagnostics.recordFailure(PortalCreationFailureReason.FALLBACK_HEIGHT_RANGE_INVALID, idealPos, level.getBlockState(idealPos));
            return Optional.empty();
        }

        BlockPos bottomLeft = new BlockPos(
            idealPos.getX() - widthDir.getStepX(),
            Mth.clamp(idealPos.getY(), minFallbackY, maxFallbackY),
            idealPos.getZ() - widthDir.getStepZ()
        ).immutable();

        bottomLeft = worldBorder.clampToBounds(bottomLeft);

        if (!canReplaceFallbackVolume(level, bottomLeft, widthDir, depthDir, diagnostics))
        {
            return Optional.empty();
        }

        final BlockState supportState = NullnessBridge.assumeNonnull(ModBlocks.NEUTRALIZED_BLIGHTWOOD.get().defaultBlockState());
        final BlockState air = NullnessBridge.assumeNonnull(Blocks.AIR.defaultBlockState());
        for (int z = -1; z <= 1; z++)
        {
            for (int x = -1; x <= 2; x++)
            {
                for (int y = -1; y <= 3; y++)
                {
                    final BlockPos pos = bottomLeft.relative(widthDir, x).relative(depthDir, z).above(y);
                    level.setBlock(pos, y < 0 ? supportState : air, y < 0 ? 3 : 18);
                }
            }
        }

        diagnostics.recordFallbackCreated(bottomLeft);
        return Optional.of(bottomLeft);
    }

    /**
     * Checks whether the fallback pocket can be safely overwritten.
     * Block entities and unbreakable blocks are treated as hard blockers so fallback creation does not silently destroy
     * special destination content.
     *
     * @param level the target level to inspect
     * @param bottomLeft the proposed bottom-left portal interior position
     * @param widthDir the horizontal direction along the two portal interior columns
     * @param depthDir the horizontal direction perpendicular to the portal plane
     * @param diagnostics counters and sample rejection reasons collected while checking
     * @return true if the fallback volume may be cleared or overwritten
     */
    private static boolean canReplaceFallbackVolume(
        final ServerLevel level,
        final @Nonnull BlockPos bottomLeft,
        final @Nonnull Direction widthDir,
        final @Nonnull Direction depthDir,
        final PortalCreationDiagnostics diagnostics)
    {
        final WorldBorder worldBorder = level.getWorldBorder();
        for (int z = -1; z <= 1; z++)
        {
            for (int x = -1; x <= 2; x++)
            {
                for (int y = -1; y <= 3; y++)
                {
                    final BlockPos pos = bottomLeft.relative(widthDir, x).relative(depthDir, z).above(y);
                    
                    if (pos ==  null) continue;

                    final BlockState state = level.getBlockState(pos);
                    if (!worldBorder.isWithinBounds(pos))
                    {
                        diagnostics.recordFailure(PortalCreationFailureReason.OUTSIDE_WORLD_BORDER, pos, state);
                        return false;
                    }

                    if (state.hasBlockEntity() || level.getBlockEntity(pos) != null)
                    {
                        diagnostics.recordFailure(PortalCreationFailureReason.BLOCK_ENTITY_BLOCKED, pos, state);
                        return false;
                    }

                    if (state.getDestroySpeed(level, pos) < 0.0F)
                    {
                        diagnostics.recordFailure(PortalCreationFailureReason.UNBREAKABLE_BLOCKED, pos, state);
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Checks whether the given bottom-left position can host the portal frame at a depth offset.
     * The layer beneath the frame must be solid, while the portal/frame area itself must be replaceable and fluid-free.
     *
     * @param level the target level to inspect
     * @param bottomLeft the proposed bottom-left portal interior position
     * @param widthDir the horizontal direction along the two portal interior columns
     * @param depthDir the horizontal direction perpendicular to the portal plane
     * @param depthOffset the perpendicular offset to validate, where zero is the portal plane
     * @param diagnostics counters and sample rejection reasons collected while checking
     * @return true if the portal frame can be hosted at the requested offset
     */
    private static boolean canHostPortalFrame(
        final ServerLevel level,
        final @Nonnull BlockPos bottomLeft,
        final @Nonnull Direction widthDir,
        final @Nonnull Direction depthDir,
        final int depthOffset,
        final PortalCreationDiagnostics diagnostics)
    {
        for (int x = -1; x <= 2; x++)
        {
            for (int y = -1; y <= 3; y++)
            {
                final BlockPos pos = bottomLeft.relative(widthDir, x).relative(depthDir, depthOffset).above(y);

                if (pos == null) continue;

                final BlockState state = level.getBlockState(pos);
                if (y < 0)
                {
                    if (!state.isFaceSturdy(level, pos, Direction.UP))
                    {
                        diagnostics.recordFailure(PortalCreationFailureReason.UNSTABLE_FOOTING, pos, state);
                        return false;
                    }
                }
                else if (!canPortalReplaceBlock(level, pos))
                {
                    diagnostics.recordFailure(PortalCreationFailureReason.SPACE_BLOCKED, pos, state);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Places the Exteritio frame and portal blocks at a validated bottom-left position.
     *
     * @param level the target level where blocks should be written
     * @param bottomLeft the bottom-left portal interior position
     * @param widthDir the horizontal direction along the two portal interior columns
     * @param axis the axis value to store on the portal blocks
     */
    @SuppressWarnings("null")
    private static void placeExteritioPortal(
        final ServerLevel level,
        final @Nonnull BlockPos bottomLeft,
        final @Nonnull Direction widthDir,
        final @Nonnull Direction.Axis axis)
    {
        final BlockState frameState = NullnessBridge.assumeNonnull(ModBlocks.NEUTRALIZED_BLIGHTWOOD.get().defaultBlockState());
        final BlockState portalState = ModBlocks.EXTERITIO_PORTAL.get().defaultBlockState().setValue(ExteritioPortalBlock.AXIS, axis);

        for (int x = -1; x <= 2; x++)
        {
            for (int y = -1; y <= 3; y++)
            {
                final BlockPos pos = bottomLeft.relative(widthDir, x).above(y);

                if (pos == null) continue;

                final boolean frameCell = x == -1 || x == 2 || y == -1 || y == 3;
                level.setBlock(pos, frameCell ? frameState : portalState, frameCell ? 3 : 18);
            }
        }
    }

    /**
     * Checks whether a block may be used as empty destination space for portal placement.
     *
     * @param level the level to inspect
     * @param pos the block position to inspect
     * @return true if the block is replaceable and contains no fluid
     */
    private static boolean canPortalReplaceBlock(final ServerLevel level, final @Nonnull BlockPos pos)
    {
        final BlockState state = level.getBlockState(pos);
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /**
     * Creates a dimension transition from the given entity's position in the source portal to the destination portal.
     * The entity is given a relative position in the source portal, and the dimension transition is created from this position.
     * The entity's delta movement, yaw, and pitch are used to set the entity's position after the dimension transition.
     *
     * @param entity the entity to create the dimension transition for
     * @param sourcePortalPos the position of the source portal
     * @param destinationRectangle the destination portal
     * @param targetLevel the target level
     * @param postTransition the post-dimension transition action
     * @return the created dimension transition, or null if unable to create portal shape or if entity is not inside portal shape
     */
    private static DimensionTransition createDimensionTransition(
        final Entity entity,
        final @Nonnull BlockPos sourcePortalPos,
        final BlockUtil.FoundRectangle destinationRectangle,
        final ServerLevel targetLevel,
        final @Nonnull DimensionTransition.PostDimensionTransition postTransition)
    {
        final BlockState sourceState = entity.level().getBlockState(sourcePortalPos);
        final Direction.Axis sourceAxis = sourceState.getOptionalValue(NullnessBridge.assumeNonnull(BlockStateProperties.HORIZONTAL_AXIS)).orElse(Direction.Axis.X);

        if (sourceAxis == null)
        {
            return null;
        }

        final Vec3 relativePos;
        if (sourceState.hasProperty(NullnessBridge.assumeNonnull(BlockStateProperties.HORIZONTAL_AXIS)))
        {
            final BlockUtil.FoundRectangle sourceRectangle = BlockUtil.getLargestRectangleAround(
                sourcePortalPos,
                sourceAxis,
                ExteritioPortalShape.MAX_WIDTH,
                Direction.Axis.Y,
                ExteritioPortalShape.MAX_HEIGHT,
                pos -> pos != null && entity.level().getBlockState(pos).is(NullnessBridge.assumeNonnull(ModBlocks.EXTERITIO_PORTAL.get()))
            );

            if (sourceRectangle == null)
            {
                return null;
            }

            relativePos = entity.getRelativePortalPosition(sourceAxis, sourceRectangle);
        }
        else
        {
            relativePos = new Vec3(0.5, 0.0, 0.0);
        }

        return createDimensionTransition(
            targetLevel,
            destinationRectangle,
            sourceAxis,
            relativePos,
            entity,
            entity.getDeltaMovement(),
            entity.getYRot(),
            entity.getXRot(),
            postTransition
        );
    }

    /**
     * Creates a dimension transition object for the given entity and destination rectangle.
     * The transition position is calculated by adding the relative position of the entity
     * within the source portal to the minimum corner of the destination rectangle.
     * The entity's motion is adjusted to account for the axis of the destination portal.
     * The entity's yaw and x-rotation are adjusted to match the destination portal.
     * The post-transition callback is executed after the transition is complete.
     *
     * @param targetLevel the level to transition to
     * @param destinationRectangle the destination rectangle
     * @param sourceAxis the axis of the source portal
     * @param relativePos the relative position of the entity within the source portal
     * @param entity the entity to transition
     * @param motion the entity's motion
     * @param yRot the entity's yaw
     * @param xRot the entity's x-rotation
     * @param postTransition the post-transition callback
     * @return the dimension transition object
     */
    private static DimensionTransition createDimensionTransition(
        final ServerLevel targetLevel,
        final BlockUtil.FoundRectangle destinationRectangle,
        final Direction.Axis sourceAxis,
        final Vec3 relativePos,
        final Entity entity,
        final Vec3 motion,
        final float yRot,
        final float xRot,
        final @Nonnull DimensionTransition.PostDimensionTransition postTransition)
    {
        final BlockPos minCorner = destinationRectangle.minCorner;

        if (minCorner == null)
        {
            return null;
        }

        final BlockState targetState = targetLevel.getBlockState(minCorner);
        final Direction.Axis targetAxis = targetState.getOptionalValue(NullnessBridge.assumeNonnull(BlockStateProperties.HORIZONTAL_AXIS)).orElse(Direction.Axis.X);
        final double width = destinationRectangle.axis1Size;
        final double height = destinationRectangle.axis2Size;
        final EntityDimensions dimensions = entity.getDimensions(NullnessBridge.assumeNonnull(entity.getPose()));
        final int yawAdjustment = sourceAxis == targetAxis ? 0 : 90;
        final Vec3 adjustedMotion = sourceAxis == targetAxis ? motion : new Vec3(motion.z, motion.y, -motion.x);
        final double offsetX = dimensions.width() / 2.0 + (width - dimensions.width()) * relativePos.x();
        final double offsetY = (height - dimensions.height()) * relativePos.y();
        final double offsetZ = 0.5 + relativePos.z();
        final boolean xAxisPortal = targetAxis == Direction.Axis.X;
        final Vec3 targetPos = new Vec3(
            minCorner.getX() + (xAxisPortal ? offsetX : offsetZ),
            minCorner.getY() + offsetY,
            minCorner.getZ() + (xAxisPortal ? offsetZ : offsetX)
        );
        final Vec3 safeTargetPos = PortalShape.findCollisionFreePosition(targetPos, targetLevel, entity, dimensions);

        if (safeTargetPos == null || adjustedMotion == null)
        {
            return null;
        }

        return new DimensionTransition(targetLevel, safeTargetPos, adjustedMotion, yRot + yawAdjustment, xRot, postTransition);
    }

    private record LocatedPortal(@Nonnull BlockUtil.FoundRectangle rectangle, @Nonnull BlockPos ticketPos)
    {
    }

    private enum PortalCreationFailureReason
    {
        OUTSIDE_WORLD_BORDER,
        INSUFFICIENT_HEIGHT,
        INSUFFICIENT_CLEARANCE,
        UNSTABLE_FOOTING,
        SPACE_BLOCKED,
        FALLBACK_HEIGHT_RANGE_INVALID,
        BLOCK_ENTITY_BLOCKED,
        UNBREAKABLE_BLOCKED
    }

    /**
     * Helper to analyze the root cause of failed portal creation.
     */
    private static final class PortalCreationDiagnostics
    {
        private int naturalCandidates;
        private boolean fallbackCreated;
        @Nullable
        private BlockPos fallbackPos;
        private final Map<PortalCreationFailureReason, Integer> failures = new EnumMap<>(PortalCreationFailureReason.class);
        private final Map<PortalCreationFailureReason, String> samples = new EnumMap<>(PortalCreationFailureReason.class);

        private void recordNaturalCandidate()
        {
            naturalCandidates++;
        }

        @SuppressWarnings("null")
        private void recordFailure(final PortalCreationFailureReason reason, final @Nonnull BlockPos pos, final BlockState state)
        {
            failures.merge(reason, 1, Integer::sum);
            samples.putIfAbsent(reason, pos + "=" + state);
        }

        private void recordFallbackCreated(final @Nonnull BlockPos pos)
        {
            fallbackCreated = true;
            fallbackPos = pos;
        }

        private String describe()
        {
            final StringJoiner joiner = new StringJoiner(", ");
            joiner.add("naturalCandidates=" + naturalCandidates);
            joiner.add("fallbackCreated=" + fallbackCreated);
            if (fallbackPos != null)
            {
                joiner.add("fallbackPos=" + fallbackPos);
            }

            for (final PortalCreationFailureReason reason : PortalCreationFailureReason.values())
            {
                final int count = failures.getOrDefault(reason, 0);
                if (count == 0)
                {
                    continue;
                }

                joiner.add(reason.name().toLowerCase() + "=" + count + " sample(" + samples.get(reason) + ")");
            }

            return joiner.toString();
        }
    }
}
