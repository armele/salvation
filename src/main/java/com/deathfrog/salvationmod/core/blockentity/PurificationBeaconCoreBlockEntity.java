package com.deathfrog.salvationmod.core.blockentity;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntities;
import com.deathfrog.salvationmod.core.blocks.PurificationBeaconCoreBlock;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
    // -----------------------------
    // Tunables (safe defaults)
    // -----------------------------

    /** How often to pulse purification when active (ticks). 200 = 10 seconds. */
    private static final int DEFAULT_PULSE_INTERVAL_TICKS = 200;

    /** How often to revalidate structure even if nothing changed (ticks). */
    private static final int DEFAULT_REVALIDATE_INTERVAL_TICKS = 200;

    /** Height of the structure in blocks, including the core layer. */
    private static final int STRUCT_HEIGHT = 5;

    /** Half-width/half-depth of the structure (3x3 => radius 1). */
    private static final int STRUCT_RADIUS = 1;

    // -----------------------------
    // Persistent-ish state
    // -----------------------------

    private boolean structureValid = false;

    /** Set when a neighbor change suggests we should re-check structure soon. */
    private boolean validationRequested = true;

    /** Countdown until next periodic revalidation. */
    private int revalidateCountdown = DEFAULT_REVALIDATE_INTERVAL_TICKS;

    /** Countdown until next pulse (only decremented when active). */
    private int pulseCountdown = DEFAULT_PULSE_INTERVAL_TICKS;

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

    private void tick(final Level level, final BlockPos pos, final BlockState state)
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
                pulseCountdown = DEFAULT_PULSE_INTERVAL_TICKS;

                this.setChanged();
            }
        }

        if (!structureValid)
        {
            return;
        }

        final IColony colony = IColonyManager.getInstance().getIColony(serverLevel, pos);

        if (colony == null)
        {
            return;
        }

        // Countdown to pulse
        if (--pulseCountdown > 0)
        {
            return;
        }

        pulseCountdown = DEFAULT_PULSE_INTERVAL_TICKS;

        // --- Hook into your Salvation system here ---
        // SalvationManager.applyPurificationPulse(serverLevel, broadcastPos, colony);

        // If you want, emit a small server-side event/packet trigger here for client VFX.
        // spawnServerParticles(serverLevel, broadcastPos);

        this.setChanged();
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
    private boolean validateStructure(final ServerLevel level, final BlockPos corePos)
    {

        for (int dy = 0; dy < STRUCT_HEIGHT; dy++)
        {
            for (int dx = -STRUCT_RADIUS; dx <= STRUCT_RADIUS; dx++)
            {
                for (int dz = -STRUCT_RADIUS; dz <= STRUCT_RADIUS; dz++)
                {
                    final BlockPos checkPos = corePos.offset(dx, dy, dz);

                    if (checkPos == null) continue;

                    final BlockState s = level.getBlockState(checkPos);

                    if (!isValidStructureBlock(s, dx, dy, dz))
                    {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Rules:
     * - (0,0,0) must be the core block
     * - (0,4,0) must be the cap block (optional: allow frame too)
     * - Everything else must be the frame block
     *
     * Adapt this to allow different materials, tags, hollow interiors, etc.
     */
    private static boolean isValidStructureBlock(final BlockState state, final int dx, final int dy, final int dz)
    {
        // Core
        if (dx == 0 && dy == 0 && dz == 0)
        {
            return state.is(NullnessBridge.assumeNonnull(ModBlocks.PURIFICATION_BEACON_CORE.get()));
        }

        // TODO: Add other rules here

        return false;
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
        pulseCountdown = tag.contains("PulseCountdown") ? tag.getInt("PulseCountdown") : DEFAULT_PULSE_INTERVAL_TICKS;

        // Defensive clamp
        revalidateCountdown = Mth.clamp(revalidateCountdown, 1, DEFAULT_REVALIDATE_INTERVAL_TICKS);
        pulseCountdown = Mth.clamp(pulseCountdown, 1, DEFAULT_PULSE_INTERVAL_TICKS);
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