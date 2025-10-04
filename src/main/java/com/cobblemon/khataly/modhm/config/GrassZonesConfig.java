package com.cobblemon.khataly.modhm.config;

import com.cobblemon.khataly.modhm.HMMod;
import com.google.gson.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;

/** Gestisce salvataggio/caricamento delle Zone d'Erba. */
public class GrassZonesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/modhm/grass_zones.json");
    private static final int CURRENT_SCHEMA_VERSION = 1;

    // ======== DATA MODEL ========
    public static final class SpawnEntry {
        public final String species;  // es. "cobblemon:oddish"
        public final int minLevel;
        public final int maxLevel;
        public final int weight;

        public SpawnEntry(String species, int minLevel, int maxLevel, int weight) {
            this.species = species;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.weight = weight;
        }
    }

    public static final class Zone {
        private final UUID id;
        private final RegistryKey<World> worldKey;
        private final int minX, minZ, maxX, maxZ;
        private final int y;
        private final long timeCreated;
        private final List<SpawnEntry> spawns;

        public Zone(UUID id, RegistryKey<World> worldKey, int minX, int minZ, int maxX, int maxZ, int y, long timeCreated, List<SpawnEntry> spawns) {
            this.id = id;
            this.worldKey = worldKey;
            this.minX = Math.min(minX, maxX);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxZ = Math.max(minZ, maxZ);
            this.y = y;
            this.timeCreated = timeCreated;
            this.spawns = List.copyOf(spawns);
        }

        public boolean contains(int x, int y, int z, RegistryKey<World> w) {
            if (!w.equals(worldKey)) return false;
            if (y != this.y) return false;
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        // getters (comodi per feedback/commands)
        public UUID id() { return id; }
        public RegistryKey<World> worldKey() { return worldKey; }
        public int minX() { return minX; }
        public int minZ() { return minZ; }
        public int maxX() { return maxX; }
        public int maxZ() { return maxZ; }
        public int y() { return y; }
        public long timeCreated() { return timeCreated; }
        public List<SpawnEntry> spawns() { return spawns; }
    }

    private static class ConfigData {
        Integer schemaVersion;
        List<ZoneData> zones = new ArrayList<>();
    }
    private static class ZoneData {
        String id;
        String worldKey;
        int minX, minZ, maxX, maxZ, y;
        long timeCreated;
        List<SpawnData> spawns = new ArrayList<>();
    }
    private static class SpawnData {
        String species;
        int minLevel, maxLevel, weight;
    }

    // ======== STATO IN MEMORIA ========
    private static final Map<UUID, Zone> ZONES = new LinkedHashMap<>();

    // ======== API ========
    public static void load() {
        if (!CONFIG_FILE.exists()) {
            HMMod.LOGGER.info("[GrassZonesConfig] Config non trovata: creo file vuoto.");
            safeRewrite(Collections.emptyList());
            return;
        }

        List<Zone> loaded = new ArrayList<>();
        boolean clean = true;

        try (FileReader r = new FileReader(CONFIG_FILE)) {
            ConfigData data = GSON.fromJson(r, ConfigData.class);
            if (data == null || data.zones == null) clean = false;
            int ver = (data != null && data.schemaVersion != null) ? data.schemaVersion : CURRENT_SCHEMA_VERSION;
            if (ver != CURRENT_SCHEMA_VERSION) {
                HMMod.LOGGER.warn("[GrassZonesConfig] schemaVersion {} != {}. Rigenero file pulito.", ver, CURRENT_SCHEMA_VERSION);
                clean = false;
            }

            if (data != null && data.zones != null) {
                for (ZoneData zd : data.zones) {
                    try {
                        UUID id = UUID.fromString(zd.id);
                        Identifier wid = Identifier.tryParse(zd.worldKey);
                        if (wid == null) throw new IllegalArgumentException("bad worldKey");
                        RegistryKey<World> wk = RegistryKey.of(RegistryKeys.WORLD, wid);

                        List<SpawnEntry> spawns = new ArrayList<>();
                        if (zd.spawns != null) {
                            for (SpawnData sd : zd.spawns) {
                                if (sd.species == null || sd.species.isBlank() || sd.minLevel <= 0 || sd.maxLevel < sd.minLevel || sd.weight <= 0) {
                                    HMMod.LOGGER.warn("[GrassZonesConfig] Spawn invalido in zona {}: {}", zd.id, sd);
                                    clean = false;
                                    continue;
                                }
                                spawns.add(new SpawnEntry(sd.species, sd.minLevel, sd.maxLevel, sd.weight));
                            }
                        }
                        long t = zd.timeCreated == 0 ? Instant.now().toEpochMilli() : zd.timeCreated;
                        loaded.add(new Zone(id, wk, zd.minX, zd.minZ, zd.maxX, zd.maxZ, zd.y, t, spawns));
                    } catch (Exception ex) {
                        HMMod.LOGGER.warn("[GrassZonesConfig] Zona invalida, la salto: {}", (zd != null ? zd.id : "<null>"));
                        clean = false;
                    }
                }
            }
        } catch (Exception e) {
            HMMod.LOGGER.error("[GrassZonesConfig] Errore in lettura, rigenero file pulito: {}", e.getMessage(), e);
            clean = false;
        }

        if (!clean) {
            safeRewrite(loaded);
        } else {
            ZONES.clear();
            for (Zone z : loaded) ZONES.put(z.id(), z);
            HMMod.LOGGER.info("[GrassZonesConfig] Loaded {} zone.", ZONES.size());
        }
    }

    public static void save() {
        try {
            File dir = CONFIG_FILE.getParentFile();
            if (dir != null && !dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created && !dir.exists()) {
                    HMMod.LOGGER.warn("[GrassZonesConfig] Impossibile creare la directory di configurazione: {}", dir.getAbsolutePath());
                }
            }

            ConfigData out = new ConfigData();
            out.schemaVersion = CURRENT_SCHEMA_VERSION;
            for (Zone z : ZONES.values()) {
                ZoneData zd = new ZoneData();
                zd.id = z.id().toString();
                zd.worldKey = z.worldKey().getValue().toString();
                zd.minX = z.minX(); zd.minZ = z.minZ(); zd.maxX = z.maxX(); zd.maxZ = z.maxZ(); zd.y = z.y();
                zd.timeCreated = z.timeCreated();
                for (SpawnEntry se : z.spawns()) {
                    SpawnData sd = new SpawnData();
                    sd.species = se.species; sd.minLevel = se.minLevel; sd.maxLevel = se.maxLevel; sd.weight = se.weight;
                    zd.spawns.add(sd);
                }
                out.zones.add(zd);
            }

            File tmp = new File(CONFIG_FILE.getParent(), CONFIG_FILE.getName() + ".tmp");
            try (FileWriter w = new FileWriter(tmp)) { GSON.toJson(out, w); }

            try {
                Files.move(tmp.toPath(), CONFIG_FILE.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicNotSupported) {
                HMMod.LOGGER.debug("[GrassZonesConfig] ATOMIC_MOVE non supportato, fallback a REPLACE_EXISTING.");
                Files.move(tmp.toPath(), CONFIG_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            HMMod.LOGGER.error("[GrassZonesConfig] Errore durante il salvataggio del file di config: {}", e.getMessage(), e);
        }
    }

    /** Creazione: lista di spawn opzionale (puoi passare List.of() e poi aggiungerli via comando). */
    public static UUID addZone(RegistryKey<World> worldKey, int minX, int minZ, int maxX, int maxZ, int y, List<SpawnEntry> spawns) {
        UUID id = UUID.randomUUID();
        Zone z = new Zone(id, worldKey, minX, minZ, maxX, maxZ, y, Instant.now().toEpochMilli(), spawns == null ? List.of() : spawns);
        ZONES.put(id, z);
        save();
        return id;
    }

    public static boolean removeZone(UUID id) {
        if (ZONES.remove(id) != null) { save(); return true; }
        return false;
    }

    public static Collection<Zone> getAll() { return Collections.unmodifiableCollection(ZONES.values()); }

    /** Trova le zone che contengono quel punto (può essercene più di una). */
    public static List<Zone> findAt(RegistryKey<World> wk, int x, int y, int z) {
        List<Zone> out = new ArrayList<>();
        for (Zone z0 : ZONES.values()) if (z0.contains(x, y, z, wk)) out.add(z0);
        return out;
    }

    /** Modifica set di spawn */
    public static boolean addSpawn(UUID zoneId, SpawnEntry entry) {
        Zone z = ZONES.get(zoneId); if (z == null) return false;
        List<SpawnEntry> ns = new ArrayList<>(z.spawns()); ns.add(entry);
        ZONES.put(zoneId, new Zone(z.id(), z.worldKey(), z.minX(), z.minZ(), z.maxX(), z.maxZ(), z.y(), z.timeCreated(), ns));
        save(); return true;
    }
    public static boolean removeSpawn(UUID zoneId, String speciesId) {
        Zone z = ZONES.get(zoneId); if (z == null) return false;
        List<SpawnEntry> ns = new ArrayList<>();
        for (SpawnEntry e : z.spawns()) if (!e.species.equalsIgnoreCase(speciesId)) ns.add(e);
        ZONES.put(zoneId, new Zone(z.id(), z.worldKey(), z.minX(), z.minZ(), z.maxX(), z.maxZ(), z.y(), z.timeCreated(), ns));
        save(); return true;
    }
    public static GrassZonesConfig.Zone get(UUID id) {
        return ZONES.get(id);
    }
    private static void safeRewrite(List<Zone> valid) {
        try {
            File dir = CONFIG_FILE.getParentFile();
            if (dir != null && !dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created && !dir.exists()) {
                    HMMod.LOGGER.warn("[GrassZonesConfig] Impossibile creare la directory: {}", dir.getAbsolutePath());
                }
            }
            ZONES.clear();
            if (valid != null) for (Zone z : valid) ZONES.put(z.id(), z);
            save();
            HMMod.LOGGER.info("[GrassZonesConfig] File rigenerato con {} zone.", ZONES.size());
        } catch (Exception ex) {
            HMMod.LOGGER.error("[GrassZonesConfig] Errore durante la riscrittura sicura del file di config: {}", ex.getMessage(), ex);
        }
    }
}
