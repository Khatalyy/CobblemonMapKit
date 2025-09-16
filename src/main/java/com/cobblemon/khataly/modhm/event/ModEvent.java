package com.cobblemon.khataly.modhm.event;

import com.cobblemon.khataly.modhm.event.custom.FlashMenuOption;
import com.cobblemon.khataly.modhm.event.custom.FlyMenuOption;

public class ModEvent {
    public static void register(){
        FlyMenuOption.register();
        FlashMenuOption.register();
    }
}
