package com.deathfrog.salvationmod.core.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.SalvationMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;

import org.slf4j.Logger;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
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
        final JsonObject generatedLang = new JsonObject();

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
            writeJson(packRoot.resolve(resourcePathForBiome(corruptedId)), corruptifyBiome(sourceBiomeJson.deepCopy(), biomeId, level));

            mappingEntries.add(mappingEntry(biomeId, corruptedId, purifiedId));
            corruptedTagValues.add(corruptedId.toString());
            purifiedTagValues.add(purifiedId.toString());
            generatedLang.addProperty(corruptedId.toLanguageKey("biome"), "Corrupted " + humanizeBiomeName(biomeId));
            generatedLang.addProperty(purifiedId.toLanguageKey("biome"), "Purified " + humanizeBiomeName(biomeId));
            generatedCount++;
        }

        writePackMetadata(packRoot);
        writeJson(packRoot.resolve("data/salvation/salvation_biome_mappings/" + GENERATED_MAPPING_FILE), mappingsFile(mappingEntries));
        writeJson(packRoot.resolve("data/salvation/tags/worldgen/biome/corrupted_biomes.json"), tagFile(corruptedTagValues));
        writeJson(packRoot.resolve("data/salvation/tags/worldgen/biome/purified_biomes.json"), tagFile(purifiedTagValues));
        writeJson(packRoot.resolve("assets/salvation/lang/en_us.json"), generatedLang);

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

    private static JsonObject corruptifyBiome(final JsonObject biomeJson, final ResourceLocation sourceBiomeId, final ServerLevel level)
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
        addVoraxianSpawns(spawners, level);

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

            final Optional<ResourceLocation> replacement = SalvationMod.CURE_MAPPINGS.getCorruptedForVanilla(ResourceLocation.parse(type));
            if (replacement.isPresent())
            {
                spawn.addProperty("type", replacement.get().toString());
            }
        }
    }

    /**
     * Adds spawns for Voraxian Minions to the given biome, if they are not already present.
     * The weight, minimum and maximum counts for each Voraxian Minion are determined by the
     * methods voraxianWeightFor, voraxianMinCountFor and voraxianMaxCountFor respectively.
     *
     * @param spawners The biome's spawner configuration.
     * @param level The level whose registry is used to determine the Voraxian Minion entities.
     */
    @SuppressWarnings("null")
    private static void addVoraxianSpawns(final JsonObject spawners, final ServerLevel level)
    {
        final JsonArray monsters = getOrCreateArray(spawners, "monster");
        final Registry<EntityType<?>> entityRegistry = level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
        final RandomSource random = level.random;

        for (Holder<EntityType<?>> holder : entityRegistry.getTagOrEmpty(ModTags.Entities.VORAXIAN_MINION))
        {
            final ResourceLocation entityId = entityRegistry.getKey(holder.value());
            if (entityId == null)
            {
                continue;
            }

            addSpawnIfMissing(monsters, entityId.toString(), voraxianWeightFor(entityId, random), voraxianMinCountFor(entityId), voraxianMaxCountFor(entityId));
        }
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

    private static int voraxianWeightFor(final ResourceLocation entityId, final RandomSource random)
    {
        final String path = entityId.getPath();
        final int baseWeight = path.contains("stinger") ? 30 : path.contains("maw") ? 20 : path.contains("observer") ? 10 : 16;
        return Math.max(1, baseWeight + random.nextIntBetweenInclusive(-3, 3));
    }

    private static int voraxianMinCountFor(final ResourceLocation entityId)
    {
        return entityId.getPath().contains("observer") ? 1 : 4;
    }

    private static int voraxianMaxCountFor(final ResourceLocation entityId)
    {
        return entityId.getPath().contains("observer") ? 1 : 4;
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

    private static String humanizeBiomeName(final ResourceLocation biomeId)
    {
        final String[] tokens = biomeId.getPath().replace('/', '_').split("_");
        final StringBuilder builder = new StringBuilder();

        for (String token : tokens)
        {
            if (token.isBlank())
            {
                continue;
            }

            if (!builder.isEmpty())
            {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1)
            {
                builder.append(token.substring(1));
            }
        }

        return builder.isEmpty() ? biomeId.toString() : builder.toString();
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
