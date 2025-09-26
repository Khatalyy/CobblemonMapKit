package com.cobblemon.khataly.modhm.util;


import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;


public class PlayerUtils {
    /**
     * Ritorna se il party contiene la move
     */
    public static boolean hasMove(ServerPlayerEntity player,String hm) {
        MoveTemplate moveToFind = Moves.INSTANCE.getByName(hm);
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        for (Pokemon pokemon : party) {
            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move.getTemplate() == moveToFind) {
                    return true;
                }
            }
        }

        return false;
    }

    public static RenderablePokemon getRenderPokemonByMove(ServerPlayerEntity player,String hm) {
        MoveTemplate HM = Moves.INSTANCE.getByName(hm);
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        for (Pokemon pokemon : party) {
            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move.getTemplate() == HM) {
                    return pokemon.asRenderablePokemon();
                }
            }
        }
        return null;
    }

    /**
     * Ritorna se il pokemon che conosce hm specifica
     */
    public static Boolean pokemonHasMoveToGUI(ServerPlayerEntity player, UUID pokemonId,String hm) {
        MoveTemplate hmToFind = Moves.INSTANCE.getByName(hm);
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        for (Pokemon pokemon : party) {
            if (!pokemon.getUuid().equals(pokemonId)) continue;

            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move.getTemplate() == hmToFind) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Controlla se il player ha l'item richiesto per la feature.
     * @param player Player da controllare
     * @param itemId Identificatore dell'item (es. "minecraft:iron_pickaxe")
     * @return true se il player ha almeno 1 di quell'item, false altrimenti
     */
    public static boolean hasRequiredItem(ServerPlayerEntity player, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return true; // 🔹 Se non è richiesto un item, consideriamo valido
        }

        try {
            Identifier id =  Identifier.of(itemId);
            Item required = Registries.ITEM.get(id);

            if (required == Items.AIR) {
                return false; // l'item non esiste
            }

            // Scorri l'inventario del player
            for (ItemStack stack : player.getInventory().main) {
                if (stack != null && stack.getItem() == required && stack.getCount() > 0) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }


}