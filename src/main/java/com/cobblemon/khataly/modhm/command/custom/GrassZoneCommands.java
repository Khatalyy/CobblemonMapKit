package com.cobblemon.khataly.modhm.command.custom;

import com.cobblemon.khataly.modhm.config.GrassZonesConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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

                // Aliases: delete / deletehere → forward using executeWithPrefix(...)
                .then(literal("delete")
                        .then(CommandManager.argument("id", StringArgumentType.string())
                                .executes(ctx -> {
                                    var src = ctx.getSource();
                                    String sid = StringArgumentType.getString(ctx, "id");
                                    src.getServer().getCommandManager()
                                            .executeWithPrefix(src, "grasszone remove " + sid);
                                    return 1;
                                })
                        )
                )
                .then(literal("deletehere").executes(ctx -> {
                    var src = ctx.getSource();
                    src.getServer().getCommandManager()
                            .executeWithPrefix(src, "grasszone removehere");
                    return 1;
                }))
        );
    }

    /**
     * Removes only decorative grass (short/tall) in the zone rectangle at the zone Y level.
     */
    private static int clearGrassInZone(World world, GrassZonesConfig.Zone z) {
        int y = z.y();
        int removed = 0;
        Block shortGrass = tryShortGrass();

        for (int x = z.minX(); x <= z.maxX(); x++) {
            for (int zed = z.minZ(); zed <= z.maxZ(); zed++) {
                BlockPos pos = new BlockPos(x, y, zed);
                BlockState st = world.getBlockState(pos);

                boolean isShort = (shortGrass != null && st.isOf(shortGrass));
                boolean isTall = st.isOf(Blocks.TALL_GRASS);
                boolean isLegacy = false;

                try {
                    // some older mappings use Blocks.GRASS for short tuft
                    isLegacy = st.isOf((Block) Blocks.class.getField("GRASS").get(null));
                } catch (Exception ignored) {}

                if (isShort || isTall || isLegacy) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    removed++;
                }
            }
        }
        return removed;
    }

    private static Block tryShortGrass() {
        try {
            return (Block) Blocks.class.getField("SHORT_GRASS").get(null);
        } catch (Exception e) {
            try {
                return (Block) Blocks.class.getField("GRASS").get(null);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
