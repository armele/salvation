package com.deathfrog.salvationmod.client.map.journeymap;

import com.deathfrog.salvationmod.SalvationMod;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;

/**
 * Optional JourneyMap plugin entrypoint.
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
@SuppressWarnings("removal")
public class SalvationJourneyMapPlugin implements IClientPlugin
{
    @Override
    public String getModId()
    {
        return SalvationMod.MODID;
    }

    @Override
    public void initialize(final IClientAPI jmClientApi)
    {
        SalvationMod.LOGGER.info("Initializing Salvation JourneyMap integration.");
        JourneyMapBeaconWaypointProvider.initialize(jmClientApi);
    }
}
