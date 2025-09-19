package com.cobblemon.khataly.modhm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.*;
import java.util.*;

public class FlyTargetConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/modhm/flytargets.json");

    private static final Map<String, TargetInfo> targets = new HashMap<>();

    public static class TargetInfo {
        public final RegistryKey<World> worldKey;
        public final BlockPos pos;

        public TargetInfo(RegistryKey<World> worldKey, BlockPos pos) {
            this.worldKey = worldKey;
            this.pos = pos;
        }
    }

    // ===========================
    //        API PUBBLICA
    // ===========================

    public static boolean addTarget(String name, RegistryKey<World> worldKey, BlockPos pos) {
        String key = name.toLowerCase();
        if (targets.containsKey(key)) return false;
        targets.put(key, new TargetInfo(worldKey, pos));
        save();
        return true;
    }

    public static boolean removeTarget(String name) {
        String key = name.toLowerCase();
        if (!targets.containsKey(key)) return false;
        targets.remove(key);
        save();
        return true;
    }

    public static TargetInfo getTarget(String name) {
        return targets.get(name.toLowerCase());
    }

    public static Map<String, TargetInfo> getAllTargets() {
        return Collections.unmodifiableMap(targets);
    }

    public static void reload() {
        load();
    }

    // ===========================
    //     LOAD & SAVE LOGICA
    // ===========================

    public static void load() {
        try {
            if (!CONFIG_FILE.exists()) {
                save(); // crea file vuoto
                return;
            }

            FileReader reader = new FileReader(CONFIG_FILE);
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            reader.close();

            targets.clear();
            if (data != null && data.targets != null) {
                for (FlyTargetData d : data.targets) {
                    RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(d.worldKey));
                    BlockPos pos = new BlockPos(d.x, d.y, d.z);
                    targets.put(d.name.toLowerCase(), new TargetInfo(worldKey, pos));
                }
            }

            System.out.println("[FlyTargetConfigServer] Loaded " + targets.size() + " fly targets.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            ConfigData data = new ConfigData();
            for (var entry : targets.entrySet()) {
                String name = entry.getKey();
                TargetInfo info = entry.getValue();
                data.targets.add(new FlyTargetData(name, info.worldKey.getValue().toString(),
                        info.pos.getX(), info.pos.getY(), info.pos.getZ()));
            }

            FileWriter writer = new FileWriter(CONFIG_FILE);
            GSON.toJson(data, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ConfigData {
        List<FlyTargetData> targets = new ArrayList<>();
    }

    private static class FlyTargetData {
        String name;
        String worldKey;
        int x, y, z;

        FlyTargetData(String name, String worldKey, int x, int y, int z) {
            this.name = name;
            this.worldKey = worldKey;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
