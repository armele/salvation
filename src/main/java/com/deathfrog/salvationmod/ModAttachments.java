package com.deathfrog.salvationmod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments
{
    private ModAttachments() {}

    @SuppressWarnings("null")
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SalvationMod.MODID);

    public record ConversionData(int ticksRemaining, boolean isCleansing)
    {
        @SuppressWarnings("null")
        public static final Codec<ConversionData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("ticksRemaining").forGetter(ConversionData::ticksRemaining),
                Codec.BOOL.fieldOf("isCleansing").forGetter(ConversionData::isCleansing)
        ).apply(inst, ConversionData::new));
    }

    @SuppressWarnings("null")
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ConversionData>> CONVERSION =
            ATTACHMENTS.register("conversion", () ->
                    AttachmentType.builder(() -> new ConversionData(0, false))
                            .serialize(ConversionData.CODEC)
                            .build()
            );
}