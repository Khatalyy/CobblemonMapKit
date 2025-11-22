package com.cobblemon.khataly.mapkit.event.client.custom;

import com.cobblemon.khataly.mapkit.CobblemonMapKitMod;
import com.cobblemon.khataly.mapkit.networking.packet.*;
import com.cobblemon.khataly.mapkit.networking.packet.teleport.TeleportMenuS2CPacket;
import com.cobblemon.khataly.mapkit.networking.packet.teleport.TeleportPacketC2S;
import com.cobblemon.khataly.mapkit.screen.custom.AnimationMoveScreen;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.interaction.PokemonInteractionGUICreationEvent;
import com.cobblemon.mod.common.api.reactive.EventObservable;
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelOption;
import kotlin.Unit;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
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
                System.out.println("[TeleportMenuOption] Flag aggiornato: canAddTeleportOption = " + canAddTeleportOption);
            });
        });
    }

    /** Evento della GUI di interazione: aggiunge Teleport solo se il flag è true */
    private static void registerGUIEvent() {
        EventObservable<PokemonInteractionGUICreationEvent> observable = CobblemonEvents.POKEMON_INTERACTION_GUI_CREATION; observable
                .subscribe(Priority.NORMAL, event -> {
                    if (!canAddTeleportOption) return null;

                    Identifier icon = Identifier.of(CobblemonMapKitMod.MOD_ID, "textures/gui/teleport/icon_teleport.png");
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

                    InteractWheelOption option = new InteractWheelOption(
                            icon,        // iconResource
                            null,        // secondaryIconResource
                            true,        // enabled (o una condizione tua)
                            tooltip,     // tooltipText
                            colourFunc,  // colour
                            onPressFunc  // onPress
                    );
                    event.addFillingOption(option);
                    return Unit.INSTANCE;
                });
    }

    /** Resetta il flag quando si chiude la GUI di interazione */
    private static void registerGUICloseListener() {
        // Intercetta tutte le schermate rimosse
        MinecraftClient.getInstance().execute(() -> {
            assert MinecraftClient.getInstance().currentScreen != null;
            ScreenEvents.remove(MinecraftClient.getInstance().currentScreen).register(screen -> {
                if (screen.getClass().getSimpleName().equals("PokemonInteractionScreen")) {
                    canAddTeleportOption = false;
                    System.out.println("[TeleportMenuOption] Flag canAddTeleportOption resettato");
                }
            });
        });
    }
}
