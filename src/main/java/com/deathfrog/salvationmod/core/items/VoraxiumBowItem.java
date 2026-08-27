package com.deathfrog.salvationmod.core.items;

import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.ModEntityTypes;
import com.deathfrog.salvationmod.ModItems;
import com.deathfrog.salvationmod.entity.CorruptionBoltEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/** A bow powered by Essence of Corruption that fires corruption bolts. */
public class VoraxiumBowItem extends BowItem
{
    private static final float PLAYER_BOLT_DAMAGE = 7.0F;
    private static final String TOOLTIP_KEY = "tooltip.salvation.voraxium_bow";
    private static final String REQUIRES_AMMO_KEY = "message.salvation.voraxium_bow.requires_ammo";
    
    @SuppressWarnings("null")
    private static final @Nonnull Predicate<ItemStack> ESSENCE_ONLY = stack -> stack.is(ModItems.ESSENCE_OF_CORRUPTION.get());

    public VoraxiumBowItem(@Nonnull final Properties properties)
    {
        super(properties);
    }

    @Override
    public @Nonnull Predicate<ItemStack> getAllSupportedProjectiles()
    {
        return ESSENCE_ONLY;
    }

    @SuppressWarnings("null")
    @Override
    public @Nonnull InteractionResultHolder<ItemStack> use(@Nonnull final Level level, @Nonnull final Player player,
        @Nonnull final InteractionHand hand)
    {
        final ItemStack bow = player.getItemInHand(hand);
        final boolean hasAmmo = !player.getProjectile(bow).isEmpty();
        final boolean canFireWithoutAmmo = player.hasInfiniteMaterials() || enchantmentLevel(level, bow, Enchantments.INFINITY) > 0;
        final InteractionResultHolder<ItemStack> eventResult =
            net.neoforged.neoforge.event.EventHooks.onArrowNock(bow, level, player, hand, hasAmmo || canFireWithoutAmmo);
        if (eventResult != null)
        {
            return eventResult;
        }

        if (!hasAmmo && !canFireWithoutAmmo)
        {
            if (!level.isClientSide())
            {
                player.displayClientMessage(Component.translatable(REQUIRES_AMMO_KEY), true);
            }
            return InteractionResultHolder.fail(bow);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(bow);
    }

    /** Adds the bow's Essence of Corruption ammunition requirement to its tooltip. */
    @Override
    public void appendHoverText(@Nonnull final ItemStack stack, @Nonnull final Item.TooltipContext context,
        @Nonnull final List<Component> tooltipComponents, @Nonnull final TooltipFlag tooltipFlag)
    {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
    }

    @SuppressWarnings("null")
    @Override
    public void releaseUsing(@Nonnull final ItemStack bow, @Nonnull final Level level,
        @Nonnull final LivingEntity livingEntity, final int timeLeft)
    {
        if (!(livingEntity instanceof Player player))
        {
            return;
        }

        final int infinityLevel = enchantmentLevel(level, bow, Enchantments.INFINITY);
        final boolean infiniteAmmo = player.hasInfiniteMaterials() || infinityLevel > 0;
        final ItemStack ammo = player.getProjectile(bow);
        if (ammo.isEmpty() && !infiniteAmmo)
        {
            return;
        }

        int chargeTicks = getUseDuration(bow, player) - timeLeft;
        chargeTicks = net.neoforged.neoforge.event.EventHooks.onArrowLoose(bow, level, player, chargeTicks, !ammo.isEmpty() || infiniteAmmo);
        if (chargeTicks < 0)
        {
            return;
        }

        final float power = BowItem.getPowerForTime(chargeTicks);
        if (power < 0.1F)
        {
            return;
        }

        if (level instanceof ServerLevel serverLevel)
        {
            final int powerLevel = enchantmentLevel(level, bow, Enchantments.POWER);
            final int punchLevel = enchantmentLevel(level, bow, Enchantments.PUNCH);
            final float damage = PLAYER_BOLT_DAMAGE + (powerLevel > 0 ? powerLevel * 0.5F + 0.5F : 0.0F);

            final CorruptionBoltEntity bolt = new CorruptionBoltEntity(ModEntityTypes.CORRUPTION_BOLT.get(), serverLevel);
            bolt.setOwner(player);
            bolt.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
            bolt.configurePlayerShot(damage, punchLevel);
            bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);
            serverLevel.addFreshEntity(bolt);

            if (!infiniteAmmo)
            {
                ammo.shrink(1);
            }

            bow.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
            player.awardStat(Stats.ITEM_USED.get(this));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT,
            SoundSource.PLAYERS, 1.0F,
            1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
    }

    @SuppressWarnings("null")
    private static int enchantmentLevel(@Nonnull final Level level, @Nonnull final ItemStack stack,
        @Nonnull final net.minecraft.resources.ResourceKey<Enchantment> enchantment)
    {
        final Holder<Enchantment> holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
        return stack.getEnchantmentLevel(holder);
    }
}
