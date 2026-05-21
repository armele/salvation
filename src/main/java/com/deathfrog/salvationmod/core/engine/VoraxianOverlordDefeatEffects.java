package com.deathfrog.salvationmod.core.engine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Coordinates the short server-side ceremony played when the Voraxian Overlord is defeated.
 * <p>
 * The effect deliberately uses particles and sounds instead of spawning damaging firework rocket
 * entities, so nearby victorious players receive spectacle without accidental post-fight damage.
 * Active ceremonies are advanced from the server tick loop and are intentionally transient; they are
 * not persisted across server restarts.
 */
public final class VoraxianOverlordDefeatEffects
{
    private static final int CEREMONY_DURATION_TICKS = 96;
    private static final List<DefeatCeremony> ACTIVE_CEREMONIES = new ArrayList<>();

    private VoraxianOverlordDefeatEffects()
    {
    }

    /**
     * Starts a new defeat ceremony at the given world position.
     *
     * @param level the server level where the Overlord died
     * @param position the center point for sounds and particles
     */
    @SuppressWarnings("null")
    public static void start(@Nonnull final ServerLevel level, @Nonnull final Vec3 position)
    {
        ACTIVE_CEREMONIES.add(new DefeatCeremony(level.dimension(), position));
    }

    /**
     * Returns whether at least one defeat ceremony is currently active.
     * <p>
     * The server tick handler uses this as a cheap guard so the all-level ceremony tick only runs
     * every tick while a ceremony is actually in progress.
     *
     * @return true when one or more ceremonies are active
     */
    public static boolean hasActiveCeremonies()
    {
        return !ACTIVE_CEREMONIES.isEmpty();
    }

    /**
     * Advances all active ceremonies for the supplied level by one server tick.
     *
     * @param level the level being processed this tick
     */
    @SuppressWarnings("null")
    public static void tick(@Nonnull final ServerLevel level)
    {
        final Iterator<DefeatCeremony> iterator = ACTIVE_CEREMONIES.iterator();
        while (iterator.hasNext())
        {
            final DefeatCeremony ceremony = iterator.next();
            if (!ceremony.dimension().equals(level.dimension()))
            {
                continue;
            }

            playTick(level, ceremony.position(), ceremony.age);
            ceremony.advance();
            if (ceremony.age > CEREMONY_DURATION_TICKS)
            {
                iterator.remove();
            }
        }
    }

    /**
     * Emits the sounds and particles scheduled for a single ceremony tick.
     * <p>
     * The sequence is staged as corruption collapsing first, then purifying light and firework-like
     * bursts blooming outward.
     *
     * @param level the level receiving the effects
     * @param position the ceremony center
     * @param age the ceremony age in ticks
     */
    @SuppressWarnings("null")
    private static void playTick(@Nonnull final ServerLevel level, @Nonnull final Vec3 position, final int age)
    {
        final double x = position.x;
        final double y = position.y + 1.2D;
        final double z = position.z;

        if (age == 0)
        {
            playSound(level, position, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.8F, 0.72F);
            playSound(level, position, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6F, 0.55F);
            level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 80, 1.25D, 1.0D, 1.25D, 0.12D);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 120, 2.2D, 1.6D, 2.2D, 0.18D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 50, 1.4D, 0.8D, 1.4D, 0.05D);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 40, 0.9D, 0.8D, 0.9D, 0.03D);
        }

        if (age >= 8 && age <= 58 && age % 10 == 8)
        {
            final double radius = 1.4D + age * 0.12D;
            level.sendParticles(ParticleTypes.END_ROD, x, y + 0.6D, z, 42, radius, 0.18D, radius, 0.02D);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.35D, z, 18, radius * 0.72D, 0.25D, radius * 0.72D, 0.01D);
            playSound(level, position, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.85F + age * 0.01F);
        }

        if (age == 22)
        {
            playSound(level, position, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 1.0F);
        }

        if (age == 34 || age == 50 || age == 66)
        {
            final double burstX = x + (level.random.nextDouble() - 0.5D) * 5.0D;
            final double burstY = y + 3.0D + level.random.nextDouble() * 2.0D;
            final double burstZ = z + (level.random.nextDouble() - 0.5D) * 5.0D;
            level.sendParticles(ParticleTypes.FIREWORK, burstX, burstY, burstZ, 70, 0.75D, 0.75D, 0.75D, 0.18D);
            level.sendParticles(ParticleTypes.END_ROD, burstX, burstY, burstZ, 30, 0.5D, 0.5D, 0.5D, 0.08D);
            playSound(level, new Vec3(burstX, burstY, burstZ), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.AMBIENT, 1.6F, 0.9F + level.random.nextFloat() * 0.25F);
            playSound(level, new Vec3(burstX, burstY, burstZ), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.AMBIENT, 1.1F, 1.0F);
        }

        if (age == 82)
        {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 0.5D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0D, z, 120, 2.0D, 1.2D, 2.0D, 0.12D);
            level.sendParticles(ParticleTypes.FIREWORK, x, y + 2.2D, z, 100, 2.3D, 1.4D, 2.3D, 0.14D);
            playSound(level, position, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.8F, 0.85F);
        }
    }

    /**
     * Plays a positioned sound to all players who can hear the supplied level event.
     *
     * @param level the level where the sound is played
     * @param position the position of the sound source
     * @param sound the sound event to play
     * @param source the sound category
     * @param volume the playback volume
     * @param pitch the playback pitch
     */
    private static void playSound(
        @Nonnull final ServerLevel level,
        @Nonnull final Vec3 position,
        @Nonnull final net.minecraft.sounds.SoundEvent sound,
        @Nonnull final SoundSource source,
        final float volume,
        final float pitch)
    {
        level.playSound(null, position.x, position.y, position.z, sound, source, volume, pitch);
    }

    /**
     * Mutable state for one in-progress defeat ceremony.
     * <p>
     * Only the dimension key and position are captured from the death event; each tick increments
     * age until the owning manager removes the ceremony after its final scheduled effect.
     */
    private static final class DefeatCeremony
    {
        private final ResourceKey<Level> dimension;
        private final Vec3 position;
        private int age;

        /**
         * Creates a ceremony anchored to a dimension and position.
         *
         * @param dimension the dimension where the ceremony should play
         * @param position the immutable world position used as the effect center
         */
        private DefeatCeremony(@Nonnull final ResourceKey<Level> dimension, @Nonnull final Vec3 position)
        {
            this.dimension = dimension;
            this.position = position;
        }

        /**
         * Returns the dimension where this ceremony should tick.
         *
         * @return the ceremony dimension
         */
        private ResourceKey<Level> dimension()
        {
            return this.dimension;
        }

        /**
         * Returns the center position for this ceremony's sounds and particles.
         *
         * @return the ceremony center
         */
        private Vec3 position()
        {
            return this.position;
        }

        /**
         * Advances this ceremony by one server tick.
         */
        private void advance()
        {
            this.age++;
        }
    }
}
