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

import java.util.function.Consumer;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModAttachments;
import com.deathfrog.salvationmod.ModAttachments.CleansingData;
import com.deathfrog.salvationmod.SalvationMod;

public final class EntityConversion
{
    private static final int CLEANSING_DURATION = 20 * 15; // 15 seconds
    
    private EntityConversion()
    {}

    /**
     * Attempts to convert a {@link LivingEntity} from one type to another. This method will copy some basic properties such as
     * position, rotation, custom name visibility, and some flags. It will NOT copy any attributes or variant data.
     * <p>This method is mainly useful for converting entities between different mods.
     * <p>The provided {@link Consumer} will be called with the newly created entity as an argument. This can be used to set any
     * additional data such as attributes, variant data, or custom AI goals.
     * <p>If the provided {@link LivingEntity} is not an instance of {@link ServerLevel}, this method will return null.
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
                // drop leash from source first
                sourceMob.dropLeash(true, true);
                if (holder instanceof LivingEntity livingHolder)
                {
                    targetMob.setLeashedTo(livingHolder, true);
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
        postCreateCustomizer.accept(target);

        // Spawn & remove
        level.addFreshEntity(target);

        // Stop riding / eject passengers first
        source.stopRiding();
        source.discard();

        return target;
    }

    // Effects
    /** 
     * Stages let us reuse the same helper for "start", "during", and "finish". 
     * 
     */
    public enum CureFxPhase
    {
        START,      // first application of feed/potion
        TICK,       // periodic while cleansing
        FINAL_BURST // right at conversion
    }

    /**
     * Server-side helper to broadcast cure VFX/SFX and apply a brief "shake" motion that is replicated to clients via normal entity
     * movement/rotation sync.
     */
    public static void playCureEffects(final ServerLevel level, final LivingEntity entity, final CureFxPhase phase)
    {
        final RandomSource r = entity.getRandom();
        final Vec3 pos = entity.position();
        final double x = pos.x;
        final double y = pos.y + (entity.getBbHeight() * 0.55);
        final double z = pos.z;

        // ----- Sounds -----
        switch (phase)
        {
            case START -> level.playSound(null,
                x,
                y,
                z,
                NullnessBridge.assumeNonnull(SoundEvents.ENCHANTMENT_TABLE_USE),
                SoundSource.NEUTRAL,
                0.7f,
                1.25f + (r.nextFloat() * 0.15f));
            case TICK -> {
                // light, occasional tick sound (don’t spam every tick)
                if (r.nextInt(6) == 0)
                {
                    level.playSound(null,
                        x,
                        y,
                        z,
                        NullnessBridge.assumeNonnull(SoundEvents.BREWING_STAND_BREW), // subtle “bubbling”
                        SoundSource.NEUTRAL,
                        0.25f,
                        1.6f + (r.nextFloat() * 0.2f));
                }
            }
            case FINAL_BURST -> level.playSound(null,
                x,
                y,
                z,
                NullnessBridge.assumeNonnull(SoundEvents.ZOMBIE_VILLAGER_CURE),     // strong “cure” cue
                SoundSource.NEUTRAL,
                0.9f,
                1.0f + (r.nextFloat() * 0.1f));
        }

        // ----- Particles -----
        // These are all vanilla particles so no custom registry needed.
        // You can tune counts; the "FINAL_BURST" should be noticeably stronger.
        if (phase == CureFxPhase.START)
        {
            // A gentle “sparkle + puff”
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER), x, y, z, 10, 0.35, 0.35, 0.35, 0.02);
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.POOF), x, y, z, 6, 0.25, 0.25, 0.25, 0.01);
        }
        else if (phase == CureFxPhase.TICK)
        {
            // Low intensity continuous feedback
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER), x, y, z, 3, 0.25, 0.25, 0.25, 0.01);

            // Occasional small poof to read as “corruption leaving”
            if (r.nextInt(4) == 0)
            {
                level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.SMOKE), x, y, z, 2, 0.15, 0.15, 0.15, 0.0);
            }
        }
        else // FINAL_BURST
        {
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.HAPPY_VILLAGER), x, y, z, 22, 0.45, 0.45, 0.45, 0.03);
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.POOF), x, y, z, 18, 0.35, 0.35, 0.35, 0.02);
            level.sendParticles(NullnessBridge.assumeNonnull(ParticleTypes.END_ROD), x, y, z, 10, 0.25, 0.35, 0.25, 0.01);
        }

        // ----- Shake -----
        // This is a short, replicated jitter. Call this during START + TICK.
        // Avoid doing heavy motion on FINAL_BURST (entity is about to despawn).
        if (phase != CureFxPhase.FINAL_BURST)
        {
            applyShakeJitter(level, entity, phase == CureFxPhase.START ? 0.9f : 0.6f);
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
     * Starts the cleansing process for the given entity.
     * This will reset the entity's cleans data and start the visual effects.
     * If the entity is already being cleansed, this call will not reset the timer.
     * @param level the level the entity is in
     * @param entity the entity to start cleansing on
     */
    public static void startCleansing(ServerLevel level, LivingEntity entity)
    {
        AttachmentType<CleansingData> dataAttachment = ModAttachments.CLEANSING.get();

        if (dataAttachment == null) return;

        CleansingData data = entity.getData(dataAttachment);

        // already cleansing - do not reset.
        if (data != null && data.ticksRemaining() > 0) return; 

        entity.setData(NullnessBridge.assumeNonnull(ModAttachments.CLEANSING), new CleansingData(CLEANSING_DURATION));

        EntityConversion.playCureEffects(
            level,
            entity,
            EntityConversion.CureFxPhase.START
        );
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
    public static void finishCleansing(ServerLevel level, LivingEntity entity)
    {
        EntityConversion.playCureEffects(
            level,
            entity,
            EntityConversion.CureFxPhase.FINAL_BURST
        );

        EntityType<?> entityType = entity.getType();

        if (entityType == null) return;

        // Resolve mapping
        final ResourceLocation corruptedId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

        final ResourceLocation vanillaId = SalvationMod.CURE_MAPPINGS.getVanillaForCorrupted(corruptedId).orElse(null);

        if (vanillaId == null) return;

        final EntityType<?> vanillaType = BuiltInRegistries.ENTITY_TYPE.get(vanillaId);
        
        @SuppressWarnings("unchecked")
        final EntityType<? extends LivingEntity> castedVanillaType = (EntityType<? extends LivingEntity>) vanillaType;

        EntityConversion.convertLivingEntity(
            entity,
            castedVanillaType,
            cured -> 
            {
                SalvationManager.applyMobProgression(entity, entity.blockPosition());
            }
        );
    }
}
