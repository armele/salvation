package com.deathfrog.salvationmod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.deathfrog.mctradepost.core.commands.CommandTree;
import com.deathfrog.salvationmod.core.commands.*;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.TraceUtils;

@EventBusSubscriber(modid = SalvationMod.MODID)
public class ModCommands 
{
    // Trace strings
    public static final String TRACE_CORRUPTION =           "corruption";
    public static final String TRACE_SPAWN =                "spawn";
    public static final String TRACE_RESEARCHCREDIT =       "researchcredit";
    public static final String TRACE_COLONYLOOP =           "colonyloop";
    public static final String TRACE_REFUGEES =             "refugees";
    public static final String TRACE_BLIGHT =               "blight";
    public static final String TRACE_BEACON =               "beacon";
    public static final String TRACE_LABTECH =              "labtech";
    public static final String TRACE_OVERLORD =             "overlord";

    // Command keywords
    public static final String CMD_CORRUPTION_PROGRESS =    "progress";
    public static final String CMD_CORRUPTION_RESET =       "reset";
    public static final String CMD_CORRUPTION_HISTORY =     "history";
    public static final String CMD_BIOME_MAP =              "biomeMap";
    public static final String CMD_DYNTRACE_SETTRACE =      "trace";
    public static final String CMD_EXTERITIO_LOCATION =     "location";
    public static final String CMD_EXTERITIO_RAID =         "raid";
    public static final String CMD_REGENERATE_BOSS =        "regenerateBoss";

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) 
    {

        /*
         * Corruption command tree.
         */
        final CommandTree corruption = new CommandTree("corruption")
            .addNode(new CommandCorruptionProgress(CMD_CORRUPTION_PROGRESS).build())
            .addNode(new CommandCorruptionHistory(CMD_CORRUPTION_HISTORY).build())
            .addNode(new CommandCorruptionReset(CMD_CORRUPTION_RESET).build());

        final CommandTree exteritio = new CommandTree("exteritio")
            .addNode(new CommandExteritioLocation(CMD_EXTERITIO_LOCATION).build())
            .addNode(new CommandExteritioRaid(CMD_EXTERITIO_RAID).build());

        /*
         * Root TradePost command tree, all subtrees are added here.
         */
        final CommandTree mcsvRoot = new CommandTree("mcsv")
            .addNode(exteritio)
            .addNode(corruption)
            .addNode(new CommandRegenerateBoss(CMD_REGENERATE_BOSS).build())
            .addNode(new CommandBiomeMap(CMD_BIOME_MAP).build())
            .addNode(new CommandSetTrace(CMD_DYNTRACE_SETTRACE).build());

        // Adds all command trees to the dispatcher to register the commands.
        event.getDispatcher().register(mcsvRoot.build());
    }


    /**
     * @return A list of all trace keys available in the mod.
     */
    public static @Nonnull List<String> getTraceKeys() 
    {
        List<String> keys = new ArrayList<>();
        keys.add(TraceUtils.TRACE_NONE);
        keys.add(TRACE_CORRUPTION);
        keys.add(TRACE_RESEARCHCREDIT);
        keys.add(TRACE_COLONYLOOP);
        keys.add(TRACE_REFUGEES);
        keys.add(TRACE_BLIGHT);
        keys.add(TRACE_SPAWN);
        keys.add(TRACE_BEACON);
        keys.add(TRACE_LABTECH);

        return keys;
    }
}
