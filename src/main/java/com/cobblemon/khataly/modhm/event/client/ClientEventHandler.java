package com.cobblemon.khataly.modhm.event.client;

import com.cobblemon.khataly.modhm.event.client.custom.FlashMenuOption;
import com.cobblemon.khataly.modhm.event.client.custom.FlyMenuOption;
import com.cobblemon.khataly.modhm.event.client.custom.TeleportMenuOption;
import com.cobblemon.khataly.modhm.event.client.custom.UltraHoleMenuOption;

public class ClientEventHandler {
    public static void register(){
        FlyMenuOption.register();
        FlashMenuOption.register();
        TeleportMenuOption.register();
        UltraHoleMenuOption.register();
    }
}
