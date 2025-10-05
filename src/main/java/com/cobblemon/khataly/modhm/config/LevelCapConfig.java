package com.cobblemon.khataly.modhm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.util.Identifier;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Level cap configuration (label-based, simplified masterball).
 * - enabled: if false, all level-cap logic should be considered disabled (guards elsewhere should check this)
 * - baseCap: starting cap for everyone
 * - bypassIfShiny: shiny bypasses capture-cap (NOT EXP cap)
 * - bypassOnMasterBall: if true, allows captures above cap only with cobblemon:master_ball
 * - progressions: each entry has:
 *     label    (human-friendly, unique, case-insensitive key)
 *     newCap   (int)
 *     itemIds  (list of canonical item IDs that unlock this label when found in inventory)
 * File path: config/modhm/levelcap.json
 */
public class LevelCapConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/modhm/levelcap.json");
    private static final int CURRENT_SCHEMA_VERSION = 3;

    /** Global on/off switch for level-cap logic. */
    private static boolean enabled = true;

    private static int baseCap = 20;
    private static boolean bypassIfShiny = false;
    private static boolean bypassOnMasterBall = false;

    /** labelLower -> progression */
    private static final Map<String, Progression> progressions = new LinkedHashMap<>();

    // =========================
    //        DATA MODEL
    // =========================

    private static class ConfigData {
        Integer schemaVersion;
        Boolean enabled;
        Integer baseCap;
        Boolean bypassIfShiny;
        Boolean bypassOnMasterBall;
        List<Progression> progressions = new ArrayList<>();
    }

    public static class Progression {
        public String label;            // human-friendly, unique (case-insensitive)
        public int newCap;
        public List<String> itemIds = new ArrayList<>(); // lower-case "namespace:id"

        public Progression() {}
        public Progression(String label, int newCap, Collection<String> itemIds) {
            this.label = label;
            this.newCap = newCap;
            if (itemIds != null) {
                for (String s : itemIds) if (s != null) this.itemIds.add(normalizeId(s));
            }
        }
    }

    // =========================
    //          API
    // =========================

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            logInfo("Config not found. Creating defaults...");
            applyDefaults();
            save();
            return;
        }

        boolean clean = true;

        boolean loadedEnabled = true;
        int loadedBase = 20;
        boolean loadedShiny = false;
        boolean loadedMaster = false;
        Map<String, Progression> loaded = new LinkedHashMap<>();

        try (FileReader r = new FileReader(CONFIG_FILE)) {
            ConfigData d = GSON.fromJson(r, ConfigData.class);
            if (d == null) {
                clean = false;
            } else {
                Integer ver = (d.schemaVersion == null) ? CURRENT_SCHEMA_VERSION : d.schemaVersion;
                if (!Objects.equals(ver, CURRENT_SCHEMA_VERSION)) {
                    logWarn("Schema " + ver + " differs from " + CURRENT_SCHEMA_VERSION + " — will rewrite file.");
                    clean = false;
                }

                loadedEnabled = d.enabled == null || d.enabled;
                loadedBase   = (d.baseCap == null) ? 20 : Math.max(1, d.baseCap);
                loadedShiny  = (d.bypassIfShiny != null && d.bypassIfShiny);
                loadedMaster = (d.bypassOnMasterBall != null && d.bypassOnMasterBall);

                if (d.progressions == null) {
                    clean = false;
                } else {
                    for (Progression p : d.progressions) {
                        if (!isProgressionValid(p)) {
                            logWarn("Invalid progression, skipping: " + safeLabel(p));
                            clean = false;
                            continue;
                        }
                        String key = normalizeLabel(p.label);
                        List<String> normItems = new ArrayList<>();
                        if (p.itemIds != null) {
                            for (String s : p.itemIds)
                                if (s != null && !s.isBlank()) normItems.add(normalizeId(s));
                        }
                        loaded.put(key, new Progression(p.label, Math.max(1, p.newCap), normItems));
                    }
                }
            }
        } catch (JsonParseException e) {
            logError("Malformed JSON: " + e.getMessage(), e);
            clean = false;
        } catch (IOException e) {
            logError("I/O error during load", e);
            clean = false;
        }

        enabled = loadedEnabled;
        baseCap = loadedBase;
        bypassIfShiny = loadedShiny;
        bypassOnMasterBall = loadedMaster;

        progressions.clear();
        progressions.putAll(loaded);

        if (!clean) {
            save();
            logInfo("levelcap.json rewritten with " + progressions.size() + " progression entries.");
        } else {
            logInfo("Config loaded: enabled=" + enabled
                    + ", baseCap=" + baseCap
                    + ", bypassShiny=" + bypassIfShiny
                    + ", bypassMasterBall=" + bypassOnMasterBall
                    + ", progressions=" + progressions.size());
        }
    }

    public static void save() {
        try {
            File dir = CONFIG_FILE.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                logWarn("Could not create config dir: " + dir.getAbsolutePath());
            }

            ConfigData out = new ConfigData();
            out.schemaVersion = CURRENT_SCHEMA_VERSION;
            out.enabled = enabled;
            out.baseCap = baseCap;
            out.bypassIfShiny = bypassIfShiny;
            out.bypassOnMasterBall = bypassOnMasterBall;
            out.progressions = new ArrayList<>(progressions.values());

            File tmp = new File(CONFIG_FILE.getParent(), CONFIG_FILE.getName() + ".tmp");
            try (FileWriter w = new FileWriter(tmp)) {
                GSON.toJson(out, w);
            }

            try {
                Files.move(tmp.toPath(), CONFIG_FILE.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicNotSupported) {
                Files.move(tmp.toPath(), CONFIG_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logError("Error while saving levelcap.json", e);
        }
    }

    public static void reload() { load(); }

    // -------- Getters/Setters --------

    /** Global switch. */
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; save(); }

    public static int getBaseCap() { return baseCap; }
    public static void setBaseCap(int v) { baseCap = Math.max(1, v); save(); }

    public static boolean isBypassIfShiny() { return bypassIfShiny; }
    public static void setBypassIfShiny(boolean v) { bypassIfShiny = v; save(); }

    public static boolean isBypassOnMasterBall() { return bypassOnMasterBall; }
    public static void setBypassOnMasterBall(boolean v) { bypassOnMasterBall = v; save(); }

    // -------- Label-centric progressions --------

    /** Unmodifiable view: labelLower -> cap. */
    public static Map<String, Integer> getAllLabelsWithCaps() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (var e : progressions.entrySet()) out.put(e.getKey(), e.getValue().newCap);
        return Collections.unmodifiableMap(out);
    }

    public static Progression getProgression(String label) {
        return progressions.get(normalizeLabel(label));
    }

    public static void setLabelCap(String label, int newCap) {
        String key = normalizeLabel(label);
        if (key.isEmpty()) { logWarn("setLabelCap: empty label"); return; }
        Progression p = progressions.get(key);
        if (p == null) {
            p = new Progression(label, Math.max(1, newCap), List.of());
            progressions.put(key, p);
        } else {
            p.label = label;
            p.newCap = Math.max(1, newCap);
        }
        save();
    }

    public static boolean removeLabel(String label) {
        String key = normalizeLabel(label);
        boolean removed = (progressions.remove(key) != null);
        if (removed) save();
        return removed;
    }

    public static List<String> getItemIdsForLabel(String label) {
        Progression p = getProgression(label);
        return (p == null) ? List.of() : Collections.unmodifiableList(p.itemIds);
    }

    public static boolean addItemIdToLabel(String label, String itemId) {
        Progression p = getProgression(label);
        if (p == null) { logWarn("addItemIdToLabel: unknown label " + label); return false; }
        String id = normalizeId(itemId);
        if (!isItemIdValid(id)) { logWarn("addItemIdToLabel: invalid itemId " + itemId); return false; }
        if (!p.itemIds.contains(id)) { p.itemIds.add(id); save(); return true; }
        return false;
    }

    public static boolean removeItemIdFromLabel(String label, String itemId) {
        Progression p = getProgression(label);
        if (p == null) return false;
        boolean r = p.itemIds.remove(normalizeId(itemId));
        if (r) save();
        return r;
    }

    public static OptionalInt getCapForLabel(String label) {
        Progression p = getProgression(label);
        return (p == null) ? OptionalInt.empty() : OptionalInt.of(p.newCap);
    }

    public static String displayLabel(String label) {
        if (label == null) return "";
        return toTitleCase(label.trim());
    }

    public static String normalizeLabel(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeId(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    // =========================
    //         SUPPORT
    // =========================

    private static boolean isItemIdValid(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        Identifier id = Identifier.tryParse(itemId);
        return id != null;
    }

    private static boolean isProgressionValid(Progression p) {
        if (p == null) return false;
        if (p.label == null || p.label.isBlank()) return false;
        if (p.newCap < 1) return false;
        if (p.itemIds != null) {
            for (String s : p.itemIds) if (!isItemIdValid(s)) return false;
        }
        return true;
    }

    private static void applyDefaults() {
        enabled = true;
        baseCap = 20;
        bypassIfShiny = false;
        bypassOnMasterBall = true; // default enabled

        progressions.clear();
        progressions.put(normalizeLabel("Steel Badge"),
                new Progression("Steel Badge", 30, List.of("modhm:steel_badge")));
        progressions.put(normalizeLabel("Fire Badge"),
                new Progression("Fire Badge", 40, List.of("modhm:fire_badge")));
    }

    private static String safeLabel(Progression p) {
        return (p == null) ? "<null>" : (p.label == null ? "<no-label>" : p.label);
    }

    private static String toTitleCase(String s) {
        String[] parts = s.replace('_', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    // ---------- LOGGING ----------

    private static void logInfo(String msg) {
        System.out.println("§a[LevelCapConfig] " + msg);
    }
    private static void logWarn(String msg) {
        System.out.println("§e[LevelCapConfig][WARN] " + msg);
    }
    private static void logError(String msg) {
        System.err.println("§c[LevelCapConfig][ERROR] " + msg);
    }
    private static void logError(String msg, Throwable t) {
        System.err.println("§c[LevelCapConfig][ERROR] " + msg);
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            System.err.println(sw.toString());
        }
    }
}
