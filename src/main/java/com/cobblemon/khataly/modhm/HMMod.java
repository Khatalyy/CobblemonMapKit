package com.cobblemon.khataly.modhm;


import com.cobblemon.khataly.modhm.block.ModBlocks;
import com.cobblemon.khataly.modhm.block.entity.ModBlockEntities;
import com.cobblemon.khataly.modhm.command.ModCommands;
import com.cobblemon.khataly.modhm.config.GrassZonesConfig;
import com.cobblemon.khataly.modhm.config.LevelCapConfig;
import com.cobblemon.khataly.modhm.config.ModConfig;
import com.cobblemon.khataly.modhm.event.server.FlyTargetProximityWatcher;
import com.cobblemon.khataly.modhm.event.server.ServerEventHandler;
import com.cobblemon.khataly.modhm.event.server.custom.GrassEncounterTicker;
import com.cobblemon.khataly.modhm.event.server.custom.LevelCapEnforcer;
import com.cobblemon.khataly.modhm.item.ModItemGroups;
import com.cobblemon.khataly.modhm.item.ModItems;
import com.cobblemon.khataly.modhm.config.FlyTargetConfig;
import com.cobblemon.khataly.modhm.networking.ModNetworking;
import com.cobblemon.khataly.modhm.networking.handlers.BadgeTagUseHandler;
import com.cobblemon.khataly.modhm.screen.ModScreenHandlers;
import com.cobblemon.khataly.modhm.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HMMod implements ModInitializer {
    public static final String MOD_ID = "modhm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModConfig.load();
        GrassZonesConfig.load();
        FlyTargetConfig.load();
        LevelCapConfig.load();

        ModSounds.registerSounds();
        ModScreenHandlers.registerScreenHandlers();
        ModNetworking.registerPackets();
        ModBlockEntities.registerBlockEntities();
        ModItems.registerModItems();
        ModBlocks.registerModBlocks();
        FlyTargetProximityWatcher.register();
        ModItemGroups.registerItemGroups();
        BadgeTagUseHandler.register();
        ServerEventHandler.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ModCommands.register(dispatcher);
        });
        GrassEncounterTicker.register();
        ServerTickEvents.END_SERVER_TICK.register(ModNetworking::tick);



        LOGGER.info("Hello Fabric world!");
    }
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
