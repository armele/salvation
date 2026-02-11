package com.deathfrog.salvationmod.network;

import com.deathfrog.salvationmod.SalvationMod;
import com.ldtteam.common.network.AbstractClientPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ChunkCorruptionSyncMessage extends AbstractClientPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forClient(SalvationMod.MODID, "chunk_corruption", ChunkCorruptionSyncMessage::new);

    private final long chunkKey;   // ChunkPos#toLong()
    private final int corruption;  // 0..CORRUPTION_MAX
    private final byte stageOrd;   // optional, but handy for client tuning

    public ChunkCorruptionSyncMessage(final long chunkKey, final int corruption, final byte stageOrd)
    {
        super(TYPE);
        this.chunkKey = chunkKey;
        this.corruption = corruption;
        this.stageOrd = stageOrd;
    }

    public ChunkCorruptionSyncMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, TYPE);
        this.chunkKey = buf.readLong();
        this.corruption = buf.readVarInt();
        this.stageOrd = buf.readByte();
    }

    @Override
    public void toBytes(final RegistryFriendlyByteBuf buf)
    {
        buf.writeLong(chunkKey);
        buf.writeVarInt(corruption);
        buf.writeByte(stageOrd);
    }

    @Override
    protected void onExecute(IPayloadContext arg0, Player arg1)
    {
        // Always run on client thread
        ClientChunkCorruptionState.update(chunkKey, corruption, stageOrd);
    }
}