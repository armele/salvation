package com.deathfrog.salvationmod.core.colony.buildings.modules;

import com.deathfrog.salvationmod.api.colony.buildings.jobs.ModJobs;
import com.deathfrog.salvationmod.core.colony.buildings.BuildingEnvironmentalLab;
import com.deathfrog.salvationmod.core.colony.buildings.moduleviews.LabBeaconModuleView;
import com.deathfrog.salvationmod.core.colony.buildings.moduleviews.SpecialResearchModuleView;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.CraftingWorkerBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;

public class BuildingModules 
{
    public static final BuildingEntry.ModuleProducer<BuildingSpecialResearchModule, SpecialResearchModuleView> SPECIAL_RESEARCH_MODULE = new BuildingEntry.ModuleProducer<>(
      "special_research_module", BuildingSpecialResearchModule::new,
      () -> SpecialResearchModuleView::new);    

    public static final BuildingEntry.ModuleProducer<BuildingRefugeeModule, IBuildingModuleView> REFUGEE_MODULE = new BuildingEntry.ModuleProducer<>("refugee_module", BuildingRefugeeModule::new, null);

    public static final BuildingEntry.ModuleProducer<LabBeaconModule, LabBeaconModuleView> LABTECH_BEACON_MODULE = new BuildingEntry.ModuleProducer<>(
      "labtech_beacon_module", LabBeaconModule::new,
      () -> LabBeaconModuleView::new);    

    /**
     * Craftsmanship
     */
    public static final BuildingEntry.ModuleProducer<CraftingWorkerBuildingModule,WorkerBuildingModuleView> LABTECH_WORK          =
      new BuildingEntry.ModuleProducer<>("labtech_work", () -> new CraftingWorkerBuildingModule(ModJobs.labtech.get(), Skill.Knowledge, Skill.Creativity, false, (b) -> 1),
        () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<BuildingEnvironmentalLab.CraftingModule,CraftingModuleView> LABTECH_CRAFT         =
      new BuildingEntry.ModuleProducer<>("labtech_craft", () -> new BuildingEnvironmentalLab.CraftingModule(ModJobs.labtech.get()), () -> CraftingModuleView::new);
}
