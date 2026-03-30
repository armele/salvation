package com.deathfrog.salvationmod.entity;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.SalvationMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

public final class CorruptionDamage
{
    @SuppressWarnings("null")
    public static final ResourceKey<DamageType> CORRUPTION = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corruption")
    );

    private CorruptionDamage()
    {
    }

    /**
     * Returns a new {@link DamageSource} that represents a corruption damage source caused by an attacker.
     * The type of the damage source is {@link #CORRUPTION}.
     *
     * @param attacker the entity that is attacking
     * @return a new {@link DamageSource} that represents a corruption damage source caused by the attacker
     */
    public static DamageSource mobAttack(final @Nonnull Mob attacker)
    {
        Level level = attacker.level();

        if (level == null)
        {
            return null;
        }

        return new DamageSource(lookup(level), attacker);
    }

    /**
     * Returns a new {@link DamageSource} that represents a corruption damage source caused by a projectile.
     * The type of the damage source is {@link #CORRUPTION}.
     *
     * @param level the level that the damage source will be used in
     * @param directEntity the entity that the projectile directly hit
     * @param causingEntity the entity that caused the projectile to be fired
     * @return a new {@link DamageSource} that represents a corruption damage source caused by a projectile
     */
    public static DamageSource projectile(final @Nonnull Level level, final @Nonnull Entity directEntity, final Entity causingEntity)
    {
        return new DamageSource(lookup(level), directEntity, causingEntity);
    }

    /**
     * Returns a new {@link DamageSource} that represents raw corruption energy
     * without a direct attacker.
     *
     * @param level the level that the damage source will be used in
     * @return a new corruption damage source for the given level
     */
    public static DamageSource source(final @Nonnull Level level)
    {
        return new DamageSource(lookup(level));
    }

    /**
     * Returns whether the given {@link DamageSource} is a corruption damage source.
     *
     * A damage source is considered a corruption damage source if it is either a {@link DamageSource} with the type {@link #CORRUPTION} or it is a {@link DamageSource} with the type {@link ModTags.DamageTypes#CORRUPTION_DAMAGE}.
     *
     * @param source the damage source to check
     * @return whether the given damage source is a corruption damage source
     */
    public static boolean isCorruptionDamage(final @Nonnull DamageSource source)
    {
        return source.is(ModTags.DamageTypes.CORRUPTION_DAMAGE) || source.is(NullnessBridge.assumeNonnull(CORRUPTION));
    }

    /**
     * Gets the modified melee damage of an attacker on a target with a given source.
     * This method first gets the base melee damage of the attacker, then applies any relevant damage modifiers from enchantments on the attacker's weapon item.
     * If the attacker is in a ServerLevel, it will call EnchantmentHelper#modifyDamage.
     * @param attacker the mob that is attacking
     * @param target the entity that is being attacked
     * @param source the damage source of the attack
     * @return the modified melee damage of the attacker on the target with the given source
     */
    public static float getModifiedMeleeDamage(final @Nonnull Mob attacker, final @Nonnull Entity target, final @Nonnull DamageSource source)
    {
        float damage = (float) attacker.getAttributeValue(NullnessBridge.assumeNonnull(Attributes.ATTACK_DAMAGE));
        if (attacker.level() instanceof ServerLevel serverLevel)
        {
            damage = EnchantmentHelper.modifyDamage(serverLevel, attacker.getWeaponItem(), target, source, damage);
        }

        return damage;
    }

    /**
     * Handles the post-melee attack effects on the given target and damage source.
     * If the attacker is in a ServerLevel, it will call EnchantmentHelper#doPostAttackEffects.
     * Finally, it sets the last hurt mob of the attacker to the given target.
     *
     * @param attacker the mob that is attacking
     * @param target the entity that is being attacked
     * @param source the damage source of the attack
     */
    public static void doPostMeleeAttackEffects(final @Nonnull Mob attacker, final @Nonnull Entity target, final @Nonnull DamageSource source)
    {
        if (attacker.level() instanceof ServerLevel serverLevel)
        {
            EnchantmentHelper.doPostAttackEffects(serverLevel, target, source);
        }

        attacker.setLastHurtMob(target);
    }

    /**
     * Returns the Holder of DamageType for the given Level.
     * If the Level does not have the required registry access, it will throw an exception.
     *
     * @param level the Level to lookup the DamageType for
     * @return the Holder of DamageType for the given Level
     */
    private static @Nonnull Holder<DamageType> lookup(final @Nonnull Level level)
    {
        Holder<DamageType> damageType = level.registryAccess().lookupOrThrow(NullnessBridge.assumeNonnull(Registries.DAMAGE_TYPE)).getOrThrow(NullnessBridge.assumeNonnull(CORRUPTION));

        return NullnessBridge.assumeNonnull(damageType);
    }
}
