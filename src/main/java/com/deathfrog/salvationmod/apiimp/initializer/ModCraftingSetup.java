package com.deathfrog.salvationmod.apiimp.initializer;

import com.deathfrog.mctradepost.apiimp.initializer.MCTPCraftingSetup;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.api.colony.buildings.jobs.ModJobs;

/* Sets up crafting information for this mod's Minecolonies crafters.
 * ModTags.initCrafterRules() and its supporting constants are
 * not public, so this class reproduces some of that code.
 * 
 */
public class ModCraftingSetup 
{
    /**
     * Injects Minecolonies crafting rules for the Trade Post crafters.
     * This method is called by the mod's DeferredRegister callback.
     */
    public static void injectCraftingRules() 
    { 
        MCTPCraftingSetup.initCrafterRules(SalvationMod.MODID, ModJobs.LABTECH_TAG);
    }
}
