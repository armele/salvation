package com.deathfrog.salvationmod.core.colony.buildings.modules;

import com.deathfrog.salvationmod.core.colony.buildings.moduleviews.SpecialResearchModuleView;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

public class BuildingModules 
{
    public static final BuildingEntry.ModuleProducer<BuildingSpecialResearchModule, SpecialResearchModuleView> SPECIAL_RESEARCH_MODULE = new BuildingEntry.ModuleProducer<>(
      "special_research_module", BuildingSpecialResearchModule::new,
      () -> SpecialResearchModuleView::new);    

    public static final BuildingEntry.ModuleProducer<BuildingRefugeeModule, IBuildingModuleView> REFUGEE_MODULE = new BuildingEntry.ModuleProducer<>("refugee_module", BuildingRefugeeModule::new, null);
}
