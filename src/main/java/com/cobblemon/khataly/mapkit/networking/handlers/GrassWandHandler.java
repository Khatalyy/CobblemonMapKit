package com.cobblemon.khataly.mapkit.networking.handlers;

import com.cobblemon.khataly.mapkit.config.GrassZonesConfig;
import com.cobblemon.khataly.mapkit.item.ModItems;
import com.cobblemon.khataly.mapkit.networking.packet.grasszones.PlaceGrassC2SPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.stream.Collectors;

public class GrassWandHandler {
    private static final int MAX_SIDE = 64;
    private static final String NBT_MODE = "grass_mode"; // "tall" | "short"

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                PlaceGrassC2SPacket.ID,
                (payload, ctx) -> {
                    ServerPlayerEntity player = ctx.player();
                    BlockPos a = payload.a();
                    BlockPos b = payload.b();
                    ctx.server().execute(() -> placeArea(player, a, b));
                }
        );
    }

    private static void placeArea(ServerPlayerEntity player, BlockPos a, BlockPos b) {
        if (player == null) return;
        World world = player.getWorld();

        int minX = Math.min(a.getX(), b.getX());
        int maxX = Math.max(a.getX(), b.getX());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxZ = Math.max(a.getZ(), b.getZ());

        // NUOVO: range verticale completo
        int minY = Math.min(a.getY(), b.getY());
        int maxY = Math.max(a.getY(), b.getY());

        if ((maxX - minX + 1) > MAX_SIDE || (maxZ - minZ + 1) > MAX_SIDE) {
            player.sendMessage(Text.literal("Area too large (max side " + MAX_SIDE + ")."), false);
            return;
        }

        // NUOVO: prevenzione overlap 3D (usa il range Y completo)
        if (GrassZonesConfig.overlaps(world.getRegistryKey(), minX, minZ, maxX, maxZ, minY, maxY)) {
            player.sendMessage(Text.literal("Cannot create the grass zone: it overlaps an existing one."), false);
            return;
        }

        // Read wand mode ("tall" or "short"), default = short
        boolean tallMode = readTallMode(player);

        Block shortGrassBlock = resolveShortGrass();
        int placed = 0;

        // NUOVO: posizionamento erba su tutto il volume selezionato (scansione per-Y)
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos   = new BlockPos(x, y, z);
                    BlockPos below = pos.down();

                    if (!world.isAir(pos)) continue;
                    if (!world.getBlockState(below).isOf(Blocks.GRASS_BLOCK)) continue;

                    if (tallMode) {
                        // Tall grass richiede 2 blocchi d'aria (pos e pos.up())
                        if (!world.isAir(pos.up())) continue;
                        BlockState tall = Blocks.TALL_GRASS.getDefaultState();
                        if (tall.canPlaceAt(world, pos)) {
                            TallPlantBlock.placeAt(world, tall, pos, 3);
                            placed++;
                        }
                    } else {
                        if (shortGrassBlock == null) continue;
                        BlockState st = shortGrassBlock.getDefaultState();
                        if (st.canPlaceAt(world, pos)) {
                            world.setBlockState(pos, st, 3);
                            placed++;
                        }
                    }
                }
            }
        }

        // Default spawns (examples)
        List<GrassZonesConfig.SpawnEntry> defaultSpawns = List.of(
                new GrassZonesConfig.SpawnEntry("cobblemon:sentret", 3, 7, 30, GrassZonesConfig.TimeBand.DAY),
                new GrassZonesConfig.SpawnEntry("cobblemon:rattata", 3, 7, 30, GrassZonesConfig.TimeBand.NIGHT),
                new GrassZonesConfig.SpawnEntry("cobblemon:oddish", 5, 9, 10, GrassZonesConfig.TimeBand.BOTH),
                new GrassZonesConfig.SpawnEntry("cobblemon:geodude", 10, 14, 30, GrassZonesConfig.TimeBand.BOTH, "alolan")
        );

        // New: human-friendly incremental name (Zone1, Zone2, ...)
        String zoneName = nextAvailableZoneName();

        // NUOVO: creazione zona con range verticale minY..maxY
        UUID id = GrassZonesConfig.addZone(
                zoneName,
                world.getRegistryKey(),
                minX, minZ, maxX, maxZ,
                minY, maxY,
                defaultSpawns
        );

        player.sendMessage(Text.literal("Grass zone created: " + zoneName + " (" + id + "). Blocks placed: " + placed), false);
    }

    /** Builds next free name: Zone1, Zone2, ... */
    private static String nextAvailableZoneName() {
        Set<String> existing = GrassZonesConfig.getAll()
                .stream().map(z -> z.name().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        int i = 1;
        while (true) {
            String candidate = ("Zone" + i);
            if (!existing.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
            i++;
        }
    }

    /** Reads "grass_mode" from the wand (main/offhand). Default short=false. */
    private static boolean readTallMode(ServerPlayerEntity player) {
        var main = player.getMainHandStack();
        var off  = player.getOffHandStack();

        if (!main.isEmpty() && main.getItem() == ModItems.GRASS_WAND) {
            NbtComponent data = main.get(DataComponentTypes.CUSTOM_DATA);
            if (data == null) return false;
            return "tall".equalsIgnoreCase(data.copyNbt().getString(NBT_MODE));
        } else if (!off.isEmpty() && off.getItem() == ModItems.GRASS_WAND) {
            NbtComponent data = off.get(DataComponentTypes.CUSTOM_DATA);
            if (data == null) return false;
            return "tall".equalsIgnoreCase(data.copyNbt().getString(NBT_MODE));
        }

        return false; // not holding the wand -> default short
    }

    /** SHORT_GRASS (new mappings) or legacy GRASS. */
    private static Block resolveShortGrass() {
        Optional<Block> a = Registries.BLOCK.getOrEmpty(Identifier.of("minecraft", "short_grass"));
        if (a.isPresent()) return a.get();
        Optional<Block> b = Registries.BLOCK.getOrEmpty(Identifier.of("minecraft", "grass")); // legacy id
        return b.orElse(null);
    }
}
