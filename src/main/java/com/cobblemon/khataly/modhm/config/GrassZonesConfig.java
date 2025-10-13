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

/** Manages saving/loading of Grass Zones, including per-zone shiny odds. */
public class GrassZonesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/modhm/grass_zones.json");

    /** Bump schema when changing on-disk format. */
    private static final int CURRENT_SCHEMA_VERSION = 2;

    public enum TimeBand { DAY, NIGHT, BOTH }

    // ======== DATA MODEL ========
    public static final class SpawnEntry {
        public final String species;
        public final int minLevel;
        public final int maxLevel;
        public final int weight;
        public final TimeBand time;

        public SpawnEntry(String species, int minLevel, int maxLevel, int weight, TimeBand time) {
            this.species = species;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.weight = weight;
            this.time = (time == null) ? TimeBand.BOTH : time;
        }
        // Back-compat: default to BOTH if time not specified
        public SpawnEntry(String species, int minLevel, int maxLevel, int weight) {
            this(species, minLevel, maxLevel, weight, TimeBand.BOTH);
        }
    }

    public static final class Zone {
        private final UUID id;
        private final RegistryKey<World> worldKey;
        private final int minX, minZ, maxX, maxZ;
        private final int y;
        private final long timeCreated;
        private final List<SpawnEntry> spawns;

        /**
         * Shiny odds specifiche della zona (1 su N). -1 = usa il default globale (es. 4096).
         */
        private final int shinyOdds;

        public Zone(UUID id,
                    RegistryKey<World> worldKey,
                    int minX, int minZ, int maxX, int maxZ,
                    int y,
                    long timeCreated,
                    List<SpawnEntry> spawns,
                    int shinyOdds) {
            this.id = id;
            this.worldKey = worldKey;
            this.minX = Math.min(minX, maxX);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxZ = Math.max(minZ, maxZ);
            this.y = y;
            this.timeCreated = timeCreated;
            this.spawns = List.copyOf(spawns);
            this.shinyOdds = shinyOdds <= 0 ? -1 : shinyOdds; // normalizza: <=0 -> usa default
        }

        public boolean contains(int x, int y, int z, RegistryKey<World> w) {
            if (!w.equals(worldKey)) return false;
            if (y != this.y) return false;
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        // getters
        public UUID id() { return id; }
        public RegistryKey<World> worldKey() { return worldKey; }
        public int minX() { return minX; }
        public int minZ() { return minZ; }
        public int maxX() { return maxX; }
        public int maxZ() { return maxZ; }
        public int y() { return y; }
        public long timeCreated() { return timeCreated; }
        public List<SpawnEntry> spawns() { return spawns; }
        /** 1 su N; -1 = usa default globale. */
        public int shinyOdds() { return shinyOdds; }

        /** Restituisce una copia con shiny odds aggiornate. */
        public Zone withShinyOdds(int shinyOdds) {
            return new Zone(id, worldKey, minX, minZ, maxX, maxZ, y, timeCreated, spawns, shinyOdds);
        }

        /** Restituisce una copia con lista spawns aggiornata (immutabilità leggera). */
        public Zone withSpawns(List<SpawnEntry> newSpawns) {
            return new Zone(id, worldKey, minX, minZ, maxX, maxZ, y, timeCreated, newSpawns, shinyOdds);
        }
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

        /**
         * Shiny odds serializzate (1 su N). Se assente o <=0, verrà inteso come -1 (usa default).
         */
        Integer shinyOdds;
    }
    private static class SpawnData {
        String species;
        int minLevel, maxLevel, weight;
        String time; // "day" | "night" | "both" (optional; defaults to BOTH)
    }

    // ======== IN-MEMORY STATE ========
    private static final Map<UUID, Zone> ZONES = new LinkedHashMap<>();

    // ======== API ========
    public static void load() {
        if (!CONFIG_FILE.exists()) {
            HMMod.LOGGER.info("[GrassZonesConfig] Config file not found: creating an empty one.");
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
                HMMod.LOGGER.warn("[GrassZonesConfig] schemaVersion {} != {}. Rewriting a clean file.", ver, CURRENT_SCHEMA_VERSION);
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
                                if (sd.species == null || sd.species.isBlank()
                                        || sd.minLevel <= 0 || sd.maxLevel < sd.minLevel || sd.weight <= 0) {
                                    HMMod.LOGGER.warn("[GrassZonesConfig] Invalid spawn in zone {}: {}", zd.id, sd);
                                    clean = false;
                                    continue;
                                }
                                spawns.add(new SpawnEntry(
                                        sd.species,
                                        sd.minLevel,
                                        sd.maxLevel,
                                        sd.weight,
                                        parseTime(sd.time)
                                ));
                            }
                        }
                        long t = zd.timeCreated == 0 ? Instant.now().toEpochMilli() : zd.timeCreated;

                        // shinyOdds: se nullo o <=0 -> usa -1 (default globale)
                        int shinyOdds = (zd.shinyOdds == null || zd.shinyOdds <= 0) ? -1 : zd.shinyOdds;

                        loaded.add(new Zone(id, wk, zd.minX, zd.minZ, zd.maxX, zd.maxZ, zd.y, t, spawns, shinyOdds));
                    } catch (Exception ex) {
                        HMMod.LOGGER.warn("[GrassZonesConfig] Invalid zone, skipping: {}", (zd != null ? zd.id : "<null>"));
                        clean = false;
                    }
                }
            }
        } catch (Exception e) {
            HMMod.LOGGER.error("[GrassZonesConfig] Read error, rewriting a clean file: {}", e.getMessage(), e);
            clean = false;
        }

        if (!clean) {
            safeRewrite(loaded);
        } else {
            ZONES.clear();
            for (Zone z : loaded) ZONES.put(z.id(), z);
            HMMod.LOGGER.info("[GrassZonesConfig] Loaded {} zones.", ZONES.size());
        }
    }

    private static TimeBand parseTime(String s) {
        if (s == null) return TimeBand.BOTH;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "day" -> TimeBand.DAY;
            case "night" -> TimeBand.NIGHT;
            default -> TimeBand.BOTH;
        };
    }

    // 3D overlap (same world and same exact Y)
    public static boolean overlaps(RegistryKey<World> worldKey, int minX, int minZ, int maxX, int maxZ, int y) {
        int aMinX = Math.min(minX, maxX);
        int aMaxX = Math.max(minX, maxX);
        int aMinZ = Math.min(minZ, maxZ);
        int aMaxZ = Math.max(minZ, maxZ);

        for (Zone z : ZONES.values()) {
            if (!z.worldKey().equals(worldKey)) continue;
            if (z.y() != y) continue;

            boolean xOverlap = aMinX <= z.maxX() && aMaxX >= z.minX();
            boolean zOverlap = aMinZ <= z.maxZ() && aMaxZ >= z.minZ();
            if (xOverlap && zOverlap) return true;
        }
        return false;
    }

    // 2D overlap (ignores Y) — optional helper
    public static boolean overlaps(RegistryKey<World> worldKey, int minX, int minZ, int maxX, int maxZ) {
        int aMinX = Math.min(minX, maxX);
        int aMaxX = Math.max(minX, maxX);
        int aMinZ = Math.min(minZ, maxZ);
        int aMaxZ = Math.max(minZ, maxZ);

        for (Zone z : ZONES.values()) {
            if (!z.worldKey().equals(worldKey)) continue;
            boolean xOverlap = aMinX <= z.maxX() && aMaxX >= z.minX();
            boolean zOverlap = aMinZ <= z.maxZ() && aMaxZ >= z.minZ();
            if (xOverlap && zOverlap) return true;
        }
        return false;
    }

    public static void save() {
        try {
            File dir = CONFIG_FILE.getParentFile();
            if (dir != null && !dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created && !dir.exists()) {
                    HMMod.LOGGER.warn("[GrassZonesConfig] Unable to create config directory: {}", dir.getAbsolutePath());
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
                // shiny odds (1 su N); se -1 non serializziamo nulla? Preferiamo serializzare -1 per chiarezza.
                zd.shinyOdds = (z.shinyOdds() <= 0) ? -1 : z.shinyOdds();

                for (SpawnEntry se : z.spawns()) {
                    SpawnData sd = new SpawnData();
                    sd.species = se.species;
                    sd.minLevel = se.minLevel;
                    sd.maxLevel = se.maxLevel;
                    sd.weight = se.weight;
                    sd.time = se.time.name().toLowerCase(Locale.ROOT); // "day"/"night"/"both"
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
                HMMod.LOGGER.debug("[GrassZonesConfig] ATOMIC_MOVE not supported, falling back to REPLACE_EXISTING.");
                Files.move(tmp.toPath(), CONFIG_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            HMMod.LOGGER.error("[GrassZonesConfig] Error while saving config file: {}", e.getMessage(), e);
        }
    }

    /** Creation: optional spawn list (you can pass List.of() and add later via commands). */
    public static UUID addZone(RegistryKey<World> worldKey,
                               int minX, int minZ, int maxX, int maxZ,
                               int y,
                               List<SpawnEntry> spawns) {
        // Back-compat overload: shinyOdds = -1 (usa default globale)
        return addZone(worldKey, minX, minZ, maxX, maxZ, y, spawns, -1);
    }

    /** Creation with explicit shiny odds (1 su N). Usa -1 per default globale. */
    public static UUID addZone(RegistryKey<World> worldKey,
                               int minX, int minZ, int maxX, int maxZ,
                               int y,
                               List<SpawnEntry> spawns,
                               int shinyOdds) {
        UUID id = UUID.randomUUID();
        Zone z = new Zone(
                id, worldKey,
                minX, minZ, maxX, maxZ,
                y,
                Instant.now().toEpochMilli(),
                spawns == null ? List.of() : spawns,
                shinyOdds
        );
        ZONES.put(id, z);
        save();
        return id;
    }

    public static boolean removeZone(UUID id) {
        if (ZONES.remove(id) != null) { save(); return true; }
        return false;
    }

    public static Collection<Zone> getAll() { return Collections.unmodifiableCollection(ZONES.values()); }

    public static GrassZonesConfig.Zone get(UUID id) {
        return ZONES.get(id);
    }

    /** Find zones containing a given point (there can be more than one). */
    public static List<Zone> findAt(RegistryKey<World> wk, int x, int y, int z) {
        List<Zone> out = new ArrayList<>();
        for (Zone z0 : ZONES.values()) if (z0.contains(x, y, z, wk)) out.add(z0);
        return out;
    }

    /** Spawn set modification */
    public static boolean addSpawn(UUID zoneId, SpawnEntry entry) {
        Zone z = ZONES.get(zoneId); if (z == null) return false;
        List<SpawnEntry> ns = new ArrayList<>(z.spawns()); ns.add(entry);
        ZONES.put(zoneId, z.withSpawns(ns));
        save(); return true;
    }

    public static boolean removeSpawn(UUID zoneId, String speciesId) {
        Zone z = ZONES.get(zoneId); if (z == null) return false;
        List<SpawnEntry> ns = new ArrayList<>();
        for (SpawnEntry e : z.spawns()) if (!e.species.equalsIgnoreCase(speciesId)) ns.add(e);
        ZONES.put(zoneId, z.withSpawns(ns));
        save(); return true;
    }

    /** Imposta le shiny odds (1 su N) per una zona. Usa -1 per tornare al default globale. */
    public static boolean setZoneShinyOdds(UUID zoneId, int shinyOdds) {
        Zone z = ZONES.get(zoneId); if (z == null) return false;
        ZONES.put(zoneId, z.withShinyOdds(shinyOdds));
        save(); return true;
    }

    private static void safeRewrite(List<Zone> valid) {
        try {
            File dir = CONFIG_FILE.getParentFile();
            if (dir != null && !dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created && !dir.exists()) {
                    HMMod.LOGGER.warn("[GrassZonesConfig] Unable to create directory: {}", dir.getAbsolutePath());
                }
            }
            ZONES.clear();
            if (valid != null) for (Zone z : valid) ZONES.put(z.id(), z);
            save();
            HMMod.LOGGER.info("[GrassZonesConfig] File rebuilt with {} zones.", ZONES.size());
        } catch (Exception ex) {
            HMMod.LOGGER.error("[GrassZonesConfig] Error during safe rewrite of the config file: {}", ex.getMessage(), ex);
        }
    }
}
