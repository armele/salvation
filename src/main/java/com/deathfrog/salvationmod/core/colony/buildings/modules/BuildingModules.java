package com.deathfrog.salvationmod.core.colony.buildings.modules;

import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingEconModule;
import com.deathfrog.salvationmod.core.colony.buildings.moduleviews.SpecialResearchModuleView;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

public class BuildingModules 
{
    public static final BuildingEntry.ModuleProducer<BuildingEconModule, SpecialResearchModuleView> SPECIAL_RESEARCH_MODULE = new BuildingEntry.ModuleProducer<>(
      "special_research_module", BuildingSpecialResearchModule::new,
      () -> SpecialResearchModuleView::new);    
}
