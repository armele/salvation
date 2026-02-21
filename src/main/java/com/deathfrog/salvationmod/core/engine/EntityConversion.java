package com.deathfrog.salvationmod.core.engine;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModAttachments;
import com.deathfrog.salvationmod.ModAttachments.ConversionData;
import com.deathfrog.salvationmod.SalvationMod;
import com.mojang.logging.LogUtils;

public final class EntityConversion
{
    private static final int CONVERSION_DURATION = 20 * 15; // 15 seconds
    public static final Logger LOGGER = LogUtils.getLogger();
    
    private EntityConversion()
    {}

    /**
     * Attempts to convert a {@link LivingEntity} from one type to another. This method will copy some basic properties such as
     * position, rotation, custom name visibility, and some flags. It will NOT copy any attributes or variant data.
     * <p>This method is mainly useful for converting entities between different mods.
     * <p>The provided {@link Consumer} will be called with the newly created entity as an argument. This can be used to set any
     * additional data such as attributes, variant data, or custom AI goals.
     * <p>If the provided entity's level is not an instance of {@link ServerLevel}, this method will return null.
     * <p>If the provided {@link EntityType} is unable to create an entity in the provided level, this method will return null.
     * <p>If the provided {@link Consumer} is null, it will be ignored.
     * <p>This method will spawn the newly created entity and remove the original entity from the world.
     * <p>If the original entity is currently being ridden, you may want to call {@link LivingEntity#stopRiding()} first to eject all
     * passengers.
     *
     * @param source               the entity to convert
     * @param targetType           the type of entity to convert to
     * @param postCreateCustomizer an optional consumer that will be called with the newly created entity as an argument
     * @return the newly created entity, or null if the conversion failed
     */
    public static <T extends LivingEntity> T convertLivingEntity(final LivingEntity source,
        final EntityType<T> targetType,
        final Consumer<T> postCreateCustomizer)
    {
        if (!(source.level() instanceof ServerLevel level)) return null;

        final T target = targetType.create(level);
        if (target == null) return null;

        // Basic spatial/pose setup
        final Vec3 pos = source.position();
        target.moveTo(pos.x, pos.y, pos.z, source.getYRot(), source.getXRot());
        target.setYBodyRot(source.getVisualRotationYInDegrees());
        target.setYHeadRot(source.getYHeadRot());

        // Common flags
        if (source.hasCustomName()) target.setCustomName(source.getCustomName());
        target.setCustomNameVisible(source.isCustomNameVisible());

        target.setSilent(source.isSilent());
        target.setNoGravity(source.isNoGravity());
        target.setInvulnerable(source.isInvulnerable());
        target.setGlowingTag(source.isCurrentlyGlowing());

        if (target instanceof Mob targetMob && source instanceof Mob sourceMob)
        {
            targetMob.setPersistenceRequired();
            targetMob.setLeftHanded(sourceMob.isLeftHanded());
            targetMob.setNoAi(sourceMob.isNoAi());

            // Age (baby/adult) if possible
            if (targetMob instanceof AgeableMob targetAge && sourceMob instanceof AgeableMob sourceAge)
            {
                targetAge.setAge(sourceAge.getAge());
            }

            // Leash transfer (simple case)
            if (sourceMob.isLeashed())
            {
                final Entity holder = sourceMob.getLeashHolder();

                if (holder != null)
                {
                    // drop leash from source first
                    sourceMob.dropLeash(true, true);
                    targetMob.setLeashedTo(holder, true); 
                }
            }
        }

        // Preserve health percentage
        final float srcMax = source.getMaxHealth();
        final float srcHealth = source.getHealth();
        final float pct = (srcMax <= 0f) ? 1f : (srcHealth / srcMax);

        final float tgtMax = target.getMaxHealth();
        target.setHealth(Math.max(1.0f, Math.min(tgtMax, tgtMax * pct)));

        // Allow caller to set variant data, attributes, etc.
        if (postCreateCustomizer != null) postCreateCustomizer.accept(target);

        // Stop riding / eject passengers first
        source.stopRiding();

        // Spawn & remove
        level.addFreshEntity(target);

        source.discard();

        return target;
    }

    // Effects
    /** 
     * Stages let us reuse the same helper for "start", "during", and "finish". 
     * 
     */
    public enum ConversionFxPhase
    {
        START,      // first application of feed/potion
        TICK,       // periodic while cleansing
        FINAL_BURST // right at conversion
    }


    /**
     * Server-side helper to broadcast cure/corrupt VFX/SFX and apply a brief "shake" motion that is replicated to clients via
     * normal entity movement/rotation sync.
     */
    public static void playConversionEffects(final ServerLevel level,
                                            final LivingEntity entity,
                                            final ConversionFxPhase phase,
                                            final boolean isCuring)
    {
        final RandomSource r = entity.getRandom();
        final Vec3 pos = entity.position();
        final double x = pos.x;
        final double y = pos.y + (entity.getBbHeight() * 0.55);
        final double z = pos.z;

        // =========================
        // CURING PATH (existing)
        // =========================
        if (isCuring)
        {
            // ----- Sounds -----
            switch (phase)
            {
                case START -> level.playSound(null, x, y, z,
                    NullnessBridge.assumeNonnull(SoundEvents.ENCHANTMENT_TABLE_USE),
                    SoundSource.NEUTRAL, 0.7f, 1.25f + (r.nextFloat() * 0.15f));
                case TICK -> {
                    if (r.nextInt(6) == 0)
                    {
                        level.playSound(null, x, y, z,
                            NullnessBridge.assumeNonnull(SoundEvents.BREWING_STAND_BREW),
                            SoundSource.NEUTRAL, 0.25f, 1.6f + (r.nextFloat() * 0.2f));
                    }
                }
                case FINAL_BURST -> level.playSound(null, x, y, z,
                    NullnessBridge.assumeNonnull(SoundEvents.ZOMBIE_VILLAGER_CURE),
                    SoundSource.NEUTRAL, 0.9f, 1.0f + (r.nextFloat() * 0.1f));
            }

            // ----- Particles -----
            if (phase == ConversionFxPhase.START)
            {
                level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER), x, y, z, 10, 0.35, 0.35, 0.35, 0.02);
                level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.POOF),          x, y, z,  6, 0.25, 0.25, 0.25, 0.01);
            }
            else if (phase == ConversionFxPhase.TICK)
            {
                level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER), x, y, z, 3, 0.25, 0.25, 0.25, 0.01);

                if (r.nextInt(4) == 0)
                {
                    level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SMOKE), x, y, z, 2, 0.15, 0.15, 0.15, 0.0);
                }
            }
            else // FINAL_BURST
            {
                level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER), x, y, z, 22, 0.45, 0.45, 0.45, 0.03);
                level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.POOF),          x, y, z, 18, 0.35, 0.35, 0.35, 0.02);
                level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.END_ROD),       x, y, z, 10, 0.25, 0.35, 0.25, 0.01);
            }

            // ----- Shake -----
            if (phase != ConversionFxPhase.FINAL_BURST)
            {
                applyShakeJitter(level, entity, phase == ConversionFxPhase.START ? 0.9f : 0.6f);
            }
            return;
        }

        // =========================
        // CORRUPTING PATH (new)
        // Ominous, dark, “infection taking hold”
        // =========================

        // ----- Sounds -----
        switch (phase)
        {
            case START -> {
                // “Rift opens / bad magic begins” — low, unsettling
                level.playSound(null, x, y, z,
                    NullnessBridge.assumeNonnull(SoundEvents.ENDERMAN_STARE),
                    SoundSource.NEUTRAL,
                    0.55f,
                    0.75f + (r.nextFloat() * 0.1f));

                // A faint echo to make it feel “wrong” without being too loud
                if (r.nextBoolean())
                {
                    level.playSound(null, x, y, z,
                        NullnessBridge.assumeNonnull(SoundEvents.AMETHYST_BLOCK_RESONATE),
                        SoundSource.NEUTRAL,
                        0.25f,
                        0.6f + (r.nextFloat() * 0.1f));
                }
            }
            case TICK -> {
                // Keep it sparse; a rhythmic “pulse” / “creep”
                if (r.nextInt(5) == 0)
                {
                    level.playSound(null, x, y, z,
                        NullnessBridge.assumeNonnull(SoundEvents.SCULK_BLOCK_SPREAD),
                        SoundSource.NEUTRAL,
                        0.35f,
                        0.65f + (r.nextFloat() * 0.08f));
                }

                // Very occasional breathy whoosh
                if (r.nextInt(14) == 0)
                {
                    level.playSound(null, x, y, z,
                        NullnessBridge.assumeNonnull(SoundEvents.SOUL_ESCAPE),
                        SoundSource.NEUTRAL,
                        0.35f,
                        0.85f + (r.nextFloat() * 0.1f));
                }
            }
            case FINAL_BURST -> {
                // Strong “seal the deal” cue; dark and forceful
                level.playSound(null, x, y, z,
                    NullnessBridge.assumeNonnull(SoundEvents.WITHER_SPAWN),
                    SoundSource.NEUTRAL,
                    0.7f,
                    0.9f + (r.nextFloat() * 0.08f));

                // Add a short “boom” layer so it reads as a completed conversion
                level.playSound(null, x, y, z,
                    NullnessBridge.assumeNonnull(SoundEvents.GENERIC_EXPLODE),
                    SoundSource.NEUTRAL,
                    0.35f,
                    0.55f + (r.nextFloat() * 0.08f));
            }
        }

        // ----- Particles -----
        // Lean into “smoke + ash + soul fire + sculk” (all vanilla).
        if (phase == ConversionFxPhase.START)
        {
            // Initial “shadow seep” around the torso
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.LARGE_SMOKE),    x, y, z,  6, 0.30, 0.35, 0.30, 0.01);
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.ASH),           x, y, z, 10, 0.45, 0.55, 0.45, 0.005);

            // A few “cold blue” sparks to hint unnatural energy
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SOUL_FIRE_FLAME), x, y, z, 4, 0.25, 0.35, 0.25, 0.0);

            // Subtle sculk motes (reads as corruption) — keep low count
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.DUST_PLUME),  x, y, z,  2, 0.18, 0.22, 0.18, 0.0);
        }
        else if (phase == ConversionFxPhase.TICK)
        {
            // Persistent “smothering” haze
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SMOKE),         x, y, z,  3, 0.28, 0.35, 0.28, 0.0);
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.ASH),           x, y, z,  4, 0.35, 0.55, 0.35, 0.003);

            // Occasional “soul lick” upward
            if (r.nextInt(4) == 0)
            {
                level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SOUL), x, y, z, 1, 0.20, 0.35, 0.20, 0.0);
            }

            // Occasional sculk “pulse”
            if (r.nextInt(5) == 0)
            {
                level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SCULK_CHARGE_POP), x, y, z, 1, 0.15, 0.20, 0.15, 0.0);
            }
        }
        else // FINAL_BURST
        {
            // Big “dark bloom” burst
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.LARGE_SMOKE),     x, y, z, 20, 0.55, 0.60, 0.55, 0.02);
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.ASH),            x, y, z, 28, 0.65, 0.90, 0.65, 0.01);

            // Strong soul-fire flare (unholy ignition vibe)
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SOUL_FIRE_FLAME), x, y, z, 14, 0.35, 0.45, 0.35, 0.0);
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SOUL),            x, y, z, 10, 0.40, 0.55, 0.40, 0.0);

            // Sculk pulse to “stamp” the conversion
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.DUST_PLUME),     x, y, z,  6, 0.30, 0.35, 0.30, 0.0);
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SCULK_CHARGE_POP), x, y, z,  6, 0.25, 0.30, 0.25, 0.0);
        }

        // ----- Shake -----
        // Corruption should feel more forceful than curing.
        if (phase != ConversionFxPhase.FINAL_BURST)
        {
            applyShakeJitter(level, entity, phase == ConversionFxPhase.START ? 1.15f : 0.85f);
        }
    }
    /**
     * “Shaking” that is visible to clients without custom packets: - jitter yaw/head yaw - tiny sideways nudge (doesn't meaningfully
     * displace) Call this occasionally (e.g. every 5–10 ticks) while cleansing.
     */
    public static void applyShakeJitter(final ServerLevel level, final LivingEntity entity, final float intensity)
    {
        final RandomSource r = entity.getRandom();

        // Small rotation jitter
        final float yawJitter = (r.nextFloat() - 0.5f) * 18.0f * intensity; // degrees
        final float headJitter = (r.nextFloat() - 0.5f) * 26.0f * intensity;

        entity.setYRot(entity.getYRot() + yawJitter);
        entity.setYHeadRot(entity.getYHeadRot() + headJitter);
        entity.setXRot(clamp(entity.getXRot() + ((r.nextFloat() - 0.5f) * 6.0f * intensity), -35.0f, 35.0f));

        // Micro-nudge sideways to read as tremor; keep it tiny
        final double dx = (r.nextDouble() - 0.5) * 0.05 * intensity;
        final double dz = (r.nextDouble() - 0.5) * 0.05 * intensity;

        Vec3 vec = entity.getDeltaMovement().add(dx, 0.0, dz);
        // Don't launch it into the air; keep Y velocity intact
        entity.setDeltaMovement(NullnessBridge.assumeNonnull(vec));

        // Make sure the movement is considered for syncing
        entity.hurtMarked = true;
    }

    /**
     * Clamps a float value to be within a specified range.
     * @param v value to be clamped
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @return clamped value
     */
    private static float clamp(final float v, final float min, final float max)
    {
        return Math.max(min, Math.min(max, v));
    }


    /**
     * Start the conversion process on a LivingEntity.
     * 
     * If the entity is already being converted, this method will return false.
     * If the entity is not already being converted, this method will set the entity's
     * ConversionData to the specified duration and play the cure visual effects.
     * 
     * @param level the level the entity is in
     * @param entity the entity to start the conversion process on
     * @return true if the conversion process was started, false otherwise
     */
    public static boolean startConversion(ServerLevel level, LivingEntity entity, boolean isCleansing)
    {
        AttachmentType<ConversionData> dataAttachment = ModAttachments.CONVERSION.get();

        if (dataAttachment == null) return false;

        ConversionData data = entity.getData(dataAttachment);

        // already cleansing - do not reset.
        if (data != null && data.ticksRemaining() > 0) return false; 

        entity.setData(NullnessBridge.assumeNonnull(ModAttachments.CONVERSION), new ConversionData(CONVERSION_DURATION, isCleansing));

        EntityConversion.playConversionEffects(
            level,
            entity,
            EntityConversion.ConversionFxPhase.START,
            isCleansing
        );

        return true;
    }

    /**
     * Finish cleansing process on a LivingEntity.
     * 
     * Plays visual effects, resolves entity mapping (if applicable), and
     * converts the entity to its vanilla equivalent.
     * 
     * @param level the level the entity is in
     * @param entity the entity to finish cleansing on
     */
    public static void finishConversion(ServerLevel level, LivingEntity entity, boolean isCleansing)
    {
        EntityConversion.playConversionEffects(
            level,
            entity,
            EntityConversion.ConversionFxPhase.FINAL_BURST,
            isCleansing
        );

        EntityType<?> entityType = entity.getType();

        if (entityType == null) return;

        final Optional<EntityType<? extends LivingEntity>> targetOpt = isCleansing ? resolveVanillaTarget(entityType) : resolveCorruptedTarget(entityType);

        if (targetOpt.isEmpty()) return;


        EntityConversion.convertLivingEntity(
            entity,
            targetOpt.get(),
            cured -> 
            {
                // We get the purification credits from "killing" (curing) the original entity; this deliberately uses 
                // the original entity to achieve that - at the new entities position (in case it changed).
                SalvationManager.applyMobProgression(entity, cured.blockPosition(), null);
            }
        );
    }

    /**
     * Resolves the given corrupted entity type into a EntityType.
     * This method is used to look up the corrupted entity type for a given vanilla entity type.
     * 
     * @param vanillaType the vanilla entity type to resolve
     * @return an Optional containing the resolved EntityType, or an empty Optional if the resolution failed
     */
    public static Optional<EntityType<? extends LivingEntity>> resolveCorruptedTarget(final @Nonnull EntityType<?> vanillaType)
    {
        final ResourceLocation vanillaId = EntityType.getKey(vanillaType);
        final Optional<ResourceLocation> corruptedId = SalvationMod.CURE_MAPPINGS.getCorruptedForVanilla(vanillaId);
        if (corruptedId.isEmpty()) return Optional.empty();

        final EntityType<?> resolved = BuiltInRegistries.ENTITY_TYPE.get(corruptedId.get());

        @SuppressWarnings("unchecked")
        final EntityType<? extends LivingEntity> casted = (EntityType<? extends LivingEntity>) resolved;
        return Optional.of(casted);
    }

    /**
     * Resolves the given corrupted entity type to its original vanilla entity type.
     * 
     * @param corruptedType the corrupted entity type to resolve
     * @return an Optional containing the resolved vanilla entity type, or an empty Optional if no mapping was found
     */
    public static Optional<EntityType<? extends LivingEntity>> resolveVanillaTarget(final @Nonnull EntityType<?> corruptedType)
    {
        final ResourceLocation corruptedId = BuiltInRegistries.ENTITY_TYPE.getKey(corruptedType);
        final ResourceLocation vanillaId = SalvationMod.CURE_MAPPINGS.getVanillaForCorrupted(corruptedId).orElse(null);
        if (vanillaId == null) return Optional.empty();

        final EntityType<?> resolved = BuiltInRegistries.ENTITY_TYPE.get(vanillaId);

        @SuppressWarnings("unchecked")
        final EntityType<? extends LivingEntity> casted = (EntityType<? extends LivingEntity>) resolved;
        return Optional.of(casted);
    }
}
