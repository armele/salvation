package com.deathfrog.salvationmod.core.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;

import org.slf4j.Logger;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.LevelResource;

public final class BiomeMapGenerationService
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String GENERATED_PACK_NAME = "salvation_generated_biomes";
    private static final String GENERATED_MAPPING_FILE = "generated.json";
    private static final String GENERATED_DESCRIPTION = "Generated Salvation biome mappings";

    private static final int CORRUPTED_WATER_COLOR = 4416093;
    private static final int CORRUPTED_WATER_FOG_COLOR = 1515552;
    private static final int CORRUPTED_FOG_COLOR = 9084063;
    private static final int CORRUPTED_GRASS_COLOR = 8294202;
    private static final int CORRUPTED_FOLIAGE_COLOR = 5597999;
    private static final int CORRUPTED_SKY_TINT = 8101042;

    private static final String FEATURE_SCARRED_STONE = "salvation:scarred_stone_ore_placed";
    private static final String FEATURE_BLIGHTWOOD_SPARSE = "salvation:blightwood_grove_sparse";
    private static final String FEATURE_BLIGHTWOOD_NORMAL = "salvation:blightwood_grove_normal";
    private static final String FEATURE_BLIGHTWOOD_DENSE = "salvation:blightwood_grove_dense";

    private static final Map<String, String> CORRUPTED_CREATURE_REPLACEMENTS = Map.of(
        "minecraft:cow", "salvation:corrupted_cow",
        "minecraft:pig", "salvation:corrupted_pig",
        "minecraft:chicken", "salvation:corrupted_chicken",
        "minecraft:sheep", "salvation:corrupted_sheep",
        "minecraft:cat", "salvation:corrupted_cat",
        "minecraft:fox", "salvation:corrupted_fox",
        "minecraft:polar_bear", "salvation:corrupted_polarbear"
    );

    private BiomeMapGenerationService()
    {
    }

    @SuppressWarnings("null")
    public static GenerationResult generateMissingBiomeMappings(final ServerLevel level) throws IOException
    {
        final BiomeMappingsManager mappings = BiomeMappingsManager.get();
        final HolderLookup.RegistryLookup<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        final RegistryOps<JsonElement> jsonOps = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        final Path packRoot = level.getServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve(GENERATED_PACK_NAME);

        final List<ResourceLocation> biomeIds = biomeRegistry.listElements()
            .map(reference -> reference.key().location())
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .toList();

        final JsonArray mappingEntries = new JsonArray();
        final JsonArray corruptedTagValues = new JsonArray();
        final JsonArray purifiedTagValues = new JsonArray();

        int generatedCount = 0;

        for (ResourceLocation biomeId : biomeIds)
        {
            if (!shouldGenerateForBiome(mappings, biomeId))
            {
                continue;
            }

            final Optional<Biome> biome = biomeRegistry.get(ResourceLocationToKey.of(biomeId)).map(reference -> reference.value());
            if (biome.isEmpty())
            {
                continue;
            }

            final JsonObject sourceBiomeJson = encodeBiome(jsonOps, biome.get(), biomeId);
            if (sourceBiomeJson == null)
            {
                continue;
            }

            final ResourceLocation purifiedId = generatedPurifiedId(biomeId);
            final ResourceLocation corruptedId = generatedCorruptedId(biomeId);

            writeJson(packRoot.resolve(resourcePathForBiome(purifiedId)), sourceBiomeJson.deepCopy());
            writeJson(packRoot.resolve(resourcePathForBiome(corruptedId)), corruptifyBiome(sourceBiomeJson.deepCopy(), biomeId));

            mappingEntries.add(mappingEntry(biomeId, corruptedId, purifiedId));
            corruptedTagValues.add(corruptedId.toString());
            purifiedTagValues.add(purifiedId.toString());
            generatedCount++;
        }

        writePackMetadata(packRoot);
        writeJson(packRoot.resolve("data/salvation/salvation_biome_mappings/" + GENERATED_MAPPING_FILE), mappingsFile(mappingEntries));
        writeJson(packRoot.resolve("data/salvation/tags/worldgen/biome/corrupted_biomes.json"), tagFile(corruptedTagValues));
        writeJson(packRoot.resolve("data/salvation/tags/worldgen/biome/purified_biomes.json"), tagFile(purifiedTagValues));

        return new GenerationResult(packRoot, generatedCount);
    }

    private static boolean shouldGenerateForBiome(final BiomeMappingsManager mappings, final ResourceLocation biomeId)
    {
        if (biomeId == null)
        {
            return false;
        }

        if (mappings.isCorruptedBiome(biomeId) || mappings.isPurifiedBiome(biomeId))
        {
            return false;
        }

        if (mappings.getCorruptedForVanilla(biomeId).isPresent() || mappings.getPurifiedForVanilla(biomeId).isPresent())
        {
            return false;
        }

        return !(biomeId.getNamespace().equals("salvation") && (biomeId.getPath().startsWith("corrupted")
            || biomeId.getPath().startsWith("purified")
            || biomeId.getPath().startsWith("generated/")));
    }

    private static JsonObject encodeBiome(final RegistryOps<JsonElement> jsonOps, final Biome biome, final ResourceLocation biomeId)
    {
        return Biome.DIRECT_CODEC.encodeStart(jsonOps, biome)
            .resultOrPartial(error -> LOGGER.error("Failed to encode biome {}: {}", biomeId, error))
            .filter(JsonElement::isJsonObject)
            .map(JsonElement::getAsJsonObject)
            .orElse(null);
    }

    private static JsonObject corruptifyBiome(final JsonObject biomeJson, final ResourceLocation sourceBiomeId)
    {
        final JsonObject effects = getOrCreateObject(biomeJson, "effects");
        effects.addProperty("water_color", CORRUPTED_WATER_COLOR);
        effects.addProperty("water_fog_color", CORRUPTED_WATER_FOG_COLOR);
        effects.addProperty("fog_color", CORRUPTED_FOG_COLOR);
        effects.addProperty("grass_color", CORRUPTED_GRASS_COLOR);
        effects.addProperty("foliage_color", CORRUPTED_FOLIAGE_COLOR);
        effects.addProperty("sky_color", blendColor(getInt(effects, "sky_color", CORRUPTED_SKY_TINT), CORRUPTED_SKY_TINT, 0.40F));

        if (!effects.has("mood_sound"))
        {
            final JsonObject moodSound = new JsonObject();
            moodSound.addProperty("block_search_extent", 8);
            moodSound.addProperty("offset", 2.0D);
            moodSound.addProperty("sound", "minecraft:ambient.cave");
            moodSound.addProperty("tick_delay", 6000);
            effects.add("mood_sound", moodSound);
        }

        final JsonArray features = getOrCreateArray(biomeJson, "features");
        if (features.size() > 0)
        {
            final int oreIndex = Math.min(6, features.size() - 1);
            if (containsFeature(features, oreIndex, "minecraft:ore_dirt"))
            {
                addFeatureIfMissing(features, oreIndex, FEATURE_SCARRED_STONE);
            }

            if (isLikelyOverworldBiome(sourceBiomeId, biomeJson) && features.size() >= 2)
            {
                addFeatureIfMissing(features, features.size() - 2, blightwoodFeatureFor(sourceBiomeId));
            }
        }

        final JsonObject spawners = getOrCreateObject(biomeJson, "spawners");
        replaceCreatureSpawns(spawners);
        addVoraxianSpawns(spawners);

        return biomeJson;
    }

    private static void replaceCreatureSpawns(final JsonObject spawners)
    {
        if (!spawners.has("creature") || !spawners.get("creature").isJsonArray())
        {
            return;
        }

        for (JsonElement element : spawners.getAsJsonArray("creature"))
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            final JsonObject spawn = element.getAsJsonObject();
            final String type = spawn.has("type") ? spawn.get("type").getAsString() : null;
            if (type == null)
            {
                continue;
            }

            final String replacement = CORRUPTED_CREATURE_REPLACEMENTS.get(type);
            if (replacement != null)
            {
                spawn.addProperty("type", replacement);
            }
        }
    }

    private static void addVoraxianSpawns(final JsonObject spawners)
    {
        final JsonArray monsters = getOrCreateArray(spawners, "monster");
        addSpawnIfMissing(monsters, "salvation:voraxian_stinger", 30, 4, 4);
        addSpawnIfMissing(monsters, "salvation:voraxian_maw", 20, 4, 4);
        addSpawnIfMissing(monsters, "salvation:voraxian_observer", 10, 1, 1);
    }

    private static void addSpawnIfMissing(final JsonArray spawns, final String entityId, final int weight, final int minCount, final int maxCount)
    {
        for (JsonElement element : spawns)
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            final JsonObject spawn = element.getAsJsonObject();
            if (spawn.has("type") && entityId.equals(spawn.get("type").getAsString()))
            {
                return;
            }
        }

        final JsonObject spawn = new JsonObject();
        spawn.addProperty("type", entityId);
        spawn.addProperty("weight", weight);
        spawn.addProperty("minCount", minCount);
        spawn.addProperty("maxCount", maxCount);
        spawns.add(spawn);
    }

    private static String blightwoodFeatureFor(final ResourceLocation biomeId)
    {
        final String path = biomeId.getPath();
        if (path.contains("forest") || path.contains("jungle") || path.contains("grove") || path.contains("swamp")
            || path.contains("mangrove") || path.contains("lush") || path.contains("taiga") || path.contains("cherry"))
        {
            return FEATURE_BLIGHTWOOD_DENSE;
        }

        if (path.contains("desert") || path.contains("badlands") || path.contains("beach") || path.contains("ocean")
            || path.contains("river") || path.contains("savanna") || path.contains("peaks") || path.contains("stony")
            || path.contains("end") || path.contains("void") || path.contains("basalt") || path.contains("soul_sand"))
        {
            return FEATURE_BLIGHTWOOD_SPARSE;
        }

        return FEATURE_BLIGHTWOOD_NORMAL;
    }

    private static boolean isLikelyOverworldBiome(final ResourceLocation biomeId, final JsonObject biomeJson)
    {
        final String path = biomeId.getPath();
        if (path.contains("nether") || path.contains("crimson") || path.contains("warped") || path.contains("basalt") || path.contains("soul_sand"))
        {
            return false;
        }

        if (path.contains("end") || path.contains("void"))
        {
            return false;
        }

        return !biomeJson.has("has_precipitation") || biomeJson.get("has_precipitation").getAsBoolean() || !path.contains("ocean");
    }

    private static int blendColor(final int baseColor, final int tintColor, final float tintWeight)
    {
        final float clampedWeight = Math.max(0.0F, Math.min(1.0F, tintWeight));
        final int r = blendChannel((baseColor >> 16) & 0xFF, (tintColor >> 16) & 0xFF, clampedWeight);
        final int g = blendChannel((baseColor >> 8) & 0xFF, (tintColor >> 8) & 0xFF, clampedWeight);
        final int b = blendChannel(baseColor & 0xFF, tintColor & 0xFF, clampedWeight);
        return (r << 16) | (g << 8) | b;
    }

    private static int blendChannel(final int base, final int tint, final float weight)
    {
        return Math.round(base + (tint - base) * weight);
    }

    private static int getInt(final JsonObject object, final String key, final int fallback)
    {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static JsonObject getOrCreateObject(final JsonObject object, final String key)
    {
        if (object.has(key) && object.get(key).isJsonObject())
        {
            return object.getAsJsonObject(key);
        }

        final JsonObject child = new JsonObject();
        object.add(key, child);
        return child;
    }

    private static JsonArray getOrCreateArray(final JsonObject object, final String key)
    {
        if (object.has(key) && object.get(key).isJsonArray())
        {
            return object.getAsJsonArray(key);
        }

        final JsonArray array = new JsonArray();
        object.add(key, array);
        return array;
    }

    private static boolean containsFeature(final JsonArray features, final int index, final String featureId)
    {
        if (index < 0 || index >= features.size() || !features.get(index).isJsonArray())
        {
            return false;
        }

        for (JsonElement element : features.get(index).getAsJsonArray())
        {
            if (element.isJsonPrimitive() && featureId.equals(element.getAsString()))
            {
                return true;
            }
        }

        return false;
    }

    private static void addFeatureIfMissing(final JsonArray features, final int index, final String featureId)
    {
        if (index < 0 || index >= features.size())
        {
            return;
        }

        if (!features.get(index).isJsonArray())
        {
            return;
        }

        final JsonArray featureStep = features.get(index).getAsJsonArray();
        for (JsonElement element : featureStep)
        {
            if (element.isJsonPrimitive() && featureId.equals(element.getAsString()))
            {
                return;
            }
        }

        featureStep.add(featureId);
    }

    private static JsonObject mappingEntry(final ResourceLocation sourceBiomeId,
        final ResourceLocation corruptedBiomeId,
        final ResourceLocation purifiedBiomeId)
    {
        final JsonObject mapping = new JsonObject();
        mapping.addProperty("vanilla", sourceBiomeId.toString());
        mapping.addProperty("corrupted", corruptedBiomeId.toString());
        mapping.addProperty("purified", purifiedBiomeId.toString());
        return mapping;
    }

    private static JsonObject mappingsFile(final JsonArray mappings)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        json.add("mappings", mappings);
        return json;
    }

    private static JsonObject tagFile(final JsonArray values)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        json.add("values", values);
        return json;
    }

    private static void writePackMetadata(final Path packRoot) throws IOException
    {
        final JsonObject pack = new JsonObject();
        final JsonObject packInfo = new JsonObject();
        packInfo.addProperty("description", GENERATED_DESCRIPTION);
        packInfo.addProperty("pack_format", SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
        pack.add("pack", packInfo);
        writeJson(packRoot.resolve("pack.mcmeta"), pack);
    }

    private static void writeJson(final Path path, final JsonObject object) throws IOException
    {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(object), StandardCharsets.UTF_8);
    }

    private static Path resourcePathForBiome(final ResourceLocation biomeId)
    {
        return Path.of("data", biomeId.getNamespace(), "worldgen", "biome", biomeId.getPath() + ".json");
    }

    private static ResourceLocation generatedPurifiedId(final ResourceLocation sourceBiomeId)
    {
        return ResourceLocation.fromNamespaceAndPath("salvation", "generated/purified/" + sourceBiomeId.getNamespace() + "/" + sourceBiomeId.getPath());
    }

    private static ResourceLocation generatedCorruptedId(final ResourceLocation sourceBiomeId)
    {
        return ResourceLocation.fromNamespaceAndPath("salvation", "generated/corrupted/" + sourceBiomeId.getNamespace() + "/" + sourceBiomeId.getPath());
    }

    public record GenerationResult(Path outputRoot, int generatedBiomeCount)
    {
        public Component toComponent()
        {
            return Component.literal("Generated " + generatedBiomeCount + " biome mapping set(s) in " + outputRoot);
        }
    }

    private static final class ResourceLocationToKey
    {
        private ResourceLocationToKey()
        {
        }

        @SuppressWarnings("null")
        private static net.minecraft.resources.ResourceKey<Biome> of(final ResourceLocation biomeId)
        {
            return net.minecraft.resources.ResourceKey.create(Registries.BIOME, biomeId);
        }
    }
}
