package com.cobblemon.khataly.mapkit.util;

import com.cobblemon.khataly.mapkit.item.custom.BadgeCaseItem;
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

import java.util.Iterator;
import java.util.UUID;

public class PlayerUtils {

    /* ================= Cobblemon helpers ================= */

    public static boolean hasMove(ServerPlayerEntity player, String hm) {
        MoveTemplate moveToFind = Moves.getByName(hm);
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (Pokemon pokemon : party) {
            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move.getTemplate() == moveToFind) return true;
            }
        }
        return false;
    }
    public static boolean hasUsablePokemon(ServerPlayerEntity player) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (Pokemon pk : party) {
            if (pk != null && !pk.isFainted()) return true;
        }
        return false;
    }

    public static RenderablePokemon getRenderPokemonByMove(ServerPlayerEntity player, String hm) {
        MoveTemplate HM = Moves.getByName(hm);
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (Pokemon pokemon : party) {
            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move.getTemplate() == HM) return pokemon.asRenderablePokemon();
            }
        }
        return null;
    }

    public static Boolean pokemonHasMoveToGUI(ServerPlayerEntity player, UUID pokemonId, String hm) {
        MoveTemplate hmToFind = Moves.getByName(hm);
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (Pokemon pokemon : party) {
            if (!pokemon.getUuid().equals(pokemonId)) continue;
            for (Move move : pokemon.getMoveSet().getMoves()) {
                if (move.getTemplate() == hmToFind) return true;
            }
        }
        return false;
    }

    /* ================= Requirements (item in inventario o dentro Badge Case) ================= */

    /**
     * true se il player possiede l'item richiesto:
     * 1) direttamente in inventario (main/offhand/armor)
     * 2) oppure dentro QUALSIASI Badge Case nel suo inventario (match per Identifier esatto).
     */
    public static boolean hasRequiredItem(ServerPlayerEntity player, String itemId) {
        if (itemId == null || itemId.isEmpty()) return true;

        final Identifier id;
        try {
            id = Identifier.of(itemId);
        } catch (Exception e) {
            return false; // id malformato
        }

        Item required = Registries.ITEM.get(id);
        if (required == Items.AIR) return false; // item inesistente

        // (1) Controllo inventario “normale”
        for (ItemStack stack : iterateAllStacks(player)) {
            if (!stack.isEmpty() && stack.getItem() == required && stack.getCount() > 0) {
                return true;
            }
        }

        // (2) Controllo dentro TUTTI i Badge Case (usando l'API senza decadimento)
        for (ItemStack stack : iterateAllStacks(player)) {
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof BadgeCaseItem)) continue;
            if (BadgeCaseItem.readBadges(stack).contains(id)) return true;
        }

        return false;
    }

    /** Itera su main, offhand, armor. */
    private static Iterable<ItemStack> iterateAllStacks(ServerPlayerEntity player) {
        return () -> new Iterator<>() {
            private int phase = 0; // 0=main, 1=offhand, 2=armor
            private int idx = 0;

            @Override
            public boolean hasNext() {
                switch (phase) {
                    case 0:
                        if (idx < player.getInventory().main.size()) return true;
                        phase = 1; idx = 0;
                    case 1:
                        if (idx < player.getInventory().offHand.size()) return true;
                        phase = 2; idx = 0;
                    case 2:
                        return idx < player.getInventory().armor.size();
                    default:
                        return false;
                }
            }

            @Override
            public ItemStack next() {
                return switch (phase) {
                    case 0 -> player.getInventory().main.get(idx++);
                    case 1 -> player.getInventory().offHand.get(idx++);
                    case 2 -> player.getInventory().armor.get(idx++);
                    default -> ItemStack.EMPTY;
                };
            }
        };
    }
}
