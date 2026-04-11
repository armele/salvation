package com.deathfrog.salvationmod.core.engine;

import com.deathfrog.salvationmod.ModTags;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class FurnaceMachineProfileManager
{
    public static final String FOLDER = "salvation_furnace_profiles";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FurnaceMachineProfileManager INSTANCE = new FurnaceMachineProfileManager();

    private volatile List<FurnaceMachineProfile> profiles = List.of();

    public static FurnaceMachineProfileManager get()
    {
        return INSTANCE;
    }

    public Optional<FurnaceMachineProfile> profileFor(final BlockState state)
    {
        if (state == null)
        {
            return Optional.empty();
        }

        final Block block = state.getBlock();
        for (final FurnaceMachineProfile profile : this.profiles)
        {
            if (profile.matches(block, state))
            {
                return Optional.of(profile);
            }
        }

        return Optional.empty();
    }

    public MachineTuning tuningFor(final BlockState state)
    {
        if (state == null)
        {
            return MachineTuning.DEFAULT;
        }

        if (state.is(ModTags.Blocks.IGNORED_FURNACES))
        {
            return MachineTuning.IGNORED;
        }

        final Optional<FurnaceMachineProfile> profile = profileFor(state);
        if (profile.isPresent())
        {
            return profile.get().tuning();
        }

        if (state.is(ModTags.Blocks.PURIFICATION_FURNACES))
        {
            return MachineTuning.purifying(1.0F, 0.0D, 0.0D);
        }

        if (state.is(ModTags.Blocks.CORRUPTION_FURNACES))
        {
            return MachineTuning.corrupting(1.0F, 0.0D, 0.0D);
        }

        return MachineTuning.DEFAULT;
    }

    public boolean isDesignatedFurnace(final BlockState state)
    {
        return tuningFor(state).designated();
    }

    private void setProfiles(final List<FurnaceMachineProfile> profiles)
    {
        this.profiles = Collections.unmodifiableList(new ArrayList<>(profiles));
    }

    public record MachineTuning(
        boolean designated,
        boolean purifying,
        float corruptionMultiplier,
        double machineCorruptionPer1000,
        double machinePurificationPer1000
    )
    {
        public static final MachineTuning DEFAULT = new MachineTuning(false, false, 1.0F, 0.0D, 0.0D);
        public static final MachineTuning IGNORED = new MachineTuning(false, false, 1.0F, 0.0D, 0.0D);

        public static MachineTuning corrupting(final float corruptionMultiplier,
                                               final double machineCorruptionPer1000,
                                               final double machinePurificationPer1000)
        {
            return new MachineTuning(true, false, corruptionMultiplier, machineCorruptionPer1000, machinePurificationPer1000);
        }

        public static MachineTuning purifying(final float corruptionMultiplier,
                                              final double machineCorruptionPer1000,
                                              final double machinePurificationPer1000)
        {
            return new MachineTuning(true, true, corruptionMultiplier, machineCorruptionPer1000, machinePurificationPer1000);
        }
    }

    public record FurnaceMachineProfile(
        ResourceLocation id,
        AdapterKind adapter,
        List<ResourceLocation> blockIds,
        List<TagKey<Block>> blockTags,
        List<Integer> inputSlots,
        List<Integer> outputSlots,
        List<Integer> fuelSlots,
        Optional<String> activityProperty,
        Optional<ResourceLocation> recipeTypeId,
        int cookTime,
        MachineTuning tuning
    )
    {
        public boolean matches(final Block block, final BlockState state)
        {
            if (block == null || state == null)
            {
                return false;
            }

            final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (blockId != null && this.blockIds.contains(blockId))
            {
                return true;
            }

            for (final TagKey<Block> blockTag : this.blockTags)
            {
                if (blockTag == null)
                {
                    continue;
                }

                if (state.is(blockTag))
                {
                    return true;
                }
            }

            return false;
        }
    }

    public enum AdapterKind
    {
        ABSTRACT_FURNACE,
        ITEM_HANDLER
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
            final List<FurnaceMachineProfile> loaded = new ArrayList<>();

            for (final Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet())
            {
                if (!entry.getValue().isJsonObject())
                {
                    LOGGER.warn("Ignoring non-object furnace machine profile {}", entry.getKey());
                    continue;
                }

                try
                {
                    JsonObject root = entry.getValue().getAsJsonObject();

                    if (root == null)
                    {
                        LOGGER.error("No furnace machine profile found for {}: {}", entry.getKey());
                        continue;
                    }

                    loaded.add(parseProfile(entry.getKey(), root));
                }
                catch (final RuntimeException ex)
                {
                    LOGGER.error("Failed to parse furnace machine profile {}: {}", entry.getKey(), ex.getMessage());
                }
            }

            FurnaceMachineProfileManager.get().setProfiles(loaded);
            LOGGER.info("Loaded {} furnace machine profile(s)", loaded.size());
        }

        /**
         * Parses a furnace machine profile from a JSON object.
         * 
         * @param id the resource location of the profile
         * @param root the JSON object to parse
         * @return the parsed furnace machine profile
         * @throws JsonParseException if the JSON object is invalid
         */
        private static FurnaceMachineProfile parseProfile(final ResourceLocation id, final @Nonnull JsonObject root)
        {
            final AdapterKind adapter = parseAdapter(GsonHelper.getAsString(root, "adapter", "item_handler"));
            final List<ResourceLocation> blockIds = parseResourceLocationList(root, "blocks");
            final List<TagKey<Block>> blockTags = parseBlockTags(root, "block_tags");
            if (blockIds.isEmpty() && blockTags.isEmpty())
            {
                throw new JsonParseException("FurnaceMachineProfile must define at least one block or block_tag target");
            }

            final List<Integer> inputSlots = parseIntList(root, "input_slots");
            final List<Integer> outputSlots = parseIntList(root, "output_slots");
            final List<Integer> fuelSlots = parseIntList(root, "fuel_slots");
            if (adapter == AdapterKind.ITEM_HANDLER && (inputSlots.isEmpty() || outputSlots.isEmpty()))
            {
                throw new JsonParseException("item_handler profiles must define input_slots and output_slots");
            }

            final Optional<String> activityProperty = Optional.ofNullable(GsonHelper.getAsString(root, "activity_blockstate_property", null));
            final Optional<ResourceLocation> recipeTypeId = Optional.ofNullable(GsonHelper.getAsString(root, "recipe_type", null))
                .map(ResourceLocation::parse);
            final int cookTime = Math.max(1, GsonHelper.getAsInt(root, "cook_time", 200));
            final float corruptionMultiplier = GsonHelper.getAsFloat(root, "corruption_multiplier", 1.0F);
            final double machineCorruptionPer1000 = GsonHelper.getAsDouble(root, "machine_corruption_per_1000", 0.0D);
            final double machinePurificationPer1000 = GsonHelper.getAsDouble(root, "machine_purification_per_1000", 0.0D);

            final String designation = GsonHelper.getAsString(root, "designation", "");

            if (designation == null)
            {
                throw new JsonParseException("FurnaceMachineProfile must define a designation");
            }

            final MachineTuning tuning = switch (designation)
            {
                case "purification" -> MachineTuning.purifying(corruptionMultiplier, machineCorruptionPer1000, machinePurificationPer1000);
                case "corruption" -> MachineTuning.corrupting(corruptionMultiplier, machineCorruptionPer1000, machinePurificationPer1000);
                case "" -> MachineTuning.DEFAULT;
                default -> throw new JsonParseException("FurnaceMachineProfile unsupported designation: " + designation);
            };

            return new FurnaceMachineProfile(
                id,
                adapter,
                List.copyOf(blockIds),
                List.copyOf(blockTags),
                List.copyOf(inputSlots),
                List.copyOf(outputSlots),
                List.copyOf(fuelSlots),
                activityProperty,
                recipeTypeId,
                cookTime,
                tuning
            );
        }

        /**
         * Parses an adapter kind from a string.
         * 
         * @param raw the string to parse
         * @return the parsed adapter kind
         * @throws JsonParseException if the string is invalid
         */
        private static AdapterKind parseAdapter(final String raw)
        {
            return switch (raw)
            {
                case "abstract_furnace" -> AdapterKind.ABSTRACT_FURNACE;
                case "item_handler" -> AdapterKind.ITEM_HANDLER;
                default -> throw new JsonParseException("unsupported adapter: " + raw);
            };
        }

        /**
         * Parses a list of resource locations from a JSON object.
         * 
         * @param root the JSON object to parse
         * @param key the key of the list to parse
         * @return the parsed list of resource locations
         */
        private static List<ResourceLocation> parseResourceLocationList(final JsonObject root, final @Nonnull String key)
        {
            final List<ResourceLocation> values = new ArrayList<>();
            if (!root.has(key))
            {
                return values;
            }

            for (final JsonElement element : GsonHelper.getAsJsonArray(root, key))
            {
                String s = element.getAsString();

                if (s == null)
                {
                    continue;
                }

                values.add(ResourceLocation.parse(s));
            }

            return values;
        }

        /**
         * Parses a list of block tags from a JSON object.
         * 
         * @param root the JSON object to parse
         * @param key the key of the list to parse
         * @return the parsed list of block tags
         */
        @SuppressWarnings("null")
        private static List<TagKey<Block>> parseBlockTags(final JsonObject root, final String key)
        {
            final List<TagKey<Block>> values = new ArrayList<>();
            if (!root.has(key))
            {
                return values;
            }

            for (final JsonElement element : GsonHelper.getAsJsonArray(root, key))
            {
                String s = element.getAsString();

                if (s == null)
                {
                    continue;
                }

                values.add(TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse(s)));
            }

            return values;
        }

        /**
         * Parses a list of integers from a JSON object.
         *
         * @param root the JSON object to parse
         * @param key the key of the list to parse
         * @return the parsed list of integers
         */
        private static List<Integer> parseIntList(final JsonObject root, final @Nonnull String key)
        {
            final List<Integer> values = new ArrayList<>();
            if (!root.has(key))
            {
                return values;
            }

            for (final JsonElement element : GsonHelper.getAsJsonArray(root, key))
            {
                values.add(element.getAsInt());
            }

            return values;
        }
    }
}
