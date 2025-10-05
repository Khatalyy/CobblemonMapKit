package com.cobblemon.khataly.modhm.event.server.custom;

import com.cobblemon.khataly.modhm.config.LevelCapConfig;
import com.cobblemon.khataly.modhm.config.PlayerLevelCapProgress;
import com.cobblemon.khataly.modhm.util.LevelCapService;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import kotlin.Unit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Progression watcher (label-based):
 * - Scansiona a DATA_SYNCHRONIZED (login/ready)
 * - Scansiona periodicamente (1s)
 * - Quando trova un item collegato a una LABEL, applica la LABEL (se non già applicata)
 * Messaggi al player:
 *   - §a verde = progresso sbloccato / info positiva
 */
public final class LevelCapProgressionWatcher {

    private LevelCapProgressionWatcher() {}

    private static final Map<UUID, Long> nextScanAt = new HashMap<>();
    private static final long RESCAN_INTERVAL_MS = 1000L;

    public static void register() {
        CobblemonEvents.DATA_SYNCHRONIZED.subscribe(Priority.NORMAL, player -> {
            if (player != null) scheduleSoon(player.getUuid(), 0L);
            return Unit.INSTANCE;
        });

        ServerTickEvents.END_SERVER_TICK.register(LevelCapProgressionWatcher::tickServer);
    }

    /** Può essere richiamato da altri punti per forzare una scansione immediata. */
    public static void requestScan(ServerPlayerEntity player) {
        if (player != null) scheduleSoon(player.getUuid(), 0L);
    }

    // ================= CORE =================

    private static void tickServer(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = nextScanAt.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if (e.getValue() > now) continue;

            UUID uuid = e.getKey();
            it.remove();

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                scanAndApply(player);
                scheduleSoon(uuid, RESCAN_INTERVAL_MS);
            }
        }
    }

    private static void scanAndApply(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) return;

        Map<String, Integer> labelsCaps = LevelCapConfig.getAllLabelsWithCaps();
        if (labelsCaps.isEmpty()) return;

        UUID uuid = player.getUuid();
        int appliedNow = 0;

        // Per ogni LABEL configurata, guarda gli itemIds collegati
        for (String labelKey : labelsCaps.keySet()) {
            if (PlayerLevelCapProgress.isApplied(uuid, labelKey)) continue;

            var itemIds = LevelCapConfig.getItemIdsForLabel(labelKey);
            if (itemIds == null || itemIds.isEmpty()) continue;

            if (!hasAnyOf(player, itemIds)) continue;

            boolean added = PlayerLevelCapProgress.apply(uuid, labelKey);
            if (added) {
                appliedNow++;
                int newCap = labelsCaps.getOrDefault(labelKey, LevelCapConfig.getBaseCap());
                int effectiveAfter = Math.max(LevelCapService.getEffectiveCap(player), newCap);

                String display = LevelCapConfig.displayLabel(labelKey);
                player.sendMessage(Text.literal("§aProgress unlocked: §f" + display + " §a→ level cap now " + effectiveAfter + "."), false);
            }
        }

        if (appliedNow > 0) {
            int cap = LevelCapService.getEffectiveCap(player);
            player.sendMessage(Text.literal("§aYour effective cap is §f" + cap + "§a."), false);
        }
    }

    // ================ HELPERS ================

    private static void scheduleSoon(UUID uuid, long delayMs) {
        long when = System.currentTimeMillis() + Math.max(0L, delayMs);
        long existing = nextScanAt.getOrDefault(uuid, 0L);
        if (existing == 0L || when < existing) nextScanAt.put(uuid, when);
    }

    /** true se il player ha in inventario *almeno uno* degli itemIds. */
    private static boolean hasAnyOf(ServerPlayerEntity player, Collection<String> itemIds) {
        for (String raw : itemIds) {
            if (raw == null || raw.isBlank()) continue;
            if (hasItemInInventory(player, raw)) return true;
        }
        return false;
    }

    /** Checks if player's inventory contains at least one of itemId ("namespace:path"). */
    private static boolean hasItemInInventory(ServerPlayerEntity player, String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return false;

        Item target = Registries.ITEM.get(id);

        // main
        for (int i = 0; i < player.getInventory().main.size(); i++) {
            ItemStack s = player.getInventory().main.get(i);
            if (!s.isEmpty() && s.getItem() == target) return true;
        }
        // offhand
        for (int i = 0; i < player.getInventory().offHand.size(); i++) {
            ItemStack s = player.getInventory().offHand.get(i);
            if (!s.isEmpty() && s.getItem() == target) return true;
        }
        // armor
        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            ItemStack s = player.getInventory().armor.get(i);
            if (!s.isEmpty() && s.getItem() == target) return true;
        }
        return false;
    }
}
