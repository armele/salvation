package com.deathfrog.salvationmod.api.sounds;

import com.deathfrog.mctradepost.api.sounds.MCTPModSoundEvents;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.api.colony.buildings.jobs.ModJobs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import com.minecolonies.api.util.Tuple;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.minecolonies.api.sounds.EventType;
import java.util.*;

public class ModSoundEvents 
{
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(NullnessBridge.assumeNonnull(Registries.SOUND_EVENT), SalvationMod.MODID);
    public static Map<String, Map<EventType, List<Tuple<SoundEvent, SoundEvent>>>> SALVATION_CITIZEN_SOUND_EVENT_MAP = new HashMap<>();     // These will be injected into MineColonies' CITIZEN_SOUND_EVENTS

    /**
     * Register the {@link SoundEvent}s.  
     * Note that this implementation adds the sound events to the MineColonies list of CITIZEN_SOUND_EVENTS as well.
     * Not preferable, but required.
     *
     * @param registry the registry to register at.
     */
    static
    {
        final List<ResourceLocation> jobList = new ArrayList<>(ModJobs.getJobs());

        MCTPModSoundEvents.registerSoundsForJobs(SalvationMod.MODID, jobList, SOUND_EVENTS, SALVATION_CITIZEN_SOUND_EVENT_MAP);
    }

    /**
     * Injects the citizen sound events from MCTradePost into MineColonies' CITIZEN_SOUND_EVENTS.
     * This is a temporary solution until sounds in MineColonies have the flexibility to look up sound events from other modpacks.
     */
    public static void injectSounds() {
        if (SALVATION_CITIZEN_SOUND_EVENT_MAP.isEmpty()) 
        {
            SalvationMod.LOGGER.info("There are no sounds to inject.");
        } 
        else 
        {
            int size = SALVATION_CITIZEN_SOUND_EVENT_MAP.size();
            SalvationMod.LOGGER.info("Injecting {} sound events.", size);
            com.minecolonies.api.sounds.ModSoundEvents.CITIZEN_SOUND_EVENTS.putAll(SALVATION_CITIZEN_SOUND_EVENT_MAP);
        }
    }
}
