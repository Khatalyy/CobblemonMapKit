package com.cobblemon.khataly.mapkit.sound;

import com.cobblemon.khataly.mapkit.CobblemonMapKitMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final SoundEvent DIRECTIONAL_PANEL_BLOCK = registerSoundEvent("directional_panel_block");
    public static final SoundEvent BREAKABLE_ROCK = registerSoundEvent("breakable_rock");
    public static final SoundEvent CUTTABLE_TREE = registerSoundEvent("cuttable_tree");
    public static final SoundEvent MOVABLE_ROCK = registerSoundEvent("movable_rock");
    public static final SoundEvent CLIMBABLE_ROCK = registerSoundEvent("climbable_rock");
    public static final SoundEvent WALL_BUMP = registerSoundEvent("wall_bump");
    public static final SoundEvent TELEPORT = registerSoundEvent("teleport");
    public static final SoundEvent FLY = registerSoundEvent("fly");
    public static final SoundEvent FLASH = registerSoundEvent("flash");

    private static SoundEvent registerSoundEvent(String name) {
        Identifier id = Identifier.of(CobblemonMapKitMod.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void registerSounds() {
        CobblemonMapKitMod.LOGGER.info("Registering Mod Sounds for " + CobblemonMapKitMod.MOD_ID);
    }
}
