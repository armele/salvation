package com.deathfrog.salvationmod.core.engine;

import java.util.Optional;

/**
 * Stable corruption stage identities.
 *
 * Gameplay tuning for each stage is datapack-backed via {@link CorruptionStageRulesManager}.
 */
public enum CorruptionStage
{
    STAGE_0_UNTRIGGERED("stage_0_untriggered"),
    STAGE_1_NORMAL("stage_1_normal"),
    STAGE_2_AWAKENED("stage_2_awakened"),
    STAGE_3_SPREADING("stage_3_spreading"),
    STAGE_4_DANGEROUS("stage_4_dangerous"),
    STAGE_5_CRITICAL("stage_5_critical"),
    STAGE_6_TERMINAL("stage_6_terminal");

    private final String serializedName;

    CorruptionStage(final String serializedName)
    {
        this.serializedName = serializedName;
    }

    public String getSerializedName()
    {
        return serializedName;
    }

    public int getThreshold()
    {
        return CorruptionStageRulesManager.get().rulesFor(this).threshold();
    }

    public float getLootCorruptionChance()
    {
        return CorruptionStageRulesManager.get().rulesFor(this).lootCorruptionChance();
    }

    public float getEntitySpawnChance()
    {
        return CorruptionStageRulesManager.get().rulesFor(this).entitySpawnChance();
    }

    public int getDecayCooldown()
    {
        return CorruptionStageRulesManager.get().rulesFor(this).decayCooldown();
    }

    public int getBlightCooldown()
    {
        return CorruptionStageRulesManager.get().rulesFor(this).blightCooldown();
    }

    public float getDailyRaidSpawnChance()
    {
        return CorruptionStageRulesManager.get().rulesFor(this).dailyRaidSpawnChance();
    }

    public Optional<String> getTransitionMessageKey()
    {
        return CorruptionStageRulesManager.get().rulesFor(this).transitionMessageKey();
    }
}
