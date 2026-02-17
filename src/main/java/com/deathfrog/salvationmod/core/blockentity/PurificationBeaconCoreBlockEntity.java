package com.deathfrog.salvationmod.core.blockentity;

import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.ModCommands;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntities;
import com.deathfrog.salvationmod.core.blocks.PurificationBeaconCoreBlock;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
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
    public static final int DEFAULT_PULSES_PER_DAY = 6;

    /** How often to revalidate structure even if nothing changed (ticks). */
    private static final int DEFAULT_REVALIDATE_INTERVAL_TICKS = 200;

    /** Height of the pillar. */
    private static final int PILLAR_HEIGHT = 4;

    // -----------------------------
    // Persistent-ish state
    // -----------------------------

    private boolean structureValid = false;
    private boolean registered = false;

    /** Set when a neighbor change suggests we should re-check structure soon. */
    private boolean validationRequested = true;

    /** Countdown until next periodic revalidation. */
    private int revalidateCountdown = DEFAULT_REVALIDATE_INTERVAL_TICKS;

    /** Countdown until next pulse (only decremented when active). */
    private int pulseCountdown = DEFAULT_DAY_LENGTH / DEFAULT_PULSES_PER_DAY;

    private int boostingFuel = 0;

    private static Map<IColony, Map<BlockPos, Beacon>> colonyBeacons = new HashMap<>();

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

    @Override
    public void onLoad() 
    {
        super.onLoad();

        if (!(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        registered = tryRegisterBeacon(serverLevel, worldPosition);
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

        // Reregister if not registered.
        if (!registered)
        {
            registered = tryRegisterBeacon(serverLevel, pos);
        }

        final IColony colony = IColonyManager.getInstance().getIColony(serverLevel, pos);
        Beacon beacon = getBeaconAt(serverLevel, pos);

        // Purification beacons only work when placed within the bounds of the colony.
        if (colony == null || beacon == null)
        {
            setLit(false);
            return;
        }

        double enabled = colony.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_ENBABLE_BEACONS);

        // Colony does not have becons enabled
        if (enabled == 0)
        {
            setLit(false);
            return;
        }

        // Periodic revalidation, plus "requested" revalidation
        if (validationRequested || --revalidateCountdown <= 0)
        {
            revalidateCountdown = DEFAULT_REVALIDATE_INTERVAL_TICKS;
            validationRequested = false;

            boolean newValid = validateStructure(serverLevel, pos);

            TraceUtils.dynamicTrace(ModCommands.TRACE_BEACON, () -> LOGGER.info("Beacon validation at {}: {}", pos, newValid));

            if (newValid != structureValid)
            {
                structureValid = newValid;
                
                if (beacon != null)
                {
                    beacon.setValid(structureValid);
                }

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
            return;
        }

        final ChunkPos origin = new ChunkPos(pos);
        pulseCountdown = calcPulseCountdown();
        int radius = 1;
        double corruptionAmount = -10.0; // Negative means purification!

        double range = colony.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_BEACON_RANGE);
        double power = colony.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_BEACON_POWER);

        radius = radius + (int) range;
        corruptionAmount = corruptionAmount * (1 + power);

        // Boosting fuel: when unfueled the beacon runs at half power.
        if (boostingFuel <= 0)
        {
            setLit(false);
            corruptionAmount = corruptionAmount / 2;
        }
        else
        {
            setLit(true);
        }

        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                final ChunkPos cp = new ChunkPos(origin.x + dx, origin.z + dz);

                // Choose where in the chunk you want to “apply” the purification.
                // Using chunk center at the beacon Y is a nice, stable choice.
                final BlockPos applyPos = chunkCenterAtY(cp, pos.getY());

                final int finalCorruptionAmount = (int) corruptionAmount;
                TraceUtils.dynamicTrace(ModCommands.TRACE_BEACON, () -> LOGGER.info("Beacon pulse of strength {} at {} from origin: {}", finalCorruptionAmount, applyPos, pos));

                SalvationManager.recordCorruption(
                    serverLevel,
                    ProgressionSource.COLONY,
                    applyPos,
                    finalCorruptionAmount
                );

                if (boostingFuel > 0)
                {
                    // Consume boosting fuel
                    boostingFuel--;

                    if (beacon != null)
                    {
                        beacon.setFuel(boostingFuel);
                    }
                }
            }
        }

        this.setChanged();
    }

    /**
     * Attempt to register a beacon at the given position for the given colony.
     *
     * If the colony is null, this method returns false.
     * Otherwise, it adds the given position to the set of beacons for the given colony.
     *
     * @param level The level to register the beacon in.
     * @param pos The position of the beacon to register.
     * @return True if the beacon was successfully registered or is already registered, false otherwise.
     */
    private boolean tryRegisterBeacon(final ServerLevel level, final BlockPos pos)
    {
        final IColony colony = IColonyManager.getInstance().getIColony(level, pos);
        if (colony == null)
        {
            return false;
        }

        colonyBeacons.computeIfAbsent(colony, k -> new HashMap<>());

        if (!colonyBeacons.get(colony).containsKey(pos))
        {
            Beacon newBeacon = new Beacon(pos, structureValid, isLit(), boostingFuel);
            colonyBeacons.get(colony).put(pos, newBeacon);
            TraceUtils.dynamicTrace(ModCommands.TRACE_BEACON, () -> LOGGER.info("Beacon registration at {}: {}", pos, newBeacon));
        }

        return true;
    }

    /**
     * Get the set of block positions that are beacons for the given colony.
     * 
     * @param colony The colony to get the beacons for.
     * @return A set of block positions that are beacons for the given colony.
     */
    public static Set<Beacon> getBeacons(final IColony colony)
    {
        Map<BlockPos,Beacon> beacons = colonyBeacons.get(colony);

        if (beacons == null)
        {
            return Set.of();
        }

        return beacons.values().stream().collect(Collectors.toSet());
    }

    /**
     * Gets the beacon at the given position, if one exists.
     *
     * @param pos The position to check.
     * @return The beacon at the given position, or null if none exists.
     */
    public static @Nullable Beacon getBeaconAt(@Nonnull ServerLevel serverLevel, @Nonnull BlockPos pos)
    {
        IColony colony = IColonyManager.getInstance().getIColony(serverLevel, pos);
        if (colony == null)
        {
            return null;
        }

        Map<BlockPos, Beacon> beaconMap = colonyBeacons.get(colony);

        if (beaconMap == null)
        {
            return null;
        }

        Beacon beacon = beaconMap.get(pos);

        return beacon;
    }

    /**
     * Removes a beacon from the given colony.
     * 
     * @param colony The colony to clear the beacon from.
     * @param pos The block position of the beacon to clear.
     * @return True if the beacon was found and cleared, false otherwise.
     */
    public static boolean clearBeacon(final IColony colony, BlockPos pos)
    {
        boolean didClear = false;

        if (colonyBeacons.containsKey(colony))
        {
            Map<BlockPos, Beacon> beacons = colonyBeacons.get(colony);

            if (beacons.containsKey(pos))
            {
                beacons.remove(pos);
                didClear = true;
            }
        }

        return didClear;
    }

    /**
     * Calculate the number of pulse countdowns that should happen in a day,
     * taking into account the colony's research effect on the beacon's frequency.
     * <p>
     * This method first checks if the beacon is placed within a colony, and if so,
     * it adds the colony's research effect on the beacon's frequency to the default pulses per day.
     * Then, it returns the number of pulse countdowns that should happen in a day,
     * calculated by dividing the default day length by the number of pulses per day.
     * <p>
     * This method is used to calculate the pulse countdown for the purification beacon's purification broadcast.
     */
    private int calcPulseCountdown() 
    {
        int pulsesPerDay = DEFAULT_PULSES_PER_DAY;

        final IColony colony = IColonyManager.getInstance().getIColony(level, worldPosition);

        // Purification beacons only work when placed within the bounds of the colony.
        if (colony != null)
        {
            double extraPulses = colony.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_BEACON_FREQUENCY);
            pulsesPerDay = pulsesPerDay + (int) extraPulses;
        }

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
     * Validates the structure of the beacon.
     * The beacon core must be on top of a pillar of beacon posts (total height 5)
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
    // Persistence
    // ---------------------------------------------------------------------
    @Override
    protected void saveAdditional(final @Nonnull CompoundTag tag, final @Nonnull HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);

        tag.putBoolean("StructureValid", structureValid);
        tag.putBoolean("ValidationRequested", validationRequested);
        tag.putInt("RevalidateCountdown", revalidateCountdown);
        tag.putInt("PulseCountdown", pulseCountdown);
        tag.putInt("BoostingFuel", pulseCountdown);
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

        boostingFuel = tag.contains("BoostingFuel") ? tag.getInt("BoostingFuel") : 0;
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

        if (localLevel == null || position == null || (!(localLevel instanceof ServerLevel serverLevel))) return;

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
                Beacon beacon = getBeaconAt(serverLevel, position);

                if (beacon != null)
                {
                    beacon.setLit(lit);
                }
            }
        }
    }

    /**
     * Returns the lit state of the beacon core block. 
     * Defaults to false on any error path.
     */
    public boolean isLit()
    {
        Level localLevel = level;
        BlockPos position = worldPosition;

        if (localLevel == null || position == null || (!(localLevel instanceof ServerLevel))) return false;

        BlockState state = localLevel.getBlockState(position);

        if (state == null) return false;

        BooleanProperty litProp = NullnessBridge.assumeNonnull(PurificationBeaconCoreBlock.LIT);

        if (!state.is(NullnessBridge.assumeNonnull(ModBlocks.PURIFICATION_BEACON_CORE.get()))) return false;

        return state.getValue(litProp);
    }

    // ---------------------------------------------------------------------
    // Accessors (optional)
    // ---------------------------------------------------------------------

    public boolean isStructureValid()
    {
        return structureValid;
    }

    public int getBoostingFuel()
    {
        return boostingFuel;
    }

    public void setBoostingFuel(int boostingFuel)
    {
        this.boostingFuel = boostingFuel;
    }

    public int addBoostingFuel(int amtToAdd)
    {
        boostingFuel += amtToAdd;
        return boostingFuel;
    }
}
