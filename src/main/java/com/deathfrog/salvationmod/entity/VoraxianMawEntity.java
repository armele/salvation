package com.deathfrog.salvationmod.entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModEntityTypes;
import com.deathfrog.salvationmod.core.engine.CombatEffects;
import com.deathfrog.salvationmod.entity.goals.AggressiveFloatTowardsTargetGoal;
import com.deathfrog.salvationmod.entity.goals.OccasionalThreatChompGoal;
import com.deathfrog.salvationmod.entity.goals.RandomFloatAroundGoal;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class VoraxianMawEntity extends Monster implements RangedAttackMob
{
    private static final double BASE_HEALTH = 25.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.33D;
    private static final double BASE_ATTACK_DAMAGE = 4.0D;
    private static final double BASE_FOLLOW_RANGE = 28.0D;
    private static final double BASE_ARMOR = 3.0D;

    @SuppressWarnings("null")
    private @Nonnull static final EntityDataAccessor<Integer> CHOMP_TICKS =
        SynchedEntityData.defineId(VoraxianMawEntity.class, NullnessBridge.assumeNonnull(EntityDataSerializers.INT));
    @SuppressWarnings("null")
    private @Nonnull static final EntityDataAccessor<Integer> THREAT_CHOMP_TICKS =
        SynchedEntityData.defineId(VoraxianMawEntity.class, NullnessBridge.assumeNonnull(EntityDataSerializers.INT));

    private static final int ATTACK_CHOMP_DURATION = 8;
    private static final int THREAT_CHOMP_DURATION = 20;

    public VoraxianMawEntity(final EntityType<? extends Monster> type, final Level level)
    {
        super(type, level);
        this.xpReward = 8;
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(final @Nonnull SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(CHOMP_TICKS, 0);
        builder.define(THREAT_CHOMP_TICKS, 0);
    }

    // -------- Attributes --------

    public static AttributeSupplier.Builder createAttributes()
    {
        return CombatEffects.corruptionAttributeEffects(null,
                BASE_HEALTH,
                BASE_MOVEMENT_SPEED,
                BASE_ATTACK_DAMAGE,
                BASE_FOLLOW_RANGE,
                BASE_ARMOR)
            .add(NullnessBridge.assumeNonnull(Attributes.FLYING_SPEED), BASE_MOVEMENT_SPEED);
    }

    // -------- Goals (Zombie-ish melee) --------

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(1, new AggressiveFloatTowardsTargetGoal<>(this));
        this.goalSelector.addGoal(6, new RandomFloatAroundGoal(this));
        this.goalSelector.addGoal(9, new OccasionalThreatChompGoal(this, 10.0D, 180, 30));

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

        final int chompTicks = this.entityData.get(CHOMP_TICKS);
        if (chompTicks > 0)
        {
            this.entityData.set(CHOMP_TICKS, chompTicks - 1);
        }

        final int threatChompTicks = this.entityData.get(THREAT_CHOMP_TICKS);
        if (threatChompTicks > 0)
        {
            this.entityData.set(THREAT_CHOMP_TICKS, threatChompTicks - 1);
        }

        final LivingEntity target = this.getTarget();
        if (target != null)
        {
            // While engaging a target, let the whole body follow the tracked look direction.
            this.setYRot(this.yHeadRot);
            this.yBodyRot = this.yHeadRot;
        }
        else
        {
            // When idle, let travel direction determine facing and keep the head/body aligned to it.
            this.yBodyRot = this.getYRot();
            this.yBodyRotO = this.getYRot();
            this.yHeadRot = this.getYRot();
            this.yHeadRotO = this.getYRot();
        }
    }

    @Override
    protected void checkFallDamage(final double y, final boolean onGroundIn, final @Nonnull BlockState state, final @Nonnull BlockPos pos)
    {
        // Hovering maws should not accumulate or apply fall damage.
    }
    
    /**
     * Checks if the entity is aggressive (i.e. has a target set) and thus should do the "attack swing" animation.
     *
     * @return true if the entity is aggressive, false otherwise
     */
    @Override
    public boolean isAggressive()
    {
        // Used by the model to decide whether to do the “attack swing”
        return this.getTarget() != null;
    }

    public float getAttackChompProgress()
    {
        final int remainingTicks = this.entityData.get(CHOMP_TICKS);
        if (remainingTicks <= 0)
        {
            return 0.0F;
        }

        return remainingTicks / (float) ATTACK_CHOMP_DURATION;
    }

    public float getThreatChompProgress()
    {
        final int remainingTicks = this.entityData.get(THREAT_CHOMP_TICKS);
        if (remainingTicks <= 0)
        {
            return 0.0F;
        }

        return remainingTicks / (float) THREAT_CHOMP_DURATION;
    }

    private void triggerAttackChomp()
    {
        this.entityData.set(CHOMP_TICKS, ATTACK_CHOMP_DURATION);
    }

    public void triggerThreatChomp()
    {
        this.entityData.set(THREAT_CHOMP_TICKS, THREAT_CHOMP_DURATION);
    }

    // -------- Spawn / finalize --------

    /**
     * Called after the entity has been spawned and initialized. Can be used to apply random variance to the entity's attributes.
     *
     * @param level      the level the entity is spawned in
     * @param difficulty the difficulty of the level
     * @param reason     the reason the entity is being spawned
     * @param spawnData  the spawn data of the entity
     * @return the spawn data of the entity, possibly modified
     */
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(final @Nonnull ServerLevelAccessor level,
        final @Nonnull DifficultyInstance difficulty,
        final @Nonnull MobSpawnType reason,
        @Nullable final SpawnGroupData spawnData)
    {
        @SuppressWarnings("deprecation")
        final SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);

        // Leave hook with no op for now.

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
        return 0.9F;
    }

    /**
     * Performs a ranged attack on the target entity.
     * 
     * @param target the entity to attack
     * @param velocity the velocity of the attack
     */
    @Override
    public void performRangedAttack(@Nonnull LivingEntity target, float velocity)
    {
        final Level level = this.level();
        if (level.isClientSide() || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel))
        {
            return;
        }

        this.triggerAttackChomp();

        final double dx = target.getX() - this.getX();
        final double dy = target.getY(0.4D) - this.getY(0.4D);
        final double dz = target.getZ() - this.getZ();
        this.alignLookToTarget(dx, dy, dz, target);
        final Vec3 boltSpawn = this.getBoltSpawnPosition();

        final CorruptionBoltEntity bolt = new CorruptionBoltEntity(ModEntityTypes.CORRUPTION_BOLT.get(), level);
        bolt.setOwner(this);
        bolt.setPos(boltSpawn.x, boltSpawn.y, boltSpawn.z);
        bolt.shoot(dx, dy, dz, 1.1F, 2.0F);

        serverLevel.addFreshEntity(bolt);

        this.playSound(NullnessBridge.assumeNonnull(SoundEvents.ENDER_EYE_DEATH), 1.0F, 0.9F + this.getRandom().nextFloat() * 0.2F);
    }

    /**
     * Aligns the entity's look direction to target the given entity.
     *
     * @param dx the x delta from the entity to the target
     * @param dy the y delta from the entity to the target
     * @param dz the z delta from the entity to the target
     * @param target the entity to target
     */
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

    /**
     * Returns the position at which the bolts should spawn relative to the maw's body.
     * The maw's eye is modeled on the front face of the body, not at the vanilla mob eye height.
     * @return the position at which bolts should spawn
     */
    private Vec3 getBoltSpawnPosition()
    {
        final Vec3 look = this.getViewVector(1.0F).normalize();
        final Vec3 bodyCenter = this.position().add(0.0D, this.getBbHeight() * 0.46D, 0.0D);

        // The maw eye is modeled on the front face of the body, not at the vanilla mob eye height.
        return bodyCenter.add(NullnessBridge.assumeNonnull(look.scale(0.52D)));
    }
}
