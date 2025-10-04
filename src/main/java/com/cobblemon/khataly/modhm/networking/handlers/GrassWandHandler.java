package com.cobblemon.khataly.modhm.networking.handlers;

import com.cobblemon.khataly.modhm.config.GrassZonesConfig;
import com.cobblemon.khataly.modhm.networking.packet.PlaceGrassC2SPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.UUID;

public class GrassWandHandler {
    private static final int MAX_SIDE = 64;

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
        int y    = Math.min(a.getY(), b.getY());

        if ((maxX - minX + 1) > MAX_SIDE || (maxZ - minZ + 1) > MAX_SIDE) return;

        Block shortGrassBlock = tryShortGrass();
        int placed = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos   = new BlockPos(x, y, z);
                BlockPos below = pos.down();

                if (!world.isAir(pos) || world.isAir(below)) continue;

                // Short grass preferita
                if (shortGrassBlock != null) {
                    var st = shortGrassBlock.getDefaultState();
                    if (st.canPlaceAt(world, pos)) {
                        world.setBlockState(pos, st, 3);
                        placed++;
                        continue;
                    }
                }

                // Fallback Tall grass
                var tall = Blocks.TALL_GRASS.getDefaultState();
                if (tall.canPlaceAt(world, pos)) {
                    world.setBlockState(pos, tall, 3);
                    placed++;
                }
            }
        }

        // Se abbiamo davvero piazzato dell'erba: crea zona e salva
        if (placed > 0) {
            var defaultSpawns = java.util.List.of(
                    new GrassZonesConfig.SpawnEntry("cobblemon:sentret", 3, 7, 30),
                    new GrassZonesConfig.SpawnEntry("cobblemon:rattata", 3, 7, 30)
            );

            UUID id = GrassZonesConfig.addZone(
                    world.getRegistryKey(),
                    minX, minZ, maxX, maxZ,
                    y,
                    defaultSpawns
            );
            player.sendMessage(Text.literal("Zona d'erba creata: " + id), false);
        }

    }

    private static Block tryShortGrass() {
        try { return (Block) Blocks.class.getField("SHORT_GRASS").get(null); }
        catch (Exception e) {
            try { return (Block) Blocks.class.getField("GRASS").get(null); }
            catch (Exception ignored) { return null; }
        }
    }
}
