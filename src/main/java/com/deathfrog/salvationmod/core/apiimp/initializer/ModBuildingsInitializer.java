package com.deathfrog.salvationmod.core.apiimp.initializer;

import com.deathfrog.salvationmod.ModBlocks;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.api.colony.buildings.ModBuildings;
import com.deathfrog.salvationmod.client.buildingviews.BuildingEnvironmentalLabView;
import com.deathfrog.salvationmod.core.colony.buildings.BuildingEnvironmentalLab;
import com.deathfrog.salvationmod.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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

            // Using the a deferred holder is used for hut blocks to ensure they are not null at this point.
            BuildingEntry.Builder labBuilder = new BuildingEntry.Builder();
            labBuilder.setBuildingBlock(ModBlocks.blockHutEnvironmentalLab.get());
            labBuilder.setBuildingProducer(BuildingEnvironmentalLab::new);
            labBuilder.setBuildingViewProducer(() -> BuildingEnvironmentalLabView::new);
            labBuilder.setRegistryName(ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, ModBuildings.ENVIRONMENTAL_LAB));
            labBuilder.addBuildingModuleProducer(BuildingModules.LABTECH_WORK);
            labBuilder.addBuildingModuleProducer(BuildingModules.LABTECH_BEACON_MODULE);
            labBuilder.addBuildingModuleProducer(BuildingModules.LABTECH_CRAFT);
            labBuilder.addBuildingModuleProducer(com.minecolonies.core.colony.buildings.modules.BuildingModules.FURNACE);
            labBuilder.addBuildingModuleProducer(com.minecolonies.core.colony.buildings.modules.BuildingModules.ITEMLIST_FUEL);
            labBuilder.addBuildingModuleProducer(com.minecolonies.core.colony.buildings.modules.BuildingModules.CRAFT_TASK_VIEW);
            labBuilder.addBuildingModuleProducer(com.minecolonies.core.colony.buildings.modules.BuildingModules.STATS_MODULE);

            ModBuildings.environmentalLab = labBuilder.createBuildingEntry();

            registerBuilding(event, ModBuildings.environmentalLab);
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

        final DeferredHolder<BuildingEntry,BuildingEntry> townHall = com.minecolonies.api.colony.buildings.ModBuildings.townHall;

        com.deathfrog.mctradepost.apiimp.initializer.ModBuildingsInitializer.injectModuleToBuilding(BuildingModules.REFUGEE_MODULE, townHall, 2);
    }

    /**
     * Registers a building with the given building entry to the given RegisterEvent.
     *
     * @param event The event to register the building with.
     * @param buildingEntry The building entry to register.
     */
    protected static void registerBuilding(RegisterEvent event, BuildingEntry buildingEntry)
    {
            ResourceKey<Registry<BuildingEntry>> buildingsRegistry = CommonMinecoloniesAPIImpl.BUILDINGS;

            if (buildingsRegistry == null)
            {
                throw new IllegalStateException("Building registry is null while attempting to register Trade Post buildings.");
            }

            ResourceLocation registryName = buildingEntry.getRegistryName();

            if (registryName == null)
            {
                throw new IllegalStateException("Attempting to register a building with no registry name.");
            }

            event.register(buildingsRegistry, registry -> {
                registry.register(registryName, buildingEntry);
            });
    }
}
