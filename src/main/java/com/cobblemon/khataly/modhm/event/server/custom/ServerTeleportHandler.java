package com.cobblemon.khataly.modhm.event.server.custom;

import com.cobblemon.khataly.modhm.networking.packet.TeleportMenuS2CPacket;
import com.cobblemon.khataly.modhm.util.PartyUtils;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public class ServerTeleportHandler {
    public static void register() {
        CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.NORMAL, event -> {

            // Invia il pacchetto al client
            ServerPlayerEntity player = event.getPokemon().getOwnerPlayer();
            if (player == null) return null; // NPC

            boolean canTeleport = PartyUtils.pokemonHasMoveToGUI(player,event.getPokemon().getUuid(), "teleport");
            System.out.println("hasmoveguiTeleport: " + canTeleport);
            ServerPlayNetworking.send(player, TeleportMenuS2CPacket.fromServerData(event.getPokemon().getUuid(), canTeleport));

            return null;
        });
    }
}
