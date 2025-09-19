package com.cobblemon.khataly.modhm.command.custom;

import com.cobblemon.khataly.modhm.config.FlyTargetConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

public class FlyTargetCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("flytarget")
                .requires(source -> source.hasPermissionLevel(2))

                // flytarget list
                .then(CommandManager.literal("list")
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            Map<String, FlyTargetConfig.TargetInfo> targets = FlyTargetConfig.getAllTargets();

                            if (targets.isEmpty()) {
                                source.sendMessage(Text.literal("§eNo fly targets registered."));
                                return 1;
                            }

                            source.sendMessage(Text.literal("§6-- Registered Fly Targets --"));
                            for (var entry : targets.entrySet()) {
                                String name = entry.getKey();
                                var pos = entry.getValue().pos;
                                source.sendMessage(Text.literal("§7" + name + " §f@ (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"));
                            }

                            return 1;
                        })
                )

                // flytarget create <name>
                .then(CommandManager.literal("create")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    ServerPlayerEntity player = source.getPlayer();
                                    String name = StringArgumentType.getString(context, "name");
                                    assert player != null;
                                    BlockPos pos = player.getBlockPos();

                                    boolean success = FlyTargetConfig.addTarget(name, player.getServerWorld().getRegistryKey(), pos);

                                    if (success) {
                                        source.sendMessage(Text.literal("§aFly target '" + name + "' created at position " +
                                                pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
                                        return 1;
                                    } else {
                                        source.sendMessage(Text.literal("§cFly target with name '" + name + "' already exists."));
                                        return 0;
                                    }
                                })
                        )
                )

                // flytarget remove <name>
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    String name = StringArgumentType.getString(context, "name");

                                    boolean removed = FlyTargetConfig.removeTarget(name);
                                    if (removed) {
                                        source.sendMessage(Text.literal("§aFly target '" + name + "' removed."));
                                        return 1;
                                    } else {
                                        source.sendMessage(Text.literal("§cFly target '" + name + "' not found."));
                                        return 0;
                                    }
                                })
                        )
                )

                // flytarget reload
                .then(CommandManager.literal("reload")
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            FlyTargetConfig.reload();
                            source.sendMessage(Text.literal("§aFly targets reloaded from file."));
                            return 1;
                        })
                )
        );
    }
}
