package com.deathfrog.salvationmod.entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModEntityTypes;
import com.deathfrog.salvationmod.core.engine.ArchitecturalCorruption;
import com.deathfrog.salvationmod.core.engine.BlightSurfaceSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class CorruptionBoltEntity extends AbstractHurtingProjectile
{
    private static final float BASE_DAMAGE = 5.0F;
    private static final int EMBEDDED_LIFETIME_TICKS = 10 * 20;
    private static final int EMBEDDED_PARTICLE_INTERVAL = 8;
    @SuppressWarnings("null")
    private static final EntityDataAccessor<Boolean> PLAYER_SHOT =
        SynchedEntityData.defineId(CorruptionBoltEntity.class, EntityDataSerializers.BOOLEAN);
    @SuppressWarnings("null")
    private static final EntityDataAccessor<Float> PLAYER_DAMAGE =
        SynchedEntityData.defineId(CorruptionBoltEntity.class, EntityDataSerializers.FLOAT);
    @SuppressWarnings("null")
    private static final EntityDataAccessor<Integer> PUNCH_LEVEL =
        SynchedEntityData.defineId(CorruptionBoltEntity.class, EntityDataSerializers.INT);
    @SuppressWarnings("null")
    private static final EntityDataAccessor<Boolean> EMBEDDED =
        SynchedEntityData.defineId(CorruptionBoltEntity.class, EntityDataSerializers.BOOLEAN);
    private BlockPos supportingBlock;
    private BlockState supportingState;
    private int embeddedTicks;

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

    @SuppressWarnings("null")
    @Override
    protected void defineSynchedData(final @Nonnull SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(PLAYER_SHOT, false);
        builder.define(PLAYER_DAMAGE, BASE_DAMAGE);
        builder.define(PUNCH_LEVEL, 0);
        builder.define(EMBEDDED, false);
    }

    /** Configures combat values for a bolt fired from a player-held Voraxium Bow. */
    @SuppressWarnings("null")
    public void configurePlayerShot(final float damage, final int punchLevel)
    {
        this.entityData.set(PLAYER_SHOT, true);
        this.entityData.set(PLAYER_DAMAGE, Math.max(0.0F, damage));
        this.entityData.set(PUNCH_LEVEL, Math.max(0, punchLevel));
    }

    @SuppressWarnings("null")
    @Override
    public void addAdditionalSaveData(final @Nonnull CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PlayerShot", this.entityData.get(PLAYER_SHOT));
        tag.putFloat("PlayerDamage", this.entityData.get(PLAYER_DAMAGE));
        tag.putInt("PunchLevel", this.entityData.get(PUNCH_LEVEL));
        tag.putBoolean("Embedded", this.entityData.get(EMBEDDED));
        tag.putInt("EmbeddedTicks", embeddedTicks);
        if (supportingBlock != null)
        {
            tag.putLong("SupportingBlock", supportingBlock.asLong());
        }
    }

    @SuppressWarnings("null")
    @Override
    public void readAdditionalSaveData(final @Nonnull CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.entityData.set(PLAYER_SHOT, tag.getBoolean("PlayerShot"));
        if (tag.contains("PlayerDamage"))
        {
            this.entityData.set(PLAYER_DAMAGE, Math.max(0.0F, tag.getFloat("PlayerDamage")));
        }
        this.entityData.set(PUNCH_LEVEL, Math.max(0, tag.getInt("PunchLevel")));
        this.entityData.set(EMBEDDED, tag.getBoolean("Embedded"));
        embeddedTicks = Math.max(0, tag.getInt("EmbeddedTicks"));
        if (tag.contains("SupportingBlock"))
        {
            supportingBlock = BlockPos.of(tag.getLong("SupportingBlock"));
            supportingState = this.level().getBlockState(supportingBlock);
        }
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
        if (isEmbedded())
        {
            tickEmbedded();
            return;
        }

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

    /** Returns whether this bolt is currently acting as a temporary block-impact mark. */
    @SuppressWarnings("null")
    public boolean isEmbedded()
    {
        return this.entityData.get(EMBEDDED);
    }

    /** Returns the number of ticks elapsed since this bolt embedded itself. */
    public int getEmbeddedTicks()
    {
        return embeddedTicks;
    }

    /** Advances an embedded bolt without running projectile movement or collision logic. */
    @SuppressWarnings("null")
    private void tickEmbedded()
    {
        this.baseTick();
        this.setDeltaMovement(Vec3.ZERO);
        embeddedTicks++;

        if (!this.level().isClientSide())
        {
            if (embeddedTicks >= EMBEDDED_LIFETIME_TICKS || supportingBlock == null || supportingState == null
                || this.level().getBlockState(supportingBlock) != supportingState)
            {
                this.discard();
            }
            return;
        }

        if (embeddedTicks % EMBEDDED_PARTICLE_INTERVAL == 0)
        {
            this.level().addParticle(NullnessBridge.assumeNonnull(ParticleTypes.WITCH),
                this.getX(), this.getY(), this.getZ(), 0.0D, 0.01D, 0.0D);
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

        @SuppressWarnings("null")
        final float damage = this.entityData.get(PLAYER_SHOT)
            ? this.entityData.get(PLAYER_DAMAGE)
            : VoraxianStageScaling.scaleProjectileDamage(serverLevel, BASE_DAMAGE);
        if (target.hurt(source, damage) && owner instanceof LivingEntity livingOwner)
        {
            livingOwner.setLastHurtMob(target);
            applyPunch(target);
        }
    }

    /** Applies horizontal Punch knockback recorded by a player-fired bolt. */
    private void applyPunch(@Nonnull final Entity target)
    {
        @SuppressWarnings("null")
        final int punchLevel = this.entityData.get(PUNCH_LEVEL);
        final Vec3 movement = this.getDeltaMovement();
        final double horizontalLength = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        if (punchLevel <= 0 || horizontalLength <= 1.0E-7D)
        {
            return;
        }

        final double strength = punchLevel * 0.6D;
        target.push(movement.x / horizontalLength * strength, 0.1D, movement.z / horizontalLength * strength);
    }

    /**
     * Called when this entity hits a block.
     * 
     * @param hitResult the hit result, which contains information about the block that was hit
     */
    @SuppressWarnings("null")
    @Override
    protected void onHitBlock(final @Nonnull BlockHitResult hitResult)
    {
        super.onHitBlock(hitResult);
        if (this.level() instanceof ServerLevel serverLevel)
        {
            if (serverLevel.getBlockState(hitResult.getBlockPos()).is(Blocks.GRASS_BLOCK))
            {
                BlightSurfaceSystem.tryBlightGrassFromAttack(serverLevel, hitResult.getBlockPos());
            }
            else
            {
                ArchitecturalCorruption.tryCorrupt(serverLevel, hitResult.getBlockPos(), hitResult.getDirection(), this.getOwner());
            }
        }

        embedInBlock(hitResult);
    }

    /** Stops this projectile immediately outside the struck face and begins its temporary mark lifetime. */
    @SuppressWarnings("null")
    private void embedInBlock(@Nonnull final BlockHitResult hitResult)
    {
        final Vec3 normal = Vec3.atLowerCornerOf(hitResult.getDirection().getNormal());
        final Vec3 embeddedPosition = hitResult.getLocation().add(normal.scale(0.08D));
        this.setPos(embeddedPosition.x, embeddedPosition.y, embeddedPosition.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.noPhysics = true;
        this.supportingBlock = hitResult.getBlockPos().immutable();
        this.supportingState = this.level().getBlockState(supportingBlock);
        this.embeddedTicks = 0;
        this.entityData.set(EMBEDDED, true);
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
        if (!this.level().isClientSide() && !isEmbedded())
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
