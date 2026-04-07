package com.deathfrog.salvationmod.client.corruptioneffects;

import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.SalvationMod;
import com.mojang.blaze3d.shaders.FogShape;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = SalvationMod.MODID, value = Dist.CLIENT)
public final class CorruptedBiomeFogHandler
{
    private static final float FOG_RED = 0x5A / 255.0F;
    private static final float FOG_GREEN = 0x3F / 255.0F;
    private static final float FOG_BLUE = 0x63 / 255.0F;
    private static final float COLOR_BLEND = 0.55F;

    private CorruptedBiomeFogHandler()
    {
    }

    @SubscribeEvent
    public static void onComputeFogColor(final ViewportEvent.ComputeFogColor event)
    {
        if (!isCorruptedBiome(event.getCamera())) return;

        event.setRed(Mth.lerp(COLOR_BLEND, event.getRed(), FOG_RED));
        event.setGreen(Mth.lerp(COLOR_BLEND, event.getGreen(), FOG_GREEN));
        event.setBlue(Mth.lerp(COLOR_BLEND, event.getBlue(), FOG_BLUE));
    }

    @SubscribeEvent
    public static void onRenderFog(final ViewportEvent.RenderFog event)
    {
        if (event.getType() != FogType.NONE || !isCorruptedBiome(event.getCamera())) return;

        final float farPlane = event.getFarPlaneDistance();
        final boolean skyFog = event.getMode() == FogRenderer.FogMode.FOG_SKY;
        final float fogEnd = skyFog
            ? Math.min(farPlane * 0.70F, 144.0F)
            : Math.min(farPlane * 0.48F, 96.0F);

        event.setNearPlaneDistance(skyFog ? 0.0F : Math.min(fogEnd * 0.22F, 18.0F));
        event.setFarPlaneDistance(Math.max(fogEnd, skyFog ? 48.0F : 32.0F));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SuppressWarnings("null")
    private static boolean isCorruptedBiome(final Camera camera)
    {
        final Level level = camera.getEntity().level();
        final BlockPos pos = camera.getBlockPosition();
        return level.getBiome(pos).is(ModTags.Biomes.CORRUPTED_BIOMES);
    }
}
