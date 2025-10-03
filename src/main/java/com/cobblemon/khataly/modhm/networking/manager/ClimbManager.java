package com.cobblemon.khataly.modhm.networking.manager;

import com.cobblemon.khataly.modhm.block.ModBlocks;
import com.cobblemon.khataly.modhm.block.custom.ClimbableRock;
import com.cobblemon.khataly.modhm.networking.util.NetUtil;
import com.cobblemon.khataly.modhm.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestisce lo stato di arrampicata dei giocatori.
 * - Azzera fallDistance durante la scalata
 * - Breve immunità al danno da caduta dopo la fine
 * - Al termine di una SALITA sposta dolcemente il player di esattamente 1 blocco in avanti (lontano dalla parete)
 */
public final class ClimbManager {
    private static final ClimbManager INSTANCE = new ClimbManager();
    public static ClimbManager get() { return INSTANCE; }
    private ClimbManager() {}

    // --- Stato per-giocatore ---
    private final Map<ServerPlayerEntity, BlockPos> playersClimbing = new ConcurrentHashMap<>();
    private final Map<ServerPlayerEntity, Integer> climbingTicks = new ConcurrentHashMap<>();
    private final Map<ServerPlayerEntity, Set<BlockPos>> visitedClimbBlocks = new ConcurrentHashMap<>();
    private final Map<ServerPlayerEntity, BlockPos> climbingTargets = new ConcurrentHashMap<>();
    private final Set<ServerPlayerEntity> playersPlayingSound = ConcurrentHashMap.newKeySet();

    // Glide post-salita per 1 blocco (dolce)
    private static final class GlideState {
        final double destX, destY, destZ;
        int ticksLeft;
        GlideState(double x, double y, double z, int ticks) {
            this.destX = x; this.destY = y; this.destZ = z; this.ticksLeft = ticks;
        }
    }
    private final Map<ServerPlayerEntity, GlideState> forwardGlide = new ConcurrentHashMap<>();

    // Immunità al danno da caduta post-scalata (grace period)
    private final Map<ServerPlayerEntity, Integer> fallImmunityTicks = new ConcurrentHashMap<>();

    // Config “interna” per la scalata
    private static final double CLIMB_SPEED = 0.15;
    private static final double REACH_EPSILON = 0.2;
    private static final int SOUND_TICK_DELAY = 4;
    private static final int FALL_IMMUNITY_GRACE_TICKS = 10;

    // Parametri glide
    private static final double GLIDE_SPEED = 0.12;     // dolce
    private static final int GLIDE_MAX_TICKS = 10;      // massimo tempo di glide
    private static final double GLIDE_SNAP_EPS = 0.06;  // quando abbastanza vicino, snap

    /** Avvia la scalata da una posizione iniziale. */
    public void start(ServerPlayerEntity player, BlockPos startPos) {
        playersClimbing.put(player, startPos);
        climbingTicks.put(player, 0);
        player.fallDistance = 0f; // evita accumulo iniziale
    }

    /** Da chiamare ad ogni tick server. */
    public void tick() {
        // --- Fase scalata: muove i player e azzera fallDistance ---
        playersClimbing.forEach((player, startPos) -> {
            if (!player.isAlive()) {
                cleanup(player);
                return;
            }

            player.fallDistance = 0f; // niente danno da caduta mentre scala
            climbingTicks.put(player, climbingTicks.getOrDefault(player, 0) + 1);

            visitedClimbBlocks.putIfAbsent(player, new HashSet<>());
            Set<BlockPos> visited = visitedClimbBlocks.get(player);
            visited.add(startPos);

            BlockPos target = climbingTargets.get(player);
            if (target == null) {
                target = findNextClimbStep((ServerWorld) player.getWorld(), startPos, visited);
                if (target == null) {
                    NetUtil.msg(player, "🧗 No climbable blocks!");
                    grantFallImmunity(player);
                    cleanup(player);
                    return;
                }
                climbingTargets.put(player, target);
                visited.add(target);
            }

            double dx = target.getX() + 0.5 - player.getX();
            double dy = target.getY() + 1.0 - player.getY();
            double dz = target.getZ() + 0.5 - player.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distance < REACH_EPSILON) {
                BlockPos next = findNextClimbStep((ServerWorld) player.getWorld(), target, visited);
                if (next == null) {
                    // Ultimo step raggiunto
                    player.fallDistance = 0f;
                    grantFallImmunity(player);

                    if (dy > 0) {
                        // Finito in SALITA: avvia glide di 1 blocco in avanti (lontano dalla parete)
                        Direction forwardDir = getForwardDirectionAwayFromWall((ServerWorld) player.getWorld(), target, player);

                        BlockPos destBlock = target.up().offset(forwardDir); // un blocco sopra + uno in avanti
                        double destX = destBlock.getX() + 0.5;
                        double destY = destBlock.getY();      // piedi sul blocco
                        double destZ = destBlock.getZ() + 0.5;

                        // Registra glide dolce verso la destinazione
                        forwardGlide.put(player, new GlideState(destX, destY, destZ, GLIDE_MAX_TICKS));

                        // piccolo start boost verticale (molto leggero)
                        player.setVelocity(0, 0.08, 0);
                        player.velocityModified = true;

                        NetUtil.msg(player, "🧗 You climbed up!");
                    } else {
                        // Fine verso il basso: niente push
                        player.setVelocity(0, -0.08, 0);
                        player.velocityModified = true;
                        NetUtil.msg(player, "🧗 You climbed down!");
                    }

                    cleanup(player); // chiudi stato scalata (l'immunità resta; il glide è gestito sotto)
                    return;
                }
                climbingTargets.put(player, next);
                visited.add(next);
                return;
            }

            // Movimento verso il target
            player.setVelocity(dx / distance * CLIMB_SPEED, dy / distance * CLIMB_SPEED, dz / distance * CLIMB_SPEED);
            player.velocityModified = true;

            // Suono di arrampicata dopo un breve delay
            if (climbingTicks.get(player) >= SOUND_TICK_DELAY && !playersPlayingSound.contains(player)) {
                player.playSoundToPlayer(ModSounds.CLIMBABLE_ROCK, SoundCategory.PLAYERS, 1f, 1f);
                playersPlayingSound.add(player);
            }
        });

        // --- Fase glide dolce di 1 blocco avanti (post-salita) ---
        var itGlide = forwardGlide.entrySet().iterator();
        while (itGlide.hasNext()) {
            Map.Entry<ServerPlayerEntity, GlideState> e = itGlide.next();
            ServerPlayerEntity player = e.getKey();
            GlideState g = e.getValue();

            if (!player.isAlive()) {
                itGlide.remove();
                continue;
            }

            // niente danno da caduta durante il glide
            player.fallDistance = 0f;

            double dx = g.destX - player.getX();
            double dy = g.destY - player.getY();
            double dz = g.destZ - player.getZ();
            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);

            if (dist < GLIDE_SNAP_EPS || g.ticksLeft <= 0) {
                // Snap finale sul centro del blocco di destinazione
                // Uso una teletraslazione leggera: mantiene yaw/pitch
                player.teleport(((ServerWorld)player.getWorld()), g.destX, g.destY, g.destZ, player.getYaw(), player.getPitch());
                player.setVelocity(0, 0, 0);
                player.velocityModified = true;

                itGlide.remove();
                continue;
            }

            // Avanza dolcemente verso la destinazione
            double vx = (dx / dist) * GLIDE_SPEED;
            double vy = (dy / dist) * GLIDE_SPEED;
            double vz = (dz / dist) * GLIDE_SPEED;
            player.setVelocity(vx, vy, vz);
            player.velocityModified = true;

            g.ticksLeft--;
        }

        // --- Fase post-scalata: mantieni immunità per qualche tick ---
        var it = fallImmunityTicks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ServerPlayerEntity, Integer> e = it.next();
            ServerPlayerEntity player = e.getKey();
            int ticksLeft = e.getValue();

            player.fallDistance = 0f; // continua ad azzerare durante l’immunità

            int next = ticksLeft - 1;
            if (next <= 0) {
                it.remove();
            } else {
                e.setValue(next);
            }
        }
    }

    /** Direzione "avanti": lontano dalla parete. Se il blocco non è climbable, fallback sulla facing del player. */
    private Direction getForwardDirectionAwayFromWall(ServerWorld world, BlockPos target, ServerPlayerEntity player) {
        BlockState targetState = world.getBlockState(target);
        if (targetState.isOf(ModBlocks.CLIMBABLE_ROCK)) {
            // ClimbableRock.FACING punta *verso* il player (fronte del blocco).
            // Per andare *via* dalla parete usiamo l'opposto.
            return targetState.get(ClimbableRock.FACING).getOpposite();
        }
        return player.getHorizontalFacing(); // fallback
    }

    /** Ripulisce tutto lo stato di scalata (ma NON l'immunità né il glide). */
    private void cleanup(ServerPlayerEntity player) {
        playersClimbing.remove(player);
        climbingTicks.remove(player);
        playersPlayingSound.remove(player);
        climbingTargets.remove(player);
        visitedClimbBlocks.remove(player);
        player.fallDistance = 0f;
        // forwardGlide e immunità restano gestiti dalle rispettive sezioni.
    }

    /** Concede immunità al danno da caduta per un certo numero di tick. */
    private void grantFallImmunity(ServerPlayerEntity player) {
        fallImmunityTicks.merge(player, ClimbManager.FALL_IMMUNITY_GRACE_TICKS, Math::max);
    }

    /** Trova il prossimo passo di arrampicata. */
    private BlockPos findNextClimbStep(ServerWorld world, BlockPos from, Set<BlockPos> visited) {
        BlockState state = world.getBlockState(from);

        // 1) Su
        BlockPos up = from.up();
        if (!visited.contains(up) && state.isOf(ModBlocks.CLIMBABLE_ROCK) && world.getBlockState(up).isOf(ModBlocks.CLIMBABLE_ROCK)) {
            return up;
        }
        if (state.isOf(ModBlocks.CLIMBABLE_ROCK)) {
            Direction facing = state.get(ClimbableRock.FACING);
            BlockPos upForward = up.offset(facing);
            if (!visited.contains(upForward) && world.getBlockState(upForward).isOf(ModBlocks.CLIMBABLE_ROCK)) return upForward;

            BlockPos upBackward = up.offset(facing.getOpposite());
            if (!visited.contains(upBackward) && world.getBlockState(upBackward).isOf(ModBlocks.CLIMBABLE_ROCK)) return upBackward;
        }

        // 2) Giù
        BlockPos down = from.down();
        if (!visited.contains(down) && world.getBlockState(down).isOf(ModBlocks.CLIMBABLE_ROCK)) return down;
        if (state.isOf(ModBlocks.CLIMBABLE_ROCK)) {
            Direction facing = state.get(ClimbableRock.FACING);
            BlockPos downForward = down.offset(facing);
            if (!visited.contains(downForward) && world.getBlockState(downForward).isOf(ModBlocks.CLIMBABLE_ROCK)) return downForward;

            BlockPos downBackward = down.offset(facing.getOpposite());
            if (!visited.contains(downBackward) && world.getBlockState(downBackward).isOf(ModBlocks.CLIMBABLE_ROCK)) return downBackward;
        }

        // 3) Orizzontali vicini
        Direction[] horizontals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction dir : horizontals) {
            BlockPos n = from.offset(dir);
            if (!visited.contains(n) && world.getBlockState(n).isOf(ModBlocks.CLIMBABLE_ROCK)) return n;
        }

        return null;
    }
}
