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
import com.deathfrog.salvationmod.ModItems;
import com.deathfrog.salvationmod.ModTags;
import com.deathfrog.salvationmod.client.menu.BeaconMenu;
import com.deathfrog.salvationmod.api.advancements.ModAdvancementTriggers;
import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntities;
import com.deathfrog.salvationmod.core.blocks.PurificationBeaconCoreBlock;
import com.deathfrog.salvationmod.core.engine.BlightSurfaceSystem;
import com.deathfrog.salvationmod.core.engine.EntityConversion;
import com.deathfrog.salvationmod.core.colony.SalvationColonyHandler;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData.ProgressionSource;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.util.AdvancementUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Containers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;

/**
 * Purification Beacon Core BE
 */
public final class PurificationBeaconCoreBlockEntity extends BlockEntity implements Container, MenuProvider
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final int SLOT_COUNT = 5;
    private static final int MAX_MODULE_STACK_SIZE = 1;
    private static final Component CONTAINER_TITLE = Component.translatable("container.salvation.purification_beacon");
    
    // -----------------------------
    // Tunables (safe defaults)
    // -----------------------------

    /** 
     * How often to pulse purification when active (ticks).  
     * Default: 6 times per day.
     */
    public static final int DEFAULT_DAY_LENGTH = 24000;
    public static final int DEFAULT_PULSES_PER_DAY = 6;
    private static final int EXTRACTION_ENTITY_FUEL_COST = 10;
    private static final int EXTRACTION_GRASS_FUEL_COST = 1;
    private static final int EXTRACTION_MAX_ENTITY_CONVERSIONS_PER_PULSE = 2;
    private static final int EXTRACTION_MAX_GRASS_REVERTS_PER_PULSE = 64;
    private static final int SOLAR_FUEL_INTERVAL_TICKS = 220;
    private static final int SOLAR_FUEL_PER_INTERVAL = 1;
    private static final int SOLAR_MAX_BUFFER = 200;

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
    private int solarChargeCountdown = SOLAR_FUEL_INTERVAL_TICKS;

    private int boostingFuel = 0;
    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, NullnessBridge.assumeNonnull(ItemStack.EMPTY));

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

    /**
     * Called when the block entity is first loaded into the world.
     *
     * This method is responsible for registering the beacon with the colony.
     * If the registration fails (for example, if the beacon is not part of a
     * valid colony), the beacon will not function.
     */
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

        boolean enabled = colony.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_ENABLE_BEACONS) > 0;

        // Colony does not have becons enabled
        if (!enabled)
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

                    AdvancementUtils.TriggerAdvancementPlayersForColony(colony,
                            player -> {
                                if (player != null)
                                {
                                    ModAdvancementTriggers.BEACON_CONSTRUCTED.get().trigger(player);
                                }
                            });
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

        tickSolarUpgrade(serverLevel, pos);
        setLit(boostingFuel > 0);

        // Countdown to pulse
        if (--pulseCountdown > 0)
        {
            return;
        }

        final ChunkPos origin = new ChunkPos(pos);
        pulseCountdown = calcPulseCountdown();

        double corruptionAmount = -10.0; // Negative means purification!

        double range = colony.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_BEACON_RANGE);
        double power = colony.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_BEACON_POWER);

        final int radius = 1 + (int) range;
        corruptionAmount = corruptionAmount * (1 + power);

        // Boosting fuel: when unfueled the beacon runs at half power.
        if (boostingFuel <= 0)
        {
            corruptionAmount = corruptionAmount / 2;
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
                
                TraceUtils.dynamicTrace(ModCommands.TRACE_BEACON, () -> LOGGER.info("Beacon pulse of strength {} at {} from origin: {} with range {}", finalCorruptionAmount, applyPos, pos, radius));

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

        if (hasExtractionUpgrade())
        {
            applyExtractionUpgrade(serverLevel, pos, origin, radius);
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
    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(final @Nonnull CompoundTag tag, final @Nonnull HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);

        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putBoolean("StructureValid", structureValid);
        tag.putBoolean("ValidationRequested", validationRequested);
        tag.putInt("RevalidateCountdown", revalidateCountdown);
        tag.putInt("PulseCountdown", pulseCountdown);
        tag.putInt("SolarChargeCountdown", solarChargeCountdown);
        tag.putInt("BoostingFuel", boostingFuel);
    }

    /**
     * Deserializes the block entity's state from the given compound tag.
     * This method is responsible for deserializing the block entity's state from the given compound tag.
     * It will read the block entity's items, structure validation state, validation request state, revalidation countdown, pulse countdown, and boosting fuel from the compound tag and store them in the block entity's state.
     * The block will then continue deserializing its state from the tag.
     */
    @SuppressWarnings("null")
    @Override
    public void loadAdditional(final @Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);

        items = NonNullList.withSize(SLOT_COUNT, NullnessBridge.assumeNonnull(ItemStack.EMPTY));
        ContainerHelper.loadAllItems(tag, items, registries);
        structureValid = tag.getBoolean("StructureValid");
        validationRequested = tag.getBoolean("ValidationRequested");
        revalidateCountdown = tag.contains("RevalidateCountdown") ? tag.getInt("RevalidateCountdown") : DEFAULT_REVALIDATE_INTERVAL_TICKS;
        pulseCountdown = tag.contains("PulseCountdown") ? tag.getInt("PulseCountdown") : calcPulseCountdown();
        solarChargeCountdown = tag.contains("SolarChargeCountdown") ? tag.getInt("SolarChargeCountdown") : SOLAR_FUEL_INTERVAL_TICKS;

        // Defensive clamp
        revalidateCountdown = Mth.clamp(revalidateCountdown, 1, DEFAULT_REVALIDATE_INTERVAL_TICKS);
        pulseCountdown = Mth.clamp(pulseCountdown, 1, (int) (DEFAULT_DAY_LENGTH / DEFAULT_PULSES_PER_DAY));
        solarChargeCountdown = Mth.clamp(solarChargeCountdown, 1, SOLAR_FUEL_INTERVAL_TICKS);

        boostingFuel = tag.contains("BoostingFuel") ? tag.getInt("BoostingFuel") : 0;
    }

    /**
     * Set the lit state of the beacon core block.
     * This method is a no-op if the block is not a beacon core block, or if the block is already in the desired state.
     * This method will only succeed if the block is a beacon core block and the level is not null.
     * This method will not send a block update packet to the client.
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
        final int previousFuel = this.boostingFuel;
        this.boostingFuel = boostingFuel;
        handleFuelStateTransition(previousFuel, this.boostingFuel);
        syncBeaconFuel();
        this.setChanged();
    }

    public int addBoostingFuel(int amtToAdd)
    {
        final int previousFuel = boostingFuel;
        boostingFuel += amtToAdd;
        handleFuelStateTransition(previousFuel, boostingFuel);
        syncBeaconFuel();
        this.setChanged();
        return boostingFuel;
    }

    private void handleFuelStateTransition(final int previousFuel, final int currentFuel)
    {
        if (previousFuel <= 0 && currentFuel > 0)
        {
            pulseCountdown = calcPulseCountdown();

            if (structureValid)
            {
                setLit(true);
            }
        }
    }

    private boolean hasExtractionUpgrade()
    {
        return hasInstalledItem(NullnessBridge.assumeNonnull(ModItems.BEACON_UPGRADE_EXTRACTION.get()));
    }

    private boolean hasSolarUpgrade()
    {
        return hasInstalledItem(NullnessBridge.assumeNonnull(ModItems.BEACON_UPGRADE_SOLAR.get()));
    }

    private boolean hasHarvestUpgrade()
    {
        return hasInstalledItem(NullnessBridge.assumeNonnull(ModItems.BEACON_UPGRADE_HARVEST.get()));
    }

    public static boolean isHarvestProtectionInRange(@Nonnull final ServerLevel level, @Nonnull final BlockPos pos)
    {
        final IColony colony = IColonyManager.getInstance().getIColony(level, pos);
        if (colony == null)
        {
            return false;
        }

        final double beaconsEnabled = colony.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_ENABLE_BEACONS);
        if (beaconsEnabled <= 0.0D)
        {
            return false;
        }

        final int radius = 1 + (int) colony.getResearchManager().getResearchEffects().getEffectStrength(SalvationColonyHandler.RESEARCH_BEACON_RANGE);
        final ChunkPos targetChunk = new ChunkPos(pos);

        for (final Beacon beaconInfo : getBeacons(colony))
        {
            if (beaconInfo == null || !beaconInfo.isValid())
            {
                continue;
            }

            final BlockPos beaconPos = beaconInfo.getPosition();
            if (beaconPos == null)
            {
                continue;
            }

            final BlockEntity blockEntity = level.getBlockEntity(beaconPos);
            if (!(blockEntity instanceof PurificationBeaconCoreBlockEntity beacon) || !beacon.hasHarvestUpgrade())
            {
                continue;
            }

            final ChunkPos beaconChunk = new ChunkPos(beaconPos);
            if (Math.abs(beaconChunk.x - targetChunk.x) <= radius && Math.abs(beaconChunk.z - targetChunk.z) <= radius)
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasInstalledItem(@Nonnull final net.minecraft.world.item.Item item)
    {
        for (final ItemStack stack : items)
        {
            if (stack.is(item))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Applies the extraction upgrade to the given beacon.
     * 
     * @param level
     * @param pos
     * @param origin
     * @param radius
     */
    private void applyExtractionUpgrade(@Nonnull final ServerLevel level, @Nonnull final BlockPos pos, @Nonnull final ChunkPos origin, final int radius)
    {
        if (boostingFuel <= 0)
        {
            return;
        }

        int entitiesConverted = 0;
        final int radiusBlocks = (radius * 16) + 8;
        final AABB searchBox = new AABB(
            pos.getX() - radiusBlocks,
            level.getMinBuildHeight(),
            pos.getZ() - radiusBlocks,
            pos.getX() + radiusBlocks + 1,
            level.getMaxBuildHeight(),
            pos.getZ() + radiusBlocks + 1
        );

        for (final LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, searchBox,
            living -> living != null && living.isAlive() && SalvationManager.isCorruptedEntity(living.getType())))
        {
            if (entitiesConverted >= EXTRACTION_MAX_ENTITY_CONVERSIONS_PER_PULSE || boostingFuel < EXTRACTION_ENTITY_FUEL_COST)
            {
                break;
            }

            if (EntityConversion.startConversion(level, entity, true))
            {
                boostingFuel -= EXTRACTION_ENTITY_FUEL_COST;
                entitiesConverted++;
            }
        }

        final int grassBudget = Math.min(boostingFuel / EXTRACTION_GRASS_FUEL_COST, EXTRACTION_MAX_GRASS_REVERTS_PER_PULSE);
        if (grassBudget > 0)
        {
            final int reverted = BlightSurfaceSystem.revertTrackedBlightAroundBeacon(level, origin, radius, grassBudget);
            boostingFuel -= (reverted * EXTRACTION_GRASS_FUEL_COST);
        }

        BlockPos localPos = worldPosition;

        if (localPos == null)
        {
            return;
        }

        final Beacon beacon = getBeaconAt(level, localPos);
        if (beacon != null)
        {
            beacon.setFuel(boostingFuel);
        }
    }

    /**
     * Ticks the solar upgrade for the Purification Beacon Core block entity.
     * This method is responsible for:
     * - Checking if the solar upgrade is installed.
     * - Checking if the Purification Beacon Core can harvest solar fuel at the given position.
     * - Checking if the Purification Beacon Core is already fully charged.
     * - If the above conditions are met, decrementing the solar charge countdown and
     *   recharging the Purification Beacon Core's boosting fuel if the countdown reaches 0.
     * @param level The current level
     * @param pos The position of the block entity
     */
    private void tickSolarUpgrade(@Nonnull final ServerLevel level, @Nonnull final BlockPos pos)
    {
        if (!hasSolarUpgrade())
        {
            solarChargeCountdown = SOLAR_FUEL_INTERVAL_TICKS;
            return;
        }

        if (!canHarvestSolarFuel(level, pos) || boostingFuel >= SOLAR_MAX_BUFFER)
        {
            solarChargeCountdown = SOLAR_FUEL_INTERVAL_TICKS;
            return;
        }

        if (--solarChargeCountdown <= 0)
        {
            solarChargeCountdown = SOLAR_FUEL_INTERVAL_TICKS;
            addBoostingFuel(SOLAR_FUEL_PER_INTERVAL);
        }
    }

    @SuppressWarnings("null")
    private boolean canHarvestSolarFuel(@Nonnull final ServerLevel level, @Nonnull final BlockPos pos)
    {
        return level.dimensionType().hasSkyLight()
            && level.canSeeSky(pos.above())
            && level.isDay()
            && !level.isRaining()
            && !level.isThundering();
    }

    /**
     * Synchronizes the boosting fuel of the beacon core block entity with the corresponding beacon object.
     * This method is a no-op if the level is not a server level or if the position is null.
     * This method will only succeed if the level is a server level and the position is not null.
     * This method will not send a block update packet to the client.
     */
    private void syncBeaconFuel()
    {
        Level localLevel = level;
        BlockPos localPos = worldPosition;

        if (!(localLevel instanceof ServerLevel serverLevel) || localPos == null)
        {
            return;
        }

        final Beacon beacon = getBeaconAt(serverLevel, localPos);
        if (beacon != null)
        {
            beacon.setFuel(boostingFuel);
        }
    }

    @Override
    public int getContainerSize()
    {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty()
    {
        for (final ItemStack stack : items)
        {
            if (!stack.isEmpty())
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the item at the given slot.
     * If the slot is out of range, returns an empty ItemStack.
     * 
     * @param slot The slot to get the item from
     * @return The item at the given slot, or an empty ItemStack if out of range
     */
    @Override
    public @Nonnull ItemStack getItem(final int slot)
    {
        if (slot < 0 || slot >= items.size())
        {
            return NullnessBridge.assumeNonnull(ItemStack.EMPTY);
        }

        return items.get(slot);
    }

    @SuppressWarnings("null")
    @Override
    public @Nonnull ItemStack removeItem(final int slot, final int amount)
    {
        final ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty())
        {
            this.setChanged();
        }

        return result;
    }

    @SuppressWarnings("null")
    @Override
    public @Nonnull ItemStack removeItemNoUpdate(final int slot)
    {
        return ContainerHelper.takeItem(items, slot);
    }

    /**
     * Sets the item at the given slot.
     * If the slot is out of range, or if the stack is not empty and cannot be placed in the given slot, this method does nothing.
     * If the stack is empty, it will clear the given slot.
     * If the stack is not empty, it will copy the given stack and set the given slot to the copied stack.
     * The copied stack will have its size limited to MAX_MODULE_STACK_SIZE.
     * This method will call setChanged() after modifying the slot.
     * 
     * @param slot The slot to set the item in
     * @param stack The item to set in the given slot
     */
    @Override
    public void setItem(final int slot, final @Nonnull ItemStack stack)
    {
        if (slot < 0 || slot >= items.size())
        {
            return;
        }

        if (!stack.isEmpty() && !canPlaceItem(slot, stack))
        {
            return;
        }

        items.set(slot, stack.copy());
        items.get(slot).limitSize(MAX_MODULE_STACK_SIZE);
        this.setChanged();
    }

    @Override
    public boolean stillValid(final @Nonnull Player player)
    {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(final int slot, final @Nonnull ItemStack stack)
    {
        return slot >= 0 && slot < SLOT_COUNT && stack.is(ModTags.Items.BEACON_MODULES);
    }

    @Override
    public int getMaxStackSize()
    {
        return MAX_MODULE_STACK_SIZE;
    }

    @Override
    public void clearContent()
    {
        items.clear();
        this.setChanged();
    }

    @Override
    public Component getDisplayName()
    {
        return CONTAINER_TITLE;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(final int id, final @Nonnull Inventory playerInventory, final @Nonnull Player player)
    {
        return new BeaconMenu(id, playerInventory, this);
    }

    /**
     * Drops all items in the container and clears its content.
     * If the level is null or is on the client side, this method does nothing.
     * If the world position is null, this method does nothing.
     * Otherwise, it calls Containers.dropContents to drop all items in the container
     * and then clears the container's content.
     */
    public void dropContents()
    {
        Level localLevel = level;

        if (localLevel == null || localLevel.isClientSide())
        {
            return;
        }

        BlockPos localPos = worldPosition;

        if (localPos == null)
        {
            return;
        }

        Containers.dropContents(localLevel, localPos, this);
        clearContent();
    }
}
