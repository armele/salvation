package com.deathfrog.salvationmod.client.render;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.entity.CorruptionBoltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class CorruptionBoltRender extends EntityRenderer<CorruptionBoltEntity>
{
    private static final @Nonnull ResourceLocation TEXTURE =
        NullnessBridge.assumeNonnull(ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/entity/corruption_bolt.png"));
         
    private static final @Nonnull RenderType RENDER_TYPE = NullnessBridge.assumeNonnull(RenderType.entityCutoutNoCull(TEXTURE));
    private static final @Nonnull RenderType TAIL_RENDER_TYPE = NullnessBridge.assumeNonnull(RenderType.entityTranslucent(TEXTURE));

    public CorruptionBoltRender(final EntityRendererProvider.Context context)
    {
        super(context);
    }

    /**
     * Gets the block light level for the given corruption bolt entity at the given position.
     * <p>
     * This method always returns 15, as the corruption bolt entity is always a bright, glowing entity.
     *
     * @param entity the entity to get the block light level for
     * @param pos the position to get the block light level for
     * @return the block light level for the entity at the given position
     */
    @Override
    protected int getBlockLightLevel(final @Nonnull CorruptionBoltEntity entity, final @Nonnull BlockPos pos)
    {
        return 15;
    }

    @Override
    public void render(final @Nonnull CorruptionBoltEntity entity,
        final float entityYaw,
        final float partialTick,
        final @Nonnull PoseStack poseStack,
        final @Nonnull MultiBufferSource bufferSource,
        final int packedLight)
    {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        poseStack.scale(0.7F, 0.7F, 0.7F);

        final PoseStack.Pose pose = poseStack.last();
        final VertexConsumer tailBuffer = bufferSource.getBuffer(TAIL_RENDER_TYPE);
        renderEnergyTail(tailBuffer, pose, packedLight);
        final VertexConsumer buffer = bufferSource.getBuffer(RENDER_TYPE);
        renderBoltModel(buffer, pose, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void renderEnergyTail(final VertexConsumer buffer, final @Nonnull PoseStack.Pose pose, final int packedLight)
    {
        final float tipTail = -0.45F;
        final float beamEnd = -2.10F;
        final float rootHalfWidth = 0.18F;
        final float tailHalfWidth = 0.03F;

        addQuad(buffer, pose, packedLight,
            tipTail, -rootHalfWidth, 0.0F,
            tipTail, rootHalfWidth, 0.0F,
            beamEnd, tailHalfWidth, 0.0F,
            beamEnd, -tailHalfWidth, 0.0F,
            0.0F, 0.0F, 1.0F,
            210);

        addQuad(buffer, pose, packedLight,
            tipTail, rootHalfWidth, 0.0F,
            tipTail, -rootHalfWidth, 0.0F,
            beamEnd, -tailHalfWidth, 0.0F,
            beamEnd, tailHalfWidth, 0.0F,
            0.0F, 0.0F, -1.0F,
            210);

        addQuad(buffer, pose, packedLight,
            tipTail, 0.0F, -rootHalfWidth,
            tipTail, 0.0F, rootHalfWidth,
            beamEnd, 0.0F, tailHalfWidth,
            beamEnd, 0.0F, -tailHalfWidth,
            0.0F, 1.0F, 0.0F,
            170);

        addQuad(buffer, pose, packedLight,
            tipTail, 0.0F, rootHalfWidth,
            tipTail, 0.0F, -rootHalfWidth,
            beamEnd, 0.0F, -tailHalfWidth,
            beamEnd, 0.0F, tailHalfWidth,
            0.0F, -1.0F, 0.0F,
            170);
    }

    private static void renderBoltModel(final VertexConsumer buffer, final @Nonnull PoseStack.Pose pose, final int packedLight)
    {
        final float tail = -0.45F;
        final float nose = 0.45F;
        final float body = 0.06F;
        final float fin = 0.14F;

        addQuad(buffer, pose, packedLight,
            nose, -body, -body,
            nose, body, -body,
            nose, body, body,
            nose, -body, body,
            1.0F, 0.0F, 0.0F);

        addQuad(buffer, pose, packedLight,
            tail, -body, body,
            tail, body, body,
            tail, body, -body,
            tail, -body, -body,
            -1.0F, 0.0F, 0.0F);

        addQuad(buffer, pose, packedLight,
            tail, body, -body,
            nose, body, -body,
            nose, body, body,
            tail, body, body,
            0.0F, 1.0F, 0.0F);

        addQuad(buffer, pose, packedLight,
            tail, -body, body,
            nose, -body, body,
            nose, -body, -body,
            tail, -body, -body,
            0.0F, -1.0F, 0.0F);

        addQuad(buffer, pose, packedLight,
            tail, -body, -body,
            nose, -body, -body,
            nose, body, -body,
            tail, body, -body,
            0.0F, 0.0F, -1.0F);

        addQuad(buffer, pose, packedLight,
            tail, body, body,
            nose, body, body,
            nose, -body, body,
            tail, -body, body,
            0.0F, 0.0F, 1.0F);

        addQuad(buffer, pose, packedLight,
            tail + 0.04F, -fin, 0.0F,
            tail + 0.22F, -body, 0.0F,
            tail + 0.22F, body, 0.0F,
            tail + 0.04F, fin, 0.0F,
            0.0F, 0.0F, 1.0F);

        addQuad(buffer, pose, packedLight,
            tail + 0.04F, fin, 0.0F,
            tail + 0.22F, body, 0.0F,
            tail + 0.22F, -body, 0.0F,
            tail + 0.04F, -fin, 0.0F,
            0.0F, 0.0F, -1.0F);

        addQuad(buffer, pose, packedLight,
            tail + 0.04F, 0.0F, -fin,
            tail + 0.22F, 0.0F, -body,
            tail + 0.22F, 0.0F, body,
            tail + 0.04F, 0.0F, fin,
            0.0F, 1.0F, 0.0F);

        addQuad(buffer, pose, packedLight,
            tail + 0.04F, 0.0F, fin,
            tail + 0.22F, 0.0F, body,
            tail + 0.22F, 0.0F, -body,
            tail + 0.04F, 0.0F, -fin,
            0.0F, -1.0F, 0.0F);
    }

    private static void addQuad(final VertexConsumer buffer,
        final @Nonnull PoseStack.Pose pose,
        final int packedLight,
        final float x0,
        final float y0,
        final float z0,
        final float x1,
        final float y1,
        final float z1,
        final float x2,
        final float y2,
        final float z2,
        final float x3,
        final float y3,
        final float z3,
        final float nx,
        final float ny,
        final float nz,
        final int alpha)
    {
        vertex(buffer, pose, packedLight, x0, y0, z0, 0.0F, 1.0F, nx, ny, nz, alpha);
        vertex(buffer, pose, packedLight, x1, y1, z1, 1.0F, 1.0F, nx, ny, nz, alpha);
        vertex(buffer, pose, packedLight, x2, y2, z2, 1.0F, 0.0F, nx, ny, nz, alpha);
        vertex(buffer, pose, packedLight, x3, y3, z3, 0.0F, 0.0F, nx, ny, nz, alpha);
    }

    private static void addQuad(final VertexConsumer buffer,
        final @Nonnull PoseStack.Pose pose,
        final int packedLight,
        final float x0,
        final float y0,
        final float z0,
        final float x1,
        final float y1,
        final float z1,
        final float x2,
        final float y2,
        final float z2,
        final float x3,
        final float y3,
        final float z3,
        final float nx,
        final float ny,
        final float nz)
    {
        addQuad(buffer, pose, packedLight, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, nx, ny, nz, 255);
    }

    private static void vertex(final VertexConsumer buffer,
        final @Nonnull PoseStack.Pose pose,
        final int packedLight,
        final float x,
        final float y,
        final float z,
        final float u,
        final float v,
        final float nx,
        final float ny,
        final float nz,
        final int alpha)
    {
        buffer.addVertex(pose, x, y, z)
            .setColor(255, 255, 255, alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(final @Nonnull CorruptionBoltEntity entity)
    {
        return TEXTURE;
    }
}
