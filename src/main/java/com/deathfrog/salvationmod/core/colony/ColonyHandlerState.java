package com.deathfrog.salvationmod.core.colony;

import net.minecraft.nbt.CompoundTag;

public class ColonyHandlerState
{
    static public final String TAG_NEXT_PROCESS_TICK = "nextProcessTick";
    static public final String TAG_LAST_EVAL = "lastEval";

    public long nextProcessTick = 0L;
    public long lastEvaluationGameTime = 0L;

    public ColonyHandlerState() 
    {

    }

    public CompoundTag toTag()
    {
        CompoundTag t = new CompoundTag();
        t.putLong(TAG_NEXT_PROCESS_TICK, nextProcessTick);
        t.putLong(TAG_LAST_EVAL, lastEvaluationGameTime);
        return t;
    }

    public static ColonyHandlerState fromTag(CompoundTag t)
    {
        ColonyHandlerState s = new ColonyHandlerState();
        s.nextProcessTick = t.getLong(TAG_NEXT_PROCESS_TICK);
        s.lastEvaluationGameTime = t.getLong(TAG_LAST_EVAL);
        return s;
    }
}