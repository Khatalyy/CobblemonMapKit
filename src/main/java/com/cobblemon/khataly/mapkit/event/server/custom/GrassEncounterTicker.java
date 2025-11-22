package com.cobblemon.khataly.mapkit.event.server.custom;

import com.cobblemon.khataly.mapkit.config.GrassZonesConfig;
import com.cobblemon.khataly.mapkit.util.PlayerUtils;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.*;

/**
 * Incontri “a passo” nelle Grass Zones (supporto minY..maxY):
 * - Cooldown per player, trigger su cambio blocco;
 * - Filtro DAY/NIGHT/BOTH;
 * - Shiny odds per zona (1/N; -1 = default globale);
 * - Aspect opzionale per variante regionale (es. "alola");
 * - Non spawna se il player è già in battaglia;
 * - Se il player fugge dalla lotta, il selvatico viene despawnato.
 */
public class GrassEncounterTicker {

    // ~3s a 20 TPS
    private static final int ENCOUNTER_COOLDOWN_TICKS = 60;
    // 8% per step
    private static final double BASE_STEP_CHANCE = 0.08;
    // Default globale shiny 1/N (se zona mette -1 o non specifica)
    private static final int DEFAULT_GLOBAL_SHINY_ODDS = 4096;

    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_BLOCK = new HashMap<>();
    /** PlayerUUID -> WildEntityUUID (solo per incontri generati da questo ticker). */
    private static final Map<UUID, UUID> ACTIVE_WILD = new HashMap<>();

    private static volatile boolean EVENTS_HOOKED = false;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GrassEncounterTicker::onServerTick);
        hookBattleFleeDespawnOnce();
    }

    /** Despawn del selvatico quando il player fugge dalla battaglia. */
    private static void hookBattleFleeDespawnOnce() {
        if (EVENTS_HOOKED) return;
        EVENTS_HOOKED = true;

        CobblemonEvents.BATTLE_FLED.subscribe(
                Priority.NORMAL,
                (BattleFledEvent event) -> {
                    try {
                        PlayerBattleActor actor = event.getPlayer();

                        ServerPlayerEntity player = actor.getEntity();
                        if (player == null) return kotlin.Unit.INSTANCE;

                        UUID pid = player.getUuid();
                        UUID wid = ACTIVE_WILD.remove(pid);
                        if (wid == null) return kotlin.Unit.INSTANCE;

                        var sw = (net.minecraft.server.world.ServerWorld) player.getWorld();
                        var ent = sw.getEntity(wid);
                        if (ent instanceof PokemonEntity pe && pe.isAlive() && !pe.isRemoved()) {
                            pe.discard(); // despawn immediato del selvatico
                        }
                    } catch (Throwable ignored) {}
                    return kotlin.Unit.INSTANCE;
                }
        );
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

            // in cooldown?
            if (COOLDOWN.getOrDefault(player.getUuid(), 0) > 0) continue;

            var world = player.getWorld();
            var wk = world.getRegistryKey();

            // zone alla posizione esatta (X/Z nel box e Y nel range minY..maxY)
            var zones = GrassZonesConfig.findAt(wk, now.getX(), now.getY(), now.getZ());
            if (zones.isEmpty()) continue;

            // scegliamo la prima zona valida; si può randomizzare se serve
            GrassZonesConfig.Zone zone = zones.getFirst();
            // (Niente check su Y singolo: il match per Y è già garantito da findAt)

            // già in battaglia? niente encounter
            if (isInBattle(player)) continue;

            // roll chance base per step
            Random rng = player.getRandom();
            if (rng.nextDouble() >= BASE_STEP_CHANCE) continue;

            // filtra spawns per fascia oraria
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

            // singles
            BattleFormat format = BattleFormat.Companion.getGEN_9_SINGLES();

            if (startWildBattle(player, choice.species, level, format, isShiny, choice.aspect)) {
                COOLDOWN.put(player.getUuid(), ENCOUNTER_COOLDOWN_TICKS);
            }
        }
    }

    /** Condizioni minime per il passo valido. */
    private static boolean isValidStepState(ServerPlayerEntity p) {
        if (p.isSpectator()) return false;
        if (!p.isOnGround()) return false;
        if (p.hasVehicle()) return false;
        // evita incontri durante/alla soglia di una battaglia
        return !isInBattle(p);
    }

    private static boolean isInBattle(ServerPlayerEntity player) {
        try {
            var reg = Cobblemon.INSTANCE.getBattleRegistry();
            return reg.getBattleByParticipatingPlayer(player) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Applica il filtro DAY/NIGHT/BOTH rispetto al ciclo vanilla. */
    private static List<GrassZonesConfig.SpawnEntry> filterByTime(List<GrassZonesConfig.SpawnEntry> entries, net.minecraft.world.World world) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();

        // Dimensioni senza skylight => non filtriamo
        if (!world.getDimension().hasSkyLight()) return entries;

        long dayTime = world.getTimeOfDay() % 24000L; // 0..23999
        boolean isDay = dayTime < 12000L;             // 0..11999 = giorno

        List<GrassZonesConfig.SpawnEntry> out = new ArrayList<>(entries.size());
        for (GrassZonesConfig.SpawnEntry e : entries) {
            if (e == null) continue;
            switch (e.time) {
                case BOTH -> out.add(e);
                case DAY -> { if (isDay) out.add(e); }
                case NIGHT -> { if (!isDay) out.add(e); }
            }
        }
        return out;
    }

    /** Estrazione pesata classica. */
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

    /** Shiny odds della zona o default globale. */
    private static int getZoneShinyOddsOrDefault(GrassZonesConfig.Zone zone) {
        return (zone.shinyOdds() <= 0) ? DEFAULT_GLOBAL_SHINY_ODDS : zone.shinyOdds();
    }

    /** 1 su N. */
    private static boolean rollShiny(Random rng, int odds) {
        if (odds <= 1) return true;
        return rng.nextInt(odds) == 0;
    }

    /**
     * Avvia una battle PvE 1v1; passa sempre il party; supporta shiny e aspect.
     */
    private static boolean startWildBattle(ServerPlayerEntity player,
                                           String speciesId,
                                           int level,
                                           BattleFormat format,
                                           boolean shiny,
                                           String aspect) {
        var server = player.getServer();
        if (server == null) return false;
        if (!PlayerUtils.hasUsablePokemon(player)) return false;

        // Non iniziare se già in battaglia
        if (isInBattle(player)) return false;

        String key = speciesId == null ? "" : speciesId.toLowerCase(Locale.ROOT);
        if (key.contains(":")) key = key.substring(key.indexOf(':') + 1);

        Species species = PokemonSpecies.getByName(key);
        if (species == null) return false;

        Pokemon pokemon = new Pokemon();
        pokemon.setSpecies(species);

        // Aspect opzionale (variante regionale)
        if (aspect != null && !aspect.isBlank()) {
            pokemon.setForcedAspects(Collections.singleton(aspect.toLowerCase(Locale.ROOT)));
            try { pokemon.updateForm(); } catch (Throwable ignored) {}
        }

        pokemon.setLevel(level);
        pokemon.setShiny(shiny);
        pokemon.initializeMoveset(true);
        pokemon.heal();

        var sw = (ServerWorld) player.getWorld();

        // -------- NUOVO: spawn alla stessa Y del player --------
        // targetY = Y "reale" del player (non solo il BlockPos.getY()).
        final double targetY = player.getY();

        // proviamo alcuni offset vicino al player mantenendo la stessa Y
        // ordine: +X, -X, +Z, -Z, +X+Z, -X-Z
        Vec3d spawnPos = null;
        Vec3d base = player.getPos(); // double-precision position
        Vec3d[] candidates = new Vec3d[] {
                new Vec3d(base.x + 1.0, targetY, base.z),
                new Vec3d(base.x - 1.0, targetY, base.z),
                new Vec3d(base.x, targetY, base.z + 1.0),
                new Vec3d(base.x, targetY, base.z - 1.0),
                new Vec3d(base.x + 1.0, targetY, base.z + 1.0),
                new Vec3d(base.x - 1.0, targetY, base.z - 1.0)
        };

        for (Vec3d cand : candidates) {
            // centro del blocco più vicino
            BlockPos bp = BlockPos.ofFloored(cand.x, cand.y, cand.z);
            // deve esserci aria nello spazio del Pokémon (grezzo: blocco target e quello sopra aria)
            boolean airHere  = sw.isAir(bp);
            boolean airAbove = sw.isAir(bp.up());
            // e terreno solido sotto (per evitare di spawna­re sospesi)
            boolean solidBelow = !sw.isAir(bp.down());
            if (airHere && airAbove && solidBelow) {
                spawnPos = new Vec3d(bp.getX() + 0.5, targetY, bp.getZ() + 0.5);
                break;
            }
        }

        // fallback: comunque a +1 in X, stessa Y
        if (spawnPos == null) {
            spawnPos = new Vec3d(Math.floor(base.x) + 1.5, targetY, Math.floor(base.z) + 0.5);
        }
        // -------------------------------------------------------

        PokemonEntity entity = pokemon.sendOut(sw, spawnPos, null, e -> null);
        if (entity == null) return false;

        // Traccia selvatico per possibile despawn in caso di fuga
        ACTIVE_WILD.put(player.getUuid(), entity.getUuid());

        var party = Cobblemon.INSTANCE.getStorage().getParty(player);

        // Avvia la battle dopo 1–2 tick per sicurezza
        server.execute(() -> server.execute(() -> {
            if (!entity.isRemoved() && entity.isAlive() && !isInBattle(player)) {
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
