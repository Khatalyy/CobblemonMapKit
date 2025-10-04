package com.cobblemon.khataly.modhm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public class ModConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModConfig.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();
    private static final File CONFIG_FILE = new File("config/modhm/config.json");

    // --- Respawn times ---
    public static int ROCKSMASH_RESPAWN = 60;
    public static int CUT_RESPAWN = 60;
    public static int STRENGTH_RESPAWN = 60;

    // --- Flash duration ---
    public static int FLASH_DURATION = 60;

    // --- Required Items ---
    public static RequiredItem ROCKSMASH = new RequiredItem("modhm:steel_badge", "❌ You need the Steel Badge to use Rock Smash!");
    public static RequiredItem FLY = new RequiredItem(null, "❌ You need a special item to use Fly!");
    public static RequiredItem CUT = new RequiredItem(null, "❌ You need a knife or machete to use Cut!");
    public static RequiredItem STRENGTH = new RequiredItem(null, "❌ You need a heavy item to use Strength!");
    public static RequiredItem FLASH = new RequiredItem(null, "❌ You need a Flash Item to use Flash!");
    public static RequiredItem TELEPORT = new RequiredItem(null, "❌ You need a Teleport Item to use Teleport!");
    public static RequiredItem ROCKCLIMB = new RequiredItem(null, "❌ You need climbing gear to use Rock Climb!");
    public static RequiredItem ULTRAHOLE = new RequiredItem(null, "❌ You need Ultrabeast to summon a Ultra Hole!");

    // --- UltraHole default settings ---
    public static UltraHoleSettings ULTRAHOLE_SETTINGS = new UltraHoleSettings("minecraft:the_end", 0, 64, 0, 800); // default durata 200 tick

    // --- Load config from JSON ---
    public static void load() {
        try {
            if (!CONFIG_FILE.exists()) {
                save();
                return;
            }

            String jsonContent = Files.readString(CONFIG_FILE.toPath());

            if (!jsonContent.contains("\"respawn_time_seconds\"") ||
                    !jsonContent.contains("\"flash_duration_seconds\"") ||
                    !jsonContent.contains("\"required_items\"") ||
                    !jsonContent.contains("\"ultrahole_settings\"")) {

                LOGGER.warn("⚠️ Config obsolete or corrupted, regenerating con valori di default.");
                if (!CONFIG_FILE.delete()) LOGGER.error("⚠️ Cannot delete config file: {}", CONFIG_FILE.getAbsolutePath());
                save();
                return;
            }

            FileReader reader = new FileReader(CONFIG_FILE);
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            reader.close();

            boolean missingItem = data.required_items == null
                    || data.required_items.rocksmash == null
                    || data.required_items.fly == null
                    || data.required_items.cut == null
                    || data.required_items.strength == null
                    || data.required_items.flash == null
                    || data.required_items.teleport == null
                    || data.required_items.rockclimb == null
                    || data.required_items.ultrahole == null
                    || data.ultrahole_settings == null
                    || data.ultrahole_settings.destinationDimension == null;

            if (missingItem) {
                LOGGER.warn("⚠️ Uno o più RequiredItems o UltraHole settings mancanti, rigenero il config.");
                if (!CONFIG_FILE.delete()) LOGGER.error("⚠️ Cannot delete config file: {}", CONFIG_FILE.getAbsolutePath());
                save();
                return;
            }

            // Load respawn times
            if (data.respawn_time_seconds != null) {
                ROCKSMASH_RESPAWN = data.respawn_time_seconds.rocksmash;
                CUT_RESPAWN = data.respawn_time_seconds.cut;
                STRENGTH_RESPAWN = data.respawn_time_seconds.strength;
            }

            // Load flash duration
            if (data.flash_duration_seconds != null) FLASH_DURATION = data.flash_duration_seconds;

            // Load required items
            ROCKSMASH = data.required_items.rocksmash;
            FLY = data.required_items.fly;
            CUT = data.required_items.cut;
            STRENGTH = data.required_items.strength;
            FLASH = data.required_items.flash;
            TELEPORT = data.required_items.teleport;
            ROCKCLIMB = data.required_items.rockclimb;
            ULTRAHOLE = data.required_items.ultrahole;

            // Load UltraHole settings
            ULTRAHOLE_SETTINGS = data.ultrahole_settings;

        } catch (IOException e) {
            LOGGER.error("Error loading configuration file", e);
        }
    }

    // --- Save config to JSON ---
    public static void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                LOGGER.warn("⚠️ Cannot create configuration directory: {}", parent.getAbsolutePath());
            }

            ConfigData data = new ConfigData();

            data.respawn_time_seconds.rocksmash = ROCKSMASH_RESPAWN;
            data.respawn_time_seconds.cut = CUT_RESPAWN;
            data.respawn_time_seconds.strength = STRENGTH_RESPAWN;
            data.flash_duration_seconds = FLASH_DURATION;

            data.required_items.rocksmash = ROCKSMASH;
            data.required_items.fly = FLY;
            data.required_items.cut = CUT;
            data.required_items.strength = STRENGTH;
            data.required_items.flash = FLASH;
            data.required_items.teleport = TELEPORT;
            data.required_items.rockclimb = ROCKCLIMB;
            data.required_items.ultrahole = ULTRAHOLE;

            data.ultrahole_settings = ULTRAHOLE_SETTINGS;

            FileWriter writer = new FileWriter(CONFIG_FILE);
            GSON.toJson(data, writer);
            writer.close();
        } catch (IOException e) {
            LOGGER.error("Error saving configuration file", e);
        }
    }

    // --- Internal classes for JSON ---
    private static class ConfigData {
        RespawnTimes respawn_time_seconds = new RespawnTimes();
        Integer flash_duration_seconds = FLASH_DURATION;
        RequiredItems required_items = new RequiredItems();
        UltraHoleSettings ultrahole_settings = new UltraHoleSettings();
    }

    private static class RespawnTimes {
        int rocksmash = ROCKSMASH_RESPAWN;
        int cut = CUT_RESPAWN;
        int strength = STRENGTH_RESPAWN;
    }

    public static class RequiredItem {
        public String item;
        public String message;

        public RequiredItem() {}
        public RequiredItem(String item, String message) { this.item = item; this.message = message; }
    }

    private static class RequiredItems {
        RequiredItem rocksmash = null;
        RequiredItem fly = null;
        RequiredItem cut = null;
        RequiredItem strength = null;
        RequiredItem flash = null;
        RequiredItem teleport = null;
        RequiredItem rockclimb = null;
        RequiredItem ultrahole = null;
    }

    // --- UltraHole settings with coordinates and duration ---
    public static class UltraHoleSettings {
        public String destinationDimension;
        public double x;
        public double y;
        public double z;
        public int durationTicks; // durata del portale in tick

        public UltraHoleSettings() {
            this.destinationDimension = "minecraft:the_end";
            this.x = 0;
            this.y = 64;
            this.z = 0;
            this.durationTicks = 800;
        }

        public UltraHoleSettings(String destinationDimension, double x, double y, double z, int durationTicks) {
            this.destinationDimension = destinationDimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.durationTicks = durationTicks;
        }
    }
}
