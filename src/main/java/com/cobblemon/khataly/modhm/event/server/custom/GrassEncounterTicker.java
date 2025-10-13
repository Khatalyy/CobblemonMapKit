package com.cobblemon.khataly.modhm.event.server.custom;

import com.cobblemon.khataly.modhm.config.GrassZonesConfig;
import com.cobblemon.khataly.modhm.util.PlayerUtils;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Incontri "a passo" dentro le Grass Zones, indipendenti dall'erba decorativa.
 * - Rispetta il cooldown per giocatore.
 * - Triggera solo quando il player cambia blocco.
 * - Filtra gli spawn per fascia oraria (DAY/NIGHT/BOTH).
 * - Applica shiny rate per zona (1 su N); -1 = usa default globale.
 */
public class GrassEncounterTicker {

    // ~3s a 20 TPS
    private static final int ENCOUNTER_COOLDOWN_TICKS = 60;
    // 8% per step
    private static final double BASE_STEP_CHANCE = 0.08;
    // Default globale shiny 1/N (usato se la zona non imposta shinyOdds o mette -1)
    private static final int DEFAULT_GLOBAL_SHINY_ODDS = 4096;

    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_BLOCK = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GrassEncounterTicker::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // scala il cooldown
            COOLDOWN.computeIfPresent(player.getUuid(), (id, cd) -> Math.max(0, cd - 1));

            if (!isValidStepState(player)) continue;

            // controllo movimento a nuovo blocco
            BlockPos now = player.getBlockPos();
            BlockPos prev = LAST_BLOCK.put(player.getUuid(), now);
            if (prev != null && prev.equals(now)) continue;

            // se in cooldown, salta
            if (COOLDOWN.getOrDefault(player.getUuid(), 0) > 0) continue;

            var world = player.getWorld();
            var wk = world.getRegistryKey();

            // trova zone alla posizione esatta (incluso Y)
            var zones = GrassZonesConfig.findAt(wk, now.getX(), now.getY(), now.getZ());
            if (zones.isEmpty()) continue;

            // scegliamo la prima zona valida; se ne vuoi più d'una, puoi randomizzarle
            GrassZonesConfig.Zone zone = zones.getFirst();
            if (now.getY() != zone.y()) continue;

            // roll chance base per step
            Random rng = player.getRandom();
            if (rng.nextDouble() >= BASE_STEP_CHANCE) continue;

            // filtra gli spawn per fascia oraria
            List<GrassZonesConfig.SpawnEntry> pool = filterByTime(zone.spawns(), world);
            if (pool.isEmpty()) continue;

            // scelta pesata
            GrassZonesConfig.SpawnEntry choice = weightedRandom(pool, rng);
            if (choice == null) continue;

            int levelRange = Math.max(1, choice.maxLevel - choice.minLevel + 1);
            int level = choice.minLevel + rng.nextInt(levelRange);

            // shiny roll per zona
            int shinyOdds = getZoneShinyOddsOrDefault(zone);
            boolean isShiny = rollShiny(rng, shinyOdds);

            // per ora solo Singles
            BattleFormat format = BattleFormat.Companion.getGEN_9_SINGLES();

            if (startWildBattle(player, choice.species, level, format, isShiny)) {
                COOLDOWN.put(player.getUuid(), ENCOUNTER_COOLDOWN_TICKS);
            }
        }
    }

    /** Condizioni minime perché il passo sia valido per un encounter. */
    private static boolean isValidStepState(ServerPlayerEntity p) {
        if (p.isSpectator()) return false;
        if (!p.isOnGround()) return false;
        return !p.hasVehicle();
    }

    /** Applica il filtro DAY/NIGHT/BOTH rispetto al ciclo vanilla 0..23999. */
    private static List<GrassZonesConfig.SpawnEntry> filterByTime(List<GrassZonesConfig.SpawnEntry> entries, net.minecraft.world.World world) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();

        // Dimensioni senza sky light (Nether/End o custom) => non filtriamo per orario
        if (!world.getDimension().hasSkyLight()) return entries;

        long dayTime = world.getTimeOfDay() % 24000L; // 0..23999
        boolean isDay = dayTime < 12000L;             // 0..11999 = giorno; 12000..23999 = notte

        List<GrassZonesConfig.SpawnEntry> out = new ArrayList<>(entries.size());
        for (GrassZonesConfig.SpawnEntry e : entries) {
            if (e == null) continue;
            if (e.time == GrassZonesConfig.TimeBand.BOTH) {
                out.add(e);
            } else if (e.time == GrassZonesConfig.TimeBand.DAY && isDay) {
                out.add(e);
            } else if (e.time == GrassZonesConfig.TimeBand.NIGHT && !isDay) {
                out.add(e);
            }
        }
        return out;
    }

    /** Estrazione pesata classica. Ritorna null se lista vuota o pesi non validi. */
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

    /** Ritorna le shiny odds della zona, oppure il default globale se la zona non imposta. */
    private static int getZoneShinyOddsOrDefault(GrassZonesConfig.Zone zone) {
        return (zone.shinyOdds() <= 0) ? DEFAULT_GLOBAL_SHINY_ODDS : zone.shinyOdds();
    }

    /** Tira un dado 1 su N. */
    private static boolean rollShiny(Random rng, int odds) {
        if (odds <= 1) return true;   // 1 su 1 => sempre shiny
        int v = rng.nextInt(odds);    // [0, odds)
        return v == 0;
    }

    /**
     * Avvia una battle PvE 1v1; passa sempre il party per evitare crash.
     * Supporta shiny via flag.
     */
    private static boolean startWildBattle(ServerPlayerEntity player, String speciesId, int level, BattleFormat format, boolean shiny) {
        var server = player.getServer();
        if (server == null) return false;
        if (!PlayerUtils.hasUsablePokemon(player)) return false;

        String key = speciesId == null ? "" : speciesId.toLowerCase(Locale.ROOT);
        if (key.contains(":")) key = key.substring(key.indexOf(':') + 1);

        Species species = PokemonSpecies.INSTANCE.getByName(key);
        if (species == null) return false;

        Pokemon pokemon = new Pokemon();
        pokemon.setSpecies(species);
        pokemon.setLevel(level);
        pokemon.setShiny(shiny);
        pokemon.initializeMoveset(true);
        pokemon.heal();

        var sw = (net.minecraft.server.world.ServerWorld) player.getWorld();
        BlockPos base = player.getBlockPos();
        BlockPos pos = base.add(1, 0, 0);
        Vec3d vec = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        PokemonEntity entity = pokemon.sendOut(sw, vec, null, e -> null);
        if (entity == null) return false;

        var party = Cobblemon.INSTANCE.getStorage().getParty(player); // non-null in 1.6

        // avvia la battle dopo 1–2 tick
        server.execute(() -> server.execute(() -> {
            if (!entity.isRemoved() && entity.isAlive()) {
                BattleBuilder.INSTANCE.pve(
                        player,
                        entity,
                        null,        // leading
                        format,      // singles
                        false,       // cloneParties
                        false,       // healFirst
                        16f,         // fleeDistance
                        party
                );
            }
        }));

        return true;
    }
}
