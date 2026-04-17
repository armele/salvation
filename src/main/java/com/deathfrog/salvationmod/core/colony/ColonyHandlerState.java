package com.deathfrog.salvationmod.core.colony;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class ColonyHandlerState
{
    static private final String TAG_NEXT_PROCESS_TICK = "nextProcessTick";
    static private final String TAG_LAST_EVAL = "lastEval";
    static private final String TAG_LAST_NOTIFICATION = "lastNotification";
    static private final String TAG_PURIFICATION_CREDITS = "purificationCredits";
    static private final String TAG_CORRUPTION_CONTRIBUTION = "corruptionContribution";
    static private final String TAG_ROLLING_MITIGATION = "rollingMitigation";
    static private final String TAG_ROLLING_DAY = "day";
    static private final String TAG_ROLLING_PURIFICATION = "purification";
    static private final String TAG_ROLLING_CORRUPTION = "corruption";
    static private final String TAG_LAST_EXTERITIO_RAID_TICK = "lastExteritioRaidTick";
    static private final String TAG_LAST_EXTERITIO_RAID_DAY_CHECK = "lastExteritioRaidDayCheck";
    static private final String TAG_REFUGEE_RECRUITMENT_COUNT = "refugeeRecruitmentCount";

    protected long nextProcessTick = 0L;
    protected long lastEvaluationGameTime = 0L;
    protected long lastNotificationGameTime = 0L;
    protected int purificationCredits = 0;
    protected int corruptionContribution = 0;
    protected final List<RollingMitigationEntry> rollingMitigation = new ArrayList<>();
    protected long lastExteritioRaidTick = 0L;
    protected int lastExteritioRaidDayCheck = -1;
    protected int refugeeRecruitmentCount = 0;

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
        tag.putInt(TAG_CORRUPTION_CONTRIBUTION, corruptionContribution);
        ListTag rollingMitigationTag = new ListTag();
        for (RollingMitigationEntry entry : rollingMitigation)
        {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong(TAG_ROLLING_DAY, entry.day);
            entryTag.putInt(TAG_ROLLING_PURIFICATION, entry.purificationCredits);
            entryTag.putInt(TAG_ROLLING_CORRUPTION, entry.corruptionContribution);
            rollingMitigationTag.add(entryTag);
        }
        tag.put(TAG_ROLLING_MITIGATION, rollingMitigationTag);
        tag.putLong(TAG_LAST_EXTERITIO_RAID_TICK, lastExteritioRaidTick);
        tag.putInt(TAG_LAST_EXTERITIO_RAID_DAY_CHECK, lastExteritioRaidDayCheck);
        tag.putInt(TAG_REFUGEE_RECRUITMENT_COUNT, refugeeRecruitmentCount);
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
        state.corruptionContribution = tag.getInt(TAG_CORRUPTION_CONTRIBUTION);
        if (tag.contains(TAG_ROLLING_MITIGATION, Tag.TAG_LIST))
        {
            ListTag rollingMitigationTag = tag.getList(TAG_ROLLING_MITIGATION, Tag.TAG_COMPOUND);
            for (int i = 0; i < rollingMitigationTag.size(); i++)
            {
                CompoundTag entryTag = rollingMitigationTag.getCompound(i);
                state.rollingMitigation.add(new RollingMitigationEntry(
                    entryTag.getLong(TAG_ROLLING_DAY),
                    entryTag.getInt(TAG_ROLLING_PURIFICATION),
                    entryTag.getInt(TAG_ROLLING_CORRUPTION)));
            }
        }
        state.lastExteritioRaidTick = tag.getLong(TAG_LAST_EXTERITIO_RAID_TICK);
        state.lastExteritioRaidDayCheck = tag.getInt(TAG_LAST_EXTERITIO_RAID_DAY_CHECK);
        state.refugeeRecruitmentCount = tag.getInt(TAG_REFUGEE_RECRUITMENT_COUNT);
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

    public int getCorruptionContribution()
    {
        return corruptionContribution;
    }

    public void setCorruptionContribution(final int corruptionContribution)
    {
        this.corruptionContribution = corruptionContribution;
    }

    public void recordPurificationForDay(final long day, final int amount)
    {
        if (amount <= 0)
        {
            return;
        }

        getOrCreateRollingMitigationEntry(day).purificationCredits += amount;
    }

    public void recordCorruptionForDay(final long day, final int amount)
    {
        if (amount <= 0)
        {
            return;
        }

        getOrCreateRollingMitigationEntry(day).corruptionContribution += amount;
    }

    public void pruneRollingMitigation(final long currentDay, final int windowDays)
    {
        final long earliestIncludedDay = Math.max(0L, currentDay - Math.max(1, windowDays) + 1L);
        for (Iterator<RollingMitigationEntry> iterator = rollingMitigation.iterator(); iterator.hasNext(); )
        {
            if (iterator.next().day < earliestIncludedDay)
            {
                iterator.remove();
            }
        }
    }

    public long getRollingPurificationCredits(final long currentDay, final int windowDays)
    {
        pruneRollingMitigation(currentDay, windowDays);
        long total = 0L;
        for (RollingMitigationEntry entry : rollingMitigation)
        {
            total += entry.purificationCredits;
        }
        return total;
    }

    public long getRollingCorruptionContribution(final long currentDay, final int windowDays)
    {
        pruneRollingMitigation(currentDay, windowDays);
        long total = 0L;
        for (RollingMitigationEntry entry : rollingMitigation)
        {
            total += entry.corruptionContribution;
        }
        return total;
    }

    private RollingMitigationEntry getOrCreateRollingMitigationEntry(final long day)
    {
        for (RollingMitigationEntry entry : rollingMitigation)
        {
            if (entry.day == day)
            {
                return entry;
            }
        }

        RollingMitigationEntry entry = new RollingMitigationEntry(day, 0, 0);
        rollingMitigation.add(entry);
        return entry;
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

    public int getRefugeeRecruitmentCount()
    {
        return refugeeRecruitmentCount;
    }

    public void setRefugeeRecruitmentCount(final int refugeeRecruitmentCount)
    {
        this.refugeeRecruitmentCount = refugeeRecruitmentCount;
    }

    protected static final class RollingMitigationEntry
    {
        private final long day;
        private int purificationCredits;
        private int corruptionContribution;

        private RollingMitigationEntry(final long day, final int purificationCredits, final int corruptionContribution)
        {
            this.day = day;
            this.purificationCredits = purificationCredits;
            this.corruptionContribution = corruptionContribution;
        }
    }
}
