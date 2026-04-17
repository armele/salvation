package com.deathfrog.salvationmod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;


public class Config 
{
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    
    public static final ConfigValue<Boolean>  corruptionDisabled;
    public static final ModConfigSpec.ConfigValue<Integer> biomeMutationStage;
    public static final ModConfigSpec.ConfigValue<Integer> corruptedEntityAggroStage;
    public static final ModConfigSpec.ConfigValue<Integer> exteritioRaidCooldown;
    public static final ModConfigSpec.ConfigValue<Integer> researchCreditValue;
    public static final ModConfigSpec.ConfigValue<Integer> globalNotificationCooldown;
    public static final ModConfigSpec.ConfigValue<Integer> colonyNotificationCooldown;
    public static final ModConfigSpec.ConfigValue<Integer> colonyMitigationRollingDays;
    public static final ModConfigSpec.ConfigValue<Integer> citizenCommentFrequency;
    public static final ModConfigSpec.ConfigValue<Integer> labTechAnalysisValue;
    public static final ModConfigSpec.ConfigValue<Integer> baseBeaconPower;

    static {
        BUILDER.push("engine");

        corruptionDisabled = BUILDER
            .comment("Disable the corruption system.")
            .define("corruptionDisabled", false);
        biomeMutationStage = BUILDER.comment("At what stage do biomes start having a chance to mutate? (-1 disables)").defineInRange("biomeMutationStage", 4, -1, 6);
        BUILDER.pop();

        // Research
        BUILDER.push("research");
        researchCreditValue = BUILDER.comment("What is the value of a Research Credit (ƒ)?").define("researchCreditValue", 100);
        labTechAnalysisValue = BUILDER.comment("How many Research Credit (ƒ) are earned from each corrupted item purification?").defineInRange("labTechAnalysisValue", 2, 1, 5);
        BUILDER.pop();

        // Combat
        BUILDER.push("combat");
        corruptedEntityAggroStage = BUILDER.comment("At what corruption stage will corrupted entities start to attack?").define("corruptedEntityAggroStage", 3);
        exteritioRaidCooldown = BUILDER.comment("What base number of days between possible Exteritio raids? -1 disables. (This is reduced by corruption stage.)").define("exteritioRaidCooldown", 3);
        BUILDER.pop();

        // Notifications
        BUILDER.push("notifications");
        globalNotificationCooldown = BUILDER.comment("Cooldown for global notifications (in minutes). 0 disables.").define("globalNotificationCooldown", 15);
        colonyNotificationCooldown = BUILDER.comment("Cooldown for colony notifications (in minutes). 0 disables.").define("colonyNotificationCooldown", 15);
        colonyMitigationRollingDays = BUILDER.comment("Rolling window for colony mitigation messaging (in Minecraft days).").defineInRange("colonyMitigationRollingDays", 14, 1, 100);
        citizenCommentFrequency = BUILDER.comment("Percent chance of a citizen making a comment about the corruption?. 0 disables.").defineInRange("citizenCommentFrequency", 5, 0, 100);
        BUILDER.pop();

            // Notifications
        BUILDER.push("beacons");
        baseBeaconPower = BUILDER.comment("Base beacon power.").defineInRange("baseBeaconPower", 5, 1, 20);
        BUILDER.pop();

        SPEC = BUILDER.build(); // Last

    }

    /**
     * Register the config with the given mod container.
     * @param modContainer The mod container to register the config with.
     */
    public static void register(ModContainer modContainer) {
        LOGGER.info("Registering Salvation Mod to handle configurations.");
        modContainer.registerConfig(ModConfig.Type.SERVER, SPEC, "salvation-server.toml");
    }

}
