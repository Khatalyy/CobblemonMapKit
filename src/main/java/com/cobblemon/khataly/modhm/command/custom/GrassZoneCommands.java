package com.cobblemon.khataly.modhm.command.custom;

import com.cobblemon.khataly.modhm.config.GrassZonesConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;

public class GrassZoneCommands {

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("grasszone")
                .requires(src -> src.hasPermissionLevel(2))

                // LIST — multi-line, readable
                .then(literal("list").executes(ctx -> {
                    var src = ctx.getSource();
                    int count = 0;
                    for (var z : GrassZonesConfig.getAll()) {
                        count++;
                        src.sendFeedback(() -> Text.literal("§6— Zone §e" + z.id() + "§6 —"), false);
                        src.sendFeedback(() -> Text.literal(" §7Dim:§f " + z.worldKey().getValue() + " §7Y:§f " + z.y()), false);
                        src.sendFeedback(() -> Text.literal(" §7Area:§f [" + z.minX() + ", " + z.minZ() + "] → [" + z.maxX() + ", " + z.maxZ() + "]"), false);

                        int shown = 0;
                        if (z.spawns().isEmpty()) {
                            src.sendFeedback(() -> Text.literal(" §7Spawns:§f (empty)"), false);
                        } else {
                            src.sendFeedback(() -> Text.literal(" §7Spawns:§f"), false);
                            for (var s : z.spawns()) {
                                src.sendFeedback(() -> Text.literal(
                                        " • §a" + s.species + "§7 lvl§f " + s.minLevel + "-" + s.maxLevel + " §7w§f " + s.weight
                                ), false);
                                if (++shown >= 6) {
                                    src.sendFeedback(() -> Text.literal(" …"), false);
                                    break;
                                }
                            }
                        }
                    }
                    int finalCount = count;
                    src.sendFeedback(() -> Text.literal("§7Total zones: §f" + finalCount), false);
                    return 1;
                }))

                // REMOVE — removes zone and clears grass
                .then(literal("remove")
                        .then(CommandManager.argument("id", StringArgumentType.string())
                                .executes(ctx -> {
                                    var src = ctx.getSource();
                                    String sid = StringArgumentType.getString(ctx, "id");

                                    UUID zid;
                                    try {
                                        zid = UUID.fromString(sid);
                                    } catch (IllegalArgumentException ex) {
                                        src.sendFeedback(() -> Text.literal("§cInvalid ID."), false);
                                        return 1;
                                    }

                                    var zone = GrassZonesConfig.get(zid);
                                    if (zone == null) {
                                        src.sendFeedback(() -> Text.literal("§cZone not found."), false);
                                        return 1;
                                    }

                                    MinecraftServer server = src.getServer();
                                    World world = server.getWorld(zone.worldKey());
                                    if (world == null) {
                                        src.sendFeedback(() -> Text.literal("§cDimension not loaded: " + zone.worldKey().getValue()), false);
                                        return 1;
                                    }

                                    int removed = clearGrassInZone(world, zone);
                                    boolean ok = GrassZonesConfig.removeZone(zid);
                                    src.sendFeedback(() -> Text.literal(
                                            (ok ? "§aZone removed. " : "§cRemoval error. ") + "§7Grass removed: §f" + removed
                                    ), false);
                                    return 1;
                                })
                        )
                )

                // REMOVEHERE — removes the zone at player's position and clears grass
                .then(literal("removehere").executes(ctx -> {
                    var src = ctx.getSource();
                    ServerPlayerEntity p = src.getPlayer();
                    if (p == null) return 0;

                    var wk = p.getWorld().getRegistryKey();
                    BlockPos bp = p.getBlockPos();
                    var zones = GrassZonesConfig.findAt(wk, bp.getX(), bp.getY(), bp.getZ());
                    if (zones.isEmpty()) {
                        // prova anche y-1 per tollerare tall_grass (upper/under)
                        zones = GrassZonesConfig.findAt(wk, bp.getX(), bp.getY() - 1, bp.getZ());
                    }

                    if (zones.isEmpty()) {
                        src.sendFeedback(() -> Text.literal("§7No zone here."), false);
                        return 1;
                    }

                    var z = zones.getFirst();
                    int removed = clearGrassInZone(p.getWorld(), z);
                    boolean ok = GrassZonesConfig.removeZone(z.id());
                    src.sendFeedback(() -> Text.literal(
                            (ok ? "§aZone removed. " : "§cRemoval error. ") + "§7Grass removed: §f" + removed
                    ), false);
                    return 1;
                }))
        );
    }

    /**
     * Removes only decorative grass (short/tall) in the zone rectangle at the zone Y level.
     * Gestisce correttamente le piante doppie (TALL_GRASS), cancellando entrambe le metà.
     */
    private static int clearGrassInZone(World world, GrassZonesConfig.Zone z) {
        int y = z.y();
        int removed = 0;
        Block shortGrass = resolveShortGrass();

        for (int x = z.minX(); x <= z.maxX(); x++) {
            for (int zed = z.minZ(); zed <= z.maxZ(); zed++) {
                BlockPos pos = new BlockPos(x, y, zed);
                BlockState st = world.getBlockState(pos);

                // --- SHORT GRASS ---
                if (shortGrass != null && st.isOf(shortGrass)) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    removed++;
                    continue;
                }

                // --- TALL GRASS (double block) ---
                if (st.isOf(Blocks.TALL_GRASS)) {
                    DoubleBlockHalf half = st.get(Properties.DOUBLE_BLOCK_HALF);
                    if (half == DoubleBlockHalf.LOWER) {
                        // base + top
                        BlockPos up = pos.up();
                        if (world.getBlockState(up).isOf(Blocks.TALL_GRASS)) {
                            world.setBlockState(up, Blocks.AIR.getDefaultState(), 3);
                        }
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    } else {
                        // top + base
                        BlockPos down = pos.down();
                        if (world.getBlockState(down).isOf(Blocks.TALL_GRASS)) {
                            world.setBlockState(down, Blocks.AIR.getDefaultState(), 3);
                        }
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    }
                    removed++;
                    continue;
                }

                // --- Safety: se siamo sull'upper a Y, prova a pulire anche la base a Y-1 ---
                BlockState below = world.getBlockState(pos.down());
                if (below.isOf(Blocks.TALL_GRASS)) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    world.setBlockState(pos.down(), Blocks.AIR.getDefaultState(), 3);
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * Risolve il blocco di short grass in modo robusto (senza reflection),
     * con fallback a "minecraft:grass" per mapping più vecchie.
     */
    private static Block resolveShortGrass() {
        Block b = Registries.BLOCK.get(Identifier.of("minecraft", "short_grass"));
        if (b != Blocks.AIR) return b;
        Block legacy = Registries.BLOCK.get(Identifier.of("minecraft", "grass"));
        return legacy != Blocks.AIR ? legacy : null;
    }
}
