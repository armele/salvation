package com.deathfrog.salvationmod.core.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
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
import net.minecraft.world.entity.MobCategory;
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
    private static final int CORRUPTED_FOG_COLOR = 5914467;
    private static final int CORRUPTED_GRASS_COLOR = 4857666;
    private static final int CORRUPTED_GRASS_COLOR_COLD = 4600918;
    private static final int CORRUPTED_GRASS_COLOR_DRY = 5713983;
    private static final int CORRUPTED_GRASS_COLOR_FOREST = 5383240;
    private static final int CORRUPTED_GRASS_COLOR_LUSH = 4139581;
    private static final int CORRUPTED_GRASS_COLOR_NETHER = 5318463;
    private static final int CORRUPTED_GRASS_COLOR_OCEAN = 4010312;
    private static final int CORRUPTED_GRASS_COLOR_WET = 3877437;
    private static final int CORRUPTED_FOLIAGE_COLOR = 5914467;
    private static final int CORRUPTED_SKY_TINT = 8101042;

    private static final String FEATURE_SCARRED_STONE = "salvation:scarred_stone_ore_placed";
    private static final String FEATURE_BLIGHTWOOD_SPARSE = "salvation:blightwood_grove_sparse";
    private static final String FEATURE_BLIGHTWOOD_NORMAL = "salvation:blightwood_grove_normal";
    private static final String FEATURE_BLIGHTWOOD_DENSE = "salvation:blightwood_grove_dense";

    private BiomeMapGenerationService()
    {
    }

    /**
     * Generates missing biome mappings, writing the results to the specified pack path.
     * This method is idempotent and can be safely called multiple times.
     *
     * @param level The server level to generate mappings for.
     * @return A GenerationResult containing the pack path and the number of mappings generated.
     * @throws IOException If an IO error occurs while writing the pack metadata.
     */
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

            writeJson(packRoot.resolve(resourcePathForBiome(purifiedId)), normalizeBiomeSpawns(sourceBiomeJson.deepCopy(), level));
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

    /**
     * Determines whether or not a biome should have its corrupted and purified variants generated.
     * <p>
     * A biome should not have its variants generated if it is already corrupted or purified, or if it has a
     * corrupted or purified variant already mapped.
     * <p>
     * Additionally, biomes with IDs in the "salvation" namespace and paths starting with "corrupted",
     * "purified", or "generated/" are skipped.
     * @param mappings the biome mappings manager
     * @param biomeId the biome ID
     * @return whether or not the biome should have its variants generated
     */
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

    /**
     * Applies corruption-side biome mutation to the biome JSON.
     * <p>This function makes the following changes to the biome JSON:
     * <ul>
     *     <li>Changes the water color to a corrupted color.</li>
     *     <li>Changes the water fog color to a corrupted color.</li>
     *     <li>Changes the fog color to a corrupted color.</li>
     *     <li>Changes the grass color to a corrupted color based on the source biome.</li>
     *     <li>Changes the foliage color to a corrupted color.</li>
     *     <li>Changes the sky color to a tinted version of the corrupted sky color.</li>
     *     <li>
     *         Adds a mood sound to the biome if it does not already have one.
     *         The mood sound added is a cave sound.
     *     </li>
     *     <li>
     *         Adds a feature to the biome if it does not already have one.
     *         The feature added is a scarred stone feature if the biome already has an ore feature.
     *     </li>
     *     <li>
     *         Adds a feature to the biome if it does not already have one.
     *         The feature added is a blighted wood feature if the biome is likely to be an overworld biome.
     *     </li>
     *     <li>
     *         Replaces vanilla mob spawn types in the biome JSON with their corrupted equivalents.
     *     </li>
     *     <li>
     *         Normalizes the spawner categories in the biome JSON.
     *     </li>
     *     <li>
     *         Adds voraxian mob spawns to the biome JSON.
     *     </li>
     * </ul>
     * @param biomeJson The biome JSON to corrupt.
     * @param sourceBiomeId The resource location of the source biome.
     * @param level The server level.
     * @return The corrupted biome JSON.
     */
    private static JsonObject corruptifyBiome(final JsonObject biomeJson, final ResourceLocation sourceBiomeId, final ServerLevel level)
    {
        final JsonObject effects = getOrCreateObject(biomeJson, "effects");
        effects.addProperty("water_color", CORRUPTED_WATER_COLOR);
        effects.addProperty("water_fog_color", CORRUPTED_WATER_FOG_COLOR);
        effects.addProperty("fog_color", CORRUPTED_FOG_COLOR);
        effects.addProperty("grass_color", corruptedGrassColorFor(sourceBiomeId));
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
        normalizeSpawnerCategories(spawners, level);
        addVoraxianSpawns(spawners, level);

        return biomeJson;
    }

    private static JsonObject normalizeBiomeSpawns(final JsonObject biomeJson, final ServerLevel level)
    {
        normalizeSpawnerCategories(getOrCreateObject(biomeJson, "spawners"), level);
        return biomeJson;
    }

    /**
     * Returns the corrupted grass color for the given biome id.
     * The color is determined based on the path of the biome id.
     * For example, if the path contains "ocean", "river", or "beach", the color returned will be
     * {@link #CORRUPTED_GRASS_COLOR_OCEAN}.
     * If the path contains "swamp" or "mangrove", the color returned will be
     * {@link #CORRUPTED_GRASS_COLOR_WET}.
     * If the path contains "nether", "crimson", "warped", "soul_sand", "basalt", or "end", the color returned will be
     * {@link #CORRUPTED_GRASS_COLOR_NETHER}.
     * If the path contains "frozen", "snowy", "ice", "peaks", or "slopes", the color returned will be
     * {@link #CORRUPTED_GRASS_COLOR_COLD}.
     * If the path contains "jungle", "lush", or "mushroom", the color returned will be
     * {@link #CORRUPTED_GRASS_COLOR_LUSH}.
     * If the path contains "forest", "taiga", "grove", or "cherry", the color returned will be
     * {@link #CORRUPTED_GRASS_COLOR_FOREST}.
     * If the path contains "desert", "badlands", or "savanna", the color returned will be
     * {@link #CORRUPTED_GRASS_COLOR_DRY}.
     * Otherwise, the color returned will be {@link #CORRUPTED_GRASS_COLOR}.
     *
     * @param sourceBiomeId The biome id to get the corrupted grass color for.
     * @return The corrupted grass color for the given biome id.
     */
    private static int corruptedGrassColorFor(final ResourceLocation sourceBiomeId)
    {
        final String path = sourceBiomeId.getPath();

        if (path.contains("ocean") || path.contains("river") || path.contains("beach"))
        {
            return CORRUPTED_GRASS_COLOR_OCEAN;
        }

        if (path.contains("swamp") || path.contains("mangrove"))
        {
            return CORRUPTED_GRASS_COLOR_WET;
        }

        if (path.contains("nether") || path.contains("crimson") || path.contains("warped") || path.contains("soul_sand") || path.contains("basalt")
            || path.contains("end"))
        {
            return CORRUPTED_GRASS_COLOR_NETHER;
        }

        if (path.contains("frozen") || path.contains("snowy") || path.contains("ice") || path.contains("peaks") || path.contains("slopes"))
        {
            return CORRUPTED_GRASS_COLOR_COLD;
        }

        if (path.contains("jungle") || path.contains("lush") || path.contains("mushroom"))
        {
            return CORRUPTED_GRASS_COLOR_LUSH;
        }

        if (path.contains("forest") || path.contains("taiga") || path.contains("grove") || path.contains("cherry"))
        {
            return CORRUPTED_GRASS_COLOR_FOREST;
        }

        if (path.contains("desert") || path.contains("badlands") || path.contains("savanna"))
        {
            return CORRUPTED_GRASS_COLOR_DRY;
        }

        return CORRUPTED_GRASS_COLOR;
    }

    /**
     * Replaces vanilla mob spawn types in a biome's JSON with their corrupted equivalents.
     * <p>If a vanilla mob spawn type does not have a corrupted equivalent, it is left unchanged.
     * @param spawners The biome's JSON object containing the mob spawns.
     */
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
     * Normalizes the given spawner configuration by separating the spawns into different arrays, one for each category of entity.
     * This is done by taking the existing spawns and grouping them by their category name.
     * After normalization, the original keys are removed and the new normalized categories are added under their respective names.
     * This is useful for easily accessing and modifying specific categories of entities.
     *
     * @param spawners The spawner configuration to be normalized.
     * @param level The level whose registry is used to determine the entity categories.
     */
    private static void normalizeSpawnerCategories(final JsonObject spawners, final ServerLevel level)
    {
        final Registry<EntityType<?>> entityRegistry = level.registryAccess().registryOrThrow(NullnessBridge.assumeNonnull(Registries.ENTITY_TYPE));
        final JsonObject normalizedSpawners = new JsonObject();

        for (Entry<String, JsonElement> entry : spawners.entrySet())
        {
            if (!entry.getValue().isJsonArray())
            {
                normalizedSpawners.add(entry.getKey(), entry.getValue().deepCopy());
                continue;
            }

            final JsonArray sourceSpawns = entry.getValue().getAsJsonArray();
            if (sourceSpawns.isEmpty())
            {
                getOrCreateArray(normalizedSpawners, entry.getKey());
                continue;
            }

            for (JsonElement element : sourceSpawns)
            {
                final String categoryName = entityCategoryName(element, entry.getKey(), entityRegistry);
                getOrCreateArray(normalizedSpawners, categoryName).add(element.deepCopy());
            }
        }

        final List<String> originalKeys = spawners.entrySet().stream()
            .map(Entry::getKey)
            .toList();
        for (String key : originalKeys)
        {
            spawners.remove(key);
        }

        for (Entry<String, JsonElement> entry : normalizedSpawners.entrySet())
        {
            spawners.add(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Attempts to determine the category name for the given spawn information.
     * If the spawn does not contain type information, the fallback category name is returned.
     * If the type information is invalid, the fallback category name is returned.
     * If the type is not found in the entity registry, the fallback category name is returned.
     * If the type is found, the category name is returned.
     *
     * @param spawn the spawn information to determine the category for
     * @param fallbackCategoryName the category name to return if the spawn does not contain valid type information
     * @param entityRegistry the entity registry to use for looking up the type information
     * @return the category name for the given spawn information, or the fallback category name if the information is invalid or not found.
     */
    private static String entityCategoryName(final JsonElement spawn,
        final String fallbackCategoryName,
        final Registry<EntityType<?>> entityRegistry)
    {
        if (!spawn.isJsonObject())
        {
            return fallbackCategoryName;
        }

        final JsonObject spawnObject = spawn.getAsJsonObject();
        if (!spawnObject.has("type"))
        {
            return fallbackCategoryName;
        }

        final ResourceLocation entityId;
        try
        {
            entityId = ResourceLocation.parse(spawnObject.get("type").getAsString()+"");
        }
        catch (IllegalArgumentException ex)
        {
            return fallbackCategoryName;
        }

        return entityRegistry.getOptional(entityId)
            .map(EntityType::getCategory)
            .filter(category -> category != MobCategory.MISC)
            .map(MobCategory::getSerializedName)
            .orElse(fallbackCategoryName);
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

    /**
     * Adds a spawn entry for the given entity if it is not already present in the given list of spawns.
     * The new spawn entry is added with the given weight, minimum and maximum counts.
     *
     * @param Spawns The list of spawns to add the new entry to.
     * @param entityId The ID of the entity to add a spawn entry for.
     * @param weight The weight of the new spawn entry.
     * @param minCount The minimum count of the new spawn entry.
     * @param maxCount The maximum count of the new spawn entry.
     */
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

    /**
     * Calculates the spawn weight for the given voraxian entity.
     *
     * The base weight is determined by the entity ID path:
     * - "stinger" entities have a base weight of 30
     * - "maw" entities have a base weight of 20
     * - "observer" entities have a base weight of 10
     * - All other entities have a base weight of 16
     *
     * The final weight is calculated by adding a random value between -3 and 3 (inclusive) to the base weight.
     * The result is then clamped to a minimum of 1 to ensure that the entity will always have a non-zero spawn weight.
     *
     * @param entityId The ID of the voraxian entity to calculate the spawn weight for.
     * @param random A random source to generate the random offset.
     * @return The calculated spawn weight for the given voraxian entity.
     */
    private static int voraxianWeightFor(final ResourceLocation entityId, final RandomSource random)
    {
        final String path = entityId.getPath();
        final int baseWeight = path.contains("stinger") ? 30 : path.contains("maw") ? 20 : path.contains("observer") ? 10 : 16;
        return Math.max(1, baseWeight + random.nextIntBetweenInclusive(-3, 3));
    }

    /**
     * Calculates the minimum spawn count for the given voraxian entity.
     *
     * The minimum count is determined by the entity ID path:
     * - "observer" entities have a minimum count of 1
     * - All other entities have a minimum count of 4
     */
    private static int voraxianMinCountFor(final ResourceLocation entityId)
    {
        return entityId.getPath().contains("observer") ? 1 : 4;
    }

    /**
     * Calculates the maximum spawn count for the given voraxian entity.
     *
     * The maximum count is determined by the entity ID path:
     * - "observer" entities have a maximum count of 1
     * - All other entities have a maximum count of 4
     */
    private static int voraxianMaxCountFor(final ResourceLocation entityId)
    {
        return entityId.getPath().contains("observer") ? 1 : 4;
    }

    /**
     * Calculates the Blightwood feature for the given biome based on its ID path.
     * 
     * The feature is determined as follows:
     * - If the biome ID path contains "forest", "jungle", "grove", "swamp", "mangrove", "lush", "taiga", or "cherry",
     *   the feature is {@link #FEATURE_BLIGHTWOOD_DENSE}.
     * - If the biome ID path contains "desert", "badlands", "beach", "ocean", "river", "savanna", "peaks", "stony",
     *   "end", "void", "basalt", or "soul_sand", the feature is {@link #FEATURE_BLIGHTWOOD_SPARSE}.
     * - Otherwise, the feature is {@link #FEATURE_BLIGHTWOOD_NORMAL}.
     * 
     * @param biomeId The biome ID to calculate the feature for.
     * @return The calculated Blightwood feature for the given biome.
     */
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

    /**
     * Determines whether the given biome is likely to be an Overworld biome.
     * 
     * The biome is considered an Overworld biome if its ID path does not contain "nether", "crimson", "warped", "basalt", or "soul_sand",
     * and does not contain "end" or "void", and either does not have a "has_precipitation" property in its JSON definition,
     * or has a "has_precipitation" property set to true, or does not contain "ocean" in its ID path.
     * 
     * @param biomeId The biome ID to check.
     * @param biomeJson The biome JSON definition to check.
     * @return Whether the biome is likely to be an Overworld biome.
     */
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

    /**
     * Blends the given base color with the given tint color according to the given tint weight.
     * 
     * The tint weight is clamped to the range [0.0, 1.0] before being used.
     * 
     * @param baseColor The base color to blend.
     * @param tintColor The tint color to blend.
     * @param tintWeight The tint weight to use.
     * @return The blended color.
     */
    private static int blendColor(final int baseColor, final int tintColor, final float tintWeight)
    {
        final float clampedWeight = Math.max(0.0F, Math.min(1.0F, tintWeight));
        final int r = blendChannel((baseColor >> 16) & 0xFF, (tintColor >> 16) & 0xFF, clampedWeight);
        final int g = blendChannel((baseColor >> 8) & 0xFF, (tintColor >> 8) & 0xFF, clampedWeight);
        final int b = blendChannel(baseColor & 0xFF, tintColor & 0xFF, clampedWeight);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Blends the given base color channel with the given tint color channel according to the given tint weight.
     *
     * The tint weight is used to interpolate between the base color channel and the tint color channel.
     * A weight of 0.0 will result in the base color channel being returned, while a weight of 1.0 will result in the tint color channel being returned.
     * All other weights will result in a color channel that is a mix of the base color channel and the tint color channel.
     *
     * @param base The base color channel to blend.
     * @param tint The tint color channel to blend.
     * @param weight The tint weight to use.
     * @return The blended color channel.
     */
    private static int blendChannel(final int base, final int tint, final float weight)
    {
        return Math.round(base + (tint - base) * weight);
    }

    /**
     * Retrieves the integer value associated with the given key from the given object.
     *
     * If the given object does not contain the given key, the fallback value is returned.
     *
     * @param object The object to retrieve the value from.
     * @param key The key to retrieve the value for.
     * @param fallback The fallback value to return if the key is not present in the object.
     * @return The retrieved value, or the fallback value if the key is not present.
     */
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

    /**
     * Converts a biome ID to a human-readable name.
     * 
     * The name is constructed by splitting the biome ID path on underscores, and then capitalizing the first letter of each token.
     * Tokens that are blank after splitting are ignored.
     * If the resulting name is empty, the original biome ID string is returned.
     * 
     * @param biomeId The biome ID to humanize.
     * @return The human-readable name for the biome ID.
     */
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
