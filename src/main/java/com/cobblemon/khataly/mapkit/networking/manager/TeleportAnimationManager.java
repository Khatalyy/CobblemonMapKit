package com.cobblemon.khataly.mapkit.networking.manager;

import com.cobblemon.khataly.mapkit.networking.packet.RotatePlayerS2CPacket;
import com.cobblemon.khataly.mapkit.sound.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.*;

/**
 * Teleport block logic:
 * - Salita lentissima (~1s) senza rotazioni lato server
 * - Rotazione fluida lato client via pacchetto S2C
 * - Teletrasporto immediato se tocca il soffitto
 * - Atterraggio sicuro e anti-loop
 * - Solo suono custom del teleport block
 */
public final class TeleportAnimationManager {

    private TeleportAnimationManager() {}

    /* ===== Parametri ===== */
    private static final int ANIM_TICKS = 20;           // durata salita ≈1s
    private static final double LIFT_Y = 0.004;         // salita lenta
    private static final float TOTAL_ROT_DEG = 160f;     // rotazione complessiva “soft”
    private static final int ARRIVAL_SUPPRESS_TICKS = 6;

    /* ===== Stato ===== */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_ARRIVAL_TICK = new HashMap<>();
    private static final Map<UUID, Loc> ARRIVAL_SUPPRESS_WHILE_ON_BLOCK = new HashMap<>();

    /* ===== Records ===== */
    private record Loc(RegistryKey<World> dim, BlockPos block) {}
    private record Pending(ServerWorld targetWorld, BlockPos targetPos, long startTick) {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TeleportAnimationManager::tick);
    }

    public static boolean shouldIgnoreStep(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        long now = world.getTime();
        Long last = LAST_ARRIVAL_TICK.get(player.getUuid());
        if (last != null && (now - last) < ARRIVAL_SUPPRESS_TICKS) return true;

        Loc loc = ARRIVAL_SUPPRESS_WHILE_ON_BLOCK.get(player.getUuid());
        return loc != null && loc.dim.equals(world.getRegistryKey()) && loc.block.equals(pos);
    }

    public static void queueTeleport(ServerPlayerEntity player, ServerWorld targetWorld, BlockPos targetPos) {
        UUID pid = player.getUuid();
        if (PENDING.containsKey(pid)) return;

        // azzera momentum verticale
        var v = player.getVelocity();
        player.setVelocity(v.x, 0.0, v.z);
        player.velocityModified = true;

        long now = player.getServerWorld().getTime();
        PENDING.put(pid, new Pending(targetWorld, targetPos, now));

        // Suono custom di avvio
        player.getServerWorld().playSound(null, player.getBlockPos(),
                ModSounds.TELEPORT_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.0f);

        // 👉 Rotazione lato client: invia 1 pacchetto con durata e gradi
        ServerPlayNetworking.send(player, new RotatePlayerS2CPacket(TOTAL_ROT_DEG, ANIM_TICKS));
    }

    private static void tick(MinecraftServer server) {
        if (!PENDING.isEmpty()) {
            List<UUID> done = new ArrayList<>();

            for (var entry : PENDING.entrySet()) {
                UUID id = entry.getKey();
                Pending pending = entry.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null) { done.add(id); continue; }

                long now = player.getServerWorld().getTime();
                long elapsed = now - pending.startTick;

                if (playerHitCeiling(player)) {
                    doTeleport(player, pending.targetWorld, pending.targetPos);
                    done.add(id);
                    continue;
                }

                if (elapsed < ANIM_TICKS) {
                    animateLift(player);
                } else {
                    doTeleport(player, pending.targetWorld, pending.targetPos);
                    done.add(id);
                }
            }

            done.forEach(PENDING::remove);
        }

        // pulizia anti-loop
        if (!ARRIVAL_SUPPRESS_WHILE_ON_BLOCK.isEmpty()) {
            List<UUID> clear = new ArrayList<>();
            for (var e : ARRIVAL_SUPPRESS_WHILE_ON_BLOCK.entrySet()) {
                UUID pid = e.getKey();
                Loc loc = e.getValue();
                ServerPlayerEntity pl = server.getPlayerManager().getPlayer(pid);
                if (pl == null) { clear.add(pid); continue; }
                if (!pl.getServerWorld().getRegistryKey().equals(loc.dim)) { clear.add(pid); continue; }
                BlockPos belowFeet = pl.getBlockPos().down();
                if (!belowFeet.equals(loc.block)) clear.add(pid);
            }
            clear.forEach(ARRIVAL_SUPPRESS_WHILE_ON_BLOCK::remove);
        }
    }

    /* ----------- Salita lenta (particelle leggere, NO suoni extra) ----------- */
    private static void animateLift(ServerPlayerEntity player) {
        var v = player.getVelocity();
        player.setVelocity(v.x, LIFT_Y, v.z);
        player.fallDistance = 0;
        player.velocityModified = true;

        var w = player.getServerWorld();
        w.spawnParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 0.5, player.getZ(),
                8, 0.15, 0.25, 0.15, 0.0);
    }

    /* ----------- Teleport + atterraggio sicuro ----------- */
    private static void doTeleport(ServerPlayerEntity player, ServerWorld targetWorld, BlockPos base) {
        BlockPos safeFeet = findSafeLandingAbove(targetWorld, base);
        double tx = safeFeet.getX() + 0.5;
        double ty = safeFeet.getY();
        double tz = safeFeet.getZ() + 0.5;

        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        player.teleport(targetWorld, tx, ty, tz, player.getYaw(), player.getPitch());

        LAST_ARRIVAL_TICK.put(player.getUuid(), targetWorld.getTime());
        ARRIVAL_SUPPRESS_WHILE_ON_BLOCK.put(player.getUuid(), new Loc(targetWorld.getRegistryKey(), base));

        targetWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                tx, ty + 0.5, tz, 25, 0.4, 0.4, 0.4, 0.0);
    }

    private static boolean playerHitCeiling(ServerPlayerEntity player) {
        BlockPos head = player.getBlockPos().up();
        return !player.getWorld().getBlockState(head).isAir();
    }

    private static BlockPos findSafeLandingAbove(ServerWorld world, BlockPos base) {
        final int x = base.getX();
        final int z = base.getZ();

        int startY = Math.max(base.getY() + 1, world.getBottomY() + 1);
        int heightmapY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int maxY = Math.min(world.getTopY(), heightmapY + 6);

        for (int feetY = startY; feetY <= maxY; feetY++) {
            BlockPos feet = new BlockPos(x, feetY, z);
            BlockPos head = feet.up();
            BlockPos below = feet.down();

            BlockState feetState = world.getBlockState(feet);
            BlockState headState = world.getBlockState(head);
            BlockState belowState = world.getBlockState(below);

            boolean spaceFree = feetState.isAir() && headState.isAir();
            boolean canStand = belowState.isOpaqueFullCube(world, below)
                    || belowState.isSideSolidFullSquare(world, below, Direction.UP);

            if (spaceFree && canStand) return feet;
        }
        return base.up(1);
    }
}
