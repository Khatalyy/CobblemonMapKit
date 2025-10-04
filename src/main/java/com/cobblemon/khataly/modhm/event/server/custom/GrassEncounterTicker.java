package com.cobblemon.khataly.modhm.event.server.custom;

import com.cobblemon.khataly.modhm.config.GrassZonesConfig;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random; // Mojang Random
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Controlla i passi dei player dentro le zone e lancia incontri casuali. */
public class GrassEncounterTicker {

    private static final Logger LOGGER = LoggerFactory.getLogger("GrassEncounterTicker");

    private static final int ENCOUNTER_COOLDOWN_TICKS = 60; // ~3s
    private static final double BASE_STEP_CHANCE = 0.08;    // 8% per step

    private static final Map<UUID, Integer> cooldown = new HashMap<>();
    private static final Map<UUID, BlockPos> lastBlock = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GrassEncounterTicker::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            // decrementa cooldown
            cooldown.computeIfPresent(p.getUuid(), (id, cd) -> Math.max(0, cd - 1));

            // skip stati non validi
            if (p.isSpectator() || p.isCreative() || !p.isOnGround() || p.hasVehicle()) continue;

            // si è spostato di blocco?
            BlockPos now = p.getBlockPos();
            BlockPos prev = lastBlock.put(p.getUuid(), now);
            if (prev != null && prev.equals(now)) continue;

            var world = p.getWorld();
            var wk = world.getRegistryKey();
            var zones = GrassZonesConfig.findAt(wk, now.getX(), now.getY(), now.getZ());
            if (zones.isEmpty()) continue;

            // cooldown attivo?
            if (cooldown.getOrDefault(p.getUuid(), 0) > 0) continue;

            // usa la prima zona trovata (se ne supporti più d'una qui puoi iterare)
            var zone = zones.getFirst();

            // vincolo che la Y del player coincida con la Y della zona (coerente con clearGrass)
            if (now.getY() != zone.y()) continue;

            // controlla che il blocco ai piedi sia erba decorativa
            BlockState stAtFeet = world.getBlockState(now);
            if (!isDecorativeGrass(stAtFeet)) continue;

            // roll probabilità
            Random rng = p.getRandom();
            if (rng.nextDouble() >= BASE_STEP_CHANCE) continue;

            // selezione ponderata dello spawn
            var choice = weightedRandom(zone.spawns(), rng);
            if (choice == null) continue;

            int levelRange = choice.maxLevel - choice.minLevel + 1;
            int level = choice.minLevel + (levelRange > 0 ? rng.nextInt(levelRange) : 0);

            // === QUI avvii davvero l'incontro senza specificare mosse ===
            boolean started = startWildBattle(p, choice.species, level);
            if (started) {
                cooldown.put(p.getUuid(), ENCOUNTER_COOLDOWN_TICKS);
            } else {
                // fallback: feedback se la specie non è stata risolta
                p.sendMessage(Text.literal("§cCould not start battle for species: " + choice.species), true);
            }
        }
    }

    /**
     * Crea un Pokémon solo con specie+livello, spawna la relativa entity e avvia la PvE battle.
     * NESSUNA mossa viene hardcodata: Cobblemon userà il moveset di default per il livello.
     */
    private static boolean startWildBattle(ServerPlayerEntity player, String speciesId, int level) {
        var server = player.getServer();
        if (server == null) return false;

        // normalizza la key
        String key = speciesId.toLowerCase();
        if (key.contains(":")) key = key.substring(key.indexOf(':') + 1);

        Species species = PokemonSpecies.INSTANCE.getByName(key);
        if (species == null) {
            LOGGER.warn("Species '{}' not found", speciesId);
            return false;
        }

        // crea il Pokémon (NO initialize())
        Pokemon pokemon = new Pokemon();
        pokemon.setSpecies(species);
        pokemon.setLevel(level);
        pokemon.initializeMoveset(true); // mosse corrette per il livello
        pokemon.heal();

        // posizione di spawn vicino al player
        BlockPos base = player.getBlockPos();
        BlockPos pos = base.add(1, 0, 0);
        var sw = (net.minecraft.server.world.ServerWorld) player.getWorld();
        var vec = new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        PokemonEntity entity =
                pokemon.sendOut(sw, vec, null, e -> {
                    return null;
                });

        if (entity == null) {
            LOGGER.warn("sendOut returned null for {}", speciesId);
            return false;
        }

        // avvia la PvE DOPO 1-2 tick per evitare "battle col nulla"
        server.execute(() -> server.execute(() -> {
            if (!entity.isRemoved() && entity.isAlive()) {
                com.cobblemon.mod.common.battles.BattleBuilder.INSTANCE.pve(player, entity);
            }
        }));

        return true;
    }



    /** true se è SHORT_GRASS / TALL_GRASS o legacy GRASS (per vecchie mapping). */
    private static boolean isDecorativeGrass(BlockState st) {
        boolean tall = st.isOf(Blocks.TALL_GRASS);
        boolean shortG = false;
        boolean legacy = false;

        Block shortBlock = tryShortGrass();
        if (shortBlock != null) shortG = st.isOf(shortBlock);

        try {
            // alcune mapping vecchie usano Blocks.GRASS per il ciuffo corto
            legacy = st.isOf((Block) Blocks.class.getField("GRASS").get(null));
        } catch (Exception ignored) {}

        return tall || shortG || legacy;
    }

    private static Block tryShortGrass() {
        try {
            return (Block) Blocks.class.getField("SHORT_GRASS").get(null);
        } catch (Exception e) {
            try {
                return (Block) Blocks.class.getField("GRASS").get(null);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static GrassZonesConfig.SpawnEntry weightedRandom(List<GrassZonesConfig.SpawnEntry> entries, Random r) {
        if (entries == null || entries.isEmpty()) return null;
        int total = 0;
        for (var e : entries) total += Math.max(0, e.weight);
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
