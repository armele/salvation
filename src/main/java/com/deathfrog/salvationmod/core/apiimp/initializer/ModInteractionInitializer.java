package com.deathfrog.salvationmod.core.apiimp.initializer;

import com.deathfrog.salvationmod.core.colony.buildings.BuildingEnvironmentalLab;
import com.deathfrog.salvationmod.core.colony.jobs.JobLabTech;
import com.deathfrog.salvationmod.core.engine.CorruptionStage;
import com.minecolonies.api.colony.interactionhandling.InteractionValidatorRegistry;
import net.minecraft.network.chat.Component;
import static com.minecolonies.api.util.constant.TranslationConstants.FURNACE_USER_NO_FUEL;
import static com.deathfrog.salvationmod.core.entity.ai.workers.crafting.EntityAIWorkLabTech.LABTECH_NO_FURNACES;
import static com.deathfrog.salvationmod.core.entity.ai.workers.crafting.EntityAIWorkLabTech.LABTECH_NOTHING_TO_PURIFY;

public class ModInteractionInitializer
{
  public static final String CITIZEN_MESSGE_BASE = "com.salvation.citizenmessage.stage";
  public static final float chanceOfCitizenMessage = .01f;

  public static void injectInteractionHandlers()
  {
    InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(FURNACE_USER_NO_FUEL),
      citizen -> citizen.getWorkBuilding() instanceof BuildingEnvironmentalLab &&
        citizen.getJob(JobLabTech.class).checkForMissingFuelInteraction());

    InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(LABTECH_NO_FURNACES),
      citizen -> citizen.getWorkBuilding() instanceof BuildingEnvironmentalLab &&
        citizen.getJob(JobLabTech.class).checkForMissingFurnaceInteraction());

    InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(LABTECH_NOTHING_TO_PURIFY),
      citizen -> citizen.getWorkBuilding() instanceof BuildingEnvironmentalLab &&
        citizen.getJob(JobLabTech.class).checkForNothingToPurifyInteraction());

    for (CorruptionStage stage : CorruptionStage.values())
    {
      for (int i = 0; i < 10; i++)
      {
        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(CITIZEN_MESSGE_BASE + stage.ordinal() + "." + i),
          citizen -> citizen != null && citizen.getRandom().nextFloat(1) <= chanceOfCitizenMessage);
      }
    }
  }
}
