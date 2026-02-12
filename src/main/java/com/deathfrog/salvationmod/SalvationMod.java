package com.deathfrog.salvationmod;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.client.render.model.*;
import com.deathfrog.salvationmod.client.render.*;
import com.deathfrog.salvationmod.core.apiimp.initializer.ModBuildingsInitializer;
import com.deathfrog.salvationmod.core.colony.SalvationHappinessFactorTypeInitializer;
import com.deathfrog.salvationmod.core.colony.buildings.modules.WithdrawResearchCreditMessage;
import com.deathfrog.salvationmod.core.engine.CureMappingsManager;
import com.deathfrog.salvationmod.core.engine.FurnaceCookLedgerTracker;
import com.deathfrog.salvationmod.core.engine.SalvationEventListener;
import com.deathfrog.salvationmod.entity.*;
import com.deathfrog.salvationmod.network.ChunkCorruptionSyncMessage;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(SalvationMod.MODID)
public class SalvationMod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "salvation";

    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "salvation" names pace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(NullnessBridge.assumeNonnull(Registries.CREATIVE_MODE_TAB), MODID);

    // Manages mapping of of corrupted entities to their vanilla counterparts
    public static final CureMappingsManager CURE_MAPPINGS = new CureMappingsManager();

    // Custom Sounds
    @SuppressWarnings("null")
    public static final @Nonnull SoundEvent RESEARCH_CREDIT = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "environment.research_credit"));


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

        // Register the Deferred Register to the mod event bus so entities get registered
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);

        SalvationHappinessFactorTypeInitializer.register(modEventBus);

        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Add a listener for the completion of the load.
        modEventBus.addListener(this::onLoadComplete);

        modEventBus.addListener(this::onEntityAttributes);

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
    private void onLoadComplete(final FMLLoadCompleteEvent event) {
        LOGGER.info("Salvation onLoadComplete"); 

        MCTradePostMod.LOGGER.info("Injecting building modules.");
        ModBuildingsInitializer.injectBuildingModules();
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

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS)
        {
            event.accept(ModItems.SCARRED_STONE_BLOCK_ITEM);
            event.accept(ModItems.SCARRED_COBBLE_BLOCK_ITEM);
        }

        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
        {
            event.accept(NullnessBridge.assumeNonnull(ModItems.CORRUPTED_WATER_BUCKET.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.CREATIVE_PURIFIER.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.RESEARCH_CREDIT.get()));
        }

        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS)
        {
            event.accept(NullnessBridge.assumeNonnull(ModItems.CORRUPTED_FLESH.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.CORRUPTED_CATCH.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.CORRUPTED_HARVEST.get()));
            event.accept(NullnessBridge.assumeNonnull(ModItems.CORRUPTED_MEAT.get()));
        }
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
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = SalvationMod.MODID, value = Dist.CLIENT)
    static class ClientModEvents
    {
        @SuppressWarnings("null")
        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> {
                ItemBlockRenderTypes.setRenderLayer(ModFluids.CORRUPTED_WATER_SOURCE.get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(ModFluids.CORRUPTED_WATER_FLOWING.get(), RenderType.translucent());
            });
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
