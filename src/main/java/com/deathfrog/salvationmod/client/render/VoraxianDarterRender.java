package com.deathfrog.salvationmod.client.render;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.client.render.model.VoraxianDarterModel;
import com.deathfrog.salvationmod.entity.VoraxianDarterEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class VoraxianDarterRender extends MobRenderer<VoraxianDarterEntity, VoraxianDarterModel<VoraxianDarterEntity>>
{
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/entity/voraxian_darter.png");

    public VoraxianDarterRender(final EntityRendererProvider.Context ctx)
    {
        super(ctx, new VoraxianDarterModel<>(ctx.bakeLayer(VoraxianDarterModel.LAYER_LOCATION)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(final @Nonnull VoraxianDarterEntity entity)
    {
        return TEXTURE;
    }

    @Override
    protected void scale(final @Nonnull VoraxianDarterEntity entity, final @Nonnull PoseStack poseStack, final float partialTickTime)
    {
        poseStack.scale(0.95F, 0.95F, 0.95F);
    }
}
