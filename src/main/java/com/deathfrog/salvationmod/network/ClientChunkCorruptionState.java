package com.deathfrog.salvationmod.network;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientChunkCorruptionState
{
    private static final AtomicLong currentChunkKey = new AtomicLong(0L);
    private static final AtomicInteger targetCorruption = new AtomicInteger(0);
    private static final AtomicInteger smoothedCorruption = new AtomicInteger(0);
    private static volatile byte stageOrd = 0;

    public static void update(final long chunkKey, final int corruption, final byte stage)
    {
        currentChunkKey.set(chunkKey);
        targetCorruption.set(corruption);
        stageOrd = stage;
    }

    /** Call once per client tick to smooth transitions. */
    public static void clientTick()
    {
        final int t = targetCorruption.get();
        final int s = smoothedCorruption.get();

        // Simple exponential-ish smoothing (fast enough to feel responsive, slow enough to avoid flicker)
        final int next = s + (int) Math.signum(t - s) * Math.max(1, Math.abs(t - s) / 6);
        smoothedCorruption.set(next);
    }

    public static int getSmoothedCorruption()
    {
        return smoothedCorruption.get();
    }

    public static byte getStageOrd()
    {
        return stageOrd;
    }
}
