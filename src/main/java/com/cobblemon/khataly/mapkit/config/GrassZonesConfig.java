package com.cobblemon.khataly.mapkit.config;

import com.cobblemon.khataly.mapkit.CobblemonMapKitMod;
import com.google.gson.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;

/**
 * Gestione Grass Zones (3D: X/Z + range verticale Y):
 *  - Una zona per file: config/cobblemonmapkit/zones/<nome>.json
 *  - Nome obbligatorio e persistenza del nome nel filename
 *  - Migrazione da formati legacy (grass_zones.json e/o singolo campo y)
 */
public class GrassZonesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File ZONES_DIR = new File("config/cobblemonmapkit/zones");
    /** Bump per minY/maxY e supporto retrocompatibile al precedente campo singolo "y". */
    private static final int CURRENT_SCHEMA_VERSION = 4;

    public enum TimeBand { DAY, NIGHT, BOTH }

    // ======== DATA MODEL ========
    public static final class SpawnEntry {
        public final String species;
        public final int minLevel;
        public final int maxLevel;
        public final int weight;
        public final TimeBand time;
        /** Variante opzionale (es. "alola", "hisui", "galar", ...). */
        public final String aspect;

        public SpawnEntry(String species, int minLevel, int maxLevel, int weight, TimeBand time, String aspect) {
            this.species = species;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.weight = weight;
            this.time = (time == null) ? TimeBand.BOTH : time;
            this.aspect = (aspect != null && !aspect.isBlank()) ? aspect : null;
        }
        public SpawnEntry(String species, int minLevel, int maxLevel, int weight, TimeBand time) {
            this(species, minLevel, maxLevel, weight, time, null);
        }
        public SpawnEntry(String species, int minLevel, int maxLevel, int weight) {
            this(species, minLevel, maxLevel, weight, TimeBand.BOTH, null);
        }
    }

    public static final class Zone {
        private final UUID id;
        private final String name;
        private final RegistryKey<World> worldKey;
        private final int minX, minZ, maxX, maxZ;
        private final int minY, maxY;
        private final long timeCreated;
        private final List<SpawnEntry> spawns;
        /** Shiny odds specifiche della zona (1 su N). -1 = usa default globale. */
        private final int shinyOdds;

        public Zone(UUID id,
                    String name,
                    RegistryKey<World> worldKey,
                    int minX, int minZ, int maxX, int maxZ,
                    int minY, int maxY,
                    long timeCreated,
                    List<SpawnEntry> spawns,
                    int shinyOdds) {
            this.id = id;
            this.name = (name == null || name.isBlank()) ? ("Zone " + shortId(id)) : name.trim();
            this.worldKey = worldKey;
            this.minX = Math.min(minX, maxX);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxZ = Math.max(minZ, maxZ);
            this.minY = Math.min(minY, maxY);
            this.maxY = Math.max(minY, maxY);
            this.timeCreated = timeCreated;
            this.spawns = List.copyOf(spawns == null ? List.of() : spawns);
            this.shinyOdds = (shinyOdds <= 0) ? -1 : shinyOdds;
        }

        public boolean contains(int x, int y, int z, RegistryKey<World> w) {
            if (!w.equals(worldKey)) return false;
            return x >= minX && x <= maxX
                    && z >= minZ && z <= maxZ
                    && y >= minY && y <= maxY;
        }

        // getters
        public UUID id() { return id; }
        public String name() { return name; }
        public RegistryKey<World> worldKey() { return worldKey; }
        public int minX() { return minX; }
        public int minZ() { return minZ; }
        public int maxX() { return maxX; }
        public int maxZ() { return maxZ; }
        public int minY() { return minY; }
        public int maxY() { return maxY; }
        public long timeCreated() { return timeCreated; }
        public List<SpawnEntry> spawns() { return spawns; }
        public int shinyOdds() { return shinyOdds; }

        public Zone withName(String newName) {
            return new Zone(id, newName, worldKey, minX, minZ, maxX, maxZ, minY, maxY, timeCreated, spawns, shinyOdds);
        }
        public Zone withShinyOdds(int newShinyOdds) {
            return new Zone(id, name, worldKey, minX, minZ, maxX, maxZ, minY, maxY, timeCreated, spawns, newShinyOdds);
        }
        public Zone withSpawns(List<SpawnEntry> newSpawns) {
            return new Zone(id, name, worldKey, minX, minZ, maxX, maxZ, minY, maxY, timeCreated, newSpawns, shinyOdds);
        }
    }

    // ======== ON-DISK STRUCTS ========
    private static class FileWrap {
        Integer schemaVersion;
        ZoneData zone;
    }
    private static class ConfigDataLegacy {
        Integer schemaVersion;
        List<ZoneData> zones;
    }
    private static class ZoneData {
        String id;
        String name;
        String worldKey;
        int minX, minZ, maxX, maxZ;

        /** NUOVO: range verticale; legacy: solo y. */
        Integer minY;
        Integer maxY;

        /** LEGACY: singolo Y; se presente viene migrato a minY==maxY==y. */
        Integer y;

        long timeCreated;
        List<SpawnData> spawns;
        Integer shinyOdds; // 1 su N; <=0 o null -> default globale
    }
    private static class SpawnData {
        String species;
        int minLevel, maxLevel, weight;
        String time;   // "day" | "night" | "both"
        String aspect; // opzionale
    }

    // ======== IN-MEMORY STATE ========
    private static final Map<UUID, Zone> ZONES = new LinkedHashMap<>();
    /** Traccia il file attuale per ogni zona (per rinominare senza cercare). */
    private static final Map<UUID, File> FILE_BY_ID = new HashMap<>();

    // ======== API ========
    public static void load() {
        ensureDir();
        ZONES.clear();
        FILE_BY_ID.clear();

        // Migrazione dal file legacy se presente
        File legacy = new File("config/cobblemonmapkit/grass_zones.json");
        if (legacy.exists()) {
            migrateLegacy(legacy);
            return;
        }

        File[] files = ZONES_DIR.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null || files.length == 0) {
            CobblemonMapKitMod.LOGGER.info("[GrassZonesConfig] No zones found.");
            return;
        }
        int ok = 0, bad = 0;
        for (File f : files) {
            try {
                Zone z = readZoneFile(f);
                ZONES.put(z.id(), z);
                FILE_BY_ID.put(z.id(), f);
                ok++;
            } catch (Exception e) {
                CobblemonMapKitMod.LOGGER.warn("[GrassZonesConfig] Could not read {}: {}", f.getName(), e.getMessage());
                bad++;
            }
        }
        CobblemonMapKitMod.LOGGER.info("[GrassZonesConfig] Loaded {} zones ({} invalid).", ok, bad);
    }

    /** Overlap 3D con Y puntuale (compat: controlla se la proiezione XY si sovrappone e il punto Y cade nel range della zona). */
    public static boolean overlaps(RegistryKey<World> worldKey, int minX, int minZ, int maxX, int maxZ, int y) {
        int aMinX = Math.min(minX, maxX);
        int aMaxX = Math.max(minX, maxX);
        int aMinZ = Math.min(minZ, maxZ);
        int aMaxZ = Math.max(minZ, maxZ);

        for (Zone z : ZONES.values()) {
            if (!z.worldKey().equals(worldKey)) continue;
            boolean xOverlap = aMinX <= z.maxX() && aMaxX >= z.minX();
            boolean zOverlap = aMinZ <= z.maxZ() && aMaxZ >= z.minZ();
            boolean yInside = y >= z.minY() && y <= z.maxY();
            if (xOverlap && zOverlap && yInside) return true;
        }
        return false;
    }

    /** Overlap 3D tra due prismi (range Y completo). */
    public static boolean overlaps(RegistryKey<World> worldKey, int minX, int minZ, int maxX, int maxZ, int minY, int maxY) {
        int aMinX = Math.min(minX, maxX);
        int aMaxX = Math.max(minX, maxX);
        int aMinZ = Math.min(minZ, maxZ);
        int aMaxZ = Math.max(minZ, maxZ);
        int aMinY = Math.min(minY, maxY);
        int aMaxY = Math.max(minY, maxY);

        for (Zone z : ZONES.values()) {
            if (!z.worldKey().equals(worldKey)) continue;
            boolean xOverlap = aMinX <= z.maxX() && aMaxX >= z.minX();
            boolean zOverlap = aMinZ <= z.maxZ() && aMaxZ >= z.minZ();
            boolean yOverlap = aMinY <= z.maxY() && aMaxY >= z.minY();
            if (xOverlap && zOverlap && yOverlap) return true;
        }
        return false;
    }

    /** Overlap 2D (ignora Y). */
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

    /** Serializza tutte le zone (sincronizza/ripulisce orfani). */
    public static void save() {
        try {
            ensureDir();
            Set<File> keep = new HashSet<>();
            for (Zone z : ZONES.values()) {
                File f = writeZoneFile(z);
                keep.add(f);
                FILE_BY_ID.put(z.id(), f);
            }
            File[] files = ZONES_DIR.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    if (!keep.contains(f)) {
                        boolean deleted = f.delete();
                        if (!deleted) CobblemonMapKitMod.LOGGER.warn("[GrassZonesConfig] Could not delete orphan {}", f.getName());
                    }
                }
            }
        } catch (IOException e) {
            CobblemonMapKitMod.LOGGER.error("[GrassZonesConfig] Save error: {}", e.getMessage(), e);
        }
    }

    /** Creazione zona (compat: singolo Y -> minY==maxY==y). */
    public static UUID addZone(String name,
                               RegistryKey<World> worldKey,
                               int minX, int minZ, int maxX, int maxZ,
                               int y,
                               List<SpawnEntry> spawns) {
        return addZone(name, worldKey, minX, minZ, maxX, maxZ, y, y, spawns, -1);
    }

    /** Creazione zona con shiny odds esplicite (compat: singolo Y). */
    public static UUID addZone(String name,
                               RegistryKey<World> worldKey,
                               int minX, int minZ, int maxX, int maxZ,
                               int y,
                               List<SpawnEntry> spawns,
                               int shinyOdds) {
        return addZone(name, worldKey, minX, minZ, maxX, maxZ, y, y, spawns, shinyOdds);
    }

    /** NUOVO: creazione zona con range verticale completo. */
    public static UUID addZone(String name,
                               RegistryKey<World> worldKey,
                               int minX, int minZ, int maxX, int maxZ,
                               int minY, int maxY,
                               List<SpawnEntry> spawns) {
        return addZone(name, worldKey, minX, minZ, maxX, maxZ, minY, maxY, spawns, -1);
    }

    /** NUOVO: creazione zona con range verticale e shiny odds. */
    public static UUID addZone(String name,
                               RegistryKey<World> worldKey,
                               int minX, int minZ, int maxX, int maxZ,
                               int minY, int maxY,
                               List<SpawnEntry> spawns,
                               int shinyOdds) {
        UUID id = UUID.randomUUID();
        Zone z = new Zone(
                id, name, worldKey,
                minX, minZ, maxX, maxZ,
                minY, maxY,
                Instant.now().toEpochMilli(),
                spawns == null ? List.of() : spawns,
                shinyOdds
        );
        ZONES.put(id, z);
        try {
            File f = writeZoneFile(z);
            FILE_BY_ID.put(id, f);
        } catch (IOException e) {
            CobblemonMapKitMod.LOGGER.error("[GrassZonesConfig] Write error on addZone {}: {}", id, e.getMessage(), e);
        }
        return id;
    }

    public static boolean removeZone(UUID id) {
        Zone removed = ZONES.remove(id);
        File f = FILE_BY_ID.remove(id);
        if (removed != null) {
            if (f == null) f = guessFileByName(removed.name());
            if (f.exists() && !f.delete()) {
                CobblemonMapKitMod.LOGGER.warn("[GrassZonesConfig] Could not delete file {}", f.getName());
            }
            return true;
        }
        return false;
    }

    /** Trova tutte le zone che contengono un punto. */
    public static List<Zone> findAt(RegistryKey<World> wk, int x, int y, int z) {
        List<Zone> out = new ArrayList<>();
        for (Zone z0 : ZONES.values()) if (z0.contains(x, y, z, wk)) out.add(z0);
        return out;
    }

    /** Aggiunge uno spawn a una zona. */
    public static boolean addSpawn(UUID zoneId, SpawnEntry entry) {
        Zone z = ZONES.get(zoneId); if (z == null) return false;
        List<SpawnEntry> ns = new ArrayList<>(z.spawns()); ns.add(entry);
        Zone nz = z.withSpawns(ns);
        ZONES.put(zoneId, nz);
        try {
            File f = writeZoneFile(nz);
            FILE_BY_ID.put(zoneId, f);
        } catch (IOException e) {
            CobblemonMapKitMod.LOGGER.error("[GrassZonesConfig] Write error on addSpawn {}: {}", zoneId, e.getMessage(), e);
        }
        return true;
    }

    /** Rimuove uno spawn per species id (case-insensitive). */
    public static boolean removeSpawn(UUID zoneId, String speciesId) {
        Zone z = ZONES.get(zoneId); if (z == null) return false;
        List<SpawnEntry> ns = new ArrayList<>();
        for (SpawnEntry e : z.spawns()) if (!e.species.equalsIgnoreCase(speciesId)) ns.add(e);
        Zone nz = z.withSpawns(ns);
        ZONES.put(zoneId, nz);
        try {
            File f = writeZoneFile(nz);
            FILE_BY_ID.put(zoneId, f);
        } catch (IOException e) {
            CobblemonMapKitMod.LOGGER.error("[GrassZonesConfig] Write error on removeSpawn {}: {}", zoneId, e.getMessage(), e);
        }
        return true;
    }

    /** Imposta shiny odds (1 su N) per zona. -1 = default globale. */
    public static boolean setZoneShinyOdds(UUID zoneId, int shinyOdds) {
        Zone z = ZONES.get(zoneId); if (z == null) return false;
        Zone nz = z.withShinyOdds(shinyOdds);
        ZONES.put(zoneId, nz);
        try {
            File f = writeZoneFile(nz);
            FILE_BY_ID.put(zoneId, f);
        } catch (IOException e) {
            CobblemonMapKitMod.LOGGER.error("[GrassZonesConfig] Write error on setZoneShinyOdds {}: {}", zoneId, e.getMessage(), e);
        }
        return true;
    }

    /** Rinomina la zona e rinomina il file su disco. */
    public static boolean setZoneName(UUID zoneId, String newName) {
        if (newName == null || newName.isBlank()) return false;
        Zone z = ZONES.get(zoneId); if (z == null) return false;
        Zone nz = z.withName(newName);
        ZONES.put(zoneId, nz);
        try {
            File oldFile = FILE_BY_ID.get(zoneId);
            File newFile = writeZoneFile(nz);
            FILE_BY_ID.put(zoneId, newFile);
            if (oldFile != null && !sameFile(oldFile, newFile) && oldFile.exists()) {
                if (!isFileUsedByOtherZone(oldFile, zoneId)) {
                    boolean deleted = oldFile.delete();
                    if (!deleted) CobblemonMapKitMod.LOGGER.warn("[GrassZonesConfig] Could not delete old file {}", oldFile.getName());
                }
            }
        } catch (IOException e) {
            CobblemonMapKitMod.LOGGER.error("[GrassZonesConfig] Write error on setZoneName {}: {}", zoneId, e.getMessage(), e);
            return false;
        }
        return true;
    }

    public static Collection<Zone> getAll() { return Collections.unmodifiableCollection(ZONES.values()); }
    public static Zone get(UUID id) { return ZONES.get(id); }

    // ======== INTERNALS ========

    private static void migrateLegacy(File legacy) {
        CobblemonMapKitMod.LOGGER.info("[GrassZonesConfig] Migrating from legacy grass_zones.json to zones/ ...");
        List<Zone> loaded = new ArrayList<>();
        try (FileReader r = new FileReader(legacy)) {
            ConfigDataLegacy data = GSON.fromJson(r, ConfigDataLegacy.class);
            if (data != null && data.zones != null) {
                for (ZoneData zd : data.zones) {
                    try {
                        Zone z = fromZoneData(zd);
                        loaded.add(z);
                    } catch (Exception ex) {
                        CobblemonMapKitMod.LOGGER.warn("[GrassZonesConfig] Invalid legacy zone, skipping: {}", (zd != null ? zd.id : "<null>"));
                    }
                }
            }
        } catch (Exception e) {
            CobblemonMapKitMod.LOGGER.error("[GrassZonesConfig] Legacy read error: {}", e.getMessage(), e);
        }

        for (Zone z : loaded) {
            ZONES.put(z.id(), z);
            try {
                File f = writeZoneFile(z);
                FILE_BY_ID.put(z.id(), f);
            } catch (IOException e) {
                CobblemonMapKitMod.LOGGER.error("[GrassZonesConfig] Error writing migrated zone {}: {}", z.id(), e.getMessage(), e);
            }
        }

        File bak = new File(legacy.getParentFile(), legacy.getName() + ".bak");
        if (!legacy.renameTo(bak)) {
            CobblemonMapKitMod.LOGGER.warn("[GrassZonesConfig] Could not rename legacy file, leaving it in place.");
        }
        CobblemonMapKitMod.LOGGER.info("[GrassZonesConfig] Migration complete: {} zones.", ZONES.size());
    }

    private static TimeBand parseTime(String s) {
        if (s == null) return TimeBand.BOTH;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "day" -> TimeBand.DAY;
            case "night" -> TimeBand.NIGHT;
            default -> TimeBand.BOTH;
        };
    }

    /** Gestisce il risultato di mkdirs(), evitando warning e segnalando eventuali problemi. */
    private static void ensureDir() {
        File parent = ZONES_DIR.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean okParent = parent.mkdirs();
            if (!okParent && !parent.exists()) {
                CobblemonMapKitMod.LOGGER.warn("[GrassZonesConfig] Unable to create parent dir: {}", parent.getAbsolutePath());
            }
        }
        if (!ZONES_DIR.exists()) {
            boolean ok = ZONES_DIR.mkdirs();
            if (!ok && !ZONES_DIR.exists()) {
                CobblemonMapKitMod.LOGGER.warn("[GrassZonesConfig] Unable to create zones dir: {}", ZONES_DIR.getAbsolutePath());
            }
        }
    }

    private static String shortId(UUID id) {
        String s = id.toString().replace("-", "");
        return s.substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private static String sanitizeForFilename(String name) {
        // rimuove accenti e caratteri non validi, compatta spazi, tronca lunghezze eccessive
        String n = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        n = n.replaceAll("[^\\w\\-.\\s]", "_").trim(); // keep letters, digits, _, -, ., space
        n = n.replaceAll("\\s+", " ");
        if (n.isEmpty()) n = "Zone";
        if (n.length() > 80) n = n.substring(0, 80).trim();
        return n;
    }

    private static File guessFileByName(String zoneName) {
        String base = sanitizeForFilename(zoneName);
        return new File(ZONES_DIR, base + ".json");
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException e) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }

    private static boolean isFileUsedByOtherZone(File f, UUID currentId) {
        for (Map.Entry<UUID, File> e : FILE_BY_ID.entrySet()) {
            if (!e.getKey().equals(currentId) && sameFile(e.getValue(), f)) return true;
        }
        return false;
    }

    /** Trova un filename unico per il nome desiderato, con suffissi " (2)", " (3)" se necessario. */
    private static File uniqueFileForName(String desiredName, UUID ownerId) {
        String base = sanitizeForFilename(desiredName);
        File f = new File(ZONES_DIR, base + ".json");
        if (!existsDifferentOwner(f, ownerId)) return f;

        int i = 2;
        while (true) {
            File cand = new File(ZONES_DIR, base + " (" + i + ").json");
            if (!existsDifferentOwner(cand, ownerId)) return cand;
            i++;
        }
    }

    /** Ritorna true se il file esiste ed è associato ad un id diverso (o non leggibile ma presente). */
    private static boolean existsDifferentOwner(File f, UUID ownerId) {
        if (!f.exists()) return false;
        try {
            Zone z = readZoneFile(f);
            return !z.id().equals(ownerId);
        } catch (Exception e) {
            // file presente ma illeggibile => trattalo come occupato
            return true;
        }
    }

    /** Scrive su disco la zona, scegliendo/aggiornando il filename in base al nome. */
    private static File writeZoneFile(Zone z) throws IOException {
        ensureDir();

        File current = FILE_BY_ID.get(z.id());
        File target = uniqueFileForName(z.name(), z.id());

        if (current != null && current.exists()) {
            if (sameFile(current, target)) return writeJson(target, z);
            File written = writeJson(target, z);
            if (!isFileUsedByOtherZone(current, z.id()) && current.exists()) {
                boolean deleted = current.delete();
                if (!deleted) CobblemonMapKitMod.LOGGER.warn("[GrassZonesConfig] Could not delete old file {}", current.getName());
            }
            return written;
        } else {
            return writeJson(target, z);
        }
    }

    private static File writeJson(File target, Zone z) throws IOException {
        ZoneData zd = toZoneData(z);
        FileWrap wrap = new FileWrap();
        wrap.schemaVersion = CURRENT_SCHEMA_VERSION;
        wrap.zone = zd;

        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileWriter w = new FileWriter(tmp)) {
            GSON.toJson(wrap, w);
        }
        try {
            Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicNotSupported) {
            CobblemonMapKitMod.LOGGER.debug("[GrassZonesConfig] ATOMIC_MOVE not supported for {}, falling back.", target.getName());
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static ZoneData toZoneData(Zone z) {
        ZoneData zd = new ZoneData();
        zd.id = z.id().toString();
        zd.name = z.name();
        zd.worldKey = z.worldKey().getValue().toString();
        zd.minX = z.minX(); zd.minZ = z.minZ(); zd.maxX = z.maxX(); zd.maxZ = z.maxZ();
        zd.minY = z.minY(); zd.maxY = z.maxY(); // nuovo formato
        zd.y = null; // non scriviamo più il campo legacy
        zd.timeCreated = z.timeCreated();
        zd.shinyOdds = (z.shinyOdds() <= 0) ? -1 : z.shinyOdds();

        zd.spawns = new ArrayList<>();
        for (SpawnEntry se : z.spawns()) {
            SpawnData sd = new SpawnData();
            sd.species = se.species;
            sd.minLevel = se.minLevel;
            sd.maxLevel = se.maxLevel;
            sd.weight = se.weight;
            sd.time = se.time.name().toLowerCase(Locale.ROOT);
            if (se.aspect != null && !se.aspect.isBlank()) sd.aspect = se.aspect;
            zd.spawns.add(sd);
        }
        return zd;
    }

    private static Zone fromZoneData(ZoneData zd) {
        UUID id = UUID.fromString(zd.id);
        Identifier wid = Identifier.tryParse(zd.worldKey);
        if (wid == null) throw new IllegalArgumentException("bad worldKey");
        RegistryKey<World> wk = RegistryKey.of(RegistryKeys.WORLD, wid);

        // Migrazione: se minY/maxY mancano ma c'è y singolo, usa y come range unitario
        int minY, maxY;
        if (zd.minY != null || zd.maxY != null) {
            // Se uno solo è null, fallback all'altro
            int minY0 = zd.minY != null ? zd.minY : zd.maxY;
            int maxY0 = (zd.maxY != null) ? zd.maxY : minY0;
            minY = Math.min(minY0, maxY0);
            maxY = Math.max(minY0, maxY0);
        } else if (zd.y != null) {
            minY = zd.y;
            maxY = zd.y;
        } else {
            // Se manca tutto, fallback sicuro (es. 0..319 in 1.20, ma preferiamo range unitario neutro)
            CobblemonMapKitMod.LOGGER.warn("[GrassZonesConfig] Missing Y info for zone {}, defaulting to 0..0", zd.id);
            minY = 0; maxY = 0;
        }

        List<SpawnEntry> spawns = new ArrayList<>();
        if (zd.spawns != null) {
            for (SpawnData sd : zd.spawns) {
                if (sd == null || sd.species == null || sd.species.isBlank()
                        || sd.minLevel <= 0 || sd.maxLevel < sd.minLevel || sd.weight <= 0) {
                    CobblemonMapKitMod.LOGGER.warn("[GrassZonesConfig] Invalid spawn in zone {}: {}", zd.id, sd);
                    continue;
                }
                spawns.add(new SpawnEntry(
                        sd.species, sd.minLevel, sd.maxLevel, sd.weight, parseTime(sd.time), sd.aspect
                ));
            }
        }
        long t = zd.timeCreated == 0 ? Instant.now().toEpochMilli() : zd.timeCreated;
        int shinyOdds = (zd.shinyOdds == null || zd.shinyOdds <= 0) ? -1 : zd.shinyOdds;
        String name = (zd.name == null || zd.name.isBlank()) ? ("Zone " + shortId(id)) : zd.name;
        return new Zone(id, name, wk, zd.minX, zd.minZ, zd.maxX, zd.maxZ, minY, maxY, t, spawns, shinyOdds);
    }

    private static Zone readZoneFile(File f) throws IOException {
        try (FileReader r = new FileReader(f)) {
            FileWrap wrap = GSON.fromJson(r, FileWrap.class);
            int ver = (wrap != null && wrap.schemaVersion != null) ? wrap.schemaVersion : CURRENT_SCHEMA_VERSION;
            if (wrap == null || wrap.zone == null) throw new IOException("empty or invalid wrap");
            Zone z = fromZoneData(wrap.zone);
            if (ver != CURRENT_SCHEMA_VERSION) {
                CobblemonMapKitMod.LOGGER.warn("[GrassZonesConfig] schemaVersion {} != {} in {}, will rewrite on save.",
                        ver, CURRENT_SCHEMA_VERSION, f.getName());
            }
            return z;
        }
    }
}
