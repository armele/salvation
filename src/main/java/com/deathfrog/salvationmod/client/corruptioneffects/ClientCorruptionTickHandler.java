package com.deathfrog.salvationmod.client.corruptioneffects;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.network.ClientChunkCorruptionState;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = SalvationMod.MODID, value = Dist.CLIENT)
public final class ClientCorruptionTickHandler
{
    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event)
    {
        // Optional guards (recommended)
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.isPaused()) return; // keep it frozen while paused

        ClientChunkCorruptionState.clientTick();
    }
}