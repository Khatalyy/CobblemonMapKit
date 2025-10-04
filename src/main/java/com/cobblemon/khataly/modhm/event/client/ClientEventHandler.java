package com.cobblemon.khataly.modhm.event.client;

import com.cobblemon.khataly.modhm.event.client.custom.*;

public class ClientEventHandler {
    public static void register(){
        FlyMenuOption.register();
        FlashMenuOption.register();
        TeleportMenuOption.register();
        UltraHoleMenuOption.register();
        GrassWandClient.register();
    }
}
