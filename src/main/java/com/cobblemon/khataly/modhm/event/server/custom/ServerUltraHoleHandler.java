package com.cobblemon.khataly.modhm.event.server.custom;

import com.cobblemon.khataly.modhm.networking.packet.FlashMenuS2CPacket;
import com.cobblemon.khataly.modhm.networking.packet.UltraHoleMenuS2CPacket;
import com.cobblemon.khataly.modhm.util.PlayerUtils;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class ServerUltraHoleHandler {
    public static void register() {
        CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.NORMAL, event -> {

            // Invia il pacchetto al client
            ServerPlayerEntity player = event.getPokemon().getOwnerPlayer();
            if (player == null) return null; // NPC

            // Lista di mosse che abilitano l'UltraHole
            List<String> ultraHoleMoves = List.of("sunsteelstrike", "moongeistbeam");

            boolean canUltraHole = ultraHoleMoves.stream().anyMatch(
                    move -> PlayerUtils.pokemonHasMoveToGUI(player, event.getPokemon().getUuid(), move)
            );

            System.out.println("hasmoveguiUltraHole: " + canUltraHole);
            ServerPlayNetworking.send(player, UltraHoleMenuS2CPacket.fromServerData(event.getPokemon().getUuid(), canUltraHole));

            return null;
        });
    }
}
