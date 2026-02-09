package com.deathfrog.salvationmod.client.render;

import javax.annotation.Nonnull;

import org.joml.Quaternionf;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.client.render.model.*;
import com.deathfrog.salvationmod.entity.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class CorruptedCatRender extends MobRenderer<CorruptedCatEntity, CorruptedCatModel<CorruptedCatEntity>>
{

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/entity/corrupted_cat.png");

    public CorruptedCatRender(final EntityRendererProvider.Context ctx)
    {
        super(ctx, new CorruptedCatModel<>(ctx.bakeLayer(CorruptedCatModel.LAYER_LOCATION)), 0.7F);
    }

    @Override
    public ResourceLocation getTextureLocation(final @Nonnull CorruptedCatEntity entity)
    {
        return TEXTURE;
    }

    @Override
    public void render(final @Nonnull CorruptedCatEntity entity, float entityYaw, float partialTicks,
                       final @Nonnull PoseStack poseStack, final @Nonnull MultiBufferSource buffer, int packedLight)
    {
        //  “feral hunch”: tilt forward a touch.
        float walk = entity.walkAnimation.position(partialTicks);
        float stagger = Mth.sin(walk * 0.8F) * 5.0F; // oscillates between -5 and +5
        Quaternionf hunch = Axis.XP.rotationDegrees(stagger);

        if (hunch != null) poseStack.mulPose(hunch);

        // Scale Y up more than X/Z
        poseStack.scale(1.0F, 1.1F, 1.0F);

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}