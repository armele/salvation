package com.deathfrog.salvationmod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.deathfrog.mctradepost.core.commands.CommandTree;
import com.deathfrog.salvationmod.core.commands.CommandCorruption;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.commands.CommandSetTrace;

@EventBusSubscriber(modid = SalvationMod.MODID)
public class ModCommands 
{
    // Trace strings
    public static final String TRACE_CORRUPTION =           "corruption";
    
    // Command keywords
    public static final String CMD_CORRUPTION_PROGRESS =    "progress";
    public static final String CMD_DYNTRACE_SETTRACE =      "trace";

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) 
    {

        /*
         * Corruption command tree.
         */
        final CommandTree corruption = new CommandTree("corruption")
            .addNode(new CommandCorruption(CMD_CORRUPTION_PROGRESS).build());

        /*
         * Root TradePost command tree, all subtrees are added here.
         */
        final CommandTree mcsvRoot = new CommandTree("mcsv")
            .addNode(corruption)
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

        return keys;
    }
}