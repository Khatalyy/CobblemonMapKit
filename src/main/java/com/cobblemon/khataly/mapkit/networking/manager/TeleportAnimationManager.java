package com.cobblemon.khataly.mapkit.networking.manager;

import com.cobblemon.khataly.mapkit.sound.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
 * - Salita lentissima (~1s) con rotazione camera fluida (cosine in-out)
 * - Teletrasporto immediato se si tocca il soffitto
 * - Ricerca atterraggio sicuro e anti-loop di riattivazione
 * - Solo suono custom del teleport block (niente ametista, niente Enderman)
 */
public final class TeleportAnimationManager {

    private TeleportAnimationManager() {}

    /* ===== Parametri animazione / comportamento ===== */
    private static final int ANIM_TICKS = 20;          // durata salita ≈1s a 20 TPS
    private static final double LIFT_Y = 0.004;        // velocità verticale molto lenta
    private static final float TOTAL_ROT_DEG = 90f;    // rotazione totale dello yaw durante la salita
    private static final int ARRIVAL_SUPPRESS_TICKS = 6; // debounce dopo arrivo

    /* ===== Stati ===== */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_ARRIVAL_TICK = new HashMap<>();
    private static final Map<UUID, Loc> ARRIVAL_SUPPRESS_WHILE_ON_BLOCK = new HashMap<>();
    private static final Map<UUID, Float> LAST_EASE = new HashMap<>(); // progress dell’easing per player

    /* ===== Records ===== */
    private record Loc(RegistryKey<World> dim, BlockPos block) {}
    private record Pending(ServerWorld targetWorld, BlockPos targetPos, long startTick) {}

    /* ===== Registrazione tick ===== */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TeleportAnimationManager::tick);
    }

    /**
     * Ritorna true se dobbiamo ignorare lo “step” sul blocco (per evitare loop all’arrivo).
     */
    public static boolean shouldIgnoreStep(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        long now = world.getTime();
        Long last = LAST_ARRIVAL_TICK.get(player.getUuid());
        if (last != null && (now - last) < ARRIVAL_SUPPRESS_TICKS) return true;

        Loc loc = ARRIVAL_SUPPRESS_WHILE_ON_BLOCK.get(player.getUuid());
        return loc != null && loc.dim.equals(world.getRegistryKey()) && loc.block.equals(pos);
    }

    /**
     * Mette in coda l’animazione + teletrasporto verso la destinazione.
     */
    public static void queueTeleport(ServerPlayerEntity player, ServerWorld targetWorld, BlockPos targetPos) {
        UUID pid = player.getUuid();
        if (PENDING.containsKey(pid)) return;

        // Annulla qualsiasi momentum verticale
        var v = player.getVelocity();
        player.setVelocity(v.x, 0.0, v.z);
        player.velocityModified = true;

        long now = player.getServerWorld().getTime();
        PENDING.put(pid, new Pending(targetWorld, targetPos, now));
        LAST_EASE.put(pid, 0f); // reset easing progress

        // Solo suono custom all’avvio
        player.getServerWorld().playSound(
                null,
                player.getBlockPos(),
                ModSounds.TELEPORT_BLOCK,
                SoundCategory.PLAYERS,
                1.0f,
                1.0f
        );
    }

    /* ===== Tick globale ===== */
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

                // Teletrasporto immediato se si tocca il soffitto
                if (playerHitCeiling(player)) {
                    doTeleport(player, pending.targetWorld, pending.targetPos);
                    done.add(id);
                    continue;
                }

                // Salita + rotazione fluida finché dura l’animazione
                if (elapsed < ANIM_TICKS) {
                    animateLift(player, elapsed);
                } else {
                    doTeleport(player, pending.targetWorld, pending.targetPos);
                    done.add(id);
                }
            }

            // Cleanup code dei pending completati
            for (UUID id : done) {
                PENDING.remove(id);
                LAST_EASE.remove(id);
            }
        }

        // Pulisce la soppressione quando il player si allontana dal blocco destinazione
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

    /* ===== Animazione: salita lentissima + rotazione camera con easing ===== */
    private static void animateLift(ServerPlayerEntity player, long elapsedTicks) {
        // Salita
        var v = player.getVelocity();
        player.setVelocity(v.x, LIFT_Y, v.z);
        player.fallDistance = 0;
        player.velocityModified = true;

        // Easing cosine-in-out in [0..1]
        UUID id = player.getUuid();
        float t = Math.min(1f, (elapsedTicks + 1f) / (float) ANIM_TICKS);  // +1 per evitare stacco iniziale
        float eased = 0.5f - 0.5f * (float) Math.cos(Math.PI * t);
        float prev = LAST_EASE.getOrDefault(id, 0f);
        float deltaDeg = (eased - prev) * TOTAL_ROT_DEG;

        // Applica solo il delta rispetto al tick precedente
        float newYaw = player.getYaw() + deltaDeg;
        player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), newYaw, player.getPitch());
        LAST_EASE.put(id, eased);

        // Particelle leggere (nessun suono extra)
        var w = player.getServerWorld();
        w.spawnParticles(
                ParticleTypes.PORTAL,
                player.getX(), player.getY() + 0.5, player.getZ(),
                8, 0.15, 0.25, 0.15, 0.0
        );
    }

    /* ===== Teletrasporto e atterraggio sicuro ===== */
    private static void doTeleport(ServerPlayerEntity player, ServerWorld targetWorld, BlockPos base) {
        BlockPos safeFeet = findSafeLandingAbove(targetWorld, base);
        double tx = safeFeet.getX() + 0.5;
        double ty = safeFeet.getY();
        double tz = safeFeet.getZ() + 0.5;

        // Ferma la velocità e teletrasporta
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        player.teleport(targetWorld, tx, ty, tz, player.getYaw(), player.getPitch());

        // Aggiorna anti-loop
        LAST_ARRIVAL_TICK.put(player.getUuid(), targetWorld.getTime());
        ARRIVAL_SUPPRESS_WHILE_ON_BLOCK.put(player.getUuid(), new Loc(targetWorld.getRegistryKey(), base));

        // Solo particelle all’arrivo (NESSUN suono Enderman; suono custom solo all’avvio)
        targetWorld.spawnParticles(
                ParticleTypes.REVERSE_PORTAL,
                tx, ty + 0.5, tz,
                25, 0.4, 0.4, 0.4, 0.0
        );
    }

    /* ===== Controllo soffitto ===== */
    private static boolean playerHitCeiling(ServerPlayerEntity player) {
        BlockPos head = player.getBlockPos().up();
        return !player.getWorld().getBlockState(head).isAir();
    }

    /* ===== Ricerca atterraggio sicuro sopra la base ===== */
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
