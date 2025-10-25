package com.cobblemon.khataly.mapkit.command.custom;

import com.cobblemon.khataly.mapkit.config.GrassZonesConfig;
import com.cobblemon.khataly.mapkit.config.GrassZonesConfig.Zone;
import com.cobblemon.khataly.mapkit.config.GrassZonesConfig.SpawnEntry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.command.CommandSource;
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

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static net.minecraft.server.command.CommandManager.literal;

public class GrassZoneCommands {

    public static void register(CommandDispatcher<ServerCommandSource> d) {

        // Suggest only for OP commands (clients without permission won't see these anyway)
        SuggestionProvider<ServerCommandSource> zoneNameSuggest = (ctx, builder) -> {
            List<String> names = GrassZonesConfig.getAll()
                    .stream()
                    .map(Zone::name)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            CommandSource.suggestMatching(names, builder);
            return builder.buildFuture();
        };

        d.register(
                literal("grasszone")
                        // --- PUBLIC (everyone) ---
                        .then(literal("checklist") // no .requires => perm level 0
                                .executes(ctx -> {
                                    var src = ctx.getSource();
                                    ServerPlayerEntity p = src.getPlayer();
                                    if (p == null) return 0;

                                    Zone z = findZoneUnderPlayer(p);
                                    if (z == null) {
                                        src.sendFeedback(() -> Text.literal("§7No grass zone here."), false);
                                        return 1;
                                    }

                                    src.sendFeedback(() -> Text.literal("§6— Zone checklist —"), false);
                                    src.sendFeedback(() -> Text.literal(" §7Name:§f " + z.name()), false);
                                    src.sendFeedback(() -> Text.literal(" §7ID:§f " + z.id()), false);
                                    src.sendFeedback(() -> Text.literal(" §7Y range:§f " + z.minY() + " .. " + z.maxY()), false);

                                    var spawns = z.spawns();
                                    if (spawns == null || spawns.isEmpty()) {
                                        src.sendFeedback(() -> Text.literal(" §7Spawns:§f (empty)"), false);
                                        return 1;
                                    }

                                    src.sendFeedback(() -> Text.literal(" §7Species and time of day:"), false);
                                    for (SpawnEntry s : spawns) {
                                        String speciesName = stripNamespace(s.species);
                                        String when = switch (s.time) {
                                            case DAY -> "§eDAY";
                                            case NIGHT -> "§9NIGHT";
                                            default -> "§aBOTH";
                                        };
                                        String aspect = (s.aspect != null && !s.aspect.isBlank()) ? (" §7(" + s.aspect + ")") : "";
                                        src.sendFeedback(() -> Text.literal("  • §a" + speciesName + aspect + " §7→ " + when + "§r"), false);
                                    }
                                    return 1;
                                })
                        )

                        // --- OP ONLY (perm level >= 2) ---
                        .then(literal("list").requires(src -> src.hasPermissionLevel(2))
                                .executes(ctx -> {
                                    var src = ctx.getSource();
                                    int count = 0;
                                    for (var z : GrassZonesConfig.getAll()) {
                                        count++;
                                        src.sendFeedback(() -> Text.literal("§6— Zone §e" + z.name() + "§6 —"), false);
                                        src.sendFeedback(() -> Text.literal(" §7ID:§f " + z.id()), false);
                                        src.sendFeedback(() -> Text.literal(" §7Dimension:§f " + z.worldKey().getValue()
                                                + " §7Y:§f " + z.minY() + " .. " + z.maxY()), false);
                                        src.sendFeedback(() -> Text.literal(" §7Area:§f [" + z.minX() + ", " + z.minZ() + "] → [" + z.maxX() + ", " + z.maxZ() + "]"), false);

                                        int shown = 0;
                                        if (z.spawns().isEmpty()) {
                                            src.sendFeedback(() -> Text.literal(" §7Spawns:§f (empty)"), false);
                                        } else {
                                            src.sendFeedback(() -> Text.literal(" §7Spawns (first entries):§f"), false);
                                            for (var s : z.spawns()) {
                                                String speciesName = stripNamespace(s.species);
                                                src.sendFeedback(() -> Text.literal(
                                                        " • §a" + speciesName + "§7 lvl§f " + s.minLevel + "-" + s.maxLevel + " §7w§f " + s.weight
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
                                })
                        )

                        .then(literal("remove").requires(src -> src.hasPermissionLevel(2))
                                .then(CommandManager.argument("name", StringArgumentType.greedyString()).suggests(zoneNameSuggest)
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            String name = StringArgumentType.getString(ctx, "name");
                                            Zone zone = getZoneByName(name);
                                            if (zone == null) {
                                                src.sendFeedback(() -> Text.literal("§cZone not found: §f" + name), false);
                                                return 1;
                                            }

                                            MinecraftServer server = ctx.getSource().getServer();
                                            World world = server.getWorld(zone.worldKey());
                                            if (world == null) {
                                                src.sendFeedback(() -> Text.literal("§cDimension not loaded: " + zone.worldKey().getValue()), false);
                                                return 1;
                                            }

                                            int removed = clearGrassInZone(world, zone);
                                            boolean ok = GrassZonesConfig.removeZone(zone.id());
                                            src.sendFeedback(() -> Text.literal(
                                                    (ok ? "§aZone removed. " : "§cRemoval error. ") + "§7Grass removed: §f" + removed
                                            ), false);
                                            return 1;
                                        })
                                )
                        )

                        .then(literal("removehere").requires(src -> src.hasPermissionLevel(2))
                                .executes(ctx -> {
                                    var src = ctx.getSource();
                                    ServerPlayerEntity p = src.getPlayer();
                                    if (p == null) return 0;

                                    Zone z = findZoneUnderPlayer(p);
                                    if (z == null) {
                                        src.sendFeedback(() -> Text.literal("§7No grass zone here."), false);
                                        return 1;
                                    }

                                    int removed = clearGrassInZone(p.getWorld(), z);
                                    boolean ok = GrassZonesConfig.removeZone(z.id());
                                    src.sendFeedback(() -> Text.literal(
                                            (ok ? "§aZone removed. " : "§cRemoval error. ") + "§7Grass removed: §f" + removed
                                    ), false);
                                    return 1;
                                })
                        )
        );
    }

    // ===== Helpers =====

    private static Zone getZoneByName(String name) {
        if (name == null) return null;
        String needle = name.trim().toLowerCase(Locale.ROOT);
        Zone found = null;
        for (Zone z : GrassZonesConfig.getAll()) {
            if (z.name().equalsIgnoreCase(needle)) {
                return z; // exact match first
            }
            if (found == null && z.name().toLowerCase(Locale.ROOT).startsWith(needle)) {
                found = z; // QoL: prefix match
            }
        }
        return found;
    }

    private static String stripNamespace(String id) {
        if (id == null) return "";
        int i = id.indexOf(':');
        return (i >= 0 && i + 1 < id.length()) ? id.substring(i + 1) : id;
    }

    private static Zone findZoneUnderPlayer(ServerPlayerEntity p) {
        var wk = p.getWorld().getRegistryKey();
        BlockPos bp = p.getBlockPos();
        var zones = GrassZonesConfig.findAt(wk, bp.getX(), bp.getY(), bp.getZ());
        if (zones.isEmpty()) {
            zones = GrassZonesConfig.findAt(wk, bp.getX(), bp.getY() - 1, bp.getZ());
        }
        return zones.isEmpty() ? null : zones.getFirst();
    }

    /** Rimuove short_grass / grass e tall_grass su TUTTO il volume verticale della zona. */
    private static int clearGrassInZone(World world, GrassZonesConfig.Zone zone) {
        int removed = 0;
        Block shortGrass = resolveShortGrass();

        for (int y = zone.minY(); y <= zone.maxY(); y++) {
            for (int x = zone.minX(); x <= zone.maxX(); x++) {
                for (int zed = zone.minZ(); zed <= zone.maxZ(); zed++) {
                    BlockPos pos = new BlockPos(x, y, zed);
                    BlockState st = world.getBlockState(pos);

                    // Short grass (o legacy grass)
                    if (shortGrass != null && st.isOf(shortGrass)) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                        removed++;
                        continue;
                    }

                    // Tall grass: gestisce upper/lower e rimuove entrambe le metà
                    if (st.isOf(Blocks.TALL_GRASS)) {
                        var half = st.get(Properties.DOUBLE_BLOCK_HALF);
                        if (half == DoubleBlockHalf.LOWER) {
                            BlockPos up = pos.up();
                            if (world.getBlockState(up).isOf(Blocks.TALL_GRASS)) {
                                world.setBlockState(up, Blocks.AIR.getDefaultState(), 3);
                            }
                            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                        } else {
                            BlockPos down = pos.down();
                            if (world.getBlockState(down).isOf(Blocks.TALL_GRASS)) {
                                world.setBlockState(down, Blocks.AIR.getDefaultState(), 3);
                            }
                            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                        }
                        removed++;
                        continue;
                    }

                    // Caso edge: tall_grass sotto (rimozione a coppia)
                    BlockState below = world.getBlockState(pos.down());
                    if (below.isOf(Blocks.TALL_GRASS)) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                        world.setBlockState(pos.down(), Blocks.AIR.getDefaultState(), 3);
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    private static Block resolveShortGrass() {
        Optional<Block> a = Registries.BLOCK.getOrEmpty(Identifier.of("minecraft", "short_grass"));
        if (a.isPresent()) return a.get();
        Optional<Block> b = Registries.BLOCK.getOrEmpty(Identifier.of("minecraft", "grass"));
        return b.orElse(Blocks.AIR);
    }
}
