package com.cobblemon.khataly.modhm.event.server.custom;

import com.cobblemon.khataly.modhm.config.GrassZonesConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random; // <-- Random di Mojang

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Controlla i passi dei player dentro le zone e lancia incontri casuali. */
public class GrassEncounterTicker {

    private static final int ENCOUNTER_COOLDOWN_TICKS = 60; // ~3s
    private static final double BASE_STEP_CHANCE = 0.08;    // 8% per step

    private static final Map<UUID, Integer> cooldown = new HashMap<>();
    private static final Map<UUID, BlockPos> lastBlock = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GrassEncounterTicker::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            // scala cooldown
            cooldown.computeIfPresent(p.getUuid(), (id, cd) -> Math.max(0, cd - 1));

            BlockPos now = p.getBlockPos();
            BlockPos prev = lastBlock.put(p.getUuid(), now);
            if (prev != null && prev.equals(now)) continue; // non si è spostato di blocco

            var wk = p.getWorld().getRegistryKey();
            var zones = GrassZonesConfig.findAt(wk, now.getX(), now.getY(), now.getZ());
            if (zones.isEmpty()) continue;

            if (!p.isOnGround() || p.isSpectator()) continue;
            if (cooldown.getOrDefault(p.getUuid(), 0) > 0) continue;

            Random rng = p.getRandom();
            if (rng.nextDouble() < BASE_STEP_CHANCE) {
                var zone = zones.getFirst();
                if (zone.spawns().isEmpty()) continue;

                var choice = weightedRandom(zone.spawns(), rng);
                if (choice == null) continue;

                int levelRange = choice.maxLevel - choice.minLevel + 1;
                int level = choice.minLevel + (levelRange > 0 ? rng.nextInt(levelRange) : 0);

                // TODO: integrazione Cobblemon — avviare incontro selvatica
                p.sendMessage(net.minecraft.text.Text.literal(
                        "Incontro selvatico: " + choice.species + " lvl " + level), true);

                cooldown.put(p.getUuid(), ENCOUNTER_COOLDOWN_TICKS);
            }
        }
    }

    private static GrassZonesConfig.SpawnEntry weightedRandom(List<GrassZonesConfig.SpawnEntry> entries, Random r) {
        int total = 0; for (var e : entries) total += Math.max(0, e.weight);
        if (total <= 0) return null;
        int roll = r.nextInt(total);
        int acc = 0;
        for (var e : entries) {
            acc += Math.max(0, e.weight);
            if (roll < acc) return e;
        }
        return null;
    }
}
