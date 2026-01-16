package com.cobblemon.khataly.mapkit.networking.manager;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RestoreManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("RestoreManager");
    private static final RestoreManager INSTANCE = new RestoreManager();
    public static RestoreManager get() { return INSTANCE; }
    private RestoreManager() {}

    /** Chiave: dimensione + posizione */
    public record DimPos(RegistryKey<World> dim, BlockPos pos) {}

    /** original (dim+pos) -> TimedBlock */
    private final Map<DimPos, TimedBlock> blocksToRestore = new ConcurrentHashMap<>();
    /** alias: posizione corrente (dim+pos) -> posizione originale (dim+pos) */
    private final Map<DimPos, DimPos> currentToOriginal = new ConcurrentHashMap<>();

    public boolean isBusy(ServerWorld world, BlockPos originalPos) {
        return blocksToRestore.containsKey(new DimPos(world.getRegistryKey(), originalPos));
    }

    public TimedBlock getTimed(ServerWorld world, BlockPos original) {
        return blocksToRestore.get(new DimPos(world.getRegistryKey(), original));
    }

    public BlockPos resolveOriginal(ServerWorld world, BlockPos clicked) {
        DimPos key = new DimPos(world.getRegistryKey(), clicked);
        return currentToOriginal.getOrDefault(key, key).pos();
    }

    public void forgetAlias(ServerWorld world, BlockPos current) {
        currentToOriginal.remove(new DimPos(world.getRegistryKey(), current));
    }

    public void addTimed(ServerWorld world, BlockPos originalPos, BlockState originalState, int seconds) {
        blocksToRestore.put(
                new DimPos(world.getRegistryKey(), originalPos),
                new TimedBlock(originalState, seconds * 20, null)
        );
    }

    public void registerMove(ServerWorld world, BlockPos originalPos, BlockPos movedTo, BlockState state, int seconds) {
        DimPos origKey = new DimPos(world.getRegistryKey(), originalPos);
        TimedBlock tb = blocksToRestore.get(origKey);

        if (tb != null) {
            tb.movedTo = movedTo;
            tb.ticksLeft = seconds * 20;
        } else {
            tb = new TimedBlock(state, seconds * 20, movedTo);
            blocksToRestore.put(origKey, tb);
        }

        currentToOriginal.put(new DimPos(world.getRegistryKey(), movedTo), origKey);
    }

    /**
     * Tick server-side: ripristina nel mondo corretto in base alla dimensione salvata.
     * Chiamalo UNA volta per tick: RestoreManager.get().tick(server);
     */
    public void tick(MinecraftServer server) {
        blocksToRestore.entrySet().removeIf(entry -> {
            DimPos originalKey = entry.getKey();
            BlockPos originalPos = originalKey.pos();
            TimedBlock tb = entry.getValue();

            ServerWorld world = server.getWorld(originalKey.dim());
            if (world == null) {
                // Dimensione non caricata/non disponibile: droppa per evitare leak.
                return true;
            }

            tb.ticksLeft--;
            if (tb.ticksLeft > 0) return false;

            if (tb.fallingEntity != null && tb.fallingEntity.isAlive()) {
                tb.fallingEntity.discard();
            }

            // Se il blocco è "moved" (es. falling block), ripulisci dove è finito (stessa dimensione)
            if (tb.movedTo != null && !tb.movedTo.equals(originalPos)) {
                BlockPos moved = tb.movedTo;
                DimPos movedKey = new DimPos(originalKey.dim(), moved);

                BlockState stateAtMoved = world.getBlockState(moved);
                if (stateAtMoved.isOf(tb.blockState.getBlock())) {
                    world.setBlockState(moved, Blocks.AIR.getDefaultState());
                    currentToOriginal.remove(movedKey);
                } else {
                    final int maxSearch = 64;
                    BlockPos scan = moved.down();
                    int steps = 0;

                    while (scan.getY() >= world.getBottomY() && steps < maxSearch) {
                        BlockState s = world.getBlockState(scan);
                        if (s.isOf(tb.blockState.getBlock())) {
                            world.setBlockState(scan, Blocks.AIR.getDefaultState());
                            currentToOriginal.remove(new DimPos(originalKey.dim(), scan));
                            break;
                        }
                        scan = scan.down();
                        steps++;
                    }

                    currentToOriginal.remove(movedKey);
                }
            }

            world.setBlockState(originalPos, tb.blockState);
            currentToOriginal.remove(originalKey);
            LOGGER.info("Block restored at {} in {}", originalPos, originalKey.dim().getValue());
            return true;
        });
    }

    public static class TimedBlock {
        public final BlockState blockState;
        public BlockPos movedTo;
        public int ticksLeft;
        public FallingBlockEntity fallingEntity;

        public TimedBlock(BlockState blockState, int ticksLeft, BlockPos movedTo) {
            this.blockState = blockState;
            this.ticksLeft = ticksLeft;
            this.movedTo = movedTo;
        }
    }
}
