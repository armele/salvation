package com.deathfrog.salvationmod;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.core.blocks.CorruptedWaterBlock;
import com.deathfrog.salvationmod.core.blocks.PurificationBeaconCoreBlock;
import com.deathfrog.salvationmod.core.blocks.PurifyingFurnace;
import com.deathfrog.salvationmod.core.blocks.ScarredStoneBlock;
import com.deathfrog.salvationmod.core.blocks.huts.BlockHutEnvironmentalLab;
import com.deathfrog.salvationmod.core.blocks.huts.SalvationBaseBlockHut;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = SalvationMod.MODID)
public class ModBlocks 
{

    // Create a Deferred Register to hold Blocks which will all be registered under the "salvation" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SalvationMod.MODID);

    // Creates a new Block with the id "salvation:example_block", combining the namespace and path
    @SuppressWarnings("null")
    public static final DeferredBlock<ScarredStoneBlock> SCARRED_STONE_BLOCK =
        BLOCKS.register("scarred_stone", () -> new ScarredStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)));
        
    @SuppressWarnings("null")
    public static final DeferredBlock<Block> BLIGHTED_GRASS =
        BLOCKS.register("blighted_grass", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GREEN)));

    @SuppressWarnings("null")
    public static final DeferredBlock<Block> INERT_FUEL_BLOCK =
        BLOCKS.register("inert_fuel_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)));

    @SuppressWarnings("null")
    public static final DeferredBlock<Block> PURIFICATION_FUEL_BLOCK =
        BLOCKS.register("purification_fuel_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)));

    @SuppressWarnings("null")
    public static final DeferredBlock<Block> SCARRED_COBBLE_BLOCK =
        BLOCKS.registerSimpleBlock("scarred_cobble", BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE));

    public static final DeferredHolder<Block, LiquidBlock> CORRUPTED_WATER_BLOCK =
        BLOCKS.register("corrupted_water",
            () -> new CorruptedWaterBlock(
                ModFluids.CORRUPTED_WATER_SOURCE.get(),
                BlockBehaviour.Properties.of()
                    .noCollission()
                    .strength(100.0F)
                    .noLootTable()
                    .mapColor(NullnessBridge.assumeNonnull(MapColor.GLOW_LICHEN))
            ));

    @SuppressWarnings("null")
    public static final DeferredBlock<PurifyingFurnace> PURIFYING_FURNACE =
        BLOCKS.register("purifying_furnace", () -> new PurifyingFurnace(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW)));

    @SuppressWarnings("null")
    public static final DeferredBlock<PurificationBeaconCoreBlock> PURIFICATION_BEACON_CORE =
        BLOCKS.register("purification_beacon_core",
            () -> new PurificationBeaconCoreBlock(
                BlockBehaviour.Properties.of()
                    .strength(4.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(PurificationBeaconCoreBlock.LIT) ? 12 : 0)
            )
        );

        

    public static final DeferredBlock<SalvationBaseBlockHut> blockHutEnvironmentalLab = BLOCKS.register(BlockHutEnvironmentalLab.HUT_NAME, () -> new BlockHutEnvironmentalLab());

    @SuppressWarnings("null")
    public static final DeferredBlock<Block> BLIGHTWOOD_SAPLING = BLOCKS.register("blightwood_sapling",
        () -> new SaplingBlock(
            ModWorldgen.BLIGHTWOOD,
            BlockBehaviour.Properties.of()
                .noCollission()
                .randomTicks()
                .instabreak()
                .sound(SoundType.GRASS)
        )
    );

    @SuppressWarnings("null")
    public static final DeferredBlock<Block> BLIGHTWOOD_LOG = BLOCKS.register("blightwood_log",
        () -> new RotatedPillarBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(2.5F, 3.0F)
                .sound(SoundType.NETHER_WOOD)
                .ignitedByLava()
                .requiresCorrectToolForDrops()
        )
    );

    @SuppressWarnings("null")
    public static final DeferredBlock<Block> BLIGHTWOOD_LEAVES = BLOCKS.register("blightwood_leaves",
        () -> new LeavesBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(0.2F)
                .randomTicks()
                .sound(SoundType.GRASS)
                .noOcclusion()
                .isValidSpawn((s, l, p, e) -> false)
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false)
                .ignitedByLava()
        )
    );

    /**
     * Subscribes to the RegisterEvent and checks if the event is related to the item registry.
     * If so, it calls registerBlockItem to register the item produced by the relevant blocks.
     * 
     * @param event The RegisterEvent to check the registry type of.
     */
    @SubscribeEvent
    public static void registerItems(RegisterEvent event)
    {
        if (event.getRegistryKey().equals(Registries.ITEM))
        {
            registerBlockItem(event.getRegistry(NullnessBridge.assumeNonnull(Registries.ITEM)));
        }
    }

    /**
     * Initializes the registry with the relevant item produced by the relevant blocks.
     *
     * @param registry The item registry to add the items too.
     */
    public static void registerBlockItem(final Registry<Item> registry)
    {
        blockHutEnvironmentalLab.get().registerBlockItem(registry, new Item.Properties());
    }

}
