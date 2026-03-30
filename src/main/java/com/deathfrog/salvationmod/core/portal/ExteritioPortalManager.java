package com.deathfrog.salvationmod.core.portal;

import java.util.Comparator;
import java.util.Optional;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;

public final class ExteritioPortalManager
{
    private static final int SEARCH_RADIUS = 32;
    private static final int PLACEMENT_RADIUS = 16;

    private ExteritioPortalManager()
    {
    }

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

        if (idealTargetPos == null) return null;

        final Optional<BlockPos> existingPortal = findClosestPortalPosition(targetLevel, idealTargetPos, SEARCH_RADIUS);

        final BlockUtil.FoundRectangle destinationRectangle;
        final DimensionTransition.PostDimensionTransition postTransition;
        if (existingPortal.isPresent())
        {
            final BlockPos foundPos = existingPortal.get();

            if (foundPos == null) return null;

            destinationRectangle = getExistingPortalRectangle(targetLevel, foundPos);
            postTransition = DimensionTransition.PLAY_PORTAL_SOUND.then(entityInTarget -> entityInTarget.placePortalTicket(foundPos));
        }
        else
        {
            final Direction.Axis preferredAxis = sourceLevel.getBlockState(portalPos)
                .getOptionalValue(ExteritioPortalBlock.AXIS)
                .orElse(Direction.Axis.X);
            final Optional<BlockUtil.FoundRectangle> createdPortal = createPortalNear(targetLevel, idealTargetPos, preferredAxis);
            if (createdPortal.isEmpty())
            {
                SalvationMod.LOGGER.error("Unable to create Exteritio portal near {}", idealTargetPos);
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
     * Attempts to create a portal near the given ideal position on the given axis.
     * This method will search a radius of {@link #PLACEMENT_RADIUS} around the ideal position, and
     * attempt to create a portal at the closest position to the ideal position.
     * If a portal is unable to be created within the given radius, an empty optional is returned.
     *
     * @param level the level accessor to use for block lookups
     * @param idealPos the ideal position to search for a portal
     * @param axis the axis to construct the primary portal shape on
     * @return an optional containing the created portal shape, or an empty optional if unable to create portal
     */
    private static Optional<BlockUtil.FoundRectangle> createPortalNear(final ServerLevel level, final @Nonnull BlockPos idealPos, final Direction.Axis axis)
    {
        final int minY = level.getMinBuildHeight() + 1;
        final int maxY = level.getMaxBuildHeight() - 5;

        return BlockPos.withinManhattanStream(idealPos, PLACEMENT_RADIUS, 0, PLACEMENT_RADIUS)
            .map(BlockPos::immutable)
            .sorted(Comparator.comparingDouble(pos -> pos.distSqr(idealPos)))
            .map(pos -> {
                final int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
                final int clampedY = Math.max(minY, Math.min(maxY, surfaceY));
                return new BlockPos(pos.getX(), clampedY, pos.getZ());
            })
            .distinct()
            .map(candidate -> tryCreatePortalAt(level, candidate, axis))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    /**
     * Attempts to create a portal shape at the given position on the given axis.
     * This method will check that the given position is suitable for a portal shape,
     * by checking that the frame cells (the cells on the edge of the portal) are
     * replaceable and that the interior cells (the cells inside the portal) are clear.
     * If the position is suitable, the method will then create the portal shape at the given position.
     * @param level the level accessor to use for block lookups
     * @param basePos the base position to attempt to create the portal at
     * @param axis the axis to construct the primary portal shape on
     * @return an optional containing the created portal shape, or an empty optional if unable to create portal
     */
    private static Optional<BlockUtil.FoundRectangle> tryCreatePortalAt(final ServerLevel level, final BlockPos basePos, final Direction.Axis axis)
    {
        final Direction right = axis == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        final Direction depth = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        final BlockPos bottomLeft = basePos.above();

        if (bottomLeft == null) return Optional.empty();

        for (int y = -1; y <= 3; y++)
        {
            for (int x = -1; x <= 2; x++)
            {
                final BlockPos framePos = bottomLeft.relative(right, x).above(y);

                if (framePos == null) continue;

                final boolean frameCell = x == -1 || x == 2 || y == -1 || y == 3;
                if (frameCell)
                {
                    if (!canReplaceFrameBlock(level, framePos))
                    {
                        return Optional.empty();
                    }
                }
                else if (!isPortalInteriorClear(level, framePos))
                {
                    return Optional.empty();
                }

                BlockPos interiorPos = framePos.relative(depth);

                if (interiorPos == null) return Optional.empty();

                if (!level.getBlockState(interiorPos).canBeReplaced())
                {
                    return Optional.empty();
                }
            }
        }

        for (int y = -1; y <= 3; y++)
        {
            for (int x = -1; x <= 2; x++)
            {
                final BlockPos framePos = bottomLeft.relative(right, x).above(y);

                if (framePos == null) continue;

                final boolean frameCell = x == -1 || x == 2 || y == -1 || y == 3;
                if (frameCell)
                {
                    level.setBlock(framePos, NullnessBridge.assumeNonnull(ModBlocks.NEUTRALIZED_BLIGHTWOOD.get().defaultBlockState()), 3);
                }
                else
                {
                    if (axis == null) continue;

                    BlockState newState = ModBlocks.EXTERITIO_PORTAL.get().defaultBlockState().setValue(ExteritioPortalBlock.AXIS, axis);

                    if (newState == null) continue;

                    level.setBlock(framePos, newState, 18);
                }
            }
        }

        return Optional.of(new BlockUtil.FoundRectangle(bottomLeft, 2, 3));
    }

    /**
     * Checks if the given position can be replaced with a frame block.
     * This involves checking if the block at the given position is either
     * a neutralized blightwood block, or if the block can be replaced.
     * 
     * @param level the level accessor to use for block lookups
     * @param pos the position to check
     * @return true if the block at the given position can be replaced with a frame block, false otherwise
     */
    private static boolean canReplaceFrameBlock(final ServerLevel level, final @Nonnull BlockPos pos)
    {
        final BlockState state = level.getBlockState(pos);
        return state.is(NullnessBridge.assumeNonnull(ModBlocks.NEUTRALIZED_BLIGHTWOOD.get())) || state.canBeReplaced();
    }

    /**
     * Checks if the block at the given position is either air, can be replaced,
     * or is a portal frame block.
     * 
     * @param level the level accessor to use for block lookups
     * @param pos the position to check
     * @return true if the block at the given position is either air, can be replaced,
     *         or is a portal frame block, false otherwise
     */
    private static boolean isPortalInteriorClear(final ServerLevel level, final @Nonnull BlockPos pos)
    {
        final BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || state.is(NullnessBridge.assumeNonnull(ModBlocks.EXTERITIO_PORTAL.get()));
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
}
