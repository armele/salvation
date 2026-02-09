package com.deathfrog.salvationmod.core.engine;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;
import java.util.Map.Entry;

import javax.annotation.Nonnull;

public final class CureMappingsManager
{
    public static final String FOLDER = "salvation_cure_mappings";

    private volatile Map<ResourceLocation, ResourceLocation> corruptedToVanilla = Map.of();
    private volatile Map<ResourceLocation, ResourceLocation> vanillaToCorrupted = Map.of();

    public Optional<ResourceLocation> getVanillaForCorrupted(final ResourceLocation corruptedId)
    {
        return Optional.ofNullable(corruptedToVanilla.get(corruptedId));
    }

    public Optional<ResourceLocation> getCorruptedForVanilla(final ResourceLocation vanillaId)
    {
        return Optional.ofNullable(vanillaToCorrupted.get(vanillaId));
    }

    /** Replace current maps atomically. */
    private void setMappings(final Map<ResourceLocation, ResourceLocation> c2v)
    {
        this.corruptedToVanilla = Map.copyOf(c2v);

        final Map<ResourceLocation, ResourceLocation> v2c = new HashMap<>();
        for (Entry<ResourceLocation, ResourceLocation> e : c2v.entrySet())
        {
            v2c.put(e.getValue(), e.getKey());
        }
        this.vanillaToCorrupted = Map.copyOf(v2c);
    }

    /** Adds entries (overrides existing keys). */
    public void mergeOverrides(final Map<ResourceLocation, ResourceLocation> overridesCorruptedToVanilla)
    {
        final Map<ResourceLocation, ResourceLocation> merged = new HashMap<>(this.corruptedToVanilla);
        merged.putAll(overridesCorruptedToVanilla);
        setMappings(merged);
    }

    /** For debugging/logging. */
    public Map<ResourceLocation, ResourceLocation> snapshotCorruptedToVanilla()
    {
        return corruptedToVanilla;
    }

    /** Reload listener (datapack JSON) */
    public static final class ReloadListener extends SimpleJsonResourceReloadListener
    {
        private final CureMappingsManager target;

        public ReloadListener(final CureMappingsManager target)
        {
            super(new GsonBuilder().create(), FOLDER);
            this.target = target;
        }

        /**
         * Reloads the datapack JSON and applies the new mappings to the target.
         * <p>
         * For each entry in the JSON, if the value is a JSON object, it is parsed
         * into a map of corrupted to vanilla mappings. If the JSON object contains
         * a "replace" field with a value of true, the loaded map is cleared before
         * adding the new mappings.
         * <p>
         * The loaded map is then validated by removing any mappings where either the
         * corrupted or vanilla ID does not exist.
         * <p>
         * Finally, the loaded map is set as the target's new mappings.
         */
        @Override
        protected void apply(final @Nonnull Map<ResourceLocation, JsonElement> jsons,
            final @Nonnull ResourceManager resourceManager,
            final @Nonnull ProfilerFiller profiler)
        {
            final Map<ResourceLocation, ResourceLocation> loaded = new HashMap<>();

            for (Entry<ResourceLocation, JsonElement> entry : jsons.entrySet())
            {
                final ResourceLocation fileId = entry.getKey();
                final JsonElement rootEl = entry.getValue();

                if (!rootEl.isJsonObject()) continue;

                final JsonObject root = rootEl.getAsJsonObject();
                final boolean replace = root.has("replace") && root.get("replace").getAsBoolean();

                final Map<ResourceLocation, ResourceLocation> fileMappings = parseFile(fileId, root);

                if (replace)
                {
                    loaded.clear();
                }
                loaded.putAll(fileMappings);
            }

            // Validate IDs exist (optional but recommended)
            loaded.entrySet()
                .removeIf(e -> BuiltInRegistries.ENTITY_TYPE.getOptional(e.getKey()).isEmpty() ||
                    BuiltInRegistries.ENTITY_TYPE.getOptional(e.getValue()).isEmpty());

            target.setMappings(loaded);
        }

        private static Map<ResourceLocation, ResourceLocation> parseFile(final ResourceLocation fileId, final JsonObject root)
        {
            final Map<ResourceLocation, ResourceLocation> out = new HashMap<>();

            if (!root.has("mappings") || !root.get("mappings").isJsonArray()) return out;

            final JsonArray arr = root.getAsJsonArray("mappings");
            for (JsonElement el : arr)
            {
                if (!el.isJsonObject()) continue;
                final JsonObject o = el.getAsJsonObject();

                final String corrupted = getString(o, "corrupted");
                final String vanilla = getString(o, "vanilla");
                if (corrupted == null || vanilla == null) continue;

                try
                {
                    out.put(ResourceLocation.parse(corrupted), ResourceLocation.parse(vanilla));
                }
                catch (Exception ignored)
                {
                    // Consider logging with fileId for easier debugging
                }
            }
            return out;
        }


        /**
         * Returns the value of the key in the given JsonObject as a string
         * if the key exists and the value is a JsonPrimitive. Otherwise
         * returns null.
         *
         * @param o the JsonObject to get the value from
         * @param key the key to get the value for
         * @return the value as a string, or null if not present
         */
        private static String getString(final JsonObject o, final String key)
        {
            return (o.has(key) && o.get(key).isJsonPrimitive()) ? o.get(key).getAsString() : null;
        }
    }
}
