package com.deathfrog.salvationmod.core.blocks.huts;

import net.minecraft.core.Registry;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntities;
import com.deathfrog.salvationmod.api.tileentities.SalvationTileEntityColonyBuilding;
import com.ldtteam.structurize.api.RotationMirror;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class SalvationBaseBlockHut extends AbstractBlockHut<SalvationBaseBlockHut>
{
    public SalvationBaseBlockHut()
    {}

    public SalvationBaseBlockHut registerSalvationHutBlock(final @Nonnull Registry<Block> registry)
    {
        Registry.register(registry, this.getRegistryName(), this);
        return this;
    }

    /**
     * Returns the resource location of this block hut in the Minecraft registry.
     * The location is of the form "mctradepost:<hut_name>" where <hut_name> is the name
     * returned by {@link #getHutName()}.
     *
     * @return the resource location of this block hut in the Minecraft registry.
     * @throws IllegalStateException if the block hut has no name.
     */
    @Nonnull
    public ResourceLocation getRegistryName()
    {
        String name = this.getHutName();
        if (name == null)
        {
            throw new IllegalStateException("Block hut has no name");
        }
        else
        {
            ResourceLocation resLoc = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, name);
            return (ResourceLocation) NullnessBridge.assumeNonnull(resLoc);
        }
    }

    /**
     * Called when a block is placed by the Build Tool.
     * This method returns a new instance of the block entity to be created at the given block position and block state.
     * The block entity created should be a {@link TileEntityColonyBuilding} and should have its {@link TileEntityColonyBuilding#registryName} field set to the
     * registry name of the building entry for this block.
     * @param blockPos the block position at which the block is being placed
     * @param blockState the block state of the block being placed
     * @return a new instance of the block entity to be created at the given block position and block state, or null if no block entity should be created
     */
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState)
    {
        BlockPos localBlockPos = (BlockPos) NullnessBridge.assumeNonnull(blockPos);
        BlockState localBlockState = (BlockState) NullnessBridge.assumeNonnull(blockState);
        TileEntityColonyBuilding building = (TileEntityColonyBuilding) ((BlockEntityType<?>) SalvationTileEntities.BUILDING.get())
            .create(localBlockPos, localBlockState);
        if (building != null)
        {
            building.registryName = this.getBuildingEntry().getRegistryName();
        }

        return building;
    }

    /**
     * Called when a block is placed by the Build Tool.
     * @param worldIn the world the block is being placed in
     * @param pos the position of the block being placed
     * @param state the block state of the block being placed
     * @param placer the entity that placed the block
     * @param stack the ItemStack that the block is being placed from
     * @param rotMir the rotation mirror used to place the block
     * @param style the style of the block being placed
     * @param blueprintPath the path to the blueprint being placed
     */
    public void onBlockPlacedByBuildTool(@NotNull Level worldIn,
        @NotNull BlockPos pos,
        BlockState state,
        LivingEntity placer,
        ItemStack stack,
        RotationMirror rotMir,
        String style,
        String blueprintPath)
    {
        BlockPos localPos = (BlockPos) NullnessBridge.assumeNonnull(pos);
        BlockEntity tileEntity = worldIn.getBlockEntity(localPos);
        if (tileEntity == null)
        {
            SalvationMod.LOGGER.error("Attempting to place a null tile entity.");
        }
        else
        {
            if (tileEntity instanceof SalvationTileEntityColonyBuilding)
            {
                SalvationTileEntityColonyBuilding hut = (SalvationTileEntityColonyBuilding) tileEntity;
                SalvationMod.LOGGER.info("Rotation mirror: {}; pack name: {}; blueprint path: {}",
                    new Object[] {rotMir, style, blueprintPath});
                hut.setRotationMirror(rotMir);
                hut.setPackName(style);
                hut.setBlueprintPath(blueprintPath);
            }
            else
            {
                SalvationMod.LOGGER.info("Wrong instance to place a tile entity of type {}", tileEntity.getClass().getName());
            }

            this.setPlacedBy(worldIn, localPos, state, placer, stack);
        }
    }
}
