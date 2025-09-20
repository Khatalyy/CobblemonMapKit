package com.cobblemon.khataly.modhm.event.server.custom;

import com.cobblemon.khataly.modhm.config.FlyTargetConfig;
import com.cobblemon.khataly.modhm.networking.packet.FlyMenuS2CPacket;
import com.cobblemon.khataly.modhm.util.PartyUtils;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public class ServerFlyHandler {
    public static void register() {
        CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.NORMAL, event -> {

            // Prende tutti i target dal config
            var targets = FlyTargetConfig.getAllTargets();

            // Invia il pacchetto al client
            ServerPlayerEntity player = event.getPokemon().getOwnerPlayer();
            if (player == null) return null; // NPC
            boolean canFly = PartyUtils.pokemonHasMoveToGUI(player,event.getPokemon().getUuid(), "fly");
            System.out.println("hasmoveguiFly: " + canFly);
            ServerPlayNetworking.send(player, FlyMenuS2CPacket.fromServerData(event.getPokemon().getUuid(), canFly, targets));

            return null;
        });
    }
}
