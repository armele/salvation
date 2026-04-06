package com.deathfrog.salvationmod.core.items;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.ModDimensions;
import com.deathfrog.salvationmod.core.engine.SalvationSavedData;
import com.deathfrog.salvationmod.core.portal.ExteritioBossStructureManager;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class VoraxianLocatorItem extends Item
{
    private static final int COOLDOWN_TICKS = 60;
    private static final int PARTICLE_COUNT = 54;
    private static final double PARTICLE_STEP = 0.22D;
    private static final double PARTICLE_START = 0.85D;
    private static final double SIDE_JITTER = 0.22D;

    private static final String MESSAGE_WRONG_DIMENSION = "message.salvation.voraxian_locator.wrong_dimension";
    private static final String MESSAGE_NO_EXTERITIO = "message.salvation.voraxian_locator.no_exteritio";
    private static final String MESSAGE_NO_TARGET = "message.salvation.voraxian_locator.no_target";

    public VoraxianLocatorItem(final Properties properties)
    {
        super(NullnessBridge.assumeNonnull(properties));
    }

    /**
     * Emits a directional pulse from the player towards the Exteritio boss location.
     * 
     * @param ctx the context
     * @return the result
     */
    @SuppressWarnings("null")
    @Override
    public InteractionResultHolder<ItemStack> use(final @Nonnull Level level,
        final @Nonnull Player player,
        final @Nonnull InteractionHand usedHand)
    {
        final ItemStack stack = player.getItemInHand(usedHand);
        
        if (level.isClientSide())
        {
            return InteractionResultHolder.success(stack);
        }

        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResultHolder.pass(stack);
        }

        if (serverLevel.dimension() != Level.OVERWORLD && serverLevel.dimension() != ModDimensions.EXTERITIO)
        {
            player.displayClientMessage(Component.translatable(MESSAGE_WRONG_DIMENSION), true);
            return InteractionResultHolder.fail(stack);
        }

        final ServerLevel exteritio = serverLevel.getServer().getLevel(NullnessBridge.assumeNonnull(ModDimensions.EXTERITIO));
        if (exteritio == null)
        {
            player.displayClientMessage(Component.translatable(MESSAGE_NO_EXTERITIO), true);
            return InteractionResultHolder.fail(stack);
        }

        ExteritioBossStructureManager.ensureSpawned(exteritio);

        final BlockPos target = SalvationSavedData.get(exteritio).getVoraxianBaseLocation();
        if (target == null)
        {
            player.displayClientMessage(Component.translatable(MESSAGE_NO_TARGET), true);
            return InteractionResultHolder.fail(stack);
        }

        emitDirectionalPulse(serverLevel, serverPlayer, target);
        consumeUse(serverPlayer, usedHand, stack);
        serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        serverPlayer.awardStat(Stats.ITEM_USED.get(this));

        return InteractionResultHolder.consume(stack);
    }

    /**
     * Emits a directional pulse from the player towards the target location.
     * The pulse is made up of a series of particles that move from the player's eye position towards the target.
     * The particles have a slight jitter in their Y position to give a sense of movement.
     * The pulse is also accompanied by a sound effect.
     * 
     * @param level the level to emit the pulse in
     * @param player the player to emit the pulse from
     * @param target the target location to emit the pulse towards
     */
    @SuppressWarnings("null")
    private static void emitDirectionalPulse(final @Nonnull ServerLevel level,
        final @Nonnull ServerPlayer player,
        final @Nonnull BlockPos target)
    {
        final Vec3 origin = player.getEyePosition();
        final boolean includeY = isWithinOneChunk(player.chunkPosition(), new ChunkPos(target));
        final Vec3 targetPoint = new Vec3(
            target.getX() + 0.5D,
            includeY ? target.getY() + 0.5D : origin.y,
            target.getZ() + 0.5D
        );

        final Vec3 direction = origin.vectorTo(targetPoint).normalize();
        if (direction == Vec3.ZERO)
        {
            return;
        }

        player.lookAt(EntityAnchorArgument.Anchor.EYES, origin.add(direction.scale(8.0D)));

        final Vec3 side = new Vec3(-direction.z, 0.0D, direction.x).normalize();
        for (int i = 0; i < PARTICLE_COUNT; i++)
        {
            final double distance = PARTICLE_START + (i * PARTICLE_STEP);
            final double jitter = ((i % 3) - 1) * SIDE_JITTER;
            final Vec3 pos = origin
                .add(direction.scale(distance))
                .add(side.scale(jitter))
                .add(0.0D, Math.sin(i * 0.7D) * 0.08D, 0.0D);

            level.sendParticles(
                NullnessBridge.assumeNonnull(ParticleTypes.PORTAL),
                pos.x,
                pos.y,
                pos.z,
                3,
                0.12D,
                0.12D,
                0.12D,
                0.08D
            );

            if (i % 3 == 0)
            {
                level.sendParticles(
                    NullnessBridge.assumeNonnull(ParticleTypes.END_ROD),
                    pos.x,
                    pos.y,
                    pos.z,
                    0,
                    direction.x * 0.08D,
                    direction.y * 0.08D,
                    direction.z * 0.08D,
                    0.9D
                );
            }
        }

        for (int i = 0; i < 6; i++)
        {
            final Vec3 pos = origin.add(direction.scale(PARTICLE_START + (i * 0.55D)));
            level.sendParticles(
                NullnessBridge.assumeNonnull(ParticleTypes.REVERSE_PORTAL),
                pos.x,
                pos.y,
                pos.z,
                8,
                0.18D,
                0.18D,
                0.18D,
                0.04D
            );
        }

        level.playSound(
            null,
            player.blockPosition(),
            NullnessBridge.assumeNonnull(SoundEvents.AMETHYST_BLOCK_RESONATE),
            SoundSource.PLAYERS,
            0.65F,
            includeY ? 0.85F : 1.05F
        );
    }

    private static boolean isWithinOneChunk(final @Nonnull ChunkPos playerChunk, final @Nonnull ChunkPos targetChunk)
    {
        return Math.abs(playerChunk.x - targetChunk.x) <= 1 && Math.abs(playerChunk.z - targetChunk.z) <= 1;
    }

    private static void consumeUse(final @Nonnull ServerPlayer player,
        final @Nonnull InteractionHand usedHand,
        final @Nonnull ItemStack stack)
    {
        if (stack.isEmpty() || !stack.isDamageableItem())
        {
            return;
        }

        final EquipmentSlot slot = LivingEntity.getSlotForHand(usedHand);

        if (slot == null)
        {
            return;
        }

        final int nextDamage = stack.getDamageValue() + 1;
        if (nextDamage >= stack.getMaxDamage())
        {
            final Item item = stack.getItem();
            stack.shrink(1);
            player.onEquippedItemBroken(NullnessBridge.assumeNonnull(item), slot);
            return;
        }

        stack.setDamageValue(nextDamage);
    }
}
