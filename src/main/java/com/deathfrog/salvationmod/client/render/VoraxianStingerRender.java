package com.deathfrog.salvationmod.client.render;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.client.render.model.VoraxianStingerModel;
import com.deathfrog.salvationmod.entity.VoraxianStingerEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class VoraxianStingerRender extends MobRenderer<VoraxianStingerEntity, VoraxianStingerModel<VoraxianStingerEntity>>
{
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/entity/voraxian_stinger.png");

    public VoraxianStingerRender(final EntityRendererProvider.Context ctx)
    {
        super(ctx, new VoraxianStingerModel<>(ctx.bakeLayer(VoraxianStingerModel.LAYER_LOCATION)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(final @Nonnull VoraxianStingerEntity entity)
    {
        return TEXTURE;
    }

    @Override
    protected void scale(final @Nonnull VoraxianStingerEntity entity, final @Nonnull PoseStack poseStack, final float partialTickTime)
    {
        poseStack.scale(0.95F, 0.95F, 0.95F);
    }
}
