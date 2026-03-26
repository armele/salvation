package com.deathfrog.salvationmod.core.colony;

import net.minecraft.nbt.CompoundTag;

public class ColonyHandlerState
{
    static private final String TAG_NEXT_PROCESS_TICK = "nextProcessTick";
    static private final String TAG_LAST_EVAL = "lastEval";
    static private final String TAG_LAST_NOTIFICATION = "lastNotification";
    static private final String TAG_PURIFICATION_CREDITS = "purificationCredits";
    static private final String TAG_LAST_EXTERITIO_RAID_TICK = "lastExteritioRaidTick";
    static private final String TAG_LAST_EXTERITIO_RAID_DAY_CHECK = "lastExteritioRaidDayCheck";

    protected long nextProcessTick = 0L;
    protected long lastEvaluationGameTime = 0L;
    protected long lastNotificationGameTime = 0L;
    protected int purificationCredits = 0;
    protected long lastExteritioRaidTick = 0L;
    protected int lastExteritioRaidDayCheck = -1;

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
        tag.putInt(TAG_PURIFICATION_CREDITS, purificationCredits);
        tag.putLong(TAG_LAST_EXTERITIO_RAID_TICK, lastExteritioRaidTick);
        tag.putInt(TAG_LAST_EXTERITIO_RAID_DAY_CHECK, lastExteritioRaidDayCheck);
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
        state.purificationCredits = tag.getInt(TAG_PURIFICATION_CREDITS);
        state.lastExteritioRaidTick = tag.getLong(TAG_LAST_EXTERITIO_RAID_TICK);
        state.lastExteritioRaidDayCheck = tag.getInt(TAG_LAST_EXTERITIO_RAID_DAY_CHECK);
        return state;
    }

    public long getNextProcessTick()
    {
        return nextProcessTick;
    }

    public void setNextProcessTick(final long nextProcessTick)
    {
        this.nextProcessTick = nextProcessTick;
    }

    public long getLastEvaluationGameTime()
    {
        return lastEvaluationGameTime;
    }

    public void setLastEvaluationGameTime(final long lastEvaluationGameTime)
    {
        this.lastEvaluationGameTime = lastEvaluationGameTime;
    }

    public long getLastNotificationGameTime()
    {
        return lastNotificationGameTime;
    }

    public void setLastNotificationGameTime(final long lastNotificationGameTime)
    {
        this.lastNotificationGameTime = lastNotificationGameTime;
    }

    public int getPurificationCredits()
    {
        return purificationCredits;
    }

    public void setPurificationCredits(final int purificationCredits)
    {
        this.purificationCredits = purificationCredits;
    }

    public long getLastExteritioRaidTick() 
    {
        return lastExteritioRaidTick;
    }

    public void setLastExteritioRaidTick(long lastExteritioRaidTick) 
    {
        this.lastExteritioRaidTick = lastExteritioRaidTick;
    }

    public int getLastExteritioRaidDayCheck() 
    {
        return lastExteritioRaidDayCheck;
    }

    public void setLastExteritioRaidDayCheck(int lastExteritioRaidDayCheck) 
    {
        this.lastExteritioRaidDayCheck = lastExteritioRaidDayCheck;
    }
}
