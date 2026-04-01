package com.deathfrog.salvationmod.client.render;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.client.render.model.VoraxianOverlordModel;
import com.deathfrog.salvationmod.entity.VoraxianOverlordEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class VoraxianOverlordRender extends MobRenderer<VoraxianOverlordEntity, VoraxianOverlordModel<VoraxianOverlordEntity>>
{
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/entity/voraxian_overlord.png");

    public VoraxianOverlordRender(final EntityRendererProvider.Context ctx)
    {
        super(ctx, new VoraxianOverlordModel<>(ctx.bakeLayer(VoraxianOverlordModel.LAYER_LOCATION)), 1.2F);
    }

    @Override
    public ResourceLocation getTextureLocation(final @Nonnull VoraxianOverlordEntity entity)
    {
        return TEXTURE;
    }

    @SuppressWarnings("null")
    @Override
    protected void setupRotations(final @Nonnull VoraxianOverlordEntity entity,
                                final @Nonnull PoseStack poseStack,
                                final float ageInTicks,
                                final float rotationYaw,
                                final float partialTicks,
                                final float scale)
    {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks, scale);

        final float time = entity.tickCount + partialTicks;
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Mth.sin(time * 0.05F) * 1.4F));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(Mth.cos(time * 0.04F) * 1.0F));

        if (entity.isAggressive())
        {
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-4.0F));
        }
    }

    @Override
    public net.minecraft.world.phys.Vec3 getRenderOffset(final @Nonnull VoraxianOverlordEntity entity, final float partialTicks)
    {
        final float time = entity.tickCount + partialTicks;
        return new net.minecraft.world.phys.Vec3(
            Mth.sin(time * 0.04F) * 0.04F,
            Mth.cos(time * 0.06F) * 0.06F,
            0.0D
        );
    }

    @Override
    protected void scale(final @Nonnull VoraxianOverlordEntity entity, final @Nonnull PoseStack poseStack, final float partialTickTime)
    {
        poseStack.scale(1.45F, 1.45F, 1.45F);
    }
}
