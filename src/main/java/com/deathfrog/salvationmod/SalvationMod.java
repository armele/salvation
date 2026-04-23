package com.deathfrog.salvationmod;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.client.render.model.*;
import com.deathfrog.salvationmod.client.screen.BeaconScreen;
import com.deathfrog.salvationmod.client.screen.PurifyingFurnaceScreen;
import com.deathfrog.salvationmod.ModItems.ModArmorMaterials;
import com.deathfrog.salvationmod.api.advancements.ModAdvancementTriggers;
import com.deathfrog.salvationmod.api.sounds.ModSoundEvents;
import com.deathfrog.salvationmod.apiimp.initializer.ModCraftingSetup;
import com.deathfrog.salvationmod.apiimp.initializer.ModJobsInitializer;
import com.deathfrog.salvationmod.apiimp.initializer.TileEntityInitializer;
import com.deathfrog.salvationmod.client.render.*;
import com.deathfrog.salvationmod.core.apiimp.initializer.ModBuildingsInitializer;
import com.deathfrog.salvationmod.core.apiimp.initializer.ModInteractionInitializer;
import com.deathfrog.salvationmod.core.colony.SalvationHappinessFactorTypeInitializer;
import com.deathfrog.salvationmod.core.colony.buildings.modules.WithdrawResearchCreditMessage;
import com.deathfrog.salvationmod.core.engine.BiomeMappingsManager;
import com.deathfrog.salvationmod.core.engine.ChunkCorruptionSystem;
import com.deathfrog.salvationmod.core.engine.CorruptionPaletteManager;
import com.deathfrog.salvationmod.core.engine.CorruptionStage;
import com.deathfrog.salvationmod.core.engine.CorruptionStageRulesManager;
import com.deathfrog.salvationmod.core.engine.CureMappingsManager;
import com.deathfrog.salvationmod.core.engine.FurnaceCookLedgerTracker;
import com.deathfrog.salvationmod.core.engine.FurnaceMachineProfileManager;
import com.deathfrog.salvationmod.core.engine.SalvationEventListener;
import com.deathfrog.salvationmod.entity.*;
import com.deathfrog.salvationmod.network.ChunkCorruptionSyncMessage;
import com.deathfrog.salvationmod.network.ClientChunkCorruptionState;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.GrassColor;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.client.renderer.item.ItemProperties;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(SalvationMod.MODID)
public class SalvationMod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "salvation";

    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SILENT_GEAR_COMPAT_PACK =
        ResourceLocation.fromNamespaceAndPath(MODID, "datapacks/salvation_silentgear_compat");
    private static final ResourceLocation MYSTICAL_AGRICULTURE_COMPAT_PACK =
        ResourceLocation.fromNamespaceAndPath(MODID, "datapacks/salvation_mysticalagriculture_compat");

    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "salvation" names pace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(NullnessBridge.assumeNonnull(Registries.CREATIVE_MODE_TAB), MODID);

    // Manages mapping of of corrupted entities to their vanilla counterparts
    public static final CureMappingsManager CURE_MAPPINGS = new CureMappingsManager();

    // Custom Sounds
    public static final @Nonnull ResourceLocation RESEARCH_CREDIT_SOUND_LOCATION = NullnessBridge.assumeNonnull(ResourceLocation.fromNamespaceAndPath(MODID, "environment.research_credit"));
    public static final @Nonnull SoundEvent RESEARCH_CREDIT = NullnessBridge.assumeNonnull(SoundEvent.createVariableRangeEvent(RESEARCH_CREDIT_SOUND_LOCATION));


    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public SalvationMod(IEventBus modEventBus, ModContainer modContainer)
    {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        ModBlocks.BLOCKS.register(modEventBus);

        // Register the Deferred Register to the mod event bus so items get registered
        ModItems.ITEMS.register(modEventBus);

        // Register fluids to the mod event bus
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModJobsInitializer.DEFERRED_REGISTER.register(modEventBus);   
        ModArmorMaterials.ARMOR_MATERIALS.register(modEventBus);

        // Register the Deferred Register to the mod event bus so entities get registered
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);

        SalvationHappinessFactorTypeInitializer.register(modEventBus);

        // Register custom advancements
        ModAdvancementTriggers.DEFERRED_REGISTER.register(modEventBus);

        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        Config.register(modContainer);

        // Add a listener for the completion of the load.
        modEventBus.addListener(this::onLoadComplete);
        modEventBus.addListener(this::addPackFinders);

        modEventBus.addListener(this::onEntityAttributes);
        
        // Register the Deferred Register to the mod event bus so tile entities get registered
        TileEntityInitializer.BLOCK_ENTITIES.register(modEventBus);

        // Register the Deferred Register to the mod event bus so loot modifiers get registered
        ModLootModifiers.LOOT_MODIFIERS.register(modEventBus);

        // Register the Deferred Register to the mod event bus so attachments get registered
        ModAttachments.ATTACHMENTS.register(modEventBus);

    }

    /**
     * This method is called on both the client and server side after the mod has finished loading.
     * It is responsible for injecting the sound events from MCTradePost into MineColonies' CITIZEN_SOUND_EVENTS.
     * This is a temporary solution until sounds in MineColonies have the flexibility to look up sound events from other modpacks.
     */
    private void onLoadComplete(final FMLLoadCompleteEvent event) 
    {
        LOGGER.info("Salvation onLoadComplete"); 
        LOGGER.info("Injecting sounds."); 
        ModSoundEvents.injectSounds();              // These need to be injected both on client (to play) and server (to register)

        ModCraftingSetup.injectCraftingRules();  

        ModBuildingsInitializer.injectBuildingModules();
    
        MCTradePostMod.LOGGER.info("Injecting interaction handlers.");
        ModInteractionInitializer.injectInteractionHandlers();
    
    }

    private void commonSetup(FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("Salvation: Common Setup");
        
        FurnaceCookLedgerTracker.init(
            // LedgerSink
            (level, furnacePos, output, count, fuelPoints, recipeType, recipeId) -> 
            {   
                if (!(level instanceof ServerLevel) || level.isClientSide()) return;
                SalvationEventListener.onCookOutputExtracted(level, furnacePos, output, count, fuelPoints, recipeType, recipeId);
            },
            // CookCompleteSink
            (level, furnacePos, output, craftsCompleted, fuelPoints, fuelSnapshot, recipeType, recipeId) -> 
            {   
                if (!(level instanceof ServerLevel) || level.isClientSide() || furnacePos == null) return;
                SalvationEventListener.onCookingComplete(level, furnacePos, output, craftsCompleted, fuelPoints, fuelSnapshot, recipeType, recipeId);
            }
        );
        
    }

    @SuppressWarnings("null")
    private void addPackFinders(final AddPackFindersEvent event)
    {
        event.addPackFinders(
            SILENT_GEAR_COMPAT_PACK,
            PackType.SERVER_DATA,
            Component.literal("Salvation Silent Gear Compatibility"),
            PackSource.DEFAULT,
            false,
            Pack.Position.BOTTOM
        );
        event.addPackFinders(
            MYSTICAL_AGRICULTURE_COMPAT_PACK,
            PackType.SERVER_DATA,
            Component.literal("Salvation Mystical Agriculture Compatibility"),
            PackSource.DEFAULT,
            false,
            Pack.Position.BOTTOM
        );
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS)
        {
            event.accept(ModItems.SCARRED_STONE_BLOCK_ITEM);
            event.accept(ModItems.SCARRED_COBBLE_BLOCK_ITEM);
            event.accept(ModItems.BLIGHTED_GRASS_BLOCK_ITEM);
            event.accept(NullnessBridge.assumeNonnull(ModItems.INERT_FUEL_BLOCK_ITEM.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_BLOCK_ITEM.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_VORAXIUM_BLOCK_ITEM.get()));
        }

        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
        {
            event.accept(NullnessBridge.assumeNonnull(ModItems.CORRUPTED_WATER_BUCKET.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.CORRUPTION_EXTRACTOR.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.RESEARCH_CREDIT.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFYING_FURNACE_ITEM.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFICATION_FILTER.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.CORRUPTION_INVERTER.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.STABILIZATION_TEMPLATE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.WARD_BINDING.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIAN_LOCATOR.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFIED_IRON_INGOT.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFIED_IRON_NUGGET.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFIED_IRON_HOE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFIED_IRON_PICKAXE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFIED_IRON_SHOVEL.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_HOE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_PICKAXE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_SHOVEL.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_VORAXIUM_HOE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_VORAXIUM_PICKAXE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_VORAXIUM_SHOVEL.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFICATION_BEACON_CORE_ITEM.get()));
        }

        if (event.getTabKey() == CreativeModeTabs.COMBAT)
        {
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFIED_IRON_AXE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFIED_IRON_SWORD.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFIED_IRON_HELMET.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFIED_IRON_BOOTS.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFIED_IRON_CHESTPLATE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFIED_IRON_LEGGINGS.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_AXE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_SWORD.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_HELMET.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_BOOTS.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_CHESTPLATE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_LEGGINGS.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_VORAXIUM_AXE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_VORAXIUM_SWORD.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_VORAXIUM_HELMET.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_VORAXIUM_BOOTS.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_VORAXIUM_CHESTPLATE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_VORAXIUM_LEGGINGS.get()));
        }

        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS)
        {
            event.accept(NullnessBridge.assumeNonnull(ModItems.INERT_FUEL.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFICATION_FUEL.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFICATION_FUEL_NUGGET.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.PURIFICATION_FUEL_BLOCK_ITEM.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_RAW_VORAXIUM.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_VORAXIUM_SCRAP.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.UNSTABLE_VORAXIUM_INGOT.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.RAW_VORAXIUM.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_INGOT.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_NUGGET.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.BEACON_UPGRADE_BASE.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.BEACON_UPGRADE_EXTRACTION.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.BEACON_UPGRADE_SOLAR.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.BEACON_UPGRADE_HARVEST.get()));
        }

        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS)
        {
            event.accept(NullnessBridge.assumeNonnull(ModItems.CORRUPTED_FLESH.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.CORRUPTED_CATCH.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.CORRUPTED_HARVEST.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.CORRUPTED_MEAT.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.ESSENCE_OF_CORRUPTION.get()));
        }

        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS)
        {
            event.accept(NullnessBridge.assumeNonnull(ModItems.VORAXIUM_ORE_BLOCK_ITEM.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.BLIGHTWOOD_SAPLING_ITEM.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.BLIGHTWOOD_LOG_ITEM.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.BLIGHTWOOD_LEAVES_ITEM.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.NEUTRALIZED_BLIGHTWOOD_ITEM.get()));
        }

        MCTradePostMod.TRADEPOST_TAB.unwrapKey().ifPresent(key -> {
            if (event.getTabKey().equals(key))
            {
                event.accept(NullnessBridge.assumeNonnull(ModBlocks.blockHutEnvironmentalLab.get()));
            }
        });
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("Salvation: Server Starting");
    }

    @SubscribeEvent
    public void onAddReloadListeners(final net.neoforged.neoforge.event.AddReloadListenerEvent event)
    {
        event.addListener(new CureMappingsManager.ReloadListener(CURE_MAPPINGS));
        event.addListener(new CorruptionStageRulesManager.ReloadListener());
        event.addListener(new CorruptionPaletteManager.ReloadListener());
        event.addListener(new BiomeMappingsManager.ReloadListener());
        event.addListener(new FurnaceMachineProfileManager.ReloadListener());
    }

    @SubscribeEvent
    public void registerFuel(FurnaceFuelBurnTimeEvent event)
    {
        if (event.getItemStack().is(NullnessBridge.assumeNonnull((ModItems.INERT_FUEL.get()))))
        {
            // 200 ticks = 10 seconds (same as a stick)
            event.setBurnTime(1600); 
            return;
        }

        if (event.getItemStack().is(NullnessBridge.assumeNonnull((ModItems.INERT_FUEL_BLOCK_ITEM.get()))))
        {
            // 200 ticks = 10 seconds (same as a stick)
            event.setBurnTime(16000); 
            return;
        }

        if (event.getItemStack().is(NullnessBridge.assumeNonnull((ModItems.PURIFICATION_FUEL_NUGGET.get()))))
        {
            // 200 ticks = 10 seconds (same as a stick)
            event.setBurnTime(200); 
        }

        if (event.getItemStack().is(NullnessBridge.assumeNonnull((ModItems.PURIFICATION_FUEL.get()))))
        {
            // 200 ticks = 10 seconds (same as a stick)
            event.setBurnTime(1600); 
        }

        if (event.getItemStack().is(NullnessBridge.assumeNonnull((ModItems.PURIFICATION_FUEL_BLOCK_ITEM.get()))))
        {
            // 200 ticks = 10 seconds (same as a stick)
            event.setBurnTime(16000); 
        }
    }

    @SuppressWarnings("null")
    public void onEntityAttributes(final EntityAttributeCreationEvent event)
    {
        event.put(ModEntityTypes.CORRUPTED_SHEEP.get(), CorruptedSheepEntity.createAttributes().build());
        event.put(ModEntityTypes.CORRUPTED_COW.get(), CorruptedCowEntity.createAttributes().build());
        event.put(ModEntityTypes.CORRUPTED_CHICKEN.get(), CorruptedChickenEntity.createAttributes().build());
        event.put(ModEntityTypes.CORRUPTED_CAT.get(), CorruptedCatEntity.createAttributes().build());
        event.put(ModEntityTypes.CORRUPTED_PIG.get(), CorruptedPigEntity.createAttributes().build());
        event.put(ModEntityTypes.CORRUPTED_POLARBEAR.get(), CorruptedPolarBearEntity.createAttributes().build());
        event.put(ModEntityTypes.CORRUPTED_FOX.get(), CorruptedFoxEntity.createAttributes().build());
        event.put(ModEntityTypes.VORAXIAN_OBSERVER.get(), VoraxianObserverEntity.createAttributes().build());
        event.put(ModEntityTypes.VORAXIAN_MAW.get(), VoraxianMawEntity.createAttributes().build());
        event.put(ModEntityTypes.VORAXIAN_STINGER.get(), VoraxianStingerEntity.createAttributes().build());
        event.put(ModEntityTypes.VORAXIAN_DARTER.get(), VoraxianDarterEntity.createAttributes().build());
        event.put(ModEntityTypes.VORAXIAN_OVERLORD.get(), VoraxianOverlordEntity.createAttributes().build());
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = SalvationMod.MODID, value = Dist.CLIENT)
    static class ClientModEvents
    {
        @SuppressWarnings("null")
        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> 
            {
                ItemBlockRenderTypes.setRenderLayer(ModFluids.CORRUPTED_WATER_SOURCE.get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(ModFluids.CORRUPTED_WATER_FLOWING.get(), RenderType.translucent());
                ItemProperties.register(
                    ModItems.VORAXIAN_LOCATOR.get(),
                    ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "chunk_corruption"),
                    (stack, level, entity, seed) -> getVoraxianLocatorCorruptionModel()
                );
            });
        }


        /**
         * Returns a float in the range [0.0, 1.0] to be used as the model for the Voraxian Locator's corruption effect.
         * The model is a simple step function based on the current corruption level of the client's chunk.
         * The model is as follows:
         *   - If the current corruption level is below the visible threshold, the model returns 0.0F.
         *   - If the current corruption level is above or equal to the standard corruption threshold, the model returns 1.0F.
         *   - Otherwise, the model returns one of three values based on the normalized corruption level (0.25F, 0.50F, 0.75F) corresponding to the three "tiers" of corruption effect.
         * The model is used to determine the strength of the corruption effect to be applied to the Voraxian Locator's model.
         * @return a float in the range [0.0, 1.0] to be used as the model for the Voraxian Locator's corruption effect.
         */
        private static float getVoraxianLocatorCorruptionModel()
        {
            if (ClientChunkCorruptionState.getStageOrd() >= CorruptionStage.STAGE_6_TERMINAL.ordinal()) return 1.0F;

            final int corruption = ClientChunkCorruptionState.getTargetCorruption();
            if (corruption < ChunkCorruptionSystem.VISIBLE_THRESHOLD) return 0.0F;
            if (corruption >= ChunkCorruptionSystem.STANDARD_CORRUPTION_THRESHOLD) return 1.0F;

            final float norm = (float) corruption / (float) ChunkCorruptionSystem.STANDARD_CORRUPTION_THRESHOLD;
            if (norm < 0.25F) return 0.25F;
            if (norm < 0.50F) return 0.50F;
            return 0.75F;
        }

        @SuppressWarnings("null")
        @SubscribeEvent
        public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event)
        {
            event.registerEntityRenderer(ModEntityTypes.CORRUPTED_SHEEP.get(), CorruptedSheepRender::new);
            event.registerEntityRenderer(ModEntityTypes.CORRUPTED_COW.get(), CorruptedCowRender::new);
            event.registerEntityRenderer(ModEntityTypes.CORRUPTED_CHICKEN.get(), CorruptedChickenRender::new);
            event.registerEntityRenderer(ModEntityTypes.CORRUPTED_CAT.get(), CorruptedCatRender::new);
            event.registerEntityRenderer(ModEntityTypes.CORRUPTED_PIG.get(), CorruptedPigRender::new);
            event.registerEntityRenderer(ModEntityTypes.CORRUPTED_POLARBEAR.get(), CorruptedPolarBearRender::new);
            event.registerEntityRenderer(ModEntityTypes.CORRUPTED_FOX.get(), CorruptedFoxRender::new);
            event.registerEntityRenderer(ModEntityTypes.VORAXIAN_OBSERVER.get(), VoraxianObserverRender::new);
            event.registerEntityRenderer(ModEntityTypes.VORAXIAN_MAW.get(), VoraxianMawRender::new);
            event.registerEntityRenderer(ModEntityTypes.VORAXIAN_STINGER.get(), VoraxianStingerRender::new);
            event.registerEntityRenderer(ModEntityTypes.VORAXIAN_DARTER.get(), VoraxianDarterRender::new);
            event.registerEntityRenderer(ModEntityTypes.VORAXIAN_OVERLORD.get(), VoraxianOverlordRender::new);
            event.registerEntityRenderer(ModEntityTypes.CORRUPTION_BOLT.get(), CorruptionBoltRender::new);
        }

        @SubscribeEvent
        public static void onRegisterLayerDefinitions(final EntityRenderersEvent.RegisterLayerDefinitions event)
        {
            event.registerLayerDefinition(CorruptedSheepModel.LAYER_LOCATION, () -> CorruptedSheepModel.createBodyLayer());
            event.registerLayerDefinition(CorruptedCowModel.LAYER_LOCATION, () -> CorruptedCowModel.createBodyLayer());
            event.registerLayerDefinition(CorruptedChickenModel.LAYER_LOCATION, () -> CorruptedChickenModel.createBodyLayer());
            event.registerLayerDefinition(CorruptedCatModel.LAYER_LOCATION, () -> CorruptedCatModel.createBodyLayer());
            event.registerLayerDefinition(CorruptedPigModel.LAYER_LOCATION, () -> CorruptedPigModel.createBodyLayer());
            event.registerLayerDefinition(CorruptedPolarBearModel.LAYER_LOCATION, () -> CorruptedPolarBearModel.createBodyLayer());
            event.registerLayerDefinition(CorruptedFoxModel.LAYER_LOCATION, () -> CorruptedFoxModel.createBodyLayer());
            event.registerLayerDefinition(VoraxianObserverModel.LAYER_LOCATION, () -> VoraxianObserverModel.createBodyLayer());
            event.registerLayerDefinition(VoraxianMawModel.LAYER_LOCATION, () -> VoraxianMawModel.createBodyLayer());
            event.registerLayerDefinition(VoraxianStingerModel.LAYER_LOCATION, () -> VoraxianStingerModel.createBodyLayer());
            event.registerLayerDefinition(VoraxianDarterModel.LAYER_LOCATION, () -> VoraxianDarterModel.createBodyLayer());
            event.registerLayerDefinition(VoraxianOverlordModel.LAYER_LOCATION, () -> VoraxianOverlordModel.createBodyLayer());
        }

        @SubscribeEvent
        public static void onRegisterMenuScreens(final RegisterMenuScreensEvent event)
        {
            event.register(NullnessBridge.assumeNonnull(ModMenus.PURIFICATION_BEACON_MENU.get()), BeaconScreen::new);
            event.register(NullnessBridge.assumeNonnull(ModMenus.PURIFYING_FURNACE_MENU.get()), PurifyingFurnaceScreen::new);
        }

        @SubscribeEvent
        public static void onRegisterBlockColors(final RegisterColorHandlersEvent.Block event)
        {
            event.register(
                (state, level, pos, tintIndex) -> level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.getDefaultColor(),
                NullnessBridge.assumeNonnull(ModBlocks.BLIGHTED_GRASS.get())
            );
        }

        @SubscribeEvent
        public static void onRegisterItemColors(final RegisterColorHandlersEvent.Item event)
        {
            event.register(
                (stack, tintIndex) -> event.getBlockColors().getColor(NullnessBridge.assumeNonnull(ModBlocks.BLIGHTED_GRASS.get().defaultBlockState()), null, null, tintIndex),
                NullnessBridge.assumeNonnull(ModItems.BLIGHTED_GRASS_BLOCK_ITEM.get())
            );
        }

    }

    @EventBusSubscriber(modid = SalvationMod.MODID)
    public class NetworkHandler 
    {

        /**
         * Handles the registration of network payload handlers for the mod.
         * This method is invoked when the RegisterPayloadHandlersEvent is fired,
         * allowing the mod to set up its network communication protocols.
         *
         * @param event The event that provides the registrar for registering payload handlers.
         */
        @SubscribeEvent
        public static void onNetworkRegistry(final RegisterPayloadHandlersEvent event) 
        {
            // Sets the current network version
            final PayloadRegistrar registrar = event.registrar("1");

            WithdrawResearchCreditMessage.TYPE.register(registrar);
            ChunkCorruptionSyncMessage.TYPE.register(registrar);
            

        }

        @EventBusSubscriber(modid = SalvationMod.MODID)
        public class ServerLoginHandler 
        {

            @SubscribeEvent
            public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) 
            {
                if (event.getEntity() instanceof ServerPlayer) 
                {
                    // No-op stub
                }
            }
        }   
    }
}
