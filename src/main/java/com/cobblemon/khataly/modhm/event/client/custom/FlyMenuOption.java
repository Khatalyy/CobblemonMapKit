package com.cobblemon.khataly.modhm.event.client.custom;

import com.cobblemon.khataly.modhm.HMMod;
import com.cobblemon.khataly.modhm.config.FlyTargetConfig;
import com.cobblemon.khataly.modhm.networking.packet.AnimationHMPacketS2C;
import com.cobblemon.khataly.modhm.networking.packet.FlyMenuS2CPacket;
import com.cobblemon.khataly.modhm.screen.custom.AnimationMoveScreen;
import com.cobblemon.khataly.modhm.screen.custom.FlyTargetListScreen;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.interaction.PokemonInteractionGUICreationEvent;
import com.cobblemon.mod.common.api.reactive.EventObservable;
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelOption;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import kotlin.Unit;

import java.util.List;

public class FlyMenuOption {

    // Flag globale lato client: indica se aggiungere Fly alla ruota
    private static boolean canAddFlyOption = false;
    private static List<FlyMenuS2CPacket.FlyTargetEntry> targets;
    public static void register() {
        registerFlyMenuResponse();
        registerGUIEvent();
        registerGUICloseListener();
    }

    /** Riceve il pacchetto dal server e aggiorna il flag globale */
    private static void registerFlyMenuResponse() {
        ClientPlayNetworking.registerGlobalReceiver(FlyMenuS2CPacket.ID, (payload, context) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                canAddFlyOption = payload.canFly();
                targets = payload.targets();
                System.out.println("[FlyMenuOption] Flag aggiornato: canAddFlyOption = " + canAddFlyOption);
            });
        });
    }

    /** Evento della GUI di interazione: aggiunge Fly solo se il flag è true */
    private static void registerGUIEvent() {
        EventObservable<PokemonInteractionGUICreationEvent> observable = CobblemonEvents.POKEMON_INTERACTION_GUI_CREATION; observable
                .subscribe(Priority.NORMAL, event -> {
            if (!canAddFlyOption) return null;

            Identifier icon = Identifier.of(HMMod.MOD_ID, "textures/gui/fly/icon_fly.png");
            String tooltip = "Fly";

            kotlin.jvm.functions.Function0<Vector3f> colourFunc = () -> new Vector3f(1f, 1f, 1f);

            kotlin.jvm.functions.Function0<Unit> onPressFunc = () -> {
                MinecraftClient mc = MinecraftClient.getInstance();

                // Ricezione animazione dal server
                ClientPlayNetworking.registerGlobalReceiver(AnimationHMPacketS2C.ID, (payload, context) -> {
                    mc.execute(() -> mc.setScreen(new AnimationMoveScreen(Text.literal("AnimationMoveScreen"), payload.pokemon())));
                });

                // Mostra menu Fly
                mc.execute(() -> mc.setScreen(new FlyTargetListScreen(Text.literal("FLY Menu"),targets)));
                return Unit.INSTANCE;
            };

            InteractWheelOption option = new InteractWheelOption(icon, null, tooltip, colourFunc, onPressFunc);
            event.addFillingOption(option);
            return Unit.INSTANCE;
        });
    }

    /** Resetta il flag quando si chiude la GUI di interazione */
    private static void registerGUICloseListener() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            mc.setScreen(new Screen(Text.literal("")) {
                @Override
                public void removed() {
                    canAddFlyOption = false;
                    super.removed();
                    System.out.println("[FlyMenuOption] Flag canAddFlyOption resettato");
                }
            });
        });
    }
}
