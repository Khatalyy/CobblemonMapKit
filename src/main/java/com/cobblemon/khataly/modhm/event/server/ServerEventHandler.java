package com.cobblemon.khataly.modhm.event.server;

import com.cobblemon.khataly.modhm.event.server.custom.ServerFlashHandler;
import com.cobblemon.khataly.modhm.event.server.custom.ServerFlyHandler;
import com.cobblemon.khataly.modhm.event.server.custom.ServerTeleportHandler;
import com.cobblemon.khataly.modhm.event.server.custom.ServerUltraHoleHandler;


public class ServerEventHandler {
    public static void register() {
        ServerFlyHandler.register();
        ServerFlashHandler.register();
        ServerTeleportHandler.register();
        ServerUltraHoleHandler.register();
    }
}
