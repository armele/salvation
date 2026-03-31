package com.deathfrog.salvationmod;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments
{
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NullnessBridge.assumeNonnull(NeoForgeRegistries.ATTACHMENT_TYPES), SalvationMod.MODID);

    public record ConversionData(int ticksRemaining, boolean isCleansing, Optional<UUID> sourcePlayerUuid)
    {
        @SuppressWarnings("null")
        public static final @Nonnull Codec<ConversionData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("ticksRemaining").forGetter(ConversionData::ticksRemaining),
                Codec.BOOL.fieldOf("isCleansing").forGetter(ConversionData::isCleansing),
                UUIDUtil.CODEC.optionalFieldOf("sourcePlayerUuid").forGetter(ConversionData::sourcePlayerUuid)
        ).apply(inst, ConversionData::new));
    }

    public record RefugeeWanderData(boolean enabled)
    {
        @SuppressWarnings("null")
        public static final @Nonnull Codec<RefugeeWanderData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.BOOL.fieldOf("enabled").forGetter(RefugeeWanderData::enabled)
        ).apply(inst, RefugeeWanderData::new));
    }

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ConversionData>> CONVERSION =
            ATTACHMENTS.register("conversion", () ->
                    AttachmentType.builder(() -> new ConversionData(0, false, Optional.empty()))
                            .serialize(ConversionData.CODEC)
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RefugeeWanderData>> REFUGEE_WANDER =
            ATTACHMENTS.register("refugee_wander", () ->
                    AttachmentType.builder(() -> new RefugeeWanderData(false))
                            .serialize(RefugeeWanderData.CODEC)
                            .build()
            );
}
