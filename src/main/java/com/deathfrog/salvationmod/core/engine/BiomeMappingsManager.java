package com.deathfrog.salvationmod.core.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class BiomeMappingsManager
{
    public static final String FOLDER = "salvation_biome_mappings";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BiomeMappingsManager INSTANCE = new BiomeMappingsManager();

    private volatile Map<ResourceLocation, ResourceLocation> vanillaToCorrupted = Map.of();
    private volatile Map<ResourceLocation, ResourceLocation> corruptedToVanilla = Map.of();
    private volatile Map<ResourceLocation, ResourceLocation> vanillaToPurified = Map.of();
    private volatile Map<ResourceLocation, ResourceLocation> purifiedToVanilla = Map.of();

    public static BiomeMappingsManager get()
    {
        return INSTANCE;
    }

    public Optional<ResourceLocation> getCorruptedForVanilla(final ResourceLocation vanillaId)
    {
        return Optional.ofNullable(vanillaToCorrupted.get(vanillaId));
    }

    public Optional<ResourceLocation> getVanillaForCorrupted(final ResourceLocation corruptedId)
    {
        return Optional.ofNullable(corruptedToVanilla.get(corruptedId));
    }

    public Optional<ResourceLocation> getPurifiedForVanilla(final ResourceLocation vanillaId)
    {
        return Optional.ofNullable(vanillaToPurified.get(vanillaId));
    }

    public Optional<ResourceLocation> getVanillaForPurified(final ResourceLocation purifiedId)
    {
        return Optional.ofNullable(purifiedToVanilla.get(purifiedId));
    }

    public boolean isCorruptedBiome(final ResourceLocation biomeId)
    {
        return biomeId != null && corruptedToVanilla.containsKey(biomeId);
    }

    public boolean isPurifiedBiome(final ResourceLocation biomeId)
    {
        return biomeId != null && purifiedToVanilla.containsKey(biomeId);
    }

    private void setMappings(final Map<ResourceLocation, BiomeMappingEntry> entries)
    {
        final Map<ResourceLocation, ResourceLocation> nextVanillaToCorrupted = new HashMap<>();
        final Map<ResourceLocation, ResourceLocation> nextCorruptedToVanilla = new HashMap<>();
        final Map<ResourceLocation, ResourceLocation> nextVanillaToPurified = new HashMap<>();
        final Map<ResourceLocation, ResourceLocation> nextPurifiedToVanilla = new HashMap<>();

        for (BiomeMappingEntry entry : entries.values())
        {
            nextVanillaToCorrupted.put(entry.vanilla(), entry.corrupted());
            nextCorruptedToVanilla.put(entry.corrupted(), entry.vanilla());

            if (entry.purified() != null)
            {
                nextVanillaToPurified.put(entry.vanilla(), entry.purified());
                nextPurifiedToVanilla.put(entry.purified(), entry.vanilla());
            }
        }

        vanillaToCorrupted = Map.copyOf(nextVanillaToCorrupted);
        corruptedToVanilla = Map.copyOf(nextCorruptedToVanilla);
        vanillaToPurified = Map.copyOf(nextVanillaToPurified);
        purifiedToVanilla = Map.copyOf(nextPurifiedToVanilla);
    }

    private record BiomeMappingEntry(ResourceLocation vanilla, ResourceLocation corrupted, ResourceLocation purified)
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
            final Map<ResourceLocation, BiomeMappingEntry> loaded = new HashMap<>();

            for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet())
            {
                final ResourceLocation fileId = entry.getKey();
                final JsonElement rootElement = entry.getValue();

                if (!rootElement.isJsonObject())
                {
                    LOGGER.warn("Ignoring non-object biome mappings file {}", fileId);
                    continue;
                }

                final JsonObject root = rootElement.getAsJsonObject();
                final boolean replace = root.has("replace") && root.get("replace").getAsBoolean();

                if (replace)
                {
                    loaded.clear();
                }

                loaded.putAll(parseFile(fileId, root));
            }

            BiomeMappingsManager.get().setMappings(loaded);
            LOGGER.info("Loaded {} biome corruption mapping(s) from {} file(s)", loaded.size(), jsons.size());
        }

        private static Map<ResourceLocation, BiomeMappingEntry> parseFile(final ResourceLocation fileId, final JsonObject root)
        {
            final Map<ResourceLocation, BiomeMappingEntry> out = new HashMap<>();

            if (!root.has("mappings") || !root.get("mappings").isJsonArray())
            {
                return out;
            }

            final JsonArray mappings = root.getAsJsonArray("mappings");
            for (JsonElement element : mappings)
            {
                if (!element.isJsonObject())
                {
                    continue;
                }

                final JsonObject mappingObject = element.getAsJsonObject();
                final String vanilla = getString(mappingObject, "vanilla");
                final String corrupted = getString(mappingObject, "corrupted");
                final String purified = getString(mappingObject, "purified");

                if (vanilla == null || corrupted == null)
                {
                    continue;
                }

                try
                {
                    final ResourceLocation vanillaId = ResourceLocation.parse(vanilla);
                    final ResourceLocation corruptedId = ResourceLocation.parse(corrupted);
                    final ResourceLocation purifiedId = purified == null ? null : ResourceLocation.parse(purified);
                    out.put(vanillaId, new BiomeMappingEntry(vanillaId, corruptedId, purifiedId));
                }
                catch (Exception ex)
                {
                    LOGGER.warn("Ignoring invalid biome mapping in {}: {}", fileId, ex.getMessage());
                }
            }

            return out;
        }

        private static String getString(final JsonObject object, final String key)
        {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
        }
    }
}
