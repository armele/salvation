package com.deathfrog.salvationmod.core.engine;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.SalvationMod;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Optional;
import java.util.Set;

/**
 * Tracks furnace cooking output extraction (player, hopper, colonist, etc.)
 * and forwards it to a corruption ledger sink.
 *
 * Performance strategy:
 *  - Maintain a set of "active furnace positions" per level.
 *  - Populate that set on chunk load (scan only BE map for the chunk),
 *    plus on neighbor-notify when a furnace becomes lit.
 *  - Poll active set once per second (configurable) and detect result-slot deltas.
 *
 * Persistence:
 *  - No custom persistence required. On restart, chunk loads rebuild the active set.
 *  - Colony caching is in-memory with TTL; safely rebuilt as needed.
 */
@EventBusSubscriber(modid = SalvationMod.MODID)
public final class FurnaceCookLedgerTracker
{
    // ---- Public integration points ----

    /**
     * Sink for ledger events.
     * "fuelPoints" are estimated as recipe cookTimeTicks * extractedCount.
     */
    @FunctionalInterface
    public interface LedgerSink
    {
        void onCookOutputExtracted(ServerLevel level,
                                   BlockPos furnacePos,
                                   ItemStack outputExtracted,
                                   int extractedCount,
                                   int fuelPoints,
                                   RecipeType<?> recipeType,
                                   Optional<ResourceLocation> recipeId);
    }

    // ---- Singleton wiring ----
    private static volatile boolean ENABLED = false;
    private static LedgerSink LEDGER_SINK =
            (lvl, pos, out, n, fp, rt, rid) -> {};

    public static void init(final LedgerSink ledgerSink)
    {
        LEDGER_SINK = (ledgerSink != null) ? ledgerSink : (lvl, pos, out, n, fp, rt, rid) -> {};
        ENABLED = true;
    }

    // ---- Tuning knobs ----
    /** How long to keep a furnace in the active set after it appears idle. */
    private static final int IDLE_GRACE_TICKS = 20 * 15; // 15 seconds

    // ---- Per-level state ----

    private static final class LevelState
    {
        final LongSet active = new LongOpenHashSet();

        // result snapshot (for delta detection)
        final Long2IntOpenHashMap lastResultCount = new Long2IntOpenHashMap();
        final Long2IntOpenHashMap lastResultItemId = new Long2IntOpenHashMap(); // System.identityHashCode(item)

        // last time we observed it "active" (lit/cooking/has result)
        final Long2LongOpenHashMap lastActiveTick = new Long2LongOpenHashMap();

        LevelState()
        {
            lastResultCount.defaultReturnValue(-1);
            lastResultItemId.defaultReturnValue(0);
            lastActiveTick.defaultReturnValue(0);
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<ResourceKey<Level>, LevelState> STATES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static LevelState state(final ServerLevel level)
    {
        return STATES.computeIfAbsent(level.dimension(), k -> new LevelState());
    }

    // ---- Events ----

    /**
     * Index furnaces when a chunk loads (cheap: only scans that chunk's BE map).
     * This is what makes the system "restart proof" without persistence.
     */
    @SubscribeEvent
    public static void onChunkLoad(final ChunkEvent.Load event)
    {
        if (!ENABLED) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        // Scan only the chunk's block entities (not the whole world)
        ChunkAccess chunk = event.getChunk();
        Set<BlockPos> bes = chunk.getBlockEntitiesPos();

        if (bes.isEmpty()) return;

        final LevelState st = state(level);
        final long now = level.getGameTime();

        for (BlockPos bePos : bes)
        {
            if (bePos == null) continue;

            BlockEntity be = level.getBlockEntity(bePos);

            if (!(be instanceof AbstractFurnaceBlockEntity furnace)) continue;
            final BlockPos pos = furnace.getBlockPos();

            if (shouldTrackNow(level, furnace))
            {
                final long key = pos.asLong();
                st.active.add(key);
                st.lastActiveTick.put(key, now);

                // Initialize snapshot so first poll doesn't spuriously "extract"
                primeSnapshot(st, furnace);
            }
        }
    }

    /**
     * Remove tracked furnaces when a chunk unloads (keeps memory bounded).
     */
    @SubscribeEvent
    public static void onChunkUnload(final ChunkEvent.Unload event)
    {
        if (!ENABLED) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ChunkAccess chunk = event.getChunk();
        Set<BlockPos> bes = chunk.getBlockEntitiesPos();

        if (bes.isEmpty()) return;

        final LevelState st = state(level);

        for (BlockPos bePos : bes)
        {
            if (bePos == null) continue;

            BlockEntity be = level.getBlockEntity(bePos);

            if (!(be instanceof AbstractFurnaceBlockEntity furnace)) continue;
            final long key = furnace.getBlockPos().asLong();
            removeTracked(st, key);
        }
    }

    /**
     * When something changes around a furnace, it often flips LIT on/off.
     * We only care about quickly discovering "newly lit" furnaces without scanning everything.
     *
     * This event can be noisy, so we:
     *  - only check the notified position (not all neighbors)
     *  - only add if it's a furnace and currently lit
     */
    @SubscribeEvent
    public static void onNeighborNotify(final BlockEvent.NeighborNotifyEvent event)
    {
        if (!ENABLED) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        final BlockPos pos = event.getPos();
        if (pos == null) return;

        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractFurnaceBlock)) return;

        if (!state.hasProperty(NullnessBridge.assumeNonnull(AbstractFurnaceBlock.LIT)) || !state.getValue(NullnessBridge.assumeNonnull(AbstractFurnaceBlock.LIT)))
            return;

        final BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return;

        final LevelState st = state(level);
        final long key = pos.asLong();
        st.active.add(key);
        st.lastActiveTick.put(key, level.getGameTime());

        // Initialize snapshot if new
        if (st.lastResultCount.get(key) < 0)
            primeSnapshot(st, furnace);
    }

    /**
     * Preferred: call this from your existing SalvationManager per-level loop (once per second).
     * This avoids needing global server tick plumbing and keeps the work bounded per level.
     */
    public static void poll(final ServerLevel level)
    {
        if (!ENABLED) return;

        final long now = level.getGameTime();

        final LevelState st = state(level);
        if (st.active.isEmpty()) return;

        // Iterate active set. Remove if dead/idle.
        final var it = st.active.iterator();
        while (it.hasNext())
        {
            final long key = it.nextLong();
            final BlockPos pos = BlockPos.of(key);

            if (pos == null) continue;

            final BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof AbstractFurnaceBlockEntity furnace))
            {
                // block changed / BE missing
                it.remove();
                cleanupMaps(st, key);
                continue;
            }

            final boolean activeNow = shouldTrackNow(level, furnace);
            if (activeNow)
            {
                st.lastActiveTick.put(key, now);
            }
            else
            {
                final long last = st.lastActiveTick.get(key);
                if (now - last > IDLE_GRACE_TICKS)
                {
                    it.remove();
                    cleanupMaps(st, key);
                    continue;
                }
            }

            // Delta detection
            detectAndEmit(level, st, furnace);
        }
    }

    // ---- Core logic ----

    /**
     * Initialize the snapshot of a furnace's result slot with the current item and count.
     * This is called when a furnace is first detected as active (i.e. newly lit or newly
     * loaded into the active set from disk). The snapshot is used to detect extracted items
     * by comparing the current item with the last observed item.
     * 
     * @param st the level state to store the snapshot in
     * @param furnace the furnace block entity to snapshot
     */    
    private static void primeSnapshot(final LevelState st, final AbstractFurnaceBlockEntity furnace)
    {
        final long key = furnace.getBlockPos().asLong();
        final ItemStack cur = furnace.getItem(2); // result slot
        st.lastResultCount.put(key, cur.isEmpty() ? 0 : cur.getCount());
        st.lastResultItemId.put(key, cur.isEmpty() ? 0 : System.identityHashCode(cur.getItem()));
    }

    /**
     * Detect and emit any extracted items from a furnace.
     * 
     * Compares the current item in the furnace result slot with the last observed item.
     * If the current item is the same as the last observed one and the count has decreased,
     * the difference is considered to be the extracted item count.
     * If the current item is different from the last observed one, the last observed item is
     * assumed to be the extracted item count.
     * 
     * The extracted item count, item type, fuel points estimate, and recipe ID (if applicable) are
     * emitted through the CookOutputExtracted event.
     * 
     * @param level the level the furnace is in
     * @param st the current level state
     * @param furnace the furnace block entity to detect and emit for
     */
    private static void detectAndEmit(final ServerLevel level, final LevelState st, final AbstractFurnaceBlockEntity furnace)
    {
        final BlockPos pos = furnace.getBlockPos();
        final long key = pos.asLong();

        final ItemStack cur = furnace.getItem(2);
        final Item curItem = cur.isEmpty() ? null : cur.getItem();
        final int curCount = cur.isEmpty() ? 0 : cur.getCount();
        final int curItemId = (curItem == null) ? 0 : System.identityHashCode(curItem);

        final int lastCount = st.lastResultCount.get(key);
        final int lastItemId = st.lastResultItemId.get(key);

        // First observation
        if (lastCount < 0)
        {
            st.lastResultCount.put(key, curCount);
            st.lastResultItemId.put(key, curItemId);
            return;
        }

        int extractedCount = 0;
        Item extractedItem = null;

        if (lastCount > 0)
        {
            if (lastItemId == curItemId)
            {
                // Same item, count decreased => extracted
                if (curCount < lastCount)
                {
                    extractedCount = lastCount - curCount;
                    extractedItem = curItem; // same item
                }
            }
            else
            {
                // Item changed. Conservatively assume previous stack was taken.
                extractedCount = lastCount;
                extractedItem = resolveItemFromLastSnapshot(level, furnace, lastItemId);
            }
        }

        // Update snapshot
        st.lastResultCount.put(key, curCount);
        st.lastResultItemId.put(key, curItemId);

        if (extractedCount <= 0 || extractedItem == null)
            return;

        // Build extracted stack (just the item + count; components not tracked here)
        final ItemStack extractedStack = new ItemStack(extractedItem, extractedCount);

        // Recipe + fuel points estimate
        final RecipeType<?> recipeType = recipeTypeFor(furnace);
        final ItemStack input = furnace.getItem(0);

        final Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> recipe =
                (furnace instanceof BlastFurnaceBlockEntity)
                        ? findCookingRecipe(level, RecipeType.BLASTING, input)
                        : (furnace instanceof SmokerBlockEntity)
                                ? findCookingRecipe(level, RecipeType.SMOKING, input)
                                : findCookingRecipe(level, RecipeType.SMELTING, input);
                                
        final int cookTime = recipe.map(r -> r.value().getCookingTime()).orElse(defaultCookTimeFor(recipeType));
        final int fuelPoints = Math.max(1, cookTime * extractedCount);

        final Optional<ResourceLocation> recipeId = recipe.map(RecipeHolder::id);

        // Emit
        LEDGER_SINK.onCookOutputExtracted(level, pos, extractedStack, extractedCount, fuelPoints, recipeType, recipeId);
    }


    /**
     * Attempts to resolve the item from the last snapshot given its identity hash.
     * If the item changed between polls, best effort is made to return the current result item if non-empty.
     * If the result item is empty and changed, null is returned and the emit is skipped.
     * @param level the level the furnace is in
     * @param furnace the furnace to resolve the item for
     * @param lastItemId the identity hash of the item from the last snapshot
     * @return the resolved item, or null if it cannot be resolved
     */
    private static Item resolveItemFromLastSnapshot(final ServerLevel level,
                                                    final AbstractFurnaceBlockEntity furnace,
                                                    final int lastItemId)
    {
        // We don't store the Item instance itself (to keep maps primitive).
        // If item changed, best effort: return current result item if non-empty, else use "unknown".
        // In practice, item changes between polls are rare; you can upgrade this to store item registry id if you want.
        final ItemStack cur = furnace.getItem(2);
        if (!cur.isEmpty()) return cur.getItem();

        // If completely empty and changed, we cannot reliably reconstruct item from identity hash.
        // Choose a safe placeholder (air is invalid), so return null and skip emit.
        return null;
    }

    /**
     * Determines whether the given furnace should be tracked now.
     * This function does a quick check to avoid calling the recipe manager.
     * It returns true if the furnace is lit, has a non-empty result slot, or has non-empty input.
     * If the furnace is not lit and has empty result and input slots, it returns false.
     * This function is used to determine whether to start tracking a furnace or not.
     * It is intended to be used in conjunction with the `onNeighborNotify` event.
     * @param level the level the furnace is in
     * @param furnace the furnace to check
     * @return true if the furnace should be tracked now, false otherwise
     */
    private static boolean shouldTrackNow(final ServerLevel level, final AbstractFurnaceBlockEntity furnace)
    {
        // Quick checks that avoid recipe manager calls.
        final BlockPos furnacePos = furnace.getBlockPos();

        if (furnacePos == null) return false;

        final BlockState bs = level.getBlockState(furnacePos);
        final boolean lit = bs.hasProperty(NullnessBridge.assumeNonnull(AbstractFurnaceBlock.LIT)) && bs.getValue(NullnessBridge.assumeNonnull(AbstractFurnaceBlock.LIT));

        if (lit) return true;

        // If result slot has something, we still want to track (extraction can happen while unlit).
        final ItemStack result = furnace.getItem(2);
        if (!result.isEmpty()) return true;

        // If input exists, furnace might start soon; optional: track it briefly.
        final ItemStack input = furnace.getItem(0);
        return !input.isEmpty();
    }

    /**
     * Returns the recipe type associated with the given furnace entity.
     *
     * This maps instances of {@link BlastFurnaceBlockEntity} to {@link RecipeType#BLASTING},
     * instances of {@link SmokerBlockEntity} to {@link RecipeType#SMOKING}, and all other
     * furnace entities to {@link RecipeType#SMELTING}.
     *
     * @param furnace the furnace entity to determine the recipe type for
     * @return the recipe type associated with the given furnace entity
     */
    private static RecipeType<?> recipeTypeFor(final AbstractFurnaceBlockEntity furnace)
    {
        if (furnace instanceof BlastFurnaceBlockEntity) return RecipeType.BLASTING;
        if (furnace instanceof SmokerBlockEntity) return RecipeType.SMOKING;
        return RecipeType.SMELTING;
    }

    /**
     * Attempts to find a cooking recipe for the given input item in the given server level
     * and recipe type. If the input item is empty or null, returns an empty Optional.
     *
     * @param level the server level to search for recipes in
     * @param recipeType the type of recipe to search for
     * @param input the item to search for a recipe for
     * @return an Optional containing the recipe holder if found, otherwise an empty Optional
     */
    private static <T extends AbstractCookingRecipe> Optional<RecipeHolder<T>> findCookingRecipe(
            final ServerLevel level,
            final RecipeType<T> recipeType,
            final ItemStack input
    ) {
        if (input == null || input.isEmpty() || recipeType == null) return Optional.empty();

        // 1.21+: furnace-like recipes use RecipeInput; SingleRecipeInput is the standard wrapper.
        return level.getRecipeManager().getRecipeFor(recipeType, new SingleRecipeInput(input), level);
    }


    /**
     * Returns a default cooking time for a given recipe type.
     * This is used when the recipe itself does not specify a cooking time.
     * The default values are based on vanilla Minecraft recipes and are as follows:
     * - Smelting: 100
     * - Smokingoking/Blasing: 100
     * - All other recipes: 200
     * @param type the recipe type to get the default cooking time for
     * @return the default cooking time for the given recipe type
     */
    private static int defaultCookTimeFor(final RecipeType<?> type)
    {
        // Vanilla defaults: smelting ~200, smoking/blasting ~100 (recipe-dependent, but good fallback).
        if (type == RecipeType.SMOKING) return 100;
        if (type == RecipeType.BLASTING) return 100;
        return 200;
    }

    /**
     * Remove all tracking data associated with the given key from the given state.
     * This is used when a furnace is no longer being tracked.
     * 
     * @param st the state to remove data from
     * @param key the key to remove data for
     */
    private static void removeTracked(final LevelState st, final long key)
    {
        st.active.remove(key);
        cleanupMaps(st, key);
    }

    /**
     * Remove all data associated with the given key from the given state.
     * This is used when a furnace is no longer being tracked.
     * 
     * @param st the state to remove data from
     * @param key the key to remove data for
     */
    private static void cleanupMaps(final LevelState st, final long key)
    {
        st.lastResultCount.remove(key);
        st.lastResultItemId.remove(key);
        st.lastActiveTick.remove(key);
    }
}