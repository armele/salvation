package com.deathfrog.salvationmod.core.engine;

/**
 * Corruption stages.
 */
public enum CorruptionStage
{
    // IDEA: (Phase 3) Make this datapacked
    STAGE_0_UNTRIGGERED(0,      0.00f, 0.01f, 0.00f, 20 * 60 * 1 , 20 * 60 * 12),
    STAGE_1_NORMAL(     3000,   0.02f, 0.04f, 0.00f, 20 * 60 * 3 , 20 * 60 * 10),
    STAGE_2_AWAKENED(   9000,   0.04f, 0.12f, 0.02f, 20 * 60 * 4 , 20 * 60 * 8),
    STAGE_3_SPREADING(  24000,  0.08f, 0.36f, 0.04f, 20 * 60 * 8 , 20 * 60 * 6),
    STAGE_4_DANGEROUS(  48000,  0.12f, 0.72f, 0.08f, 20 * 60 * 12, 20 * 60 * 4),
    STAGE_5_CRITICAL(   96000,  0.20f, 1.00f, 0.16f, 20 * 60 * 20, 20 * 60 * 2),
    STAGE_6_TERMINAL(   192000, 0.32f, 1.00f, 0.32f, 20 * 60 * 32, 20 * 60 * 1);

    private final int threshold;
    private final float lootCorruptionChance;
    private final float entitySpawnChance;
    private final float dailyRaidSpawnChance;
    private final int decayCooldown;
    private final int blightCooldown;

    CorruptionStage(int threshold, float lootCorruptionChance, float entitySpawnChance, float dailyRaidSpawnChance, int decayCooldown, int blightCooldown)
    {
        this.threshold = threshold;
        this.lootCorruptionChance = lootCorruptionChance;
        this.entitySpawnChance = entitySpawnChance;
        this.dailyRaidSpawnChance = dailyRaidSpawnChance;
        this.decayCooldown = decayCooldown;
        this.blightCooldown = blightCooldown;
    }

    public int getThreshold()
    {
        return threshold;
    }

    public float getLootCorruptionChance()
    {
        return lootCorruptionChance;
    }

    public float getEntitySpawnChance()
    {
        return entitySpawnChance;
    }

    public int getDecayCooldown()
    {
        return decayCooldown;
    }

    public int getBlightCooldown()
    {
        return blightCooldown;
    }

    public float getDailyRaidSpawnChance()
    {
        return dailyRaidSpawnChance;
    }
}
