package com.cobblemon.khataly.modhm.event.client.custom;

import com.cobblemon.khataly.modhm.HMMod;
import com.cobblemon.khataly.modhm.networking.packet.*;
import com.cobblemon.khataly.modhm.screen.custom.AnimationMoveScreen;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.interaction.PokemonInteractionGUICreationEvent;
import com.cobblemon.mod.common.api.reactive.EventObservable;
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelOption;
import kotlin.Unit;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

public class TeleportMenuOption {

    // Flag globale lato client: indica se aggiungere Teleport alla ruota
    private static boolean canAddTeleportOption = false;

    public static void register() {
        registerTeleportMenuResponse();
        registerGUIEvent();
        registerGUICloseListener();
    }

    /** Riceve il pacchetto dal server e aggiorna il flag globale */
    private static void registerTeleportMenuResponse() {
        ClientPlayNetworking.registerGlobalReceiver(TeleportMenuS2CPacket.ID, (payload, context) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                canAddTeleportOption = payload.canTeleport();
                System.out.println("[FlashMenuOption] Flag aggiornato: canAddTeleportOption = " + canAddTeleportOption);
            });
        });
    }

    /** Evento della GUI di interazione: aggiunge Teleport solo se il flag è true */
    private static void registerGUIEvent() {
        EventObservable<PokemonInteractionGUICreationEvent> observable = CobblemonEvents.POKEMON_INTERACTION_GUI_CREATION; observable
                .subscribe(Priority.NORMAL, event -> {
                    if (!canAddTeleportOption) return null;

                    Identifier icon = Identifier.of(HMMod.MOD_ID, "textures/gui/teleport/icon_teleport.png");
                    String tooltip = "Teleport";

                    kotlin.jvm.functions.Function0<Vector3f> colourFunc = () -> new Vector3f(1f, 1f, 1f);

                    kotlin.jvm.functions.Function0<Unit> onPressFunc = () -> {
                        MinecraftClient mc = MinecraftClient.getInstance();

                        // Ricezione animazione dal server
                        ClientPlayNetworking.registerGlobalReceiver(AnimationHMPacketS2C.ID, (payload, context) -> {
                            mc.execute(() -> mc.setScreen(new AnimationMoveScreen(Text.literal("AnimationMoveScreen"), payload.pokemon())));
                        });

                        // Invia pacchetto C2S per Teleport
                        if (mc.player != null) {
                            ClientPlayNetworking.send(new TeleportPacketC2S(mc.player.getBlockPos()));
                        }

                        System.out.println("[TeleportMenuOption] Opzione Teleport premuta!");
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
                    canAddTeleportOption = false;
                    super.removed();
                    System.out.println("[FlashMenuOption] Flag canAddTeleportOption resettato");
                }
            });
        });
    }
}
