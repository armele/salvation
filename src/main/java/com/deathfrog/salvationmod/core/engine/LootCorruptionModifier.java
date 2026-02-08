package com.deathfrog.salvationmod.core.engine;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.engine.SalvationManager.CorruptionStage;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

import javax.annotation.Nonnull;

public class LootCorruptionModifier extends LootModifier
{;

    @SuppressWarnings("null")
    public static final MapCodec<LootCorruptionModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
        codecStart(instance).apply(instance, LootCorruptionModifier::new)
    );

    public LootCorruptionModifier(LootItemCondition[] conditions)
    {
        super(conditions);
    }

    /**
     * Applies the loot corruption modifier to the generated loot
     * It will compute the chance of loot corruption based on the current Corruption stage.
     *
     * @param generatedLoot the generated loot to apply the modifier to
     * @param context the loot context
     * @return the modified generated loot
     */
    @Override
    protected ObjectArrayList<ItemStack> doApply(@Nonnull ObjectArrayList<ItemStack> generatedLoot, @Nonnull LootContext context)
    {
        // Only operate on server loot
        if (!(context.getLevel() instanceof ServerLevel level))
            return generatedLoot;

        // Only operate on block loot contexts (crop harvesting)
        BlockState state = context.getParamOrNull(NullnessBridge.assumeNonnull(LootContextParams.BLOCK_STATE));
        if (state == null)
            return generatedLoot;

        // Compute chance from your Salvation stage
        CorruptionStage stage = SalvationManager.stageForLevel(level);
        float chance = stage.getLootCorruptionChance();

        if (chance <= 0.0)
            return generatedLoot;

        for (int i = 0; i < generatedLoot.size(); i++)
        {
            ItemStack stack = generatedLoot.get(i);

            if (stack.isEmpty()) continue;

            // Only corrupt items we’ve declared corruptable
            if (!stack.is(ModTags.Items.CORRUPTABLE_ITEMS)) continue;

            if (context.getRandom().nextDouble() >= chance) continue;

            Item stackItem = stack.getItem();

            if (stackItem == null) continue;

            Item corrupted = getCorrupted(stackItem, level.getRandom());

            if (corrupted == null) continue;

            ItemStack replaced = new ItemStack(corrupted, stack.getCount());
            // If you care about components/NBT, copy them:
            // replaced.applyComponents(stack.getComponents());  (method name varies by version)
            generatedLoot.set(i, replaced);
        }

        return generatedLoot;
    }

    /**
     * Returns the codec for this loot modifier.
     * This codec is a map codec, with the key being the loot modifier
     * and the value being the loot item condition array.
     *
     * @return the codec for this loot modifier
     */
    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    public static Item getCorrupted(@Nonnull Item base, RandomSource random)
    {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(base);
        String corruptedPath = "corrupted_" + id.getPath();
        ResourceLocation corruptionTagLocation = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, corruptedPath);

        if (corruptionTagLocation == null) return null;

        final TagKey<Item> corruptedItemTag = TagKey.create(NullnessBridge.assumeNonnull(Registries.ITEM), corruptionTagLocation);

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