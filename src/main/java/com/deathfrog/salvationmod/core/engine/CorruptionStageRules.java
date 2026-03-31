package com.deathfrog.salvationmod.core.engine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record CorruptionStageRules(
    int threshold,
    float lootCorruptionChance,
    float entitySpawnChance,
    float dailyRaidSpawnChance,
    int decayCooldown,
    int blightCooldown,
    Optional<String> transitionMessageKey)
{
    @SuppressWarnings("null")
    public static final Codec<CorruptionStageRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("threshold").forGetter(CorruptionStageRules::threshold),
        Codec.floatRange(0.0F, 1.0F).fieldOf("loot_corruption_chance").forGetter(CorruptionStageRules::lootCorruptionChance),
        Codec.floatRange(0.0F, 1.0F).fieldOf("entity_spawn_chance").forGetter(CorruptionStageRules::entitySpawnChance),
        Codec.floatRange(0.0F, 1.0F).fieldOf("daily_raid_spawn_chance").forGetter(CorruptionStageRules::dailyRaidSpawnChance),
        Codec.INT.fieldOf("decay_cooldown").forGetter(CorruptionStageRules::decayCooldown),
        Codec.INT.fieldOf("blight_cooldown").forGetter(CorruptionStageRules::blightCooldown),
        Codec.STRING.optionalFieldOf("transition_message_key").forGetter(CorruptionStageRules::transitionMessageKey))
        .apply(instance, CorruptionStageRules::new));
}
