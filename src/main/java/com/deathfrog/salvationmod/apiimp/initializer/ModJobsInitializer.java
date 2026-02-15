package com.deathfrog.salvationmod.apiimp.initializer;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.api.colony.buildings.jobs.ModJobs;
import com.deathfrog.salvationmod.core.colony.jobs.JobLabTech;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.jobs.views.CrafterJobView;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

public final class ModJobsInitializer
{
    public final static DeferredRegister<JobEntry> DEFERRED_REGISTER = DeferredRegister.create(NullnessBridge.assumeNonnull(CommonMinecoloniesAPIImpl.JOBS), SalvationMod.MODID);

    private ModJobsInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModJobsInitializer but this is a Utility class.");
    }
    
    static
    {
        ModJobs.labtech = register(DEFERRED_REGISTER, ModJobs.LABTECH_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobLabTech::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.LABTECH_ID)
          .createJobEntry());

    }

    /**
     * Register a job at the deferred registry and store the job token in the job list.
     * @param deferredRegister the registry,
     * @param path the path.
     * @param supplier the supplier of the entry.
     * @return the registry object.
     */
    private static DeferredHolder<JobEntry, JobEntry> register(final DeferredRegister<JobEntry> deferredRegister, final String path, final @Nonnull Supplier<JobEntry> supplier)
    {
        if (path == null) return null;

        SalvationMod.LOGGER.info("Registering job: " + path);
        return deferredRegister.register(path, supplier);
    }
        
/**
 * Logs all registered job entries in MineColonies.
 * This method is used to debug which job entries are registered in MineColonies.
 * @param level the server level.
 */
    public static void logRegisteredJobEntries(ServerLevel level) 
    {
        Registry<JobEntry> jobRegistry = level.registryAccess().registryOrThrow(NullnessBridge.assumeNonnull(CommonMinecoloniesAPIImpl.JOBS));

        SalvationMod.LOGGER.info("=== Registered JobEntry objects in MineColonies ===");
        for (JobEntry entry : jobRegistry) 
        {
            if (entry == null) continue;
            SalvationMod.LOGGER.info("JobEntry ID: {}", jobRegistry.getKey(entry));
        }
    }
}