package com.deathfrog.salvationmod.network;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData;
import com.deathfrog.salvationmod.core.engine.SalvationManager.CorruptionStage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = SalvationMod.MODID)
public final class ChunkCorruptionSync
{
    private static final String NBT_LAST_KEY = "salvation:lastChunkKey";
    private static final String NBT_LAST_TIME = "salvation:lastChunkSyncTime";
    private static final String NBT_LAST_VAL = "salvation:lastChunkCorruption";

    private static final int SYNC_PERIOD_TICKS = 20; // tune: 10..40 is fine

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        final long gameTime = level.getGameTime();
        final CompoundTag ptag = player.getPersistentData();

        final ChunkPos cp = player.chunkPosition();
        final long ck = cp.toLong();

        final long lastKey = ptag.getLong(NBT_LAST_KEY);
        final long lastTime = ptag.getLong(NBT_LAST_TIME);

        // Throttle: send on chunk-change OR every SYNC_PERIOD_TICKS
        if (ck == lastKey && (gameTime - lastTime) < SYNC_PERIOD_TICKS) return;

        final SalvationSavedData data = SalvationSavedData.get(level);
        final int corruption = data.getChunkCorruption(ck);

        // Also avoid sending if value hasn’t changed and we’re just on the period tick
        final int lastVal = ptag.getInt(NBT_LAST_VAL);
        if (ck == lastKey && corruption == lastVal && (gameTime - lastTime) < (SYNC_PERIOD_TICKS * 2L)) return;

        final CorruptionStage stage = SalvationManager.stageForLevel(level);
        final byte stageOrd = (byte) (stage == null ? 0 : stage.ordinal());

        // Send packet
        PacketDistributor.sendToPlayer(player, new ChunkCorruptionSyncMessage(ck, corruption, stageOrd));

        // Remember
        ptag.putLong(NBT_LAST_KEY, ck);
        ptag.putLong(NBT_LAST_TIME, gameTime);
        ptag.putInt(NBT_LAST_VAL, corruption);
    }
}