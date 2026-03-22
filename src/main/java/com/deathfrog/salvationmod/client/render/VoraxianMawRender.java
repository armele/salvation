package com.deathfrog.salvationmod.client.render;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.client.render.model.VoraxianMawModel;
import com.deathfrog.salvationmod.entity.VoraxianMawEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class VoraxianMawRender extends MobRenderer<VoraxianMawEntity, VoraxianMawModel<VoraxianMawEntity>>
{

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/entity/voraxian_maw.png");

    public VoraxianMawRender(final EntityRendererProvider.Context ctx)
    {
        super(ctx, new VoraxianMawModel<>(ctx.bakeLayer(VoraxianMawModel.LAYER_LOCATION)), 0.7F);
    }

    @Override
    public ResourceLocation getTextureLocation(final @Nonnull VoraxianMawEntity entity)
    {
        return TEXTURE;
    }

    /**
     * Set up the pose stack for rendering the Voraxian Maw's animations.
     * This includes a gentle floating roll and a slight forward/back drift, as well as a more threatening posture when aggressive.
     *
     * @param entity    the Voraxian Maw entity being rendered
     * @param poseStack the pose stack to modify
     * @param ageInTicks the age of the entity in ticks
     * @param rotationYaw the yaw of the entity in degrees
     * @param partialTicks the partial tick time
     * @param scale       the scale of the entity
     */
    @Override
    protected void setupRotations(final @Nonnull VoraxianMawEntity entity,
                                final @Nonnull PoseStack poseStack,
                                final float ageInTicks,
                                final float rotationYaw,
                                final float partialTicks,
                                final float scale)
    {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks, scale);

        final float time = entity.tickCount + partialTicks;

        // Gentle floating roll
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Mth.sin(time * 0.08F) * 2.5F));

        // Slight forward/back drift
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(Mth.cos(time * 0.06F) * 1.5F));

        // More threatening posture when aggressive
        if (entity.isAggressive())
        {
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-6.0F));
        }
    }

    @Override
    public net.minecraft.world.phys.Vec3 getRenderOffset(final @Nonnull VoraxianMawEntity entity, final float partialTicks)
    {
        final float time = entity.tickCount + partialTicks;
        return new net.minecraft.world.phys.Vec3(
            Mth.sin(time * 0.05F) * 0.03F,
            Mth.cos(time * 0.08F) * 0.05F,
            0.0D
        );
    }

    @Override
    protected void scale(final @Nonnull VoraxianMawEntity entity, final @Nonnull PoseStack poseStack, final float partialTickTime)
    {
        poseStack.scale(1.15F, 1.15F, 1.15F);
    }
}