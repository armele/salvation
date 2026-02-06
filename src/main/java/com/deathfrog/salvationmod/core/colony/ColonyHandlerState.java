package com.deathfrog.salvationmod.core.colony;

import net.minecraft.nbt.CompoundTag;

public class ColonyHandlerState
{
    static private final String TAG_NEXT_PROCESS_TICK = "nextProcessTick";
    static private final String TAG_LAST_EVAL = "lastEval";
    static private final String TAG_LAST_NOTIFICATION = "lastNotification";

    public long nextProcessTick = 0L;
    public long lastEvaluationGameTime = 0L;
    public long lastNotificationGameTime = 0L;

    public ColonyHandlerState() 
    {

    }

    /**
     * Returns a compound tag containing the state of the colony handler.
     * 
     * @return A compound tag containing the state of the colony handler
     */
    public CompoundTag toTag()
    {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TAG_NEXT_PROCESS_TICK, nextProcessTick);
        tag.putLong(TAG_LAST_EVAL, lastEvaluationGameTime);
        tag.putLong(TAG_LAST_NOTIFICATION, lastNotificationGameTime);
        return tag;
    }

    /**
     * Creates a new ColonyHandlerState from the given CompoundTag.
     * 
     * @param tag the compound tag to deserialize the state from
     * @return the deserialized state
     */
    public static ColonyHandlerState fromTag(CompoundTag tag)
    {
        ColonyHandlerState state = new ColonyHandlerState();
        state.nextProcessTick = tag.getLong(TAG_NEXT_PROCESS_TICK);
        state.lastEvaluationGameTime = tag.getLong(TAG_LAST_EVAL);
        state.lastNotificationGameTime = tag.getLong(TAG_LAST_NOTIFICATION);
        return state;
    }
}