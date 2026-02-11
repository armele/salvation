package com.deathfrog.salvationmod.core.engine;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
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
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.SalvationMod;

import java.util.Optional;
import java.util.Set;

/**
 * Tracks furnace cooking output extraction and (optionally) cook completion.
 *
 * Performance strategy:
 *  - Maintain a set of "active furnace positions" per level.
 *  - Populate that set on chunk load (scan only BE map for the chunk),
 *    plus on neighbor-notify when a furnace becomes lit.
 *  - Poll active set once per second (caller-driven) and detect slot deltas.
 *
 * Shutdown safety strategy:
 *  - Immediately disable all hooks on ServerStoppingEvent.
 *  - Avoid BE lookups during chunk unload.
 *  - Avoid BE lookups for positions whose chunks are no longer loaded.
 *  - Provide clearLevel(level) to drop state when a dimension unloads.
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

    /**
     * Sink for cook completion events (i.e., cooking produced output, not extraction).
     * Captures a snapshot of whatever is in the fuel slot at completion time (slot 1).
     */
    @FunctionalInterface
    public interface CookCompleteSink
    {
        void onCookCompleted(ServerLevel level,
                             BlockPos furnacePos,
                             ItemStack outputProduced,
                             int craftsCompleted,
                             int fuelPoints,
                             ItemStack fuelStackAtCompletion,
                             RecipeType<?> recipeType,
                             Optional<ResourceLocation> recipeId);
    }

    // ---- Singleton wiring ----

    private static volatile boolean ENABLED = false;

    /**
     * Set true as soon as the server begins stopping. We hard-stop all work.
     * This avoids touching chunk/BE state during "Saving worlds".
     */
    private static volatile boolean STOPPING = false;

    private static LedgerSink LEDGER_SINK =
            (lvl, pos, out, n, fp, rt, rid) -> {};

    private static CookCompleteSink COOK_COMPLETE_SINK =
            (lvl, pos, out, crafts, fuelpoints, fuelitem, rt, rid) -> {};

    /**
     * Backward-compatible init: extraction-only.
     */
    public static void init(final LedgerSink ledgerSink)
    {
        init(ledgerSink, null);
    }

    /**
     * Init with both sinks.
     */
    public static void init(final LedgerSink ledgerSink, final CookCompleteSink cookCompleteSink)
    {
        LEDGER_SINK = (ledgerSink != null) ? ledgerSink : (lvl, pos, out, n, fp, rt, rid) -> {};
        COOK_COMPLETE_SINK = (cookCompleteSink != null) ? cookCompleteSink : (lvl, pos, out, crafts, fuelpoints, fuelitem, rt, rid) -> {};
        STOPPING = false;
        ENABLED = true;
    }

    /**
     * Optional: late-bind or replace the cook completion sink.
     */
    public static void setCookCompleteSink(final CookCompleteSink sink)
    {
        COOK_COMPLETE_SINK = (sink != null) ? sink : (lvl, pos, out, crafts, fuelpoints, fuelitem, rt, rid) -> {};
    }

    // ---- Optional external hooks ----

    /**
     * Drop all per-level state for a level/dimension.
     * Useful if you do custom dimension lifecycles, or if you want an explicit cleanup call.
     */
    public static void clearLevel(final ServerLevel level)
    {
        if (level == null) return;
        STATES.remove(level.dimension());
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

        // input snapshot (for cook-completion detection)
        final Long2IntOpenHashMap lastInputCount = new Long2IntOpenHashMap();
        final Long2IntOpenHashMap lastInputItemId = new Long2IntOpenHashMap(); // System.identityHashCode(item)

        // last time we observed it "active" (lit/cooking/has result/input)
        final Long2LongOpenHashMap lastActiveTick = new Long2LongOpenHashMap();

        LevelState()
        {
            lastResultCount.defaultReturnValue(-1);
            lastResultItemId.defaultReturnValue(0);

            lastInputCount.defaultReturnValue(-1);
            lastInputItemId.defaultReturnValue(0);

            lastActiveTick.defaultReturnValue(0);
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<ResourceKey<Level>, LevelState> STATES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static LevelState state(final ServerLevel level)
    {
        return STATES.computeIfAbsent(level.dimension(), k -> new LevelState());
    }

    private static boolean disabled()
    {
        return !ENABLED || STOPPING;
    }

    // ---- Lifecycle / shutdown guards ----

    /**
     * Hard-disable the tracker as soon as shutdown starts.
     * This is the #1 fix for "hangs at Saving worlds" caused by late BE access.
     */
    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event)
    {
        STOPPING = true;
        ENABLED = false;
        STATES.clear();
    }

    /**
     * When a level unloads (dimension unload), drop state so we don't retain references and
     * so we won't accidentally poll stale positions if the dimension comes back.
     */
    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event)
    {
        if (STOPPING) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        clearLevel(level);
    }

    // ---- Events ----

    /**
     * Index furnaces when a chunk loads (cheap: only scans that chunk's BE map).
     * This is what makes the system "restart proof" without persistence.
     */
    @SubscribeEvent
    public static void onChunkLoad(final ChunkEvent.Load event)
    {
        if (disabled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        // Scan only the chunk's block entities (not the whole world)
        final ChunkAccess chunk = event.getChunk();
        final Set<BlockPos> bes = chunk.getBlockEntitiesPos();
        if (bes.isEmpty()) return;

        final LevelState st = state(level);
        final long now = level.getGameTime();

        for (BlockPos bePos : bes)
        {
            if (bePos == null) continue;

            // Load is generally safe; still keep work minimal.
            final BlockEntity be = level.getBlockEntity(bePos);
            if (!(be instanceof AbstractFurnaceBlockEntity furnace)) continue;

            if (shouldTrackNow(level, furnace))
            {
                final long key = furnace.getBlockPos().asLong();
                st.active.add(key);
                st.lastActiveTick.put(key, now);

                // Initialize snapshots so first poll doesn't spuriously fire.
                primeSnapshots(st, furnace);
            }
        }
    }

    /**
     * Remove tracked positions when a chunk unloads (keeps memory bounded).
     *
     * Important: DO NOT call level.getBlockEntity() here.
     * During unload/save, BE lookups can re-enter chunk tracking and contribute to shutdown hangs.
     */
    @SubscribeEvent
    public static void onChunkUnload(final ChunkEvent.Unload event)
    {
        if (disabled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        final ChunkAccess chunk = event.getChunk();
        final Set<BlockPos> bes = chunk.getBlockEntitiesPos();
        if (bes.isEmpty()) return;

        final LevelState st = state(level);

        for (BlockPos bePos : bes)
        {
            if (bePos == null) continue;
            final long key = bePos.asLong();

            // Only cleanup if we were tracking it (cheap guard).
            if (st.active.contains(key) ||
                st.lastResultCount.containsKey(key) ||
                st.lastInputCount.containsKey(key) ||
                st.lastActiveTick.containsKey(key))
            {
                removeTracked(st, key);
            }
        }
    }

    /**
     * When something changes around a furnace, it often flips LIT on/off.
     * We only care about quickly discovering "newly lit" furnaces without scanning everything.
     */
    @SubscribeEvent
    public static void onNeighborNotify(final BlockEvent.NeighborNotifyEvent event)
    {
        if (disabled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        final BlockPos pos = event.getPos();
        if (pos == null) return;

        final BlockState bs = level.getBlockState(pos);
        if (!(bs.getBlock() instanceof AbstractFurnaceBlock)) return;

        if (!bs.hasProperty(NullnessBridge.assumeNonnull(AbstractFurnaceBlock.LIT)) ||
            !bs.getValue(NullnessBridge.assumeNonnull(AbstractFurnaceBlock.LIT)))
        {
            return;
        }

        final BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return;

        final LevelState st = state(level);
        final long key = pos.asLong();

        st.active.add(key);
        st.lastActiveTick.put(key, level.getGameTime());

        // Initialize snapshots if new
        if (st.lastResultCount.get(key) < 0 || st.lastInputCount.get(key) < 0)
        {
            primeSnapshots(st, furnace);
        }
    }

    // ---- Polling ----

    /**
     * Preferred: call this from your existing SalvationManager per-level loop (once per second).
     * This avoids global tick plumbing and keeps the work bounded per level.
     */
    public static void poll(final ServerLevel level)
    {
        if (disabled()) return;
        if (level == null) return;

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

            // Never force work for unloaded chunks (prevents teardown/save contention).
            if (!level.isLoaded(pos))
            {
                it.remove();
                cleanupMaps(st, key);
                continue;
            }

            final BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof AbstractFurnaceBlockEntity furnace))
            {
                // Block changed / BE missing
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

            // Delta detection (completion + extraction)
            detectAndEmit(level, st, furnace);
        }
    }

    // ---- Core logic ----

    /**
     * Initialize snapshots of a furnace's input + result slots.
     */
    private static void primeSnapshots(final LevelState st, final AbstractFurnaceBlockEntity furnace)
    {
        final long key = furnace.getBlockPos().asLong();

        final ItemStack in = furnace.getItem(0);
        st.lastInputCount.put(key, in.isEmpty() ? 0 : in.getCount());
        st.lastInputItemId.put(key, in.isEmpty() ? 0 : System.identityHashCode(in.getItem()));

        final ItemStack out = furnace.getItem(2);
        st.lastResultCount.put(key, out.isEmpty() ? 0 : out.getCount());
        st.lastResultItemId.put(key, out.isEmpty() ? 0 : System.identityHashCode(out.getItem()));
    }

    /**
     * Detect and emit:
     *  - cook completion (output increased AND input decreased), with a fuel slot snapshot
     *  - output extraction (output decreased)
     */
    private static void detectAndEmit(final ServerLevel level, final LevelState st, final AbstractFurnaceBlockEntity furnace)
    {
        final BlockPos pos = furnace.getBlockPos();
        final long key = pos.asLong();

        final ItemStack curIn = furnace.getItem(0);
        final ItemStack curOut = furnace.getItem(2);

        final Item curInItem = curIn.isEmpty() ? null : curIn.getItem();
        final Item curOutItem = curOut.isEmpty() ? null : curOut.getItem();

        final int curInCount = curIn.isEmpty() ? 0 : curIn.getCount();
        final int curOutCount = curOut.isEmpty() ? 0 : curOut.getCount();

        final int curInId = (curInItem == null) ? 0 : System.identityHashCode(curInItem);
        final int curOutId = (curOutItem == null) ? 0 : System.identityHashCode(curOutItem);

        final int lastInCount = st.lastInputCount.get(key);
        final int lastOutCount = st.lastResultCount.get(key);

        final int lastInId = st.lastInputItemId.get(key);
        final int lastOutId = st.lastResultItemId.get(key);

        // First observation: initialize and bail
        if (lastInCount < 0 || lastOutCount < 0)
        {
            st.lastInputCount.put(key, curInCount);
            st.lastInputItemId.put(key, curInId);
            st.lastResultCount.put(key, curOutCount);
            st.lastResultItemId.put(key, curOutId);
            return;
        }

        // Resolve recipe once (used for both events)
        final RecipeType<?> recipeType = recipeTypeFor(furnace);
        final Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> recipe =
                (furnace instanceof BlastFurnaceBlockEntity)
                        ? findCookingRecipe(level, RecipeType.BLASTING, curIn)
                        : (furnace instanceof SmokerBlockEntity)
                                ? findCookingRecipe(level, RecipeType.SMOKING, curIn)
                                : findCookingRecipe(level, RecipeType.SMELTING, curIn);

        final Optional<ResourceLocation> recipeId = recipe.map(RecipeHolder::id);

        // -------------------------
        // 1) Cook completion detection
        // -------------------------
        // Signal: output increases AND input decreases.
        //
        // We also require item continuity to avoid false positives when users manually manipulate slots:
        //  - output item is same OR last output was empty (0)
        //  - input item is same OR current input is empty (consumed last item)
        final int outDeltaUp = curOutCount - lastOutCount;
        final int inDeltaDown = lastInCount - curInCount;

        final boolean outputContinuous = (lastOutCount == 0) || (lastOutId == curOutId);
        final boolean inputContinuous = (curInCount == 0) || (lastInId == curInId);

        if (outDeltaUp > 0 && inDeltaDown > 0 && outputContinuous && inputContinuous && curOutItem != null)
        {
            // Snapshot fuel slot exactly at completion-observation time (slot 1).
            // Note: slot may be empty even if furnace is still burning (burn time is separate).
            final ItemStack fuelSnapshot = furnace.getItem(1).copy();

            RegistryAccess regAccess = level.registryAccess();


            if (regAccess != null) 
            {
                // Estimate crafts completed from recipe output size, but report the *actual produced count*.
                final int outputPerCraft = recipe
                        .map(r -> {
                            final ItemStack rOut = r.value().getResultItem(regAccess);
                            return Math.max(1, rOut.getCount());
                        })
                        .orElse(1);

                int craftsCompleted = outDeltaUp / outputPerCraft;
                if (craftsCompleted <= 0) craftsCompleted = 1;

                final int cookTime = recipe.map(r -> r.value().getCookingTime()).orElse(defaultCookTimeFor(recipeType));
                final int fuelPoints = Math.max(1, cookTime * craftsCompleted);

                final ItemStack cookedOutput = new ItemStack(curOutItem, outDeltaUp);
                COOK_COMPLETE_SINK.onCookCompleted(level, pos, cookedOutput, craftsCompleted, fuelPoints, fuelSnapshot, recipeType, recipeId);
            }
        }

        // -------------------------
        // 2) Extraction detection
        // -------------------------
        int extractedCount = 0;
        Item extractedItem = null;

        if (lastOutCount > 0)
        {
            if (lastOutId == curOutId)
            {
                // Same item, count decreased => extracted
                if (curOutCount < lastOutCount)
                {
                    extractedCount = lastOutCount - curOutCount;
                    extractedItem = curOutItem; // same item
                }
            }
            else
            {
                // Item changed. Conservatively assume previous stack was taken.
                extractedCount = lastOutCount;
                extractedItem = resolveItemFromLastSnapshot(furnace, lastOutId);
            }
        }

        if (extractedCount > 0 && extractedItem != null)
        {
            final ItemStack extractedStack = new ItemStack(extractedItem, extractedCount);

            final int cookTime = recipe.map(r -> r.value().getCookingTime()).orElse(defaultCookTimeFor(recipeType));
            final int fuelPoints = Math.max(1, cookTime * extractedCount);

            LEDGER_SINK.onCookOutputExtracted(level, pos, extractedStack, extractedCount, fuelPoints, recipeType, recipeId);
        }

        // -------------------------
        // Update snapshots last
        // -------------------------
        st.lastInputCount.put(key, curInCount);
        st.lastInputItemId.put(key, curInId);
        st.lastResultCount.put(key, curOutCount);
        st.lastResultItemId.put(key, curOutId);
    }

    /**
     * Attempts to resolve the item from the last snapshot given its identity hash.
     * If the item changed between polls, best effort is made to return the current result item if non-empty.
     * If the result item is empty and changed, null is returned and the emit is skipped.
     */
    private static Item resolveItemFromLastSnapshot(final AbstractFurnaceBlockEntity furnace, final int lastItemId)
    {
        final ItemStack cur = furnace.getItem(2);
        if (!cur.isEmpty()) return cur.getItem();

        // If completely empty and changed, we cannot reliably reconstruct item from identity hash.
        return null;
    }

    /**
     * Determines whether the given furnace should be tracked now.
     * Quick check to avoid recipe manager calls.
     */
    private static boolean shouldTrackNow(final ServerLevel level, final AbstractFurnaceBlockEntity furnace)
    {
        final BlockPos furnacePos = furnace.getBlockPos();
        if (furnacePos == null) return false;

        final BlockState bs = level.getBlockState(furnacePos);
        final boolean lit =
                bs.hasProperty(NullnessBridge.assumeNonnull(AbstractFurnaceBlock.LIT)) &&
                bs.getValue(NullnessBridge.assumeNonnull(AbstractFurnaceBlock.LIT));

        if (lit) return true;

        // If result slot has something, we still want to track (extraction can happen while unlit).
        final ItemStack result = furnace.getItem(2);
        if (!result.isEmpty()) return true;

        // If input exists, furnace might start soon; optionally track it briefly.
        final ItemStack input = furnace.getItem(0);
        return !input.isEmpty();
    }

    /**
     * Maps the BE type to the appropriate cooking recipe type.
     */
    private static RecipeType<?> recipeTypeFor(final AbstractFurnaceBlockEntity furnace)
    {
        if (furnace instanceof BlastFurnaceBlockEntity) return RecipeType.BLASTING;
        if (furnace instanceof SmokerBlockEntity) return RecipeType.SMOKING;
        return RecipeType.SMELTING;
    }

    private static <T extends AbstractCookingRecipe> Optional<RecipeHolder<T>> findCookingRecipe(
            final ServerLevel level,
            final RecipeType<T> recipeType,
            final ItemStack input
    )
    {
        if (input == null || input.isEmpty() || recipeType == null) return Optional.empty();

        // 1.21+: furnace-like recipes use RecipeInput; SingleRecipeInput is the standard wrapper.
        return level.getRecipeManager().getRecipeFor(recipeType, new SingleRecipeInput(input), level);
    }

    private static int defaultCookTimeFor(final RecipeType<?> type)
    {
        // Vanilla-ish defaults: smelting ~200, smoking/blasting ~100.
        if (type == RecipeType.SMOKING) return 100;
        if (type == RecipeType.BLASTING) return 100;
        return 200;
    }

    private static void removeTracked(final LevelState st, final long key)
    {
        st.active.remove(key);
        cleanupMaps(st, key);
    }

    private static void cleanupMaps(final LevelState st, final long key)
    {
        st.lastResultCount.remove(key);
        st.lastResultItemId.remove(key);
        st.lastInputCount.remove(key);
        st.lastInputItemId.remove(key);
        st.lastActiveTick.remove(key);
    }
}