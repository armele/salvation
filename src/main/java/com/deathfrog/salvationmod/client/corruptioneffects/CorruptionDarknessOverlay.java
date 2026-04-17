package com.deathfrog.salvationmod.client.corruptioneffects;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.ModEnchantments;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.network.ClientChunkCorruptionState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.deathfrog.salvationmod.core.engine.ChunkCorruptionSystem;
import com.deathfrog.salvationmod.core.engine.CorruptionStage;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = SalvationMod.MODID, value = Dist.CLIENT)
public final class CorruptionDarknessOverlay
{
    /**
     * Called when the GUI is rendered.
     * Responsible for rendering the "corruption overlay" which includes:
     *   - A vignette that darkens the view with a subtle breathing pulse.
     *   - A smoky motion (drifting haze) biased towards the edges.
     *   - A light overall tint in addition to the vignette and smoke.
     * @param event The event fired when the GUI is rendered.
     */
    @SubscribeEvent
    public static void onRenderGui(final RenderGuiEvent.Post event)
    {
        final Minecraft mc = Minecraft.getInstance();
        ClientLevel localLevel = mc.level;

        LocalPlayer localPlayer = mc.player; 

        if (localPlayer == null || localLevel == null) return;

        final int c = ClientChunkCorruptionState.getSmoothedCorruption();
        final int stageOrd = ClientChunkCorruptionState.getStageOrd();
        final boolean biomeMutated = ClientChunkCorruptionState.isBiomeMutated();

        BlockPos playerPos = localPlayer.blockPosition();

        if (playerPos == null) return;

        final boolean inCorruptedBiome = localLevel.getBiome(playerPos).is(ModTags.Biomes.CORRUPTED_BIOMES);

        if (ModEnchantments.hasCorruptionSight(localLevel, localPlayer.getItemBySlot(EquipmentSlot.HEAD)))
        {
            drawCorruptionMeters(event.getGuiGraphics(), mc, computeMeterNorm(c, biomeMutated, inCorruptedBiome), stageOrd);
            return;
        }

        if (c < ChunkCorruptionSystem.VISIBLE_THRESHOLD || stageOrd < CorruptionStage.STAGE_1_NORMAL.ordinal()) return;

        final float norm = computeNorm(c);

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
        final int clampedCorruption = ChunkCorruptionSystem.clampToStandardCorruptionThreshold(corruption);
        return Mth.clamp((clampedCorruption - ChunkCorruptionSystem.VISIBLE_THRESHOLD) /
            (float) (ChunkCorruptionSystem.STANDARD_CORRUPTION_THRESHOLD - ChunkCorruptionSystem.VISIBLE_THRESHOLD), 0.0F, 1.0F);
    }

    private static float computeMeterNorm(final int corruption, final boolean biomeMutated, final boolean inCorruptedBiome)
    {
        if (inCorruptedBiome)
        {
            return 1.0F;
        }

        if (biomeMutated)
        {
            final int clampedCorruption = Mth.clamp(corruption, 0, ChunkCorruptionSystem.BIOME_CONVERSION_THRESHOLD);
            return Mth.clamp(clampedCorruption / (float) ChunkCorruptionSystem.BIOME_CONVERSION_THRESHOLD, 0.0F, 1.0F);
        }

        final int clampedCorruption = Mth.clamp(corruption, 0, ChunkCorruptionSystem.BIOME_CONVERSION_THRESHOLD - 1);
        final float preMutationNorm = clampedCorruption / (float) ChunkCorruptionSystem.BIOME_CONVERSION_THRESHOLD;
        return Math.min(preMutationNorm, 0.99F);
    }

    private static float computeStageMeterNorm(final int stageOrd)
    {
        final int maxStage = CorruptionStage.STAGE_6_TERMINAL.ordinal();
        final int clampedStage = Mth.clamp(stageOrd, CorruptionStage.STAGE_0_UNTRIGGERED.ordinal(), maxStage);
        return clampedStage / (float) maxStage;
    }

    /**
     * stageOrd is synced from server (stage.ordinal()). 
     * The higher the stage, the more pronounced the effect.
     */
    private static float computeStageScalar(final int stageOrd)
    {
        return Mth.clamp((stageOrd - 3) / 4.0F, 0.0F, 1.0F);
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

    /**
     * Draws the corruption meters with gradient fills, based on corruption values [0.0, 1.0].
     * 
     * @param gg the GuiGraphics to draw with
     * @param mc the Minecraft instance to use for font rendering
     * @param localNorm the local chunk corruption value to base the meter on [0.0, 1.0]
     * @param stageOrd the synced dimension corruption stage ordinal
     */
    private static void drawCorruptionMeters(final GuiGraphics gg, final Minecraft mc, final float localNorm, final int stageOrd)
    {
        final int x = 8;
        final int y = 8;
        final int rowGap = 21;
        final int maxStage = CorruptionStage.STAGE_6_TERMINAL.ordinal();
        final int clampedStage = Mth.clamp(stageOrd, CorruptionStage.STAGE_0_UNTRIGGERED.ordinal(), maxStage);

        drawCorruptionMeter(gg, mc, x, y, Component.translatable("gui.salvation.corruption_meter.local"), localNorm, Component.literal(Math.round(localNorm * 100.0F) + "%"));
        drawCorruptionMeter(gg, mc, x, y + rowGap, Component.translatable("gui.salvation.corruption_meter.global"), computeStageMeterNorm(clampedStage),
            Component.translatable("gui.salvation.corruption_meter.stage", clampedStage, maxStage));
    }

    @SuppressWarnings("null")
    private static void drawCorruptionMeter(final GuiGraphics gg,
        final Minecraft mc,
        final int x,
        final int y,
        final Component label,
        final float norm,
        final Component valueText)
    {
        final int barW = 82;
        final int barH = 6;
        final int fillW = Mth.clamp(Math.round(barW * norm), 0, barW);

        gg.drawString(mc.font, label, x, y, 0xFFFFFFFF, true);

        final int barY = y + 11;
        gg.fill(x - 1, barY - 1, x + barW + 1, barY + barH + 1, 0xAA000000);
        gg.fill(x, barY, x + barW, barY + barH, 0xAA202020);

        for (int i = 0; i < fillW; i++)
        {
            final float t = i / (float) (barW - 1);
            gg.fill(x + i, barY, x + i + 1, barY + barH, corruptionMeterColor(t));
        }

        gg.drawString(mc.font, valueText, x + barW + 5, y + 7, corruptionMeterColor(norm), true);
    }

    private static int corruptionMeterColor(final float norm)
    {
        final float clamped = Mth.clamp(norm, 0.0F, 1.0F);
        final int red = Math.round(0x55 + (0xFF - 0x55) * clamped);
        final int green = Math.round(0xFF - (0xFF - 0x45) * clamped);
        final int blue = Math.round(0x55 - 0x55 * clamped);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
