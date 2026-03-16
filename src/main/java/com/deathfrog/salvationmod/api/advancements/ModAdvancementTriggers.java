package com.deathfrog.salvationmod.api.advancements;

import com.deathfrog.salvationmod.SalvationMod;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModAdvancementTriggers
{
    public static final DeferredRegister<CriterionTrigger<?>> DEFERRED_REGISTER = DeferredRegister.create(Registries.TRIGGER_TYPE, SalvationMod.MODID);
    public static final String ADV_CORRUPTED_ENTITY_CURED = "corrupted_entity_cured";

    public static final DeferredHolder<CriterionTrigger<?>, CureCorruptedAnimalTrigger> CORRUPTED_ENTITY_CURED = DEFERRED_REGISTER.register(ADV_CORRUPTED_ENTITY_CURED, CureCorruptedAnimalTrigger::new);
}
