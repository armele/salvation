package com.deathfrog.salvationmod.core.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class CorruptionStageRulesManager
{
    public static final String FOLDER = "salvation_corruption_stages";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CorruptionStageRulesManager INSTANCE = new CorruptionStageRulesManager();
    private static final StageRulesSnapshot DEFAULTS = createDefaultSnapshot();

    private volatile StageRulesSnapshot current = DEFAULTS;

    public static CorruptionStageRulesManager get()
    {
        return INSTANCE;
    }

    public CorruptionStageRules rulesFor(final CorruptionStage stage)
    {
        return current.rulesFor(stage);
    }

    private void setSnapshot(final StageRulesSnapshot snapshot)
    {
        this.current = snapshot;
    }

    private static StageRulesSnapshot createDefaultSnapshot()
    {
        final EnumMap<CorruptionStage, CorruptionStageRules> defaults = new EnumMap<>(CorruptionStage.class);
        defaults.put(CorruptionStage.STAGE_0_UNTRIGGERED, new CorruptionStageRules(0, 0.00F, 0.00F, 0.00F, 0.00F, 20 * 60 * 1, 20 * 60 * 12, Optional.empty()));
        defaults.put(CorruptionStage.STAGE_1_NORMAL, new CorruptionStageRules(3000, 0.02F, 0.04F, 0.00F, 0.00F, 20 * 60 * 3, 20 * 60 * 10, Optional.of("message.salvation.corruption.stage_1_normal")));
        defaults.put(CorruptionStage.STAGE_2_AWAKENED, new CorruptionStageRules(9000, 0.04F, 0.12F, 0.00F, 0.00F, 20 * 60 * 4, 20 * 60 * 8, Optional.of("message.salvation.corruption.stage_2_awakened")));
        defaults.put(CorruptionStage.STAGE_3_SPREADING, new CorruptionStageRules(24000, 0.08F, 0.36F, 0.00F, 0.02F, 20 * 60 * 8, 20 * 60 * 6, Optional.of("message.salvation.corruption.stage_3_spreading")));
        defaults.put(CorruptionStage.STAGE_4_DANGEROUS, new CorruptionStageRules(48000, 0.12F, 0.72F, 0.02F, 0.04F, 20 * 60 * 12, 20 * 60 * 4, Optional.of("message.salvation.corruption.stage_4_dangerous")));
        defaults.put(CorruptionStage.STAGE_5_CRITICAL, new CorruptionStageRules(96000, 0.20F, 1.00F, 0.04F, 0.8F, 20 * 60 * 20, 20 * 60 * 2, Optional.of("message.salvation.corruption.stage_5_critical")));
        defaults.put(CorruptionStage.STAGE_6_TERMINAL, new CorruptionStageRules(192000, 0.32F, 1.00F, 0.08F, 0.16F, 20 * 60 * 32, 20 * 60 * 1, Optional.of("message.salvation.corruption.stage_6_terminal")));
        return new StageRulesSnapshot(defaults);
    }

    private static Optional<StageRulesSnapshot> loadSnapshot(final Map<ResourceLocation, JsonElement> jsons)
    {
        final EnumMap<CorruptionStage, CorruptionStageRules> merged = new EnumMap<>(DEFAULTS.rules());
        boolean sawReplace = false;

        for (Map.Entry<ResourceLocation, JsonElement> entry : new HashMap<>(jsons).entrySet())
        {
            final ResourceLocation fileId = entry.getKey();
            final JsonElement element = entry.getValue();

            if (!element.isJsonObject())
            {
                LOGGER.warn("Ignoring non-object corruption stage rules file {}", fileId);
                continue;
            }

            final JsonObject root = element.getAsJsonObject();
            final boolean replace = root.has("replace") && root.get("replace").getAsBoolean();

            if (replace && !sawReplace)
            {
                merged.clear();
                sawReplace = true;
            }

            final JsonObject stagesObject = root.has("stages") && root.get("stages").isJsonObject()
                ? root.getAsJsonObject("stages")
                : null;

            if (stagesObject == null)
            {
                LOGGER.warn("Corruption stage rules file {} is missing a stages object", fileId);
                continue;
            }

            for (CorruptionStage stage : CorruptionStage.values())
            {
                final String key = stage.getSerializedName();
                if (!stagesObject.has(key))
                {
                    continue;
                }

                final JsonElement stageElement = stagesObject.get(key);
                final Optional<CorruptionStageRules> parsed = CorruptionStageRules.CODEC.parse(JsonOps.INSTANCE, stageElement)
                    .resultOrPartial(error -> LOGGER.error("Failed to parse corruption stage rules {} in {}: {}", key, fileId, error));

                parsed.ifPresent(rules -> merged.put(stage, rules));
            }
        }

        return StageRulesSnapshot.validate(merged);
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener
    {
        private static final Gson GSON = new GsonBuilder().create();

        public ReloadListener()
        {
            super(GSON, FOLDER);
        }

        @Override
        protected void apply(final @Nonnull Map<ResourceLocation, JsonElement> jsons,
            final @Nonnull ResourceManager resourceManager,
            final @Nonnull ProfilerFiller profiler)
        {
            final Optional<StageRulesSnapshot> loaded = loadSnapshot(jsons);
            if (loaded.isPresent())
            {
                CorruptionStageRulesManager.get().setSnapshot(loaded.get());
                LOGGER.info("Loaded datapack corruption stage rules from {} file(s)", jsons.size());
            }
            else
            {
                CorruptionStageRulesManager.get().setSnapshot(DEFAULTS);
                LOGGER.warn("Using built-in corruption stage defaults because datapack rules were invalid");
            }
        }
    }

    private record StageRulesSnapshot(Map<CorruptionStage, CorruptionStageRules> rules)
    {
        private StageRulesSnapshot(final EnumMap<CorruptionStage, CorruptionStageRules> rules)
        {
            this(Collections.unmodifiableMap(new EnumMap<>(rules)));
        }

        private CorruptionStageRules rulesFor(final CorruptionStage stage)
        {
            return rules.get(stage);
        }

        private static Optional<StageRulesSnapshot> validate(final Map<CorruptionStage, CorruptionStageRules> rules)
        {
            final EnumMap<CorruptionStage, CorruptionStageRules> validated = new EnumMap<>(CorruptionStage.class);

            int previousThreshold = Integer.MIN_VALUE;
            for (CorruptionStage stage : CorruptionStage.values())
            {
                final CorruptionStageRules stageRules = rules.get(stage);
                if (stageRules == null)
                {
                    LOGGER.error("Missing corruption stage rules for {}", stage.getSerializedName());
                    return Optional.empty();
                }

                if (stageRules.decayCooldown() <= 0 || stageRules.blightCooldown() <= 0)
                {
                    LOGGER.error("Cooldowns for {} must be positive", stage.getSerializedName());
                    return Optional.empty();
                }

                if (stageRules.threshold() <= previousThreshold)
                {
                    LOGGER.error("Thresholds must be strictly increasing; {} had {}", stage.getSerializedName(), stageRules.threshold());
                    return Optional.empty();
                }

                validated.put(stage, stageRules);
                previousThreshold = stageRules.threshold();
            }

            return Optional.of(new StageRulesSnapshot(validated));
        }
    }
}

