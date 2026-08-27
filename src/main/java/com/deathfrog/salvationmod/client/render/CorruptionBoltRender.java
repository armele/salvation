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
    private static final int EMBEDDED_FADE_START_TICKS = 7 * 20;
    private static final int EMBEDDED_LIFETIME_TICKS = 10 * 20;
    private static final float SIDE_U0 = 0.0F;
    private static final float SIDE_V0 = 0.0F;
    private static final float SIDE_U1 = 16.0F / 32.0F;
    private static final float SIDE_V1 = 7.0F / 32.0F;
    private static final float REAR_U0 = 0.0F;
    private static final float REAR_V0 = 16.0F / 32.0F;
    private static final float REAR_U1 = 5.0F / 32.0F;
    private static final float REAR_V1 = 21.0F / 32.0F;
    private static final float FRONT_U0 = 0.0F;
    private static final float FRONT_V0 = 21.0F / 32.0F;
    private static final float FRONT_U1 = 5.0F / 32.0F;
    private static final float FRONT_V1 = 26.0F / 32.0F;
    private static final float TAIL_SAMPLE_U = 1.5F / 32.0F;
    private static final float TAIL_SAMPLE_V = 2.5F / 32.0F;
    private static final @Nonnull ResourceLocation TEXTURE =
        NullnessBridge.assumeNonnull(ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/entity/corruption_bolt.png"));
         
    private static final @Nonnull RenderType RENDER_TYPE = NullnessBridge.assumeNonnull(RenderType.entityCutoutNoCull(TEXTURE));
    private static final @Nonnull RenderType TAIL_RENDER_TYPE = NullnessBridge.assumeNonnull(RenderType.entityTranslucent(TEXTURE));
    private int boltAlpha = 255;

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

    @SuppressWarnings("null")
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

        final boolean embedded = entity.isEmbedded();
        final PoseStack.Pose pose = poseStack.last();
        if (!embedded)
        {
            final VertexConsumer tailBuffer = bufferSource.getBuffer(TAIL_RENDER_TYPE);
            renderEnergyTail(tailBuffer, pose, packedLight);
        }

        boltAlpha = embedded ? embeddedAlpha(entity, partialTick) : 255;
        final VertexConsumer buffer = bufferSource.getBuffer(embedded ? TAIL_RENDER_TYPE : RENDER_TYPE);
        renderBoltModel(buffer, pose, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** Calculates linear opacity over the final three seconds of an embedded bolt's lifetime. */
    private static int embeddedAlpha(@Nonnull final CorruptionBoltEntity entity, final float partialTick)
    {
        final float age = entity.getEmbeddedTicks() + partialTick;
        if (age <= EMBEDDED_FADE_START_TICKS)
        {
            return 255;
        }

        final float remaining = (EMBEDDED_LIFETIME_TICKS - age)
            / (EMBEDDED_LIFETIME_TICKS - EMBEDDED_FADE_START_TICKS);
        return Mth.clamp((int) (255.0F * remaining), 0, 255);
    }

    private void renderEnergyTail(final VertexConsumer buffer, final @Nonnull PoseStack.Pose pose, final int packedLight)
    {
        final float tipTail = -0.45F;
        final float beamEnd = -2.10F;
        final float rootHalfWidth = 0.18F;
        final float tailHalfWidth = 0.03F;

        addTailQuad(buffer, pose, packedLight,
            tipTail, -rootHalfWidth, 0.0F,
            tipTail, rootHalfWidth, 0.0F,
            beamEnd, tailHalfWidth, 0.0F,
            beamEnd, -tailHalfWidth, 0.0F,
            0.0F, 0.0F, 1.0F,
            210);

        addTailQuad(buffer, pose, packedLight,
            tipTail, 0.0F, -rootHalfWidth,
            tipTail, 0.0F, rootHalfWidth,
            beamEnd, 0.0F, tailHalfWidth,
            beamEnd, 0.0F, -tailHalfWidth,
            0.0F, 1.0F, 0.0F,
            170);
    }

    /** Renders two crossed side cards plus dedicated rear and front cards from the texture atlas. */
    private void renderBoltModel(final VertexConsumer buffer, final @Nonnull PoseStack.Pose pose, final int packedLight)
    {
        final float rear = -0.5F;
        final float front = 0.5F;
        final float rearView = rear + (3.0F / 16.0F);
        final float frontView = front - (3.0F / 16.0F);
        final float sideHalfWidth = 7.0F / 32.0F;
        final float endHalfWidth = 5.0F / 32.0F;

        addUvQuad(buffer, pose, packedLight,
            rear, -sideHalfWidth, 0.0F, SIDE_U0, SIDE_V1,
            front, -sideHalfWidth, 0.0F, SIDE_U1, SIDE_V1,
            front, sideHalfWidth, 0.0F, SIDE_U1, SIDE_V0,
            rear, sideHalfWidth, 0.0F, SIDE_U0, SIDE_V0,
            0.0F, 0.0F, 1.0F, boltAlpha);

        addUvQuad(buffer, pose, packedLight,
            rear, 0.0F, -sideHalfWidth, SIDE_U0, SIDE_V1,
            front, 0.0F, -sideHalfWidth, SIDE_U1, SIDE_V1,
            front, 0.0F, sideHalfWidth, SIDE_U1, SIDE_V0,
            rear, 0.0F, sideHalfWidth, SIDE_U0, SIDE_V0,
            0.0F, 1.0F, 0.0F, boltAlpha);

        addUvQuad(buffer, pose, packedLight,
            rearView, -endHalfWidth, endHalfWidth, REAR_U0, REAR_V1,
            rearView, endHalfWidth, endHalfWidth, REAR_U1, REAR_V1,
            rearView, endHalfWidth, -endHalfWidth, REAR_U1, REAR_V0,
            rearView, -endHalfWidth, -endHalfWidth, REAR_U0, REAR_V0,
            -1.0F, 0.0F, 0.0F, boltAlpha);

        addUvQuad(buffer, pose, packedLight,
            frontView, -endHalfWidth, -endHalfWidth, FRONT_U0, FRONT_V1,
            frontView, endHalfWidth, -endHalfWidth, FRONT_U1, FRONT_V1,
            frontView, endHalfWidth, endHalfWidth, FRONT_U1, FRONT_V0,
            frontView, -endHalfWidth, endHalfWidth, FRONT_U0, FRONT_V0,
            1.0F, 0.0F, 0.0F, boltAlpha);
    }

    /** Renders a tapered tail card from one opaque atlas texel, fading it to transparency at the end. */
    private static void addTailQuad(final VertexConsumer buffer,
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
        vertex(buffer, pose, packedLight, x0, y0, z0, TAIL_SAMPLE_U, TAIL_SAMPLE_V, nx, ny, nz, alpha);
        vertex(buffer, pose, packedLight, x1, y1, z1, TAIL_SAMPLE_U, TAIL_SAMPLE_V, nx, ny, nz, alpha);
        vertex(buffer, pose, packedLight, x2, y2, z2, TAIL_SAMPLE_U, TAIL_SAMPLE_V, nx, ny, nz, 0);
        vertex(buffer, pose, packedLight, x3, y3, z3, TAIL_SAMPLE_U, TAIL_SAMPLE_V, nx, ny, nz, 0);
    }

    /** Adds a textured quad whose four vertices each have an explicit atlas coordinate. */
    private static void addUvQuad(final VertexConsumer buffer,
        final @Nonnull PoseStack.Pose pose,
        final int packedLight,
        final float x0, final float y0, final float z0, final float u0, final float v0,
        final float x1, final float y1, final float z1, final float u1, final float v1,
        final float x2, final float y2, final float z2, final float u2, final float v2,
        final float x3, final float y3, final float z3, final float u3, final float v3,
        final float nx,
        final float ny,
        final float nz,
        final int alpha)
    {
        vertex(buffer, pose, packedLight, x0, y0, z0, u0, v0, nx, ny, nz, alpha);
        vertex(buffer, pose, packedLight, x1, y1, z1, u1, v1, nx, ny, nz, alpha);
        vertex(buffer, pose, packedLight, x2, y2, z2, u2, v2, nx, ny, nz, alpha);
        vertex(buffer, pose, packedLight, x3, y3, z3, u3, v3, nx, ny, nz, alpha);
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
