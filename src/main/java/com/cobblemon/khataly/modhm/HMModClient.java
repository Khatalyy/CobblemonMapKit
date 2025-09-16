package com.cobblemon.khataly.modhm;

import com.cobblemon.khataly.modhm.event.ModEvent;
import com.cobblemon.khataly.modhm.screen.ModScreenHandlers;
import com.cobblemon.khataly.modhm.screen.custom.CutScreen;
import com.cobblemon.khataly.modhm.screen.custom.RockSmashScreen;
import com.cobblemon.khataly.modhm.screen.custom.StrengthScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class HMModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Registrazione schermate
        HandledScreens.register(ModScreenHandlers.ROCK_SMASH_SCREEN_HANDLER, RockSmashScreen::new);
        HandledScreens.register(ModScreenHandlers.CUT_SCREEN_HANDLER, CutScreen::new);
        HandledScreens.register(ModScreenHandlers.STRENGHT_SCREEN_HANDLER, StrengthScreen::new);
        ModEvent.register();
    }
}
