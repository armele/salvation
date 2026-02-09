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

    public record CleansingData(int ticksRemaining)
    {
        @SuppressWarnings("null")
        public static final Codec<CleansingData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("ticksRemaining").forGetter(CleansingData::ticksRemaining)
        ).apply(inst, CleansingData::new));
    }

    @SuppressWarnings("null")
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CleansingData>> CLEANSING =
            ATTACHMENTS.register("cleansing", () ->
                    AttachmentType.builder(() -> new CleansingData(0))
                            .serialize(CleansingData.CODEC)
                            .build()
            );
}