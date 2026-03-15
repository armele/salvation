package com.deathfrog.salvationmod.core.portal;

import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.core.blocks.ExteritioPortalBlock;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public class ExteritioPortalShape
{
    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;

    private final LevelAccessor level;
    private final Direction.Axis axis;
    private @Nonnull final Direction rightDir;
    @Nullable
    private BlockPos bottomLeft;
    private int numPortalBlocks;
    private int width;
    private int height;

    /**
     * Finds an empty portal shape at the given position on the given axis.
     * An empty portal shape is a shape that is valid and has no portal blocks.
     * If an empty portal shape is found at the given position, it is returned immediately.
     * If no empty portal shape is found, a shape is constructed at the given position on the alternate axis and
     * checked against the predicate. If this shape matches, it is returned, otherwise an empty optional is returned.
     *
     * @param level the level accessor to use for block lookups
     * @param pos the position to search for an empty portal shape
     * @param axis the axis to construct the primary portal shape on
     * @return an optional containing an empty portal shape, or an empty optional if no empty portal shape is found
     */
    public static Optional<ExteritioPortalShape> findEmptyPortalShape(final LevelAccessor level, final BlockPos pos, final Direction.Axis axis)
    {
        return findPortalShape(level, pos, shape -> shape.isValid() && shape.numPortalBlocks == 0, axis);
    }

    /**
     * Attempts to find a portal shape that matches the given predicate at the given position.
     * If a matching shape is found at the given position, it is returned immediately.
     * If no matching shape is found, a shape is constructed at the given position on the alternate axis and
     * checked against the predicate. If this shape matches, it is returned, otherwise an empty optional is returned.
     *
     * @param level the level accessor to use for block lookups
     * @param pos the position to search for a portal shape
     * @param predicate the predicate to check for a matching portal shape
     * @param axis the axis to construct the primary portal shape on
     * @return an optional containing a matching portal shape, or an empty optional if no matching shape is found
     */
    public static Optional<ExteritioPortalShape> findPortalShape(
        final LevelAccessor level,
        final BlockPos pos,
        final Predicate<ExteritioPortalShape> predicate,
        final Direction.Axis axis)
    {
        final Optional<ExteritioPortalShape> primary = Optional.of(new ExteritioPortalShape(level, pos, axis)).filter(predicate);
        if (primary.isPresent())
        {
            return primary;
        }

        final Direction.Axis alternateAxis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return Optional.of(new ExteritioPortalShape(level, pos, alternateAxis)).filter(predicate);
    }

    public ExteritioPortalShape(final LevelAccessor level, final BlockPos pos, final Direction.Axis axis)
    {
        this.level = level;
        this.axis = axis;
        this.rightDir = axis == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        this.bottomLeft = calculateBottomLeft(pos);
        if (this.bottomLeft == null)
        {
            this.width = 0;
            this.height = 0;
            return;
        }

        this.width = calculateWidth();
        if (this.width > 0)
        {
            this.height = calculateHeight();
        }
    }

    public Direction.Axis getAxis()
    {
        return axis;
    }

    @Nullable
    public BlockPos getBottomLeft()
    {
        return bottomLeft;
    }

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    public boolean isValid()
    {
        return bottomLeft != null && width >= MIN_WIDTH && width <= MAX_WIDTH && height >= MIN_HEIGHT && height <= MAX_HEIGHT;
    }

    public boolean isComplete()
    {
        return isValid() && numPortalBlocks == width * height;
    }

    public BlockUtil.FoundRectangle asRectangle()
    {
        if (bottomLeft == null)
        {
            throw new IllegalStateException("Cannot create rectangle for invalid portal shape.");
        }

        return new BlockUtil.FoundRectangle(bottomLeft, width, height);
    }

    /**
     * Sets all blocks within the shape's bounding box to the specified portal state.
     * This method is idempotent and will not change the level if the shape is invalid.
     */
    @SuppressWarnings("null")
    public void createPortalBlocks()
    {
        if (bottomLeft == null)
        {
            return;
        }

        final BlockState portalState = ModBlocks.EXTERITIO_PORTAL.get()
            .defaultBlockState()
            .setValue(ExteritioPortalBlock.AXIS, axis);

        BlockPos.betweenClosed(
            bottomLeft,
            bottomLeft.relative(Direction.UP, height - 1).relative(rightDir, width - 1)
        ).forEach(pos -> level.setBlock(pos, portalState, 18));
    }

    /**
     * Attempts to find the bottom left corner of a portal shape.
     * This involves finding the lowest Y position that is not empty, and then finding the offset from the given position
     * to the bottom left corner of the shape.
     * If the shape is invalid, this method will return null.
     *
     * @param pos the position to start searching from
     * @return the bottom left corner of the portal shape, or null if the shape is invalid
     */
    @SuppressWarnings("null")
    @Nullable
    private BlockPos calculateBottomLeft(BlockPos pos)
    {
        final int minY = Math.max(level.getMinBuildHeight(), pos.getY() - MAX_HEIGHT);
        while (pos.getY() > minY && isEmpty(level.getBlockState(pos.below())))
        {
            pos = pos.below();
        }

        final Direction direction = rightDir.getOpposite();
        final int offset = getDistanceUntilEdgeAboveFrame(pos, direction) - 1;
        return offset < 0 ? null : pos.relative(direction, offset);
    }

    /**
     * Calculates the width of the portal shape by measuring the distance from the bottom left corner until a non-frame block is found.
     * This method will throw an IllegalStateException if the bottom left corner is null.
     * @return the width of the portal shape, or 0 if the shape is invalid
     */
    private int calculateWidth()
    {
        BlockPos localBottomLeft = bottomLeft; 

        if (localBottomLeft == null)
        {
            throw new IllegalStateException("Cannot measure portal width without a bottom-left corner.");
        }

        final int calculatedWidth = getDistanceUntilEdgeAboveFrame(localBottomLeft, rightDir);
        return calculatedWidth >= MIN_WIDTH && calculatedWidth <= MAX_WIDTH ? calculatedWidth : 0;
    }

    private int calculateHeight()
    {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final int calculatedHeight = getDistanceUntilTop(cursor);
        return calculatedHeight >= MIN_HEIGHT && calculatedHeight <= MAX_HEIGHT && hasTopFrame(cursor, calculatedHeight) ? calculatedHeight : 0;
    }

    private boolean hasTopFrame(final BlockPos.MutableBlockPos cursor, final int portalHeight)
    {
        BlockPos localBottomLeft = bottomLeft;

        if (localBottomLeft == null) return false;

        for (int x = 0; x < width; x++)
        {
            cursor.set(localBottomLeft).move(Direction.UP, portalHeight).move(rightDir, x);
            if (!level.getBlockState(cursor).isPortalFrame(level, cursor))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the height of the portal shape by measuring the distance from the bottom left corner until a non-frame block is found.
     * This method will throw an IllegalStateException if the bottom left corner is null.
     * @param cursor a mutable block position used to measure the height
     * @return the height of the portal shape
     */
    private int getDistanceUntilTop(final BlockPos.MutableBlockPos cursor)
    {
        BlockPos localBottomLeft = bottomLeft; 

        if (localBottomLeft == null)
        {
            throw new IllegalStateException("Cannot measure portal height without a bottom-left corner.");
        }

        for (int y = 0; y < MAX_HEIGHT; y++)
        {
            cursor.set(localBottomLeft).move(Direction.UP, y).move(rightDir, -1);
            if (!level.getBlockState(cursor).isPortalFrame(level, cursor))
            {
                return y;
            }

            cursor.set(localBottomLeft).move(Direction.UP, y).move(rightDir, width);
            if (!level.getBlockState(cursor).isPortalFrame(level, cursor))
            {
                return y;
            }

            for (int x = 0; x < width; x++)
            {
                cursor.set(localBottomLeft).move(Direction.UP, y).move(rightDir, x);
                final BlockState state = level.getBlockState(cursor);
                if (!isEmpty(state))
                {
                    return y;
                }

                if (state.is(NullnessBridge.assumeNonnull(ModBlocks.EXTERITIO_PORTAL.get())))
                {
                    numPortalBlocks++;
                }
            }
        }

        return MAX_HEIGHT;
    }

    /**
     * Calculates the distance from the given position to the edge of the portal above.
     * This edge is defined as the first non-empty block that is not a portal frame
     * above the given position, or the first non-frame block below the given position.
     *
     * @param pos the position to start the search from
     * @param direction the direction to search in
     * @return the distance to the edge of the portal above, or 0 if no edge is found
     */
    @SuppressWarnings("null")
    private int getDistanceUntilEdgeAboveFrame(final @Nonnull BlockPos pos, final @Nonnull Direction direction)
    {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int distance = 0; distance <= MAX_WIDTH; distance++)
        {
            cursor.set(pos).move(direction, distance);
            final BlockState state = level.getBlockState(cursor);
            if (!isEmpty(state))
            {
                return state.isPortalFrame(level, cursor) ? distance : 0;
            }

            final BlockPos below = cursor.below();
            if (!level.getBlockState(below).isPortalFrame(level, below))
            {
                return 0;
            }
        }

        return 0;
    }

    /**
     * Checks if the given block state is empty, i.e., if it is air, on fire, or a portal frame.
     *
     * @param state the block state to check
     * @return true if the block state is empty, false otherwise
     */
    private static boolean isEmpty(final BlockState state)
    {
        return state.isAir() || state.is(NullnessBridge.assumeNonnull(BlockTags.FIRE)) || state.is(NullnessBridge.assumeNonnull(ModBlocks.EXTERITIO_PORTAL.get()));
    }
}
