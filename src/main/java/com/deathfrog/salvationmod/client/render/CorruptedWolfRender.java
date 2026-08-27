package com.deathfrog.salvationmod.client.render;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.client.render.model.CorruptedWolfModel;
import com.deathfrog.salvationmod.entity.CorruptedWolfEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CorruptedWolfRender extends MobRenderer<CorruptedWolfEntity, CorruptedWolfModel<CorruptedWolfEntity>>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        SalvationMod.MODID, "textures/entity/corrupted_wolf.png");

    public CorruptedWolfRender(final EntityRendererProvider.Context context)
    {
        super(context, new CorruptedWolfModel<>(context.bakeLayer(CorruptedWolfModel.LAYER_LOCATION)), 0.6F);
    }

    @Override
    public ResourceLocation getTextureLocation(final @Nonnull CorruptedWolfEntity entity)
    {
        return TEXTURE;
    }
}
