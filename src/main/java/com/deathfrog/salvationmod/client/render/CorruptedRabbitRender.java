package com.deathfrog.salvationmod.client.render;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.client.render.model.CorruptedRabbitModel;
import com.deathfrog.salvationmod.entity.CorruptedRabbitEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CorruptedRabbitRender extends MobRenderer<CorruptedRabbitEntity, CorruptedRabbitModel<CorruptedRabbitEntity>>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        SalvationMod.MODID, "textures/entity/corrupted_rabbit.png");

    public CorruptedRabbitRender(final EntityRendererProvider.Context context)
    {
        super(context, new CorruptedRabbitModel<>(context.bakeLayer(CorruptedRabbitModel.LAYER_LOCATION)), 0.45F);
    }

    @Override
    public ResourceLocation getTextureLocation(final @Nonnull CorruptedRabbitEntity entity)
    {
        return TEXTURE;
    }
}
