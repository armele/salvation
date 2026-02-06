package com.deathfrog.salvationmod.core.blocks;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

public class PollutedWaterBlock extends LiquidBlock
{
    public PollutedWaterBlock(FlowingFluid fluid, Properties properties)
    {
        super(fluid, properties);
    }

    /**
     * Called when a player picks up the block.
     * @param player the player picking up the block, or null if it was not a player
     * @param level the level accessor for the block
     * @param pos the position of the block
     * @param state the block state of the block
     * @return an ItemStack containing the block, or ItemStack.EMPTY if the block should not be picked up
     */
    @Nonnull
    @Override
    public ItemStack pickupBlock(@Nullable Player player, @Nonnull LevelAccessor level, @Nonnull BlockPos pos, @Nonnull BlockState state)
    {
        FluidState fluidState = state.getFluidState();
        if (fluidState.isSource())
        {
            level.setBlock(pos, NullnessBridge.assumeNonnull(Blocks.AIR.defaultBlockState()), 11);
            return new ItemStack(NullnessBridge.assumeNonnull(ModItems.POLLUTED_WATER_BUCKET.get()));
        }
        return NullnessBridge.assumeNonnull(ItemStack.EMPTY);
    }

    /**
     * Returns the sound event to play when the block is picked up by a player.
     * By default, this is SoundEvents.BUCKET_FILL, matching vanilla water behavior.
     * @return the sound event to play when the block is picked up by a player
     */
    @Override
    public Optional<SoundEvent> getPickupSound() 
    {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }
}
