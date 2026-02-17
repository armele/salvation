package com.deathfrog.salvationmod.core.blockentity;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntities;
import com.deathfrog.salvationmod.core.blocks.PurificationBeaconCoreBlock;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Purification Beacon Core BE
 *
 * Assumptions:
 * - The core block is the bottom-center of a 3x3x5 multiblock (x/z radius 1, y height 5).
 * - The broadcast position is the top-center block: corePos.above(4).
 * - Only the core has a BE; the rest are normal blocks.
 */
public final class PurificationBeaconCoreBlockEntity extends BlockEntity
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    // -----------------------------
    // Tunables (safe defaults)
    // -----------------------------

    /** 
     * How often to pulse purification when active (ticks).  
     * Default: 6 times per day.
     */
    private static final int DEFAULT_DAY_LENGTH = 24000;
    private static final int DEFAULT_PULSES_PER_DAY = 6;

    /** How often to revalidate structure even if nothing changed (ticks). */
    private static final int DEFAULT_REVALIDATE_INTERVAL_TICKS = 200;

    /** Height of the pillar. */
    private static final int PILLAR_HEIGHT = 4;

    // -----------------------------
    // Persistent-ish state
    // -----------------------------

    private boolean structureValid = false;

    /** Set when a neighbor change suggests we should re-check structure soon. */
    private boolean validationRequested = true;

    /** Countdown until next periodic revalidation. */
    private int revalidateCountdown = DEFAULT_REVALIDATE_INTERVAL_TICKS;

    /** Countdown until next pulse (only decremented when active). */
    int pulseCountdown = DEFAULT_DAY_LENGTH / DEFAULT_PULSES_PER_DAY;

    public PurificationBeaconCoreBlockEntity(final BlockPos pos, final BlockState state)
    {
        super(SalvationTileEntities.PURIFICATION_BEACON_CORE.get(), pos, state);
    }

    // ---------------------------------------------------------------------
    // External hook: call this from your block when neighbors change.
    // ---------------------------------------------------------------------
    public void requestValidation()
    {
        this.validationRequested = true;
        this.setChanged();
    }

    // ---------------------------------------------------------------------
    // Ticking
    // ---------------------------------------------------------------------
    public static <T extends BlockEntity> BlockEntityTicker<T> createTicker()
    {
        return (level, pos, state, be) ->
        {
            if (be instanceof PurificationBeaconCoreBlockEntity core)
            {
                core.tick(level, pos, state);
            }
        };
    }

    /**
     * Server-side ticking for the Purification Beacon Core block entity.
     *
     * This method is called on the server every tick, and is responsible for:
     * - Periodically revalidating the structure of the beacon.
     * - Requesting revalidation when the beacon's neighbors change.
     * - Sending out a pulse of purification to the surrounding chunks when the beacon is active.
     */
    private void tick(final Level level, final @Nonnull BlockPos pos, final BlockState state)
    {
        if (level.isClientSide())
        {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        // If the core block got replaced, do nothing (defensive)
        if (!state.is(NullnessBridge.assumeNonnull(ModBlocks.PURIFICATION_BEACON_CORE.get())))
        {
            return;
        }

        final IColony colony = IColonyManager.getInstance().getIColony(serverLevel, pos);

        // Purification beacons only work when placed within the bounds of the colony.
        if (colony == null)
        {
            setLit(false);
            return;
        }

        // TODO: Add recharge hook here (Lab Tech AI)
        // Periodic revalidation, plus "requested" revalidation
        if (validationRequested || --revalidateCountdown <= 0)
        {
            revalidateCountdown = DEFAULT_REVALIDATE_INTERVAL_TICKS;
            validationRequested = false;

            boolean newValid = validateStructure(serverLevel, pos);

            if (newValid != structureValid)
            {
                structureValid = newValid;

                // Reset pulse timer when toggling state (optional, but tidy)
                pulseCountdown = calcPulseCountdown();

                this.setChanged();
            }
        }

        if (!structureValid)
        {
            setLit(false);
            return;
        }

        // Countdown to pulse
        if (--pulseCountdown > 0)
        {
            setLit(false);
            return;
        }

        setLit(true);

        final ChunkPos origin = new ChunkPos(pos);
        pulseCountdown = calcPulseCountdown();
        int radius = 1;
        int corruptionAmount = -10; // this means purification!

        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                final ChunkPos cp = new ChunkPos(origin.x + dx, origin.z + dz);

                // Choose where in the chunk you want to “apply” the purification.
                // Using chunk center at the beacon Y is a nice, stable choice.
                final BlockPos applyPos = chunkCenterAtY(cp, pos.getY());

                TraceUtils.dynamicTrace(ModCommands.TRACE_CORRUPTION, () -> LOGGER.info("Beacon pulse of strength {} at {} from origin: {}", corruptionAmount, applyPos, pos));

                SalvationManager.recordCorruption(
                    serverLevel,
                    ProgressionSource.COLONY,
                    applyPos,
                    corruptionAmount
                );
            }
        }

        this.setChanged();
    }

    private int calcPulseCountdown() 
    {
        int pulsesPerDay = DEFAULT_PULSES_PER_DAY;

        return (int) (DEFAULT_DAY_LENGTH / pulsesPerDay);
    }


    private static BlockPos chunkCenterAtY(final ChunkPos cp, final int y)
    {
        // Chunk center: (x*16 + 8, z*16 + 8)
        return new BlockPos(cp.getMinBlockX() + 8, y, cp.getMinBlockZ() + 8);
    }

    // ---------------------------------------------------------------------
    // Structure Validation
    // ---------------------------------------------------------------------

    /**
     * Validates a 3x3x5 structure where:
     * - Core is at (0,0,0)
     * - Top center is at (0,4,0)
     * - Everything other than core/cap must be frame (or you can widen this rule).
     */
    private boolean validateStructure(final ServerLevel level, final @Nonnull BlockPos corePos)
    {

        final BlockState state = level.getBlockState(corePos);

        boolean valid = state.is(NullnessBridge.assumeNonnull(ModBlocks.PURIFICATION_BEACON_CORE.get()));

        if (!valid) return false;

        BlockPos checkPos = corePos.below();

        for (int i = 0; i < PILLAR_HEIGHT; i++) 
        {
            if (checkPos == null) return false;

            if (!level.getBlockState(checkPos).is(ModTags.Blocks.BEACON_POST))
            {
                return false;
            }

            checkPos = checkPos.below();
        }

        return true;
    }

    // ---------------------------------------------------------------------
    // Persistence (optional but useful for debugging)
    // ---------------------------------------------------------------------

    @Override
    protected void saveAdditional(final @Nonnull CompoundTag tag, final @Nonnull HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);

        tag.putBoolean("StructureValid", structureValid);
        tag.putBoolean("ValidationRequested", validationRequested);
        tag.putInt("RevalidateCountdown", revalidateCountdown);
        tag.putInt("PulseCountdown", pulseCountdown);
    }

    @Override
    public void loadAdditional(final @Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);

        structureValid = tag.getBoolean("StructureValid");
        validationRequested = tag.getBoolean("ValidationRequested");
        revalidateCountdown = tag.contains("RevalidateCountdown") ? tag.getInt("RevalidateCountdown") : DEFAULT_REVALIDATE_INTERVAL_TICKS;
        pulseCountdown = tag.contains("PulseCountdown") ? tag.getInt("PulseCountdown") : calcPulseCountdown();

        // Defensive clamp
        revalidateCountdown = Mth.clamp(revalidateCountdown, 1, DEFAULT_REVALIDATE_INTERVAL_TICKS);
        pulseCountdown = Mth.clamp(pulseCountdown, 1, (int) (DEFAULT_DAY_LENGTH / DEFAULT_PULSES_PER_DAY));
    }

    /**
     * Set the lit state of the beacon core block.
     * <p>
     * This method is a no-op if the block is not a beacon core block, or if the block is already in the desired state.
     * <p>
     * This method will only succeed if the block is a beacon core block and the level is not null.
     * <p>
     * This method will not send a block update packet to the client.
     * <p>
     * @param lit the desired lit state of the beacon core block
     */
    public void setLit(boolean lit)
    {
        Level localLevel = level;
        BlockPos position = worldPosition;

        if (localLevel == null || position == null) return;

        BlockState state = localLevel.getBlockState(position);

        if (state == null) return;

        BooleanProperty litProp = NullnessBridge.assumeNonnull(PurificationBeaconCoreBlock.LIT);

        if (!state.is(NullnessBridge.assumeNonnull(ModBlocks.PURIFICATION_BEACON_CORE.get()))) return;

        if (state.hasProperty(litProp) && state.getValue(litProp) != lit)
        {
            BlockState newState = state.setValue(litProp, lit);
            
            if (newState != null)
            {
                localLevel.setBlock(position, newState, 3);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Accessors (optional)
    // ---------------------------------------------------------------------

    public boolean isStructureValid()
    {
        return structureValid;
    }
}