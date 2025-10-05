package com.cobblemon.khataly.modhm.event.server;

import com.cobblemon.khataly.modhm.event.server.custom.*;


public class ServerEventHandler {
    public static void register() {
        ServerFlyHandler.register();
        ServerFlashHandler.register();
        ServerTeleportHandler.register();
        ServerUltraHoleHandler.register();
        LevelCapEnforcer.register();
        LevelCapProgressionWatcher.register();
    }
}
