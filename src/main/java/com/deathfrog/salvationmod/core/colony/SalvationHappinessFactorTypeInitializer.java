package com.deathfrog.salvationmod.core.colony;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.happiness.HappinessRegistry.HappinessFactorTypeEntry;
import com.minecolonies.api.entity.citizen.happiness.HappinessRegistry.HappinessFunctionEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.mojang.logging.LogUtils;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.core.engine.SalvationManager;
import com.deathfrog.salvationmod.core.engine.SalvationManager.CorruptionStage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SalvationHappinessFactorTypeInitializer
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final String PURIFICATION_HAPPINESS_MODIFIER = "purification";

    public final static DeferredRegister<HappinessFactorTypeEntry> DEFERRED_REGISTER_HAPPINESS_FACTOR =
        DeferredRegister.create(NullnessBridge.assumeNonnull(CommonMinecoloniesAPIImpl.HAPPINESS_FACTOR_TYPES), SalvationMod.MODID);

    // Create a DeferredRegister that points at MineColonies' registry, but uses OUR modid for entries.
    public static final DeferredRegister<HappinessFunctionEntry> HAPPINESS_FUNCTIONS =
        DeferredRegister.create(NullnessBridge.assumeNonnull(CommonMinecoloniesAPIImpl.HAPPINESS_FUNCTION), SalvationMod.MODID);

    public static final ResourceLocation PURIFICATION_HAPPINESS_MODIFIER_LOCATION =
        ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, PURIFICATION_HAPPINESS_MODIFIER);

    public static final DeferredHolder<HappinessFunctionEntry, HappinessFunctionEntry> PURIFICATION_FUNCTION =
        HAPPINESS_FUNCTIONS.register(PURIFICATION_HAPPINESS_MODIFIER,
            () -> new HappinessFunctionEntry(data -> getPurificationHappiness(data.getColony())));

    private SalvationHappinessFactorTypeInitializer()
    {}

    /** Call from your mod constructor. */
    public static void register(final @Nonnull IEventBus modBus)
    {
        HAPPINESS_FUNCTIONS.register(modBus);
    }

    public static double getPurificationHappiness(final IColony colony)
    {
        final double baselineHappiness = 1D;

        // LOGGER.info("Purification happiness calculation.");

        final Level level = colony.getWorld();
        if (!(level instanceof ServerLevel serverLevel)) return 0.0D;

        final CorruptionStage stage = SalvationManager.stageForLevel(serverLevel);

        return switch (stage)
        {
            case STAGE_0_UNTRIGGERED -> baselineHappiness;
            default -> {
                final int threshold = stage.getThreshold();
                final SalvationColonyHandler handler = SalvationColonyHandler.getHandler(serverLevel, colony);

                final long purificationCredits = handler.getPurificationCredits();
                final int purification = (int) Math.min(purificationCredits, (long) threshold);
                final double ratio = ((double) purification / (double) threshold);

                // LOGGER.info("Purification happiness calculation result: {}, {} ({}), {}", purification, threshold, ratio, baselineHappiness );

                yield (threshold <= 0) ? 0.0D :  ratio * baselineHappiness;
            }
        };
    }
}
