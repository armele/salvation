package com.deathfrog.salvationmod.entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModEntityTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class CorruptionBoltEntity extends AbstractHurtingProjectile
{
    private static final float BASE_DAMAGE = 5.0F;

    public CorruptionBoltEntity(final EntityType<? extends CorruptionBoltEntity> type, final Level level)
    {
        super(type, level);
    }

    public CorruptionBoltEntity(final Level level, final LivingEntity shooter, final Vec3 movement)
    {
        super(ModEntityTypes.CORRUPTION_BOLT.get(), shooter, movement, level);
    }

    public CorruptionBoltEntity(final Level level, final double x, final double y, final double z, final Vec3 movement)
    {
        super(ModEntityTypes.CORRUPTION_BOLT.get(), x, y, z, movement, level);
    }

    @Override
    protected boolean shouldBurn()
    {
        return false;
    }

    @Nullable
    @Override
    protected ParticleOptions getTrailParticle()
    {
        return ParticleTypes.WITCH;
    }

    @Override
    protected float getInertia()
    {
        return 0.98F;
    }

    /**
     * Ticks the entity to draw a trail of particles behind it.
     *
     * The trail is drawn by sampling the entity's movement vector at 4 points and drawing a particle at each point.
     * The offset from the entity's position is calculated based on the progress of the sample and the length of the movement vector.
     * The particle is drawn at the sampled position with a velocity opposite to the entity's movement.
     *
     * This method is only called on the client side.
     */
    @Override
    public void tick()
    {
        super.tick();

        if (!this.level().isClientSide())
        {
            return;
        }

        final Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-5D)
        {
            return;
        }

        final Vec3 direction = motion.normalize();
        final int samples = 4;
        for (int i = 0; i < samples; i++)
        {
            final double progress = i / (double) samples;
            final double offset = 0.18D + (progress * 0.75D);
            Vec3 scale = direction.scale(offset);
            final Vec3 sample = this.position().subtract(NullnessBridge.assumeNonnull(scale));

            this.level().addParticle(NullnessBridge.assumeNonnull(ParticleTypes.WITCH),
                sample.x,
                sample.y,
                sample.z,
                motion.x * -0.02D,
                motion.y * -0.02D,
                motion.z * -0.02D);
        }
    }

    /**
     * Called when this entity hits another entity.
     * 
     * @param hitResult the hit result, which contains information about the entity that was hit
     */
    @Override
    protected void onHitEntity(final @Nonnull EntityHitResult hitResult)
    {
        super.onHitEntity(hitResult);

        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        final Entity target = hitResult.getEntity();
        final Entity owner = this.getOwner();
        final DamageSource source = CorruptionDamage.projectile(serverLevel, this, owner);

        if (source == null)
        {
            return;
        }

        final float damage = VoraxianStageScaling.scaleProjectileDamage(serverLevel, BASE_DAMAGE);
        if (target.hurt(source, damage) && owner instanceof LivingEntity livingOwner)
        {
            livingOwner.setLastHurtMob(target);
        }
    }

    /**
     * Called when this entity hits a block.
     * 
     * @param hitResult the hit result, which contains information about the block that was hit
     */
    @Override
    protected void onHitBlock(final @Nonnull BlockHitResult hitResult)
    {
        super.onHitBlock(hitResult);
    }

    /**
     * Called when this entity hits something.
     * 
     * @param hitResult the hit result, which contains information about what was hit
     */
    @Override
    protected void onHit(final @Nonnull HitResult hitResult)
    {
        super.onHit(hitResult);
        if (!this.level().isClientSide())
        {
            this.discard();
        }
    }

    /**
     * Overridden to always return false, as the entity should not be hurtable.
     * This is important for the bolt's AI to work correctly.
     * 
     * @param source the source of the damage
     * @param amount the amount of damage to apply
     * @return false, to indicate that the entity is not hurtable
     */
    @Override
    public boolean hurt(final @Nonnull DamageSource source, final float amount)
    {
        return false;
    }
}
