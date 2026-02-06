package com.deathfrog.salvationmod.core.fluids;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.ModFluids;
import com.deathfrog.salvationmod.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidType;
import java.util.Optional;
import net.minecraft.sounds.SoundEvent;

public abstract class PollutedWaterFluid extends FlowingFluid
{
    @Override
    public FluidType getFluidType()
    {
        return ModFluids.POLLUTED_WATER_TYPE.get();
    }

    @Override
    public Fluid getSource()
    {
        return ModFluids.POLLUTED_WATER_SOURCE.get();
    }

    @Override
    public Fluid getFlowing()
    {
        return ModFluids.POLLUTED_WATER_FLOWING.get();
    }

    @Override
    public Item getBucket()
    {
        return ModItems.POLLUTED_WATER_BUCKET.get();
    }

    @Override
    protected boolean canConvertToSource(@Nonnull Level level)
    {
        return level.getGameRules().getBoolean(NullnessBridge.assumeNonnull(GameRules.RULE_WATER_SOURCE_CONVERSION));
    }

    @Override
    protected float getExplosionResistance()
    {
        return 100.0F;
    }

    /**
     * Called when the fluid is about to replace a block (i.e., the block is being destroyed by the fluid).
     * Vanilla water will drop the block's resources if appropriate.
     */
    @Override
    protected void beforeDestroyingBlock(@Nonnull LevelAccessor level, @Nonnull BlockPos pos, @Nonnull BlockState state)
    {
        // Match vanilla LiquidBlock behavior: drop resources if the block would normally drop when replaced.
        // This is server-side safe; LevelAccessor may be ServerLevel.
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        Block.dropResources(state, level, pos, blockEntity);
    }

    /**
     * Returns the distance that the fluid will spread when it is flowing on a slope.
     * A higher value means the fluid will spread further.
     * This value is used when the fluid is flowing on a slope, to determine how far it will spread.
     * This value is not used when the fluid is flowing on a flat surface.
     * The default implementation returns 4, matching vanilla water behavior.
     * @param level the level accessor for the fluid
     * @return the distance that the fluid will spread when it is flowing on a slope
     */
    @Override
    protected int getSlopeFindDistance(@Nonnull LevelReader level)
    {
        return 4; // water-like
    }

    /**
     * How quickly fluid level decreases as it flows outward. Water-like = 1, lava-like = 2.
     */
    @Override
    protected int getDropOff(@Nonnull LevelReader level)
    {
        return 1;
    }

    /**
     * Returns the number of ticks before the fluid will update its state again.
     * This is used to control the speed at which the fluid will spread.
     * A higher value means the fluid will spread more slowly.
     * This value is used when the fluid is flowing on a flat surface.
     * The default implementation returns 5, matching vanilla water behavior.
     * @param level the level accessor for the fluid
     * @return the tick delay of the fluid
     */
    @Override
    public int getTickDelay(@Nonnull LevelReader level)
    {
        return 5; // water-like
    }

    /**
     * Controls whether this fluid can be replaced by another fluid from a direction.
     * The default implementation allows replacement from sides/top, but not from below.
     * It also disallows replacement by itself.
     * @param state the fluid state that is being potentially replaced
     * @param level the level accessor for the block containing the fluid
     * @param pos the position of the block containing the fluid
     * @param fluid the fluid that is potentially replacing the current fluid
     * @param direction the direction that the potentially replacing fluid is coming from
     * @return true if the fluid can be replaced, false otherwise
     */
    @Override
    protected boolean canBeReplacedWith(@Nonnull FluidState state,
                                        @Nonnull BlockGetter level,
                                        @Nonnull BlockPos pos,
                                        @Nonnull Fluid fluid,
                                        @Nonnull Direction direction)
    {
        return direction == Direction.DOWN && !fluid.isSame(this);
    }

    /**
     * Converts a FluidState to the corresponding legacy blockstate (the in-world LiquidBlock state).
     * This is critical for the fluid to actually appear as your polluted water block.
     */
    @Override
    protected BlockState createLegacyBlock(@Nonnull FluidState state)
    {
        // Your LiquidBlock should have the LEVEL property just like vanilla water blocks.
        // LiquidBlock.LEVEL is the blockstate int property (0..15).
        return ModBlocks.POLLUTED_WATER_BLOCK.get().defaultBlockState()
            .setValue(NullnessBridge.assumeNonnull(LiquidBlock.LEVEL), getLegacyLevel(state));
    }

    /**
     * Checks if the given fluid is the same as either the source or flowing polluted water fluids.
     * @param fluid the fluid to check
     * @return true if the fluid is either the source or flowing polluted water, false otherwise
     */
    @Override
    public boolean isSame(@Nonnull Fluid fluid)
    {
        return fluid == ModFluids.POLLUTED_WATER_SOURCE.get()
            || fluid == ModFluids.POLLUTED_WATER_FLOWING.get();
    }

    /**
     * Returns the sound event to play when the fluid is picked up by a bucket.
     * The default implementation returns SoundEvents.BUCKET_FILL, matching vanilla water behavior.
     * @return the sound event to play when the fluid is picked up by a bucket
     */
    @Override
    public Optional<SoundEvent> getPickupSound()
    {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }


    // ---- Variants ----

    public static class Source extends PollutedWaterFluid
    {
        /**
         * @return true if the fluid state is a source fluid (i.e. it's not flowing)
         */
        @Override
        public boolean isSource(@Nonnull FluidState state)
        {
            return true;
        }

        /**
         * Returns the amount of fluid in the given fluid state.
         * @param state the fluid state to get the amount from
         * @return the amount of fluid in the given fluid state
         */
        @Override
        public int getAmount(@Nonnull FluidState state)
        {
            return 8;
        }
    }

    public static class Flowing extends PollutedWaterFluid
    {

        /**
         * Adds the LEVEL property to the fluid state definition.
         * This is a fluid level in the range [0, 8], where 0 is the lowest level and 8 is the highest.
         * This is used to determine the flow distance of the fluid.
         * @param builder The fluid state definition builder.
         */
        @Override
        protected void createFluidStateDefinition(@Nonnull StateDefinition.Builder<Fluid, FluidState> builder)
        {
            super.createFluidStateDefinition(builder);
            builder.add(NullnessBridge.assumeNonnull(LEVEL));
        }

        /**
         * Checks if the given fluid state is a source block.
         * A source block is the stationary block of a fluid that
         * is responsible for generating fluid flow.
         * @param state The fluid state to check.
         * @return True if the fluid state is a source block, false otherwise.
         */
        @Override
        public boolean isSource(@Nonnull FluidState state)
        {
            return false;
        }

        /**
         * Gets the amount of fluid in the given fluid state.
         * The amount of fluid is a value in the range [0, 8], where 0 is the lowest level and 8 is the highest.
         * This is used to determine the flow distance of the fluid.
         * @param state The fluid state to get the amount from.
         * @return The amount of fluid in the given fluid state.
         */
        @Override
        public int getAmount(@Nonnull FluidState state)
        {
            return state.getValue(NullnessBridge.assumeNonnull(LEVEL));
        }
    }
}
