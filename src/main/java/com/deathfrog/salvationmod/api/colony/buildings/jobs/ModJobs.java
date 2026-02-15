package com.deathfrog.salvationmod.api.colony.buildings.jobs;

import java.util.ArrayList;
import java.util.List;

import com.deathfrog.salvationmod.SalvationMod;
import com.minecolonies.api.colony.jobs.registry.JobEntry;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModJobs 
{
    public static final String LABTECH_TAG = "labtech";

    public static final ResourceLocation LABTECH_ID = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, LABTECH_TAG);

    public static DeferredHolder<JobEntry, JobEntry> labtech;

    private ModJobs()
    {
        throw new IllegalStateException("Tried to initialize: ModJobs but this is a Utility class.");
    }

    public static List<ResourceLocation> getJobs()
    {
        List<ResourceLocation> jobs = new ArrayList<>() { };
        jobs.add(LABTECH_ID);

        return jobs;
    }

}
