package com.deathfrog.salvationmod.client.render;

import javax.annotation.Nonnull;

import org.joml.Quaternionf;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.client.render.model.CorruptedHorseModel;
import com.deathfrog.salvationmod.entity.CorruptedHorseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class CorruptedHorseRender extends MobRenderer<CorruptedHorseEntity, CorruptedHorseModel<CorruptedHorseEntity>>
{
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/entity/corrupted_horse.png");

    public CorruptedHorseRender(final EntityRendererProvider.Context ctx)
    {
        super(ctx, new CorruptedHorseModel<>(ctx.bakeLayer(CorruptedHorseModel.LAYER_LOCATION)), 0.75F);
    }

    @Override
    public ResourceLocation getTextureLocation(final @Nonnull CorruptedHorseEntity entity)
    {
        return TEXTURE;
    }

    @Override
    public void render(final @Nonnull CorruptedHorseEntity entity, float entityYaw, float partialTicks,
                       final @Nonnull PoseStack poseStack, final @Nonnull MultiBufferSource buffer, int packedLight)
    {
        float walk = entity.walkAnimation.position(partialTicks);
        float stagger = Mth.sin(walk * 0.8F) * 4.0F;
        Quaternionf hunch = Axis.XP.rotationDegrees(stagger);

        if (hunch != null) poseStack.mulPose(hunch);

        poseStack.scale(1.0F, 1.08F, 1.0F);

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
