package com.deathfrog.salvationmod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ClientConfig
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<Boolean> corruptionDarknessOverlayEnabled;

    static
    {
        BUILDER.push("accessibility");

        corruptionDarknessOverlayEnabled = BUILDER
            .comment("Enable the animated corruption darkness overlay. Disable this if the vignette, breathing pulse, or smoke motion causes discomfort.")
            .define("corruptionDarknessOverlayEnabled", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
