package com.deathfrog.salvationmod.client.map;

import java.lang.reflect.Method;

import com.deathfrog.salvationmod.SalvationMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Optional map waypoint bridge for highlighted beacons.
 */
public final class BeaconMapWaypointBridge
{
    private static final String JOURNEYMAP_PROVIDER = "com.deathfrog.salvationmod.client.map.journeymap.JourneyMapBeaconWaypointProvider";
    private static final int REMOVE_DISTANCE_BLOCKS = 3;
    private static final int CHECK_INTERVAL_TICKS = 10;

    private static BlockPos activeWaypointPos;
    private static ResourceKey<Level> activeWaypointDimension;
    private static int ticksUntilCheck;

    private BeaconMapWaypointBridge()
    {
    }

    /**
     * Create or replace the active temporary beacon waypoint if a supported map is available.
     *
     * @param beaconPos beacon position
     */
    @SuppressWarnings("null")
    public static void showBeaconWaypoint(final BlockPos beaconPos)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        if (beaconPos == null || minecraft.level == null)
        {
            return;
        }

        clearActiveWaypoint();

        if (tryJourneyMapShow(beaconPos, minecraft.level.dimension()))
        {
            SalvationMod.LOGGER.info("JourneyMap beacon waypoint activated.");
            activeWaypointPos = beaconPos;
            activeWaypointDimension = minecraft.level.dimension();
            ticksUntilCheck = CHECK_INTERVAL_TICKS;
        }
        else
        {
            // SalvationMod.LOGGER.info("Unable to activate JourneyMap beacon waypoint.");
        }
    }

    /**
     * Remove the waypoint once the player reaches it.
     *
     * @param event client tick event
     */
    public static void onClientTick(final ClientTickEvent.Post event)
    {
        BlockPos localWaypointPos = activeWaypointPos;

        if (localWaypointPos == null)
        {
            return;
        }

        if (ticksUntilCheck-- > 0)
        {
            return;
        }
        ticksUntilCheck = CHECK_INTERVAL_TICKS;

        final Minecraft minecraft = Minecraft.getInstance();
        ClientLevel clientLevel = minecraft.level;
        final LocalPlayer player = minecraft.player;
        
        if (player == null || clientLevel == null)
        {
            clearActiveWaypoint();
            return;
        }

        if (!clientLevel.dimension().equals(activeWaypointDimension)
            || player.blockPosition().distSqr(localWaypointPos) <= REMOVE_DISTANCE_BLOCKS * REMOVE_DISTANCE_BLOCKS)
        {
            clearActiveWaypoint();
        }
    }

    /**
     * Remove the active temporary waypoint from any supported map.
     */
    private static void clearActiveWaypoint()
    {
        activeWaypointPos = null;
        activeWaypointDimension = null;
        tryJourneyMapRemove();
    }

    /**
     * Try to create a JourneyMap waypoint without hard-loading JourneyMap API classes when JourneyMap is absent.
     *
     * @param beaconPos beacon position
     * @param dimension current dimension
     * @return true if JourneyMap accepted the waypoint
     */
    private static boolean tryJourneyMapShow(final BlockPos beaconPos, final ResourceKey<Level> dimension)
    {
        if (!ModList.get().isLoaded("journeymap"))
        {
            // SalvationMod.LOGGER.info("JourneyMap not installed.");
            return false;
        }

        try
        {
            final Class<?> provider = Class.forName(JOURNEYMAP_PROVIDER);
            final Method show = provider.getMethod("showBeaconWaypoint", BlockPos.class, ResourceKey.class);
            return Boolean.TRUE.equals(show.invoke(null, beaconPos, dimension));
        }
        catch (final ReflectiveOperationException | LinkageError exception)
        {
            SalvationMod.LOGGER.warn("JourneyMap beacon waypoint integration is unavailable.", exception);
            return false;
        }
    }

    /**
     * Try to remove the active JourneyMap waypoint without hard-loading JourneyMap API classes when JourneyMap is absent.
     */
    private static void tryJourneyMapRemove()
    {
        if (!ModList.get().isLoaded("journeymap"))
        {
            // SalvationMod.LOGGER.info("JourneyMap not installed.");
            return;
        }

        try
        {
            final Class<?> provider = Class.forName(JOURNEYMAP_PROVIDER);
            provider.getMethod("removeActiveWaypoint").invoke(null);
        }
        catch (final ReflectiveOperationException | LinkageError exception)
        {
            SalvationMod.LOGGER.warn("JourneyMap beacon waypoint cleanup is unavailable.", exception);
        }
    }
}
