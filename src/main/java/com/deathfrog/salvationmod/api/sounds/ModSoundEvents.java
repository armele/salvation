package com.deathfrog.salvationmod.api.sounds;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.api.colony.buildings.jobs.ModJobs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import com.minecolonies.api.util.Tuple;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.minecolonies.api.sounds.EventType;
import java.util.*;

public class ModSoundEvents 
{
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(NullnessBridge.assumeNonnull(Registries.SOUND_EVENT), SalvationMod.MODID);
    public static Map<String, Map<EventType, List<Tuple<DeferredHolder<SoundEvent, SoundEvent>, DeferredHolder<SoundEvent, SoundEvent>>>>> SALVATION_CITIZEN_SOUND_EVENT_MAP = new HashMap<>();

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

        registerSoundsForJobs(jobList);
    }

    private static void registerSoundsForJobs(final List<ResourceLocation> jobs)
    {
        for (final ResourceLocation job : jobs)
        {
            final Map<EventType, List<Tuple<DeferredHolder<SoundEvent, SoundEvent>, DeferredHolder<SoundEvent, SoundEvent>>>> events = new HashMap<>();
            for (final EventType event : EventType.values())
            {
                final List<Tuple<DeferredHolder<SoundEvent, SoundEvent>, DeferredHolder<SoundEvent, SoundEvent>>> sounds = new ArrayList<>();
                for (int i = 1; i <= 4; i++)
                {
                    final String basePath = com.minecolonies.api.sounds.ModSoundEvents.CITIZEN_SOUND_EVENT_PREFIX + job.getPath();
                    final DeferredHolder<SoundEvent, SoundEvent> male = SOUND_EVENTS.register(
                        basePath + ".male" + i + "." + event.getId(), SoundEvent::createVariableRangeEvent);
                    final DeferredHolder<SoundEvent, SoundEvent> female = SOUND_EVENTS.register(
                        basePath + ".female" + i + "." + event.getId(), SoundEvent::createVariableRangeEvent);
                    sounds.add(new Tuple<>(male, female));
                }
                events.put(event, sounds);
            }
            SALVATION_CITIZEN_SOUND_EVENT_MAP.put(job.getPath(), events);
        }
    }

    /**
     * Injects the citizen sound events from MCTradePost into MineColonies' CITIZEN_SOUND_EVENTS.
     * This is a temporary solution until sounds in MineColonies have the flexibility to look up sound events from other modpacks.
     */
    @SuppressWarnings("null")
    public static void injectSounds() {
        if (SALVATION_CITIZEN_SOUND_EVENT_MAP.isEmpty()) 
        {
            SalvationMod.LOGGER.info("There are no sounds to inject.");
        } 
        else 
        {
            int size = SALVATION_CITIZEN_SOUND_EVENT_MAP.size();
            SalvationMod.LOGGER.info("Injecting {} sound events.", size);
            SALVATION_CITIZEN_SOUND_EVENT_MAP.forEach((job, eventMap) ->
                com.minecolonies.api.sounds.ModSoundEvents.CITIZEN_SOUND_EVENTS.put(
                    job,
                    eventMap.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                            .map(pair -> new Tuple<SoundEvent, SoundEvent>(pair.getA().get(), pair.getB().get()))
                            .toList()))));
        }
    }
}
