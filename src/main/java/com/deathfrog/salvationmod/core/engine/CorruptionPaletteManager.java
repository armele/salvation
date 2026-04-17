package com.deathfrog.salvationmod.core.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class CorruptionPaletteManager
{
    public static final String FOLDER = "corruption_profiles";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CorruptionPalette DEFAULTS = new CorruptionPalette(
        7362658,
        8941180,
        6309462,
        3088682,
        8480588,
        6704196,
        335.0F / 360.0F,
        0.16F,
        0.22F,
        0.18F
    );
    private static final CorruptionPaletteManager INSTANCE = new CorruptionPaletteManager();

    private volatile CorruptionPalette current = DEFAULTS;

    public static CorruptionPaletteManager get()
    {
        return INSTANCE;
    }

    public CorruptionPalette current()
    {
        return current;
    }

    private void setCurrent(final CorruptionPalette palette)
    {
        this.current = palette;
    }

    private static CorruptionPalette loadPalette(final Map<ResourceLocation, JsonElement> jsons)
    {
        CorruptionPalette palette = DEFAULTS;

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString))).toList())
        {
            final ResourceLocation fileId = entry.getKey();
            final JsonElement element = entry.getValue();
            if (!element.isJsonObject())
            {
                LOGGER.warn("Ignoring non-object corruption palette file {}", fileId);
                continue;
            }

            final JsonObject root = element.getAsJsonObject();
            final boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
            final CorruptionPalette base = replace ? DEFAULTS : palette;
            palette = parsePalette(base, root, fileId);
        }

        return palette;
    }

    private static CorruptionPalette parsePalette(final CorruptionPalette fallback, final JsonObject root, final ResourceLocation fileId)
    {
        final JsonObject anchors = root.has("anchors") && root.get("anchors").isJsonObject() ? root.getAsJsonObject("anchors") : null;
        final JsonObject hsv = root.has("hsv") && root.get("hsv").isJsonObject() ? root.getAsJsonObject("hsv") : null;

        if (anchors == null && hsv == null)
        {
            LOGGER.warn("Corruption palette file {} has neither anchors nor hsv sections; keeping previous values", fileId);
            return fallback;
        }

        return new CorruptionPalette(
            getInt(anchors, "fog", fallback.fogColor()),
            getInt(anchors, "sky", fallback.skyColor()),
            getInt(anchors, "water", fallback.waterColor()),
            getInt(anchors, "water_fog", fallback.waterFogColor()),
            getInt(anchors, "grass", fallback.grassColor()),
            getInt(anchors, "foliage", fallback.foliageColor()),
            getFloat(hsv, "hue_target", fallback.hueTarget()),
            getFloat(hsv, "hue_shift_strength", fallback.hueShiftStrength()),
            getFloat(hsv, "saturation_reduction", fallback.saturationReduction()),
            getFloat(hsv, "value_reduction", fallback.valueReduction())
        );
    }

    private static int getInt(final JsonObject object, final String key, final int fallback)
    {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isNumber())
        {
            return fallback;
        }
        return object.get(key).getAsInt();
    }

    private static float getFloat(final JsonObject object, final String key, final float fallback)
    {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isNumber())
        {
            return fallback;
        }
        return object.get(key).getAsFloat();
    }

    public record CorruptionPalette(
        int fogColor,
        int skyColor,
        int waterColor,
        int waterFogColor,
        int grassColor,
        int foliageColor,
        float hueTarget,
        float hueShiftStrength,
        float saturationReduction,
        float valueReduction)
    {
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
            final CorruptionPalette palette = loadPalette(jsons);
            CorruptionPaletteManager.get().setCurrent(palette);
            LOGGER.info("Loaded corruption palette from {} file(s)", jsons.size());
        }
    }
}
