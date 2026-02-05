package com.deathfrog.salvationmod.core.blocks;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;

public class ScarredStoneBlock extends Block
{

    public ScarredStoneBlock(Properties props)
    {
        super(props);
    }

    @Override
    public void animateTick(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull RandomSource rand)
    {
        // animateTick is called client-side for visible blocks; guard anyway
        if (!(level instanceof ClientLevel client))
        {
            return;
        }

        // Keep overall particle load gentle:
        // ~1/14 chance per tick per visible block to emit the "mote" particle.
        if (rand.nextInt(14) != 0)
        {
            return;
        }

        // Spawn slightly inside/around the block so it feels like it’s leaking through seams
        final double x = pos.getX() + 0.5 + (rand.nextDouble() - 0.5) * 0.7;
        final double y = pos.getY() + 0.5 + (rand.nextDouble() - 0.5) * 0.7;
        final double z = pos.getZ() + 0.5 + (rand.nextDouble() - 0.5) * 0.7;

        // Gentle drifting upward with tiny horizontal wander
        final double dx = (rand.nextDouble() - 0.5) * 0.01;
        final double dy = 0.008 + rand.nextDouble() * 0.012;
        final double dz = (rand.nextDouble() - 0.5) * 0.01;

        // Primary: subtle spore motes (most of the time)
        client.addParticle(NullnessBridge.assumeNonnull(ParticleTypes.SPORE_BLOSSOM_AIR), x, y, z, dx, dy, dz);

        // Secondary: rare portal flicker (about 1 in 10 of emitted motes)
        // Net effect: ~1 portal particle per ~140 animate ticks per visible block.
        if (rand.nextInt(10) == 0)
        {
            // Portal looks better with very low/flat velocity
            final double pdx = (rand.nextDouble() - 0.5) * 0.02;
            final double pdy = (rand.nextDouble() - 0.5) * 0.01;
            final double pdz = (rand.nextDouble() - 0.5) * 0.02;
            client.addParticle(NullnessBridge.assumeNonnull(ParticleTypes.PORTAL), x, y, z, pdx, pdy, pdz);
        }
    }
}
