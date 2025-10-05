package com.cobblemon.khataly.modhm.command.custom;

import com.cobblemon.khataly.modhm.config.LevelCapConfig;
import com.cobblemon.khataly.modhm.config.PlayerLevelCapProgress;
import com.cobblemon.khataly.modhm.util.LevelCapService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class LevelCapCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        var root = CommandManager.literal("levelcap");

        // ===== Public: /levelcap check =====
        root.then(CommandManager.literal("check")
                .executes(ctx -> {
                    ServerPlayerEntity self = ctx.getSource().getPlayer();
                    if (self == null) {
                        ctx.getSource().sendMessage(Text.literal("§cThis command must be executed by a player."));
                        return 0;
                    }
                    sendPlayerCapInfo(ctx.getSource(), self, false);
                    return 1;
                })
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
                            sendPlayerCapInfo(ctx.getSource(), p, true);
                            return 1;
                        })
                )
        );

        // ===== Admin: global enable/disable =====
        root.then(CommandManager.literal("enabled")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean v = BoolArgumentType.getBool(ctx, "value");
                            LevelCapConfig.setEnabled(v);
                            ctx.getSource().sendMessage(Text.literal("§aLevel-cap system enabled = §e" + v + "§a."));
                            return 1;
                        })
                )
        );

        // ===== Public: /levelcap info =====
        root.then(CommandManager.literal("info")
                .executes(ctx -> {
                    Map<String, Integer> map = LevelCapConfig.getAllLabelsWithCaps();
                    ctx.getSource().sendMessage(Text.literal("§6-- Level Cap Labels --"));
                    if (map.isEmpty()) {
                        ctx.getSource().sendMessage(Text.literal("§cNo labels configured."));
                    } else {
                        for (var e : map.entrySet()) {
                            String key = e.getKey();
                            int cap = e.getValue();
                            String nice = LevelCapConfig.displayLabel(key);
                            int items = LevelCapConfig.getItemIdsForLabel(key).size();
                            ctx.getSource().sendMessage(Text.literal("§7" + nice + " §f→ §b" + cap + (items > 0 ? " §8(" + items + " item link)" : "")));
                        }
                    }
                    ctx.getSource().sendMessage(Text.literal(
                            "§7Base: §f" + LevelCapConfig.getBaseCap()
                                    + " §7| Bypass shiny: §f" + LevelCapConfig.isBypassIfShiny()
                                    + " §7| MasterBall bypass: §f" + LevelCapConfig.isBypassOnMasterBall()
                    ));
                    return 1;
                })
        );

        // ===== Admin flags =====
        root.then(CommandManager.literal("bypass-shiny")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean v = BoolArgumentType.getBool(ctx, "value");
                            LevelCapConfig.setBypassIfShiny(v);
                            ctx.getSource().sendMessage(Text.literal("§aShiny capture bypass set to §e" + v + "§a."));
                            return 1;
                        })
                )
        );

        root.then(CommandManager.literal("bypass-masterball")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean v = BoolArgumentType.getBool(ctx, "value");
                            LevelCapConfig.setBypassOnMasterBall(v);
                            ctx.getSource().sendMessage(Text.literal("§aMaster Ball capture bypass set to §e" + v + "§a."));
                            return 1;
                        })
                )
        );

        root.then(CommandManager.literal("base")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.argument("value", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int v = IntegerArgumentType.getInteger(ctx, "value");
                            LevelCapConfig.setBaseCap(v);
                            ctx.getSource().sendMessage(Text.literal("§aBase level cap set to §e" + v + "§a."));
                            return 1;
                        })
                )
        );

        // /levelcap list
        root.then(CommandManager.literal("list")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    Map<String,Integer> map = LevelCapConfig.getAllLabelsWithCaps();
                    ctx.getSource().sendMessage(Text.literal("§6-- LevelCaps List --"));
                    if (map.isEmpty()) {
                        ctx.getSource().sendMessage(Text.literal("§c(none)"));
                    } else {
                        for (var e : map.entrySet()) {
                            String key = e.getKey();
                            int cap = e.getValue();
                            ctx.getSource().sendMessage(Text.literal("§7" + pretty(key) + " §f→ §b" + cap));
                        }
                    }
                    return 1;
                })
        );

        // ===== Admin: PLAYER tools (input = label, internamente usa key) =====
        var playerRoot = CommandManager.literal("player").requires(src -> src.hasPermissionLevel(2));

        // /levelcap player show <player>
        playerRoot.then(CommandManager.literal("show")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
                            Set<String> appliedKeys = PlayerLevelCapProgress.getApplied(p.getUuid());
                            ctx.getSource().sendMessage(Text.literal("§6-- Applied labels for §b" + p.getName().getString() + "§6 --"));
                            if (appliedKeys.isEmpty()) {
                                ctx.getSource().sendMessage(Text.literal("§c(none)"));
                            } else {
                                Map<String,Integer> caps = LevelCapConfig.getAllLabelsWithCaps();
                                for (String key : appliedKeys) {
                                    Integer cap = caps.get(key);
                                    ctx.getSource().sendMessage(Text.literal("§7" + pretty(key) + " §f→ §b" + (cap==null?"?":cap)));
                                }
                            }
                            return 1;
                        })
                )
        );

        // /levelcap player reset-key <player> <label with spaces>
        playerRoot.then(CommandManager.literal("reset-key")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("label", StringArgumentType.greedyString()) // <-- CONSENTE SPAZI
                                .suggests(LevelCapCommands::suggestAppliedLabelsForPlayer)
                                .executes(ctx -> {
                                    ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
                                    String key = keyOf(StringArgumentType.getString(ctx, "label")); // normalizza la label in key

                                    boolean ok = PlayerLevelCapProgress.remove(p.getUuid(), key);
                                    if (ok) {
                                        ctx.getSource().sendMessage(Text.literal("§aRemoved §e" + pretty(key)
                                                + " §afrom §b" + p.getName().getString() + "§a."));
                                        p.sendMessage(Text.literal("§a[Admin] Progress label §e" + pretty(key) + " §ahas been reset."), false);
                                        return 1;
                                    } else {
                                        ctx.getSource().sendMessage(Text.literal("§cPlayer §b" + p.getName().getString()
                                                + " §cdoes not have §e" + pretty(key) + "§c applied."));
                                        return 0;
                                    }
                                })
                        )
                )
        );

        // /levelcap player reset-all <player>
        playerRoot.then(CommandManager.literal("reset-all")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
                            PlayerLevelCapProgress.clearAll(p.getUuid());
                            ctx.getSource().sendMessage(Text.literal("§aCleared all progress for §b" + p.getName().getString() + "§a."));
                            p.sendMessage(Text.literal("§a[Admin] Level-cap progress has been reset."), false);
                            return 1;
                        })
                )
        );

        root.then(playerRoot);

        dispatcher.register(root);
    }

    // ======== suggestions/helpers ========

    private static String keyOf(String label) {
        return LevelCapConfig.normalizeLabel(label);
    }
    private static String pretty(String key) {
        return LevelCapConfig.displayLabel(key);
    }

    private static CompletableFuture<Suggestions> suggestAppliedLabelsForPlayer(
            CommandContext<ServerCommandSource> ctx, SuggestionsBuilder b
    ) {
        try {
            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
            Set<String> keys = PlayerLevelCapProgress.getApplied(p.getUuid());
            List<String> pretty = new ArrayList<>();
            for (String k : keys) {
                pretty.add(LevelCapConfig.displayLabel(k));
            }
            // niente keys.addAll(keys) → niente duplicati/lowercase
            return CommandSource.suggestMatching(pretty, b);
        } catch (Exception e) {
            return b.buildFuture();
        }
    }

    // ======== UI ========
    private static void sendPlayerCapInfo(ServerCommandSource src, ServerPlayerEntity p, boolean includeName) {
        int effective = LevelCapService.getEffectiveCap(p);

        var applied = PlayerLevelCapProgress.getApplied(p.getUuid());
        int bestProgressionCap = LevelCapConfig.getBaseCap();
        Map<String, Integer> labelsCaps = LevelCapConfig.getAllLabelsWithCaps();
        for (String key : applied) {
            Integer cap = labelsCaps.get(key);
            if (cap != null && cap > bestProgressionCap) bestProgressionCap = cap;
        }

        String header = includeName
                ? "§6-- Level Cap for §b" + p.getName().getString() + "§6 --"
                : "§6-- Your Level Cap --";
        src.sendMessage(Text.literal(header));
        src.sendMessage(Text.literal("§7Base Cap: §f" + LevelCapConfig.getBaseCap()));
        src.sendMessage(Text.literal("§7Best Progression Cap: §f" + bestProgressionCap));
        src.sendMessage(Text.literal("§7Effective Cap: §f" + effective));

        if (!applied.isEmpty()) {
            src.sendMessage(Text.literal("§7Progress:"));
            for (String key : applied) {
                Integer cap = labelsCaps.get(key);
                src.sendMessage(Text.literal("  §f- " + pretty(key) + " §7→ §b" + (cap == null ? "?" : cap)));
            }
        }
    }
}
