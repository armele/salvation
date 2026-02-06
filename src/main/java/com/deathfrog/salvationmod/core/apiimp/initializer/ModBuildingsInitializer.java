package com.deathfrog.salvationmod.core.apiimp.initializer;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = SalvationMod.MODID)
public final class ModBuildingsInitializer
{
    private ModBuildingsInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModBuildingsInitializer but this is a Utility class.");
    }

    @SubscribeEvent
    public static void registerBuildings(RegisterEvent event) {
        if (event.getRegistryKey().equals(CommonMinecoloniesAPIImpl.BUILDINGS))
        {
            SalvationMod.LOGGER.info("Registering buildings.");
        }
    }

    /**
     * Extends the existing buildings with Trade Post specific modules
     * if they are not already present
     */
    public static void injectBuildingModules()
    {
            SalvationMod.LOGGER.info("Injecting building modules.");

        // Get the existing entry to extend
        final DeferredHolder<BuildingEntry,BuildingEntry> university = com.minecolonies.api.colony.buildings.ModBuildings.university;

        com.deathfrog.mctradepost.apiimp.initializer.ModBuildingsInitializer.injectModuleToBuilding(BuildingModules.SPECIAL_RESEARCH_MODULE, university, 2);
    }
}
