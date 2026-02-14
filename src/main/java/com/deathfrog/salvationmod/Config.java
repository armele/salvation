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

    static {
        BUILDER.push("engine");

        corruptionDisabled = BUILDER
            .comment("Disable the corruption system.")
            .define("corruptionDisabled", false);

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
