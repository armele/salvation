package com.deathfrog.salvationmod.client.corruptioneffects;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.network.ClientChunkCorruptionState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.deathfrog.salvationmod.core.engine.ChunkCorruptionSystem;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = SalvationMod.MODID, value = Dist.CLIENT)
public final class CorruptionDarknessOverlay
{
    @SubscribeEvent
    public static void onRenderGui(final RenderGuiEvent.Post event)
    {
        final Minecraft mc = Minecraft.getInstance();
        ClientLevel localLevel = mc.level;

        if (mc.player == null || localLevel == null) return;

        final int c = ClientChunkCorruptionState.getSmoothedCorruption();
        if (c < ChunkCorruptionSystem.VISIBLE_THRESHOLD) return;

        final float norm = computeNorm(c);

        final int stageOrd = ClientChunkCorruptionState.getStageOrd();
        final float stageScalar = computeStageScalar(stageOrd);

        final GuiGraphics gg = event.getGuiGraphics();
        final int w = gg.guiWidth();
        final int h = gg.guiHeight();

        // Time (seconds-ish) for smooth animation
        final float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        final float t = (localLevel.getGameTime() + partial) / 20.0F;

        // --- “Lens focus” vignette with subtle breathing pulse ---
        final float vignetteAlpha = computeVignetteAlpha(norm, stageScalar, t);
        drawFullscreenTexture(gg, VIGNETTE, w, h, vignetteAlpha);

        // --- Smoky motion (drifting haze), biased toward the edges ---
        drawEdgeWeightedSmoke(gg, w, h, norm, stageScalar, t);

        // If we still want a very light overall tint in addition to vignette/smoke,
        // uncomment the following line. Probably keep it low since vignette+smoke carry the effect.
        // drawFullscreenTint(gg, w, h, computeBaseTintAlpha(norm, stageScalar));
    }

    @SuppressWarnings("null")
    private static final @Nonnull ResourceLocation VIGNETTE = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/gui/corruption_vignette.png");

    @SuppressWarnings("null")
    private static final @Nonnull ResourceLocation SMOKE = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "textures/gui/corruption_smoke.png");

    // Smoke texture size (must match our file). Keep it power-of-two and tileable.
    private static final int SMOKE_TEX_SIZE = 256;
    private static final int SMOKE_PAD = 16; // keep away from edges (tune: 8..32)

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static float computeNorm(final int corruption)
    {
        return Mth.clamp((corruption - ChunkCorruptionSystem.VISIBLE_THRESHOLD) /
            (float) (ChunkCorruptionSystem.CORRUPTION_MAX - ChunkCorruptionSystem.VISIBLE_THRESHOLD), 0.0F, 1.0F);
    }

    /**
     * stageOrd is synced from server (stage.ordinal()). Our stages: 0..6. We care about 2..6 for “pressure” scaling.
     */
    private static float computeStageScalar(final int stageOrd)
    {
        return Mth.clamp((stageOrd - 2) / 4.0F, 0.0F, 1.0F);
    }

    /**
     * Vignette alpha: - Always present above threshold - Increases with norm + stage - “Breathes” slightly (pulse) especially at
     * higher stages
     */
    private static float computeVignetteAlpha(final float norm, final float stageScalar, final float t)
    {
        // Base encroachment
        final float base = 0.10F + 0.30F * norm;

        // Stage makes it feel “heavier” without crushing visibility early
        final float stageBoost = 0.12F * stageScalar * norm;

        // Slow “breathing” pulse. Stronger as stage and corruption rise.
        final float pulseAmp = 0.02F + 0.05F * stageScalar * norm; // subtle
        final float pulse = (float) Math.sin(t * 0.85F) * pulseAmp; // ~0.85Hz-ish

        // Tiny secondary wobble so it doesn’t feel too periodic.
        final float wobble = (float) Math.sin(t * 0.23F + 1.7F) * (pulseAmp * 0.35F);

        // Keep within reasonable bounds. Vignette can be higher than a full-screen tint.
        return Mth.clamp(base + stageBoost + pulse + wobble, 0.0F, 0.75F);
    }

    /**
     * Draw a full-screen texture with alpha. Uses standard blending.
     */
    private static void drawFullscreenTexture(final GuiGraphics gg,
        final @Nonnull ResourceLocation tex,
        final int w,
        final int h,
        final float alpha)
    {
        if (alpha <= 0.001F) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        gg.setColor(1.0F, 1.0F, 1.0F, alpha);
        gg.blit(tex, 0, 0, 0, 0, w, h, w, h);
        gg.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        RenderSystem.disableBlend();
    }

    /**
     * A very light full-screen tint. Most of the “look” should come from vignette + smoke
     */
    @SuppressWarnings("unused")
    private static float computeBaseTintAlpha(final float norm, final float stageScalar)
    {
        return Mth.clamp(0.01F + 0.06F * norm + 0.03F * stageScalar * norm, 0.0F, 0.15F);
    }

    @SuppressWarnings("unused")
    private static void drawFullscreenTint(final GuiGraphics gg, final int w, final int h, final float alpha)
    {
        if (alpha <= 0.001F) return;

        final int a = (int) (alpha * 255.0F) & 0xFF;
        final int argb = (a << 24); // black with alpha

        gg.fill(0, 0, w, h, argb);
    }

    /**
     * Draw smoky motion, but keep center clearer by biasing smoke toward corners/edges. Implementation: - We draw two drifting smoke
     * layers - Each layer is split into 4 “edge bands”: top, bottom, left, right - That yields an “edge-only smoke” feel even if the
     * texture itself is uniform
     */
    private static void drawEdgeWeightedSmoke(final GuiGraphics gg,
        final int w,
        final int h,
        final float norm,
        final float stageScalar,
        final float t)
    {
        // Overall strength — noticeable but not annoying.
        final float base = 0.02F + 0.10F * norm + 0.05F * stageScalar * norm;
        final float alpha1 = Mth.clamp(base, 0.0F, 0.22F);
        final float alpha2 = alpha1 * 0.75F;

        // Edge band thickness increases a bit with corruption (more “encroachment”).
        final float edgeFrac = Mth.clamp(0.18F + 0.12F * norm + 0.06F * stageScalar, 0.18F, 0.42F);

        final int u1 = wrapUV((int)(t * 12), SMOKE_TEX_SIZE, SMOKE_PAD);
        final int v1 = wrapUV((int)(t * 7),  SMOKE_TEX_SIZE, SMOKE_PAD);

        final int u2 = wrapUV((int)(t * -9), SMOKE_TEX_SIZE, SMOKE_PAD);
        final int v2 = wrapUV((int)(t * 5),  SMOKE_TEX_SIZE, SMOKE_PAD);

        // Layer 1 drift
        drawSmokeEdgeBands(gg, w, h, alpha1, u1, v1, edgeFrac);

        // Layer 2 drift (different direction/speed)
        drawSmokeEdgeBands(gg, w, h, alpha2, u2, v2, edgeFrac * 0.92F);
    }

    /**
     * Draw smoke in top/bottom/left/right edge bands (keeps center clearer). Each band blits the same tileable smoke texture, with
     * offsets for motion.
     */
    private static void drawSmokeEdgeBands(final GuiGraphics gg,
        final int w,
        final int h,
        final float alpha,
        final int u0,
        final int v0,
        final float edgeFrac)
    {
        if (alpha <= 0.001F) return;

        final int edgeW = Math.max(1, (int) (w * edgeFrac));
        final int edgeH = Math.max(1, (int) (h * edgeFrac));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        gg.setColor(1.0F, 1.0F, 1.0F, alpha);

        // Top band
        gg.blit(SMOKE, 0, 0, u0, v0, w, edgeH, SMOKE_TEX_SIZE, SMOKE_TEX_SIZE);

        // Bottom band
        gg.blit(SMOKE, 0, h - edgeH, u0, v0, w, edgeH, SMOKE_TEX_SIZE, SMOKE_TEX_SIZE);

        // Left band
        gg.blit(SMOKE, 0, 0, u0, v0, edgeW, h, SMOKE_TEX_SIZE, SMOKE_TEX_SIZE);

        // Right band
        gg.blit(SMOKE, w - edgeW, 0, u0, v0, edgeW, h, SMOKE_TEX_SIZE, SMOKE_TEX_SIZE);

        gg.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /**
     * Like Math.floorMod but for positive modulus with small ints and negative drift values.
     */
    private static int floorMod(final int value, final int mod)
    {
        final int r = value % mod;
        return r < 0 ? r + mod : r;
    }

    private static int wrapUV(final int v, final int texSize, final int pad)
    {
        final int span = Math.max(1, texSize - pad * 2);
        return pad + floorMod(v, span);
    }
}
