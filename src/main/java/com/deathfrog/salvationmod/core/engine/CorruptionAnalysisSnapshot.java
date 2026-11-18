package com.deathfrog.salvationmod.core.engine;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;

import net.minecraft.server.level.ServerLevel;

/**
 * Immutable, player-facing summary of corruption progression in one dimension.
 *
 * @param stage current corruption stage
 * @param progression current effective progression
 * @param currentThreshold effective threshold at which the current stage begins
 * @param nextThreshold effective threshold for the next stage, or {@code -1} at the terminal stage
 * @param progressPercent percentage through the current stage interval
 * @param sourceTotals lifetime signed totals by progression source
 */
public record CorruptionAnalysisSnapshot(
    CorruptionStage stage,
    long progression,
    long currentThreshold,
    long nextThreshold,
    double progressPercent,
    Map<ProgressionSource, Long> sourceTotals)
{
    /**
     * Builds an analysis snapshot from the authoritative saved data for a dimension.
     *
     * @param level dimension to analyze
     * @return immutable corruption analysis snapshot
     */
    public static CorruptionAnalysisSnapshot create(final @Nonnull ServerLevel level)
    {
        final SalvationSavedData data = SalvationSavedData.get(level);
        final long progression = SalvationManager.getProgressionMeasure(level);
        final CorruptionStage stage = SalvationManager.stageForLevel(level);
        final long currentThreshold = SalvationManager.getEffectiveStageThreshold(level, stage);
        final CorruptionStage[] stages = CorruptionStage.values();
        final boolean terminal = stage.ordinal() >= stages.length - 1;

        @SuppressWarnings("null")
        final long nextThreshold = terminal
            ? -1L
            : SalvationManager.getEffectiveStageThreshold(level, stages[stage.ordinal() + 1]);
        
            final double progressPercent = calculateProgressPercent(progression, currentThreshold, nextThreshold);
        final Map<ProgressionSource, Long> sourceTotals = new EnumMap<>(ProgressionSource.class);

        for (final ProgressionSource source : ProgressionSource.values())
        {
            sourceTotals.put(source, data.getProgressionMeasure(source));
        }

        return new CorruptionAnalysisSnapshot(
            stage,
            progression,
            currentThreshold,
            nextThreshold,
            progressPercent,
            Map.copyOf(sourceTotals));
    }

    /**
     * Calculates progress through the current stage interval.
     *
     * @param progression current effective progression
     * @param currentThreshold beginning of the current stage interval
     * @param nextThreshold beginning of the next stage, or a negative value for the terminal stage
     * @return percentage clamped to the range 0 through 100
     */
    public static double calculateProgressPercent(
        final long progression,
        final long currentThreshold,
        final long nextThreshold)
    {
        if (nextThreshold < 0L)
        {
            return 100.0D;
        }

        final long interval = nextThreshold - currentThreshold;
        if (interval <= 0L)
        {
            return 0.0D;
        }

        final double percent = (double) (progression - currentThreshold) * 100.0D / (double) interval;
        return Math.max(0.0D, Math.min(100.0D, percent));
    }
}
