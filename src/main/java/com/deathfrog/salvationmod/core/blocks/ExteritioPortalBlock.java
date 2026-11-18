package com.deathfrog.salvationmod.core.blocks;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.Config;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.ModDimensions;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.deathfrog.salvationmod.core.portal.ExteritioPortalManager;
import com.deathfrog.salvationmod.core.portal.ExteritioPortalShape;
import com.mojang.serialization.MapCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ExteritioPortalBlock extends Block implements Portal
{
    public static final MapCodec<ExteritioPortalBlock> CODEC = simpleCodec(ExteritioPortalBlock::new);
    public static final @Nonnull EnumProperty<Direction.Axis> AXIS = NullnessBridge.assumeNonnull(BlockStateProperties.HORIZONTAL_AXIS);
    private static final VoxelShape X_AXIS_AABB = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape Z_AXIS_AABB = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    @SuppressWarnings("null")
    public ExteritioPortalBlock(final BlockBehaviour.Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    public MapCodec<ExteritioPortalBlock> codec()
    {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final @Nonnull StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(final @Nonnull BlockState state, final @Nonnull BlockGetter level, final @Nonnull BlockPos pos, final @Nonnull CollisionContext context)
    {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_AXIS_AABB : X_AXIS_AABB;
    }

    @Override
    protected BlockState updateShape(
        final @Nonnull BlockState state,
        final @Nonnull Direction direction,
        final @Nonnull BlockState neighborState,
        final @Nonnull LevelAccessor level,
        final @Nonnull BlockPos pos,
        final @Nonnull BlockPos neighborPos)
    {
        final Direction.Axis changedAxis = direction.getAxis();
        final Direction.Axis portalAxis = state.getValue(AXIS);
        final boolean crossedPlane = portalAxis != changedAxis && changedAxis.isHorizontal();
        return !crossedPlane && !neighborState.is(this) && !new ExteritioPortalShape(level, pos, portalAxis).isComplete()
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void entityInside(final @Nonnull BlockState state, final @Nonnull Level level, final @Nonnull BlockPos pos, final @Nonnull Entity entity)
    {
        if (entity.canUsePortal(false))
        {
            entity.setAsInsidePortal(this, pos);
        }
    }

    /**
     * Schedules the next corruption pulse for the canonical block of a portal.
     *
     * @param level the level containing the portal
     * @param pos the canonical portal block position
     */
    public static void scheduleCorruptionTick(final Level level, final @Nonnull BlockPos pos)
    {
        final int interval = Config.portalCorruptionInterval.get();
        if (!level.isClientSide() && interval > 0)
        {
            level.scheduleTick(pos, NullnessBridge.assumeNonnull(ModBlocks.EXTERITIO_PORTAL.get()), interval);
        }
    }

    /**
     * Applies a corruption pulse when this position is the canonical block of a complete portal,
     * then schedules the portal's next pulse.
     *
     * @param state the portal block state
     * @param level the server level containing the portal
     * @param pos the portal block position
     * @param random the level random source
     */
    @Override
    protected void tick(final @Nonnull BlockState state, final @Nonnull ServerLevel level, final @Nonnull BlockPos pos, final @Nonnull RandomSource random)
    {
        final ExteritioPortalShape shape = getCanonicalCompleteShape(level, pos, state);
        if (shape == null)
        {
            return;
        }

        if (level.dimension() != ModDimensions.EXTERITIO
            && !SalvationManager.isCorruptionDisabled(level)
            && !SalvationManager.isCorruptionCycleEnded(level))
        {
            final int amount = shape.getPortalBlockCount() * Config.portalCorruptionPerBlock.get();
            if (amount > 0)
            {
                SalvationManager.recordCorruption(level, ProgressionSource.PORTAL, pos, amount);
                emitCorruptionPulseParticles(level, shape, random);
            }
        }

        scheduleCorruptionTick(level, pos);
    }

    /**
     * Restores deterministic corruption scheduling for an existing portal that has no pending tick.
     *
     * @param state the portal block state
     * @param level the server level containing the portal
     * @param pos the portal block position
     * @param random the level random source
     */
    @Override
    protected void randomTick(final @Nonnull BlockState state, final @Nonnull ServerLevel level, final @Nonnull BlockPos pos, final @Nonnull RandomSource random)
    {
        if (level.getBlockTicks().hasScheduledTick(pos, this))
        {
            return;
        }

        if (getCanonicalCompleteShape(level, pos, state) != null)
        {
            scheduleCorruptionTick(level, pos);
        }
    }

    @Nullable
    private static ExteritioPortalShape getCanonicalCompleteShape(final Level level, final BlockPos pos, final BlockState state)
    {
        final Direction.Axis axis = state.getOptionalValue(AXIS).orElse(Direction.Axis.X);
        final ExteritioPortalShape shape = new ExteritioPortalShape(level, pos, axis);
        return shape.isComplete() && pos.equals(shape.getBottomLeft()) ? shape : null;
    }

    @SuppressWarnings("null")
    private static void emitCorruptionPulseParticles(
        final ServerLevel level,
        final ExteritioPortalShape shape,
        final RandomSource random)
    {
        final BlockPos bottomLeft = shape.getBottomLeft();
        if (bottomLeft == null)
        {
            return;
        }

        final int particleCount = Math.min(24, Math.max(6, (int) Math.round(Math.sqrt(shape.getPortalBlockCount()) * 2.5D)));
        final Direction spreadDirection = shape.getAxis() == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        final boolean xAxisPortal = shape.getAxis() == Direction.Axis.X;

        for (int i = 0; i < particleCount; i++)
        {
            final double across = random.nextDouble() * shape.getWidth();
            final double x = bottomLeft.getX() + 0.5D + spreadDirection.getStepX() * across;
            final double y = bottomLeft.getY() + random.nextDouble() * shape.getHeight();
            final double z = bottomLeft.getZ() + 0.5D + spreadDirection.getStepZ() * across;
            final double outwardSpeed = (0.03D + random.nextDouble() * 0.05D) * (random.nextBoolean() ? 1.0D : -1.0D);
            final double xSpeed = xAxisPortal ? 0.0D : outwardSpeed;
            final double ySpeed = 0.01D + random.nextDouble() * 0.02D;
            final double zSpeed = xAxisPortal ? outwardSpeed : 0.0D;

            level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 0, xSpeed, ySpeed, zSpeed, 1.0D);
        }
    }

    /**
     * Gets the transition time for the given entity at the given position.
     * If the entity is a player, the transition time is the maximum of 1 and the value of the game rule
     * {@link GameRules#RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY} if the player is vulnerable, or
     * {@link GameRules#RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY} if the player is not vulnerable.
     * If the entity is not a player, the transition time is 0.
     *
     * @param level the server level
     * @param entity the entity
     * @return the transition time
     */
    @SuppressWarnings("null")
    @Override
    public int getPortalTransitionTime(final @Nonnull ServerLevel level, final @Nonnull Entity entity)
    {
        if (entity instanceof Player player)
        {
            return Math.max(
                1,
                level.getGameRules().getInt(
                    player.getAbilities().invulnerable
                        ? GameRules.RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY
                        : GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY
                )
            );
        }

        return 0;
    }

    /**
     * Gets the local transition for this portal block.
     * This method is a wrapper for {@link ExteritioPortalManager#getPortalDestination(ServerLevel, Entity, BlockPos)}.
     * 
     * @param level the server level
     * @param entity the entity
     * @param pos the position of the portal block
     * @return the local transition for this portal block
     */
    @Nullable
    @Override
    public DimensionTransition getPortalDestination(final @Nonnull ServerLevel level, final @Nonnull Entity entity, final @Nonnull BlockPos pos)
    {
        return ExteritioPortalManager.getPortalDestination(level, entity, pos);
    }

    /**
     * Gets the local transition for this portal block.
     * This method should be overridden by subclasses to return the appropriate transition.
     * The default implementation returns {@link Portal.Transition#CONFUSION}.
     *
     * @return the local transition
     */
    @Override
    public Portal.Transition getLocalTransition()
    {
        return Portal.Transition.CONFUSION;
    }

    /**
     * Animates the portal block.
     *
     * <p>This method plays a portal sound every 100th tick, and spawns 4 portal particles
     * every tick, with random velocities and starting positions.</p>
     *
     * @param state the block state
     * @param level the server level
     * @param pos the position of the block
     * @param random a random source
     */
    @Override
    public void animateTick(final @Nonnull BlockState state, final @Nonnull Level level, final @Nonnull BlockPos pos, final @Nonnull RandomSource random)
    {
        if (random.nextInt(100) == 0)
        {
            level.playLocalSound(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                NullnessBridge.assumeNonnull(SoundEvents.PORTAL_AMBIENT),
                SoundSource.BLOCKS,
                0.5F,
                random.nextFloat() * 0.4F + 0.8F,
                false
            );
        }

        for (int i = 0; i < 4; i++)
        {
            double x = pos.getX() + random.nextDouble();
            final double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            double xSpeed = (random.nextFloat() - 0.5D) * 0.5D;
            final double ySpeed = (random.nextFloat() - 0.5D) * 0.5D;
            double zSpeed = (random.nextFloat() - 0.5D) * 0.5D;
            final int direction = random.nextInt(2) * 2 - 1;

            BlockPos west = pos.west();
            BlockPos east = pos.east();

            if (west == null || east == null)
            {
                continue;
            }

            if (!level.getBlockState(west).is(this) && !level.getBlockState(east).is(this))
            {
                x = pos.getX() + 0.5D + 0.25D * direction;
                xSpeed = random.nextFloat() * 2.0F * direction;
            }
            else
            {
                z = pos.getZ() + 0.5D + 0.25D * direction;
                zSpeed = random.nextFloat() * 2.0F * direction;
            }

            level.addParticle(NullnessBridge.assumeNonnull(ParticleTypes.PORTAL), x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}
