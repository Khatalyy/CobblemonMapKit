package com.cobblemon.khataly.modhm.util;


import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;


public class PartyUtils {

    /**
     * Ritorna se il party contiene spaccaroccia
     */
    public static boolean hasRockSmash(ServerPlayerEntity player) {
        MoveTemplate rockSmash = Moves.INSTANCE.getByName("rocksmash");
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        for (Pokemon pokemon : party) {
            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move.getTemplate() == rockSmash) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Ritorna se il party contiene taglio
     */
    public static boolean hasCut(ServerPlayerEntity player) {
        MoveTemplate cut = Moves.INSTANCE.getByName("cut");
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        for (Pokemon pokemon : party) {
            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move.getTemplate() == cut) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Ritorna se il party contiene forza
     */
    public static boolean hasStrength(ServerPlayerEntity player) {
        MoveTemplate strength = Moves.INSTANCE.getByName("strength");
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        for (Pokemon pokemon : party) {
            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move.getTemplate() == strength) {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * Ritorna se il party contiene fly
     */
    public static boolean hasFly(ServerPlayerEntity player) {
        MoveTemplate fly = Moves.INSTANCE.getByName("fly");
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        for (Pokemon pokemon : party) {
            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move.getTemplate() == fly) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Ritorna il pokemon che conosce fly
     */
    public static Boolean pokemonHasFlyInParty(ServerPlayerEntity player, UUID pokemonId) {
        MoveTemplate fly = Moves.INSTANCE.getByName("fly");
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        for (Pokemon pokemon : party) {
            if (!pokemon.getUuid().equals(pokemonId)) continue;

            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move.getTemplate() == fly) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Ritorna il pokemon che conosce fly
     */
    public static Boolean pokemonHasFlashInParty(ServerPlayerEntity player, UUID pokemonId) {
        MoveTemplate fly = Moves.INSTANCE.getByName("flash");
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        for (Pokemon pokemon : party) {
            if (!pokemon.getUuid().equals(pokemonId)) continue;

            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move.getTemplate() == fly) {
                    return true;
                }
            }
        }
        return false;
    }


}