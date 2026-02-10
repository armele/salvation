package com.deathfrog.salvationmod.core.items;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.core.colony.buildings.modules.BuildingSpecialResearchModule;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.mojang.logging.LogUtils;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ResearchCreditItem extends Item
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public ResearchCreditItem(Properties properties)
    {
        super(properties);
    }

    /**
     * Handles right click on the building with coins in hand.
     *
     * @param ctx the context
     * @return the result
     */
    @Override
    public InteractionResult useOn(final @Nonnull UseOnContext ctx)
    {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();

        if (player == null)
        {
            LOGGER.error("Player is null while attempting to deposit Research Credits.");
            return InteractionResult.PASS;
        }

        if (level == null || level.isClientSide())
        {
            return InteractionResult.PASS;
        }

        final ItemStack creditStack = player.getItemInHand(NullnessBridge.assumeNonnull(ctx.getHand()));
        final BlockEntity entity = ctx.getLevel().getBlockEntity(NullnessBridge.assumeNonnull(ctx.getClickedPos()));

        if (entity != null && entity instanceof TileEntityColonyBuilding buildingEntity)
        {
            IBuilding building = buildingEntity.getBuilding();

            BuildingSpecialResearchModule module = building.getModule(BuildingSpecialResearchModule.class);

            if (module != null)
            {   
                module.depositCredits(player, creditStack);
            }
        
        }

        return InteractionResult.SUCCESS;
    }
}
