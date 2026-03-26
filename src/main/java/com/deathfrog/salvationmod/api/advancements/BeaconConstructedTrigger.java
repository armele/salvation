package com.deathfrog.salvationmod.api.advancements;

import java.util.Optional;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger.SimpleInstance;
import net.minecraft.server.level.ServerPlayer;

public class BeaconConstructedTrigger extends SimpleCriterionTrigger<BeaconConstructedTrigger.BeaconConstructedTriggerInstance>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Triggers the listener checks if there are any listening in
     * 
     * @param player the player the check regards
     */
    public void trigger(final @Nonnull ServerPlayer player)
    {
        trigger(player, trigger -> true);
    }

    @Override
    public Codec<BeaconConstructedTriggerInstance> codec()
    {
        return BeaconConstructedTriggerInstance.CODEC;
    }

    public static record BeaconConstructedTriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleInstance
    {
        public static final Codec<BeaconConstructedTriggerInstance> CODEC = RecordCodecBuilder.create(builder -> builder
            .group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(BeaconConstructedTriggerInstance::player))
            .apply(builder, BeaconConstructedTriggerInstance::new));



        public static Criterion<BeaconConstructedTriggerInstance> cureCorruptedAnimal()
        {
            return ModAdvancementTriggers.BEACON_CONSTRUCTED.get().createCriterion(new BeaconConstructedTriggerInstance(Optional.empty()));
        }
    }
}
