package com.deathfrog.salvationmod.entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.core.engine.CombatEffects;
import com.deathfrog.salvationmod.entity.goals.FollowAnimalGoal;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public class CorruptedCatEntity extends Monster
{
    public CorruptedCatEntity(final EntityType<? extends Monster> type, final Level level)
    {
        super(type, level);
        this.xpReward = 6;
    }

    // -------- Attributes --------

    public static AttributeSupplier.Builder createAttributes()
    {
        return CombatEffects.corruptionAttributeEffects(null, 20.0D, .30D, 2.0D, 28.0D, 1.0D);
    }

    // -------- Goals (Zombie-ish melee) --------

    @Override
    protected void registerGoals()
    {
        // Movement / idle
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.15D, false)); // speed multiplier while attacking
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9,
            new FollowAnimalGoal<>(
                this,
                Cat.class,
                1.0D, 6.0F, 14.0F,
                0.35F,
                true,
                FollowAnimalGoal.TargetSelection.NEAREST,
                null
            )
        );
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        // Targeting
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractEntityCitizen.class, true));
    }


    /**
     * Checks if the Corrupted entity is aggressive (i.e. has a target set)
     * and thus should do the "attack swing" animation.
     *
     * @return true if the entity is aggressive, false otherwise
     */
    @Override
    public boolean isAggressive()
    {
        // Used by the model to decide whether to do the “attack swing”
        return this.getTarget() != null;
    }

    // -------- Spawn / finalize --------

    /**
     * Called after the entity has been spawned and initialized.
     * Can be used to apply random variance to the entity's attributes.
     *
     * @param level the level the entity is spawned in
     * @param difficulty the difficulty of the level
     * @param reason the reason the entity is being spawned
     * @param spawnData the spawn data of the entity
     * @return the spawn data of the entity, possibly modified
     */
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
        final @Nonnull ServerLevelAccessor level,
        final @Nonnull DifficultyInstance difficulty,
        final @Nonnull MobSpawnType reason,
        @Nullable final SpawnGroupData spawnData)
    {
        @SuppressWarnings("deprecation")
        final SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);

        // Leave hook with no op for now.

        return data;
    }

    // -------- Sounds

    @Override
    protected SoundEvent getAmbientSound()
    {
        return SoundEvents.CAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(final @Nonnull DamageSource source)
    {
        return SoundEvents.CAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return SoundEvents.CAT_DEATH;
    }

    @Override
    protected void playStepSound(final @Nonnull BlockPos pos, final @Nonnull BlockState state)
    {
        // Use vanilla step or something squishier later
        this.playSound(NullnessBridge.assumeNonnull(SoundEvents.CAT_AMBIENT), 0.15F, 1.0F);
    }

    @Override
    protected float getSoundVolume()
    {
        return 0.9F;
    }
}