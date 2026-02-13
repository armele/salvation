package com.deathfrog.salvationmod.core.engine;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.ModEnchantments;
import com.deathfrog.salvationmod.ModItems;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.engine.SalvationManager.CorruptionStage;
import com.minecolonies.core.blocks.MinecoloniesCropBlock;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

public class LootCorruptionModifier extends LootModifier
{;
    public static final String CONVERTS_ON_CORRUPTION_PREFIX = "convertsto/";

    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    

    protected static final Map<Item, TagKey<Item>> corruptedItemTagMap = new HashMap<>();

    @SuppressWarnings("null")
    public static final MapCodec<LootCorruptionModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
        codecStart(instance).apply(instance, LootCorruptionModifier::new)
    );

    public LootCorruptionModifier(LootItemCondition[] conditions)
    {
        super(conditions);
    }

    /**
     * Applies the loot corruption modifier to the given list of generated loot items.
     * This modifier will only operate on server-side loot and will only corrupt items that are
     * declared corruptible (via the {@link ModTags.Items#CORRUPTABLE_ITEMS} tag).
     * The chance of corruption is based on the current corruption stage of the level.
     * If the corruption stage is 0 or less, this modifier will not corrupt any items.
     * This modifier will only corrupt items in block drops (including crops), entity drops, and fish
     * drops. Other types of loot (such as chest loot, bartering, and gameplay rewards) are
     * excluded.
     * 
     * If the item being corrupted does not have a per-item tag mapping, this modifier will fall
     * back to corrupting the item to a "corrupted_harvest" item (or "corrupted_catch" if changed).
     * If the loot context is a crop-like block, this modifier will use the crop-path fallback and
     * corrupt the item to a "corrupted_harvest" item.
     * 
     * If the loot context is a fishing drop, this modifier will fall back to corrupting the item to a
     * corrupted_catch item.
     * 
     * @param generatedLoot the list of generated loot items to apply the modifier to
     * @param context the loot context in which the modifier is being applied
     * @return the modified list of generated loot items
     */
    @Override
    protected ObjectArrayList<ItemStack> doApply(@Nonnull ObjectArrayList<ItemStack> generatedLoot, @Nonnull LootContext context)
    {
        // Only operate on server loot
        if (!(context.getLevel() instanceof ServerLevel level))
            return generatedLoot;

        // Compute chance from your Salvation stage
        final CorruptionStage stage = SalvationManager.stageForLevel(level);
        final float chance = stage.getLootCorruptionChance();

        if (chance <= 0.0F)
        {
            return generatedLoot;
        }

        // Identify the loot context we’re in (block / entity / fishing)
        final BlockState blockState = context.getParamOrNull(NullnessBridge.assumeNonnull(LootContextParams.BLOCK_STATE));
        final Entity thisEntity = context.getParamOrNull(NullnessBridge.assumeNonnull(LootContextParams.THIS_ENTITY));

        final boolean isBlockLoot = blockState != null;
        final boolean isEntityLoot = thisEntity instanceof net.minecraft.world.entity.LivingEntity;
        final boolean isFishingLoot = thisEntity instanceof net.minecraft.world.entity.projectile.FishingHook;

        // Whitelist only: block drops (incl. crops), entity drops, fishing drops.
        // This excludes chest loot, bartering, gameplay rewards, etc.
        if (!isBlockLoot && !isEntityLoot && !isFishingLoot)
        {
            return generatedLoot;
        }

        float effectiveChance = chance;

        if (isEntityLoot && thisEntity instanceof Mob)
        {
            final Entity killer = context.getParamOrNull(NullnessBridge.assumeNonnull(LootContextParams.ATTACKING_ENTITY));
            if (killer instanceof LivingEntity killerLiving)
            {
                final float ward = SalvationManager.wardEffect(killerLiving);
                effectiveChance = (float) (chance * (1.0 - ward));
            }
        }
        else if (isBlockLoot)
        {
            final ItemStack tool = context.getParamOrNull(NullnessBridge.assumeNonnull(LootContextParams.TOOL));
            if (tool != null && !tool.isEmpty())
            {
                float wardEffect = Mth.clamp(ModEnchantments.getCorruptionWardReduction(level, tool), 0.0f, 1.0f);
                effectiveChance = chance * (1.0f - wardEffect);
            }  
        } 
        else if (isFishingLoot)
        {
            // Prefer TOOL if present (typically the fishing rod)
            final ItemStack tool = context.getParamOrNull(NullnessBridge.assumeNonnull(LootContextParams.TOOL));
            if (tool != null && !tool.isEmpty())
            {
                float wardEffect = Mth.clamp((float) ModEnchantments.getCorruptionWardReduction(level, tool), 0.0f, 1.0f);
                effectiveChance = chance * (1.0f - wardEffect);
            }
            else if (thisEntity instanceof net.minecraft.world.entity.projectile.FishingHook hook)
            {
                // Fallback: look up the player owner and use what they're holding
                final Player owner = hook.getPlayerOwner(); // exists in 1.21.x
                final float ward = SalvationManager.wardEffect(owner);
                effectiveChance = (float) (chance * (1.0 - ward));
            }
        }

        // Crop detection (used only for fallback routing)
        final boolean isCropLikeBlock = isBlockLoot && isCropHarvestBlock(NullnessBridge.assumeNonnull(blockState));

        final RandomSource random = context.getRandom();

        for (int i = 0; i < generatedLoot.size(); i++)
        {
            final ItemStack stack = generatedLoot.get(i);

            if (stack.isEmpty())
                continue;

            boolean isCorruptable = stack.is(ModTags.Items.CORRUPTABLE_ITEMS);

            final float localEffectiveChance = effectiveChance;
            TraceUtils.dynamicTrace(ModCommands.TRACE_CORRUPTION,
                () -> LOGGER.info("Checking GLM during stage {}, with a corruption chance of {} (effectively {} after ward). Examining item {}, isCorruptable: {}", stage, chance, localEffectiveChance, stack, isCorruptable));

            // Only corrupt items we’ve declared corruptable (your master allowlist)
            if (!isCorruptable) continue;

            final float roll = random.nextFloat();
            if (roll >= effectiveChance) continue;

            final Item baseItem = stack.getItem();

            if (baseItem == null) continue;

            Item corrupted = null;

            // 1) Start with per-item tag mapping
            if (corrupted == null)
            {   
                corrupted = getCorrupted(baseItem, random);
            }

            // 1) Crop-path fallback (single item)
            if (corrupted == null && isCropLikeBlock)
            {
                corrupted = ModItems.CORRUPTED_HARVEST.get();
            }

            // 3) Fishing fallback (single item)
            if (corrupted == null && isFishingLoot)
            {
                corrupted = ModItems.CORRUPTED_CATCH.get();
            }

            if (corrupted == null) 
            {
                TraceUtils.dynamicTrace(ModCommands.TRACE_CORRUPTION,
                    () -> LOGGER.info("Checking GLM during stage {}, rolled {} but no replacement found for item: {}", stage, roll, baseItem));
                continue;
            }

            generatedLoot.set(i, new ItemStack(corrupted, stack.getCount()));
        }

        return generatedLoot;
    }

    /** “Crop harvest” detection used only to route to the harvest fallback. */
    private static boolean isCropHarvestBlock(@Nonnull BlockState state)
    {
        // Vanilla crops
        if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock)
            return true;

        // Minecolonies crops
        if (state.getBlock() instanceof MinecoloniesCropBlock)
            return true;

        return false;
    }

    /**
     * Returns the codec for this loot modifier.
     * This codec is a map codec, with the key being the loot modifier
     * and the value being the loot item condition array.
     *
     * @return the codec for this loot modifier
     */
    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() 
    {
        return CODEC;
    }

    /**
     * Given an item, randomly returns a corrupted version of it.
     * If there is no corrupted version of the item, returns null.
     * If there is only one corrupted version, returns that item.
     * If there are multiple corrupted versions, randomly selects one of them.
     *
     * @param base the item to get a corrupted version of
     * @param random a random source to use for random selection
     * @return a corrupted version of the item, or null if there is no corrupted version
     */
    public static Item getCorrupted(@Nonnull Item base, RandomSource random)
    {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(base);
        String corruptedPath = CONVERTS_ON_CORRUPTION_PREFIX + id.getNamespace() + "_" + id.getPath();
        ResourceLocation corruptionTagLocation = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, corruptedPath);

        TraceUtils.dynamicTrace(ModCommands.TRACE_CORRUPTION,
                () -> LOGGER.info("Looking for replacement item for {} at path {}, in location {}", base, corruptedPath, corruptionTagLocation));

        if (corruptionTagLocation == null) return null;

        TagKey<Item> corruptedItemTag = corruptedItemTagMap.get(base);

        if (corruptedItemTag == null)
        {
            corruptedItemTag = TagKey.create(NullnessBridge.assumeNonnull(Registries.ITEM), corruptionTagLocation);
            corruptedItemTagMap.put(base, corruptedItemTag);
        }

        if (corruptedItemTag == null) return null;

        Optional<Named<Item>> taggedItems = BuiltInRegistries.ITEM.getTag(corruptedItemTag);
        if (taggedItems.isEmpty()) return null;

        Named<Item> holders = taggedItems.get();
        int size = holders.size();
        
        if (size == 0) return null;
        if (size == 1) return holders.get(0).value();

        int index = random.nextInt(size);

        return holders.get(index).value();
    }
}