package com.cobblemon.khataly.modhm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.*;

/**
 * Tracks which level-cap labels have been applied per player UUID.
 * Saved at: config/modhm/progress/player_levelcap/<uuid>.json
 * Keys are the LABELS (lowercase), not item IDs.
 */
public class PlayerLevelCapProgress {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File ROOT = new File("config/modhm/progress/player_levelcap");
    private static final int CURRENT_SCHEMA_VERSION = 1;

    /** In-memory cache: UUID -> set of applied labels (lowercase). */
    private static final Map<UUID, Set<String>> cache = new HashMap<>();

    // ===================== PUBLIC API =====================

    public static Set<String> getApplied(UUID uuid) {
        ensureLoaded(uuid);
        return Collections.unmodifiableSet(cache.getOrDefault(uuid, Collections.emptySet()));
    }

    public static boolean isApplied(UUID uuid, String label) {
        ensureLoaded(uuid);
        return cache.getOrDefault(uuid, Collections.emptySet())
                .contains(safeLower(label));
    }

    /** Apply label; returns true if newly added. */
    public static boolean apply(UUID uuid, String label) {
        ensureLoaded(uuid);
        String k = safeLower(label);
        Set<String> set = cache.computeIfAbsent(uuid, u -> new HashSet<>());
        boolean added = set.add(k);
        if (added) save(uuid);
        return added;
    }

    /** Remove one applied label; returns true if existed. */
    public static boolean remove(UUID uuid, String label) {
        ensureLoaded(uuid);
        String k = safeLower(label);
        Set<String> set = cache.getOrDefault(uuid, null);
        if (set == null) return false;
        boolean removed = set.remove(k);
        if (removed) save(uuid);
        return removed;
    }

    public static void applyAll(UUID uuid, Collection<String> labels) {
        ensureLoaded(uuid);
        if (labels == null || labels.isEmpty()) return;
        Set<String> set = cache.computeIfAbsent(uuid, u -> new HashSet<>());
        boolean changed = false;
        for (String l : labels) {
            if (l == null) continue;
            changed |= set.add(l.toLowerCase(Locale.ROOT));
        }
        if (changed) save(uuid);
    }

    /** Danger: wipe all progress for this player. */
    public static void clearAll(UUID uuid) {
        cache.put(uuid, new HashSet<>());
        save(uuid);
    }

    // ================== LOAD/SAVE ==================

    private static void ensureLoaded(UUID uuid) {
        if (cache.containsKey(uuid)) return;
        cache.put(uuid, new HashSet<>()); // prevent re-entrancy
        load(uuid);
    }

    private static File fileFor(UUID uuid) {
        return new File(ROOT, uuid.toString() + ".json");
    }

    private static void load(UUID uuid) {
        try {
            File dir = ROOT;
            if (!dir.exists() && !dir.mkdirs()) {
                logWarn("Could not create progress directory: " + dir.getAbsolutePath());
            }
            File f = fileFor(uuid);
            if (!f.exists()) {
                cache.put(uuid, new HashSet<>());
                return;
            }
            try (FileReader r = new FileReader(f)) {
                Data d = GSON.fromJson(r, Data.class);
                if (d == null || d.applied == null) {
                    cache.put(uuid, new HashSet<>());
                } else {
                    Set<String> s = new HashSet<>();
                    for (String k : d.applied) if (k != null) s.add(k.toLowerCase(Locale.ROOT));
                    cache.put(uuid, s);
                }
            }
        } catch (Exception e) {
            logError("Error loading level-cap progress for " + uuid, e);
            cache.put(uuid, new HashSet<>());
        }
    }

    private static void save(UUID uuid) {
        try {
            File dir = ROOT;
            if (!dir.exists() && !dir.mkdirs()) {
                logWarn("Could not create progress directory: " + dir.getAbsolutePath());
            }
            File f = fileFor(uuid);
            Data d = new Data();
            d.schemaVersion = CURRENT_SCHEMA_VERSION;
            d.applied = new ArrayList<>(cache.getOrDefault(uuid, Collections.emptySet()));

            File temp = new File(f.getParentFile(), f.getName() + ".tmp");
            try (FileWriter w = new FileWriter(temp)) {
                GSON.toJson(d, w);
            }
            try {
                java.nio.file.Files.move(
                        temp.toPath(), f.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                );
            } catch (IOException atomicNotSupported) {
                java.nio.file.Files.move(
                        temp.toPath(), f.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (Exception e) {
            logError("Error saving level-cap progress for " + uuid, e);
        }
    }

    // ================== MODEL/LOG ==================

    private static class Data {
        Integer schemaVersion;
        List<String> applied = new ArrayList<>();
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static void logWarn(String msg) {
        System.out.println("[PlayerLevelCapProgress][WARN] " + msg);
    }

    private static void logError(String msg, Throwable t) {
        System.err.println("[PlayerLevelCapProgress][ERROR] " + msg);
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            System.err.println(sw.toString());
        }
    }
}
