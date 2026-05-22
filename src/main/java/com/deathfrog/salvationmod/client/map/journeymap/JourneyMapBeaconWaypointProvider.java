package com.deathfrog.salvationmod.client.map.journeymap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.deathfrog.salvationmod.SalvationMod;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * JourneyMap-backed temporary waypoint support for beacon highlighting.
 */
public final class JourneyMapBeaconWaypointProvider
{
    private static final String WAYPOINT_NAME = "Purification Beacon";
    private static final int WAYPOINT_COLOR = 0x55ddff;

    private static IClientAPI api;
    private static Waypoint activeWaypoint;

    private JourneyMapBeaconWaypointProvider()
    {
    }

    /**
     * Store JourneyMap's client API instance.
     *
     * @param clientApi JourneyMap client API
     */
    public static void initialize(final IClientAPI clientApi)
    {
        api = clientApi;
    }

    /**
     * Create or replace the active temporary beacon waypoint.
     *
     * @param beaconPos beacon position
     * @param dimension beacon dimension
     * @return true if the waypoint was created
     */
    public static boolean showBeaconWaypoint(final BlockPos beaconPos, final ResourceKey<Level> dimension)
    {
        if (api == null || beaconPos == null || dimension == null)
        {
            SalvationMod.LOGGER.info(
                "JourneyMap beacon waypoint provider is not ready. apiReady={}, beaconPos={}, dimension={}",
                api != null,
                beaconPos,
                dimension);
            return false;
        }

        removeActiveWaypoint();

        try
        {
            final Waypoint waypoint = createWaypoint(
                SalvationMod.MODID,
                beaconPos,
                WAYPOINT_NAME,
                dimension.location().toString(),
                false);
            waypoint.setColor(WAYPOINT_COLOR);
            waypoint.setShowDeviation(true);

            api.addWaypoint(SalvationMod.MODID, waypoint);
            activeWaypoint = waypoint;
            return true;
        }
        catch (final Throwable throwable)
        {
            SalvationMod.LOGGER.warn("Unable to add JourneyMap beacon waypoint.", throwable);
            activeWaypoint = null;
            return false;
        }
    }

    /**
     * Create a waypoint against both older and newer JourneyMap 2.x API snapshots.
     *
     * @param modId mod id owning the waypoint
     * @param beaconPos beacon position
     * @param name waypoint name
     * @param dimension dimension id
     * @param persistent true when JourneyMap should save the waypoint to disk
     * @return created waypoint
     * @throws ReflectiveOperationException if neither supported factory method is available
     */
    private static Waypoint createWaypoint(
        final String modId,
        final BlockPos beaconPos,
        final String name,
        final String dimension,
        final boolean persistent) throws ReflectiveOperationException
    {
        try
        {
            return invokeWaypointFactory("createWaypoint", modId, beaconPos, name, dimension, persistent);
        }
        catch (final NoSuchMethodException exception)
        {
            return invokeWaypointFactory("createClientWaypoint", modId, beaconPos, name, dimension, persistent);
        }
    }

    /**
     * Invoke the selected JourneyMap waypoint factory method.
     *
     * @param methodName factory method name
     * @param modId mod id owning the waypoint
     * @param beaconPos beacon position
     * @param name waypoint name
     * @param dimension dimension id
     * @param persistent true when JourneyMap should save the waypoint to disk
     * @return created waypoint
     * @throws ReflectiveOperationException if the factory method fails
     */
    private static Waypoint invokeWaypointFactory(
        final String methodName,
        final String modId,
        final BlockPos beaconPos,
        final String name,
        final String dimension,
        final boolean persistent) throws ReflectiveOperationException
    {
        final Method method = WaypointFactory.class.getMethod(
            methodName,
            String.class,
            BlockPos.class,
            String.class,
            String.class,
            boolean.class);

        try
        {
            return (Waypoint) method.invoke(null, modId, beaconPos, name, dimension, persistent);
        }
        catch (final InvocationTargetException exception)
        {
            final Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException)
            {
                throw runtimeException;
            }
            if (cause instanceof Error error)
            {
                throw error;
            }
            throw exception;
        }
    }

    /**
     * Remove the active temporary beacon waypoint.
     */
    public static void removeActiveWaypoint()
    {
        Waypoint localWaypoint = activeWaypoint;

        if (api == null || localWaypoint == null)
        {
            activeWaypoint = null;
            return;
        }

        try
        {
            api.removeWaypoint(SalvationMod.MODID, localWaypoint);
        }
        catch (final Throwable throwable)
        {
            SalvationMod.LOGGER.warn("Unable to remove JourneyMap beacon waypoint.", throwable);
        }
        finally
        {
            activeWaypoint = null;
        }
    }
}
