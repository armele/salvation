package com.deathfrog.salvationmod.entity;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModEnchantments;
import com.deathfrog.salvationmod.ModEntityTypes;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.core.engine.CombatEffects;
import com.deathfrog.salvationmod.entity.goals.RandomFloatAroundGoal;
import com.deathfrog.salvationmod.entity.goals.VoraxianOverlordCombatGoal;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class VoraxianOverlordEntity extends Monster implements RangedAttackMob
{
    private static final double BASE_HEALTH = 320.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.34D;
    private static final double BASE_ATTACK_DAMAGE = 18.0D;
    private static final double BASE_FOLLOW_RANGE = 56.0D;
    private static final double BASE_ARMOR = 18.0D;
    private static final float RANGED_PHASE_HEALTH_FRACTION = 0.50F;
    private static final float DAMAGE_REDUCTION_MULTIPLIER = 0.22F;
    private static final float BYPASSING_DAMAGE_REDUCTION_MULTIPLIER = 0.45F;
    private static final float VORAXIUM_VULNERABILITY_MULTIPLIER = 2.25F;
    private static final float NON_DISRUPTION_DAMAGE_REDUCTION_MULTIPLIER = 0.80F;
    private static final float MELEE_DAMAGE_MULTIPLIER = 1.45F;
    private static final double CHILD_SUMMON_RADIUS = 5.0D;
    private static final double CHILD_CONSUME_RADIUS = 18.0D;
    private static final double CHILD_CONSUME_VERTICAL_RADIUS = 10.0D;

    @SuppressWarnings("null")
    private final ServerBossEvent bossEvent =
        new ServerBossEvent(Component.translatable("entity.salvation.voraxian_overlord"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS);

    private int nextSummonThreshold = 9;
    private int nextConsumeThreshold = 3;

    public VoraxianOverlordEntity(final EntityType<? extends Monster> type, final Level level)
    {
        super(type, level);
        this.xpReward = 250;
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setCreateWorldFog(true);
        this.bossEvent.setProgress(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return CombatEffects.corruptionAttributeEffects(null,
                BASE_HEALTH,
                BASE_MOVEMENT_SPEED,
                BASE_ATTACK_DAMAGE,
                BASE_FOLLOW_RANGE,
                BASE_ARMOR)
            .add(NullnessBridge.assumeNonnull(Attributes.FLYING_SPEED), BASE_MOVEMENT_SPEED)
            .add(NullnessBridge.assumeNonnull(Attributes.ARMOR_TOUGHNESS), 10.0D)
            .add(NullnessBridge.assumeNonnull(Attributes.KNOCKBACK_RESISTANCE), 0.85D)
            .add(NullnessBridge.assumeNonnull(Attributes.ATTACK_KNOCKBACK), 2.5D);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(1, new VoraxianOverlordCombatGoal(this));
        this.goalSelector.addGoal(6, new RandomFloatAroundGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractEntityCitizen.class, true));
    }

    @Override
    protected PathNavigation createNavigation(final @Nonnull Level level)
    {
        final FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    public void tick()
    {
        super.tick();
        VoraxianStageScaling.apply(this, BASE_HEALTH, BASE_ATTACK_DAMAGE, BASE_ARMOR);
        this.setNoGravity(true);

        final LivingEntity target = this.getTarget();
        if (target != null)
        {
            this.setYRot(this.yHeadRot);
            this.yBodyRot = this.yHeadRot;
        }
        else
        {
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();
        }
    }

    @Override
    protected void customServerAiStep()
    {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public void startSeenByPlayer(final @Nonnull ServerPlayer player)
    {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(final @Nonnull ServerPlayer player)
    {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @SuppressWarnings("null")
    @Override
    public void setCustomName(@Nullable final Component name)
    {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    protected void checkFallDamage(final double y, final boolean onGroundIn, final @Nonnull BlockState state, final @Nonnull BlockPos pos)
    {
        // Hovering overlords should not accumulate or apply fall damage.
    }

    @Override
    public boolean isAggressive()
    {
        return this.getTarget() != null;
    }

    public boolean isMeleePhase()
    {
        return this.getHealth() <= this.getMaxHealth() * RANGED_PHASE_HEALTH_FRACTION;
    }

    @Override
    public boolean hurt(final @Nonnull DamageSource source, final float amount)
    {
        final float adjustedAmount = this.adjustIncomingDamage(source, amount);
        if (adjustedAmount <= 0.0F)
        {
            return false;
        }

        final float oldHealth = this.getHealth();
        final boolean hit = super.hurt(source, adjustedAmount);
        if (hit && !this.level().isClientSide())
        {
            this.handleHealthThresholds(oldHealth, this.getHealth());
            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        }

        return hit;
    }

    @Override
    public boolean doHurtTarget(final @Nonnull Entity target)
    {
        final DamageSource source = CorruptionDamage.mobAttack(this);
        if (source == null)
        {
            return false;
        }

        final float damage = CorruptionDamage.getModifiedMeleeDamage(this, target, source) * MELEE_DAMAGE_MULTIPLIER;
        final boolean hit = target.hurt(source, damage);
        if (!hit)
        {
            return false;
        }

        final float knockback = this.getKnockback(target, source) + 1.0F;
        if (knockback > 0.0F && target instanceof LivingEntity livingTarget)
        {
            livingTarget.knockback(knockback * 0.7D,
                Mth.sin(this.getYRot() * ((float) Math.PI / 180.0F)),
                -Mth.cos(this.getYRot() * ((float) Math.PI / 180.0F)));
            final Vec3 slowedMomentum = this.getDeltaMovement().multiply(0.8D, 1.0D, 0.8D);
            this.setDeltaMovement(NullnessBridge.assumeNonnull(slowedMomentum));
        }

        CorruptionDamage.doPostMeleeAttackEffects(this, target, source);
        this.playAttackSound();
        return true;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(final @Nonnull ServerLevelAccessor level,
        final @Nonnull DifficultyInstance difficulty,
        final @Nonnull MobSpawnType reason,
        @Nullable final SpawnGroupData spawnData)
    {
        @SuppressWarnings("deprecation")
        final SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        return data;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return SoundEvents.GHAST_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(final @Nonnull DamageSource source)
    {
        return SoundEvents.GHAST_HURT;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return SoundEvents.GHAST_DEATH;
    }

    @Override
    protected float getSoundVolume()
    {
        return 1.1F;
    }

    @Override
    public void addAdditionalSaveData(final @Nonnull CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("NextSummonThreshold", this.nextSummonThreshold);
        tag.putInt("NextConsumeThreshold", this.nextConsumeThreshold);
    }

    @SuppressWarnings("null")
    @Override
    public void readAdditionalSaveData(final @Nonnull CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.contains("NextSummonThreshold"))
        {
            this.nextSummonThreshold = tag.getInt("NextSummonThreshold");
        }

        if (tag.contains("NextConsumeThreshold"))
        {
            this.nextConsumeThreshold = tag.getInt("NextConsumeThreshold");
        }

        this.bossEvent.setName(this.getDisplayName());
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public void performRangedAttack(@Nonnull LivingEntity target, float velocity)
    {
        final Level level = this.level();
        if (level.isClientSide() || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel))
        {
            return;
        }

        final double dx = target.getX() - this.getX();
        final double dy = target.getY(0.4D) - this.getY(0.4D);
        final double dz = target.getZ() - this.getZ();
        this.alignLookToTarget(dx, dy, dz, target);
        final Vec3 boltSpawn = this.getBoltSpawnPosition();

        final CorruptionBoltEntity bolt = new CorruptionBoltEntity(ModEntityTypes.CORRUPTION_BOLT.get(), level);
        bolt.setOwner(this);
        bolt.setPos(boltSpawn.x, boltSpawn.y, boltSpawn.z);
        bolt.shoot(dx, dy, dz, 1.2F, 1.0F);

        serverLevel.addFreshEntity(bolt);

        this.playSound(NullnessBridge.assumeNonnull(SoundEvents.ENDER_EYE_DEATH), 1.2F, 0.8F + this.getRandom().nextFloat() * 0.15F);
    }

    private void alignLookToTarget(final double dx, final double dy, final double dz, final @Nonnull LivingEntity target)
    {
        final double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        final float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        final float pitch = (float) (-(Mth.atan2(dy, horizontalDistance) * (180.0D / Math.PI)));

        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
        this.yHeadRot = yaw;
        this.yHeadRotO = yaw;
        this.yBodyRot = yaw;
        this.yBodyRotO = yaw;
        this.getLookControl().setLookAt(target, 360.0F, 360.0F);
    }

    private Vec3 getBoltSpawnPosition()
    {
        final Vec3 look = this.getViewVector(1.0F).normalize();
        final Vec3 bodyCenter = this.position().add(0.0D, this.getBbHeight() * 0.58D, 0.0D);

        return bodyCenter.add(NullnessBridge.assumeNonnull(look.scale(1.15D)));
    }

    private float adjustIncomingDamage(final @Nonnull DamageSource source, final float amount)
    {
        final Entity attacker = source.getEntity();
        if (attacker instanceof Player player && player.getAbilities().instabuild)
        {
            return amount;
        }

        final float disruptionMultiplier = this.hasCorruptionDisruption(source) ? 1.0F : NON_DISRUPTION_DAMAGE_REDUCTION_MULTIPLIER;

        if (this.isVoraxiumWeaponHit(source))
        {
            return amount * VORAXIUM_VULNERABILITY_MULTIPLIER * disruptionMultiplier;
        }

        final boolean bypassingProtection = source.getDirectEntity() == null;
        return amount * (bypassingProtection ? BYPASSING_DAMAGE_REDUCTION_MULTIPLIER : DAMAGE_REDUCTION_MULTIPLIER) * disruptionMultiplier;
    }

    private boolean isVoraxiumWeaponHit(final @Nonnull DamageSource source)
    {
        if (!(source.getEntity() instanceof LivingEntity attacker))
        {
            return false;
        }

        final ItemStack weapon = attacker.getMainHandItem();
        return weapon.is(ModTags.Items.OVERLORD_VULNERABLE_WEAPONS);
    }

    private boolean hasCorruptionDisruption(final @Nonnull DamageSource source)
    {
        if (!(source.getEntity() instanceof LivingEntity attacker))
        {
            return false;
        }

        final ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty())
        {
            return false;
        }

        return ModEnchantments.getAppliedEnchantmentLevel(this.level(), weapon, ModEnchantments.CORRUPTION_DISRUPTION) > 0;
    }

    private void handleHealthThresholds(final float oldHealth, final float newHealth)
    {
        final float maxHealth = this.getMaxHealth();
        final float oldFraction = oldHealth / maxHealth;
        final float newFraction = newHealth / maxHealth;

        while (this.nextSummonThreshold > 0)
        {
            final float thresholdFraction = this.nextSummonThreshold / 10.0F;
            if (!(oldFraction > thresholdFraction && newFraction <= thresholdFraction))
            {
                break;
            }

            this.spawnRandomChildrenWave();
            this.nextSummonThreshold--;
        }

        while (this.nextConsumeThreshold > 0)
        {
            final float thresholdFraction = this.nextConsumeThreshold / 4.0F;
            if (!(oldFraction > thresholdFraction && newFraction <= thresholdFraction))
            {
                break;
            }

            this.consumeNearbyVoraxians();
            this.nextConsumeThreshold--;
        }
    }

    private void spawnRandomChildrenWave()
    {
        final int spawnCount = this.rollD4() + this.rollD4() + 4;
        for (int i = 0; i < spawnCount; i++)
        {
            this.spawnRandomVoraxian();
        }
    }

    private int rollD4()
    {
        return this.getRandom().nextInt(4) + 1;
    }

    /**
     * Spawns a random child entity at a random position around the entity.
     * 
     * The child entity will be spawned at a radius of 2.0 to 6.0 blocks away from the entity,
     * and at a height of 0.75 to 2.25 blocks above or below the entity.
     * 
     * The child entity will be set to persist even after being removed from the world.
     * 
     * If the entity has a target set, the child entity will be set to target that entity.
     * 
     * A sound effect will be played when the child entity is spawned.
     */
    private void spawnRandomVoraxian()
    {
        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        final Monster child = switch (this.getRandom().nextInt(3))
        {
            case 0 -> new VoraxianMawEntity(ModEntityTypes.VORAXIAN_MAW.get(), serverLevel);
            case 1 -> new VoraxianObserverEntity(ModEntityTypes.VORAXIAN_OBSERVER.get(), serverLevel);
            default -> new VoraxianStingerEntity(ModEntityTypes.VORAXIAN_STINGER.get(), serverLevel);
        };

        final double angle = this.getRandom().nextDouble() * (Math.PI * 2.0D);
        final double radius = 2.0D + this.getRandom().nextDouble() * CHILD_SUMMON_RADIUS;
        final double x = this.getX() + Math.cos(angle) * radius;
        final double y = this.getY() + this.getRandom().nextDouble() * 2.5D - 0.75D;
        final double z = this.getZ() + Math.sin(angle) * radius;

        child.moveTo(x, y, z, this.getRandom().nextFloat() * 360.0F, 0.0F);
        child.setPersistenceRequired();
        final LivingEntity bossTarget = this.getTarget();
        if (bossTarget != null)
        {
            child.setTarget(bossTarget);
        }

        serverLevel.addFreshEntity(child);
        this.playSound(NullnessBridge.assumeNonnull(SoundEvents.END_PORTAL_SPAWN), 1.0F, 0.85F + this.getRandom().nextFloat() * 0.25F);
    }

    /**
     * Consumes all nearby Voraxian entities within a certain radius of the entity. This will heal the entity for a total amount of health equal to the combined health of all the consumed entities.
     *
     * @see #CHILD_CONSUME_RADIUS
     * @see #CHILD_CONSUME_VERTICAL_RADIUS
     */
    private void consumeNearbyVoraxians()
    {
        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        final AABB searchBox = this.getBoundingBox().inflate(CHILD_CONSUME_RADIUS,
            CHILD_CONSUME_VERTICAL_RADIUS,
            CHILD_CONSUME_RADIUS);

        if (searchBox == null)
        {
            return;
        }

        final List<LivingEntity> nearbyVoraxians = serverLevel.getEntitiesOfClass(LivingEntity.class,
            searchBox,
            entity -> entity.isAlive() && this.isConsumableVoraxian(entity));

        if (nearbyVoraxians.isEmpty())
        {
            return;
        }

        float totalHealing = 0.0F;
        final DamageSource consumeSource = CorruptionDamage.source(serverLevel);

        if (consumeSource == null)
        {
            return;
        }

        for (final LivingEntity voraxian : nearbyVoraxians)
        {
            totalHealing += voraxian.getHealth();
            this.spawnConsumptionParticleStream(serverLevel, voraxian);
            voraxian.hurt(consumeSource, Float.MAX_VALUE);
            if (voraxian.isAlive())
            {
                voraxian.discard();
            }
        }

        if (totalHealing > 0.0F)
        {
            this.heal(totalHealing);
        }

        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        serverLevel.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SCULK_SOUL),
            this.getX(),
            this.getY(this.getBbHeight() * 0.55D),
            this.getZ(),
            28,
            0.9D,
            1.0D,
            0.9D,
            0.05D);
        this.playSound(NullnessBridge.assumeNonnull(SoundEvents.WARDEN_HEARTBEAT), 1.8F, 0.7F);
    }

    /**
     * Spawn a particle stream to indicate the consumption of a Voraxian entity.
     * <p>
     * The particle stream is a gradient of SCULK_SOUL and SMOKE particles that is
     * emitted between the consumed entity and the Voraxian Overlord.
     * <p>
     * The particle stream is intended to be a visual cue to indicate that the
     * Voraxian Overlord is consuming a Voraxian entity.
     * <p>
     * The particle stream is emitted on the server and is visible to all clients.
     * <p>
     * The particle stream is emitted at a rate of 1 particle per step, with the
     * number of steps being randomly chosen between 8 and 12 (inclusive).
     * <p>
     * The particle stream is emitted for a duration of 1 second.
     * <p>
     * The particle stream is intended to be a visual cue to indicate that the
     * Voraxian Overlord is consuming a Voraxian entity.
     */
    @SuppressWarnings("null")
    private void spawnConsumptionParticleStream(final @Nonnull ServerLevel serverLevel, final @Nonnull LivingEntity consumedEntity)
    {
        final Vec3 start = consumedEntity.position().add(0.0D, consumedEntity.getBbHeight() * 0.55D, 0.0D);
        final Vec3 end = this.position().add(0.0D, this.getBbHeight() * 0.58D, 0.0D);
        final Vec3 delta = end.subtract(start);
        final int steps = 8 + this.getRandom().nextInt(5);

        for (int i = 0; i <= steps; i++)
        {
            final double progress = i / (double) steps;
            final Vec3 sample = start.add(delta.scale(progress));
            serverLevel.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SCULK_SOUL),
                sample.x,
                sample.y,
                sample.z,
                1,
                0.02D,
                0.02D,
                0.02D,
                0.0D);
            serverLevel.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SMOKE),
                sample.x,
                sample.y,
                sample.z,
                1,
                0.01D,
                0.01D,
                0.01D,
                0.0D);
        }
    }

    /**
     * Determines if the given entity is a consumable Voraxian entity.
     * <p>
     * An entity is considered consumable if it is a Voraxian entity and not the same as the entity calling this method.
     * <p>
     *
     * @param entity the entity to check
     * @return true if the entity is a consumable Voraxian entity, false otherwise
     */
    private boolean isConsumableVoraxian(final @Nonnull LivingEntity entity)
    {
        return entity != this && entity.getType().is(ModTags.Entities.VORAXIAN_MINION);
    }
}
