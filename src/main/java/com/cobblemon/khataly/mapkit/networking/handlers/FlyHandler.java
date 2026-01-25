package com.cobblemon.khataly.mapkit.networking.handlers;

import com.cobblemon.khataly.mapkit.config.HMConfig;
import com.cobblemon.khataly.mapkit.networking.packet.fly.FlyPacketC2S;
import com.cobblemon.khataly.mapkit.networking.util.NetUtil;
import com.cobblemon.khataly.mapkit.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class FlyHandler {
    private FlyHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(FlyPacketC2S.ID, (payload, ctx) -> {
            ServerPlayerEntity p = ctx.player();

            ctx.server().execute(() -> {
                if (!NetUtil.requireMove(p, "fly", "❌ No Pokémon in your party knows Fly!")) return;
                if (!NetUtil.requireItem(p, HMConfig.FLY.item, HMConfig.FLY.message)) return;

                String worldKeyStr = payload.worldKey();
                BlockPos pos = payload.pos();

                // Se non è permesso cross-dimension: blocca se il target non è nella stessa dimensione
                if (!HMConfig.FLY_ACROSS_DIM) {
                    RegistryKey<World> playerKey = p.getWorld().getRegistryKey();
                    RegistryKey<World> reqKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(worldKeyStr));

                    if (!playerKey.equals(reqKey)) {
                        NetUtil.msg(p, "❌ You can only Fly inside the same dimension.");
                        return;
                    }

                    NetUtil.sendAnimation(p, "fly");
                    NetUtil.teleportTo(p, (ServerWorld) p.getWorld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    NetUtil.playPlayerSound(p, ModSounds.FLY);
                    NetUtil.msg(p, "🛫 Teleported to " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                    return;
                }

                // Cross-dimension abilitato: teleporta nel world richiesto
                RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(worldKeyStr));
                ServerWorld targetWorld = ctx.server().getWorld(worldKey);

                if (targetWorld == null) {
                    NetUtil.msg(p, "❌ That dimension is not available on this server: " + worldKeyStr);
                    return;
                }

                NetUtil.sendAnimation(p, "fly");
                NetUtil.teleportTo(p, targetWorld, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                NetUtil.playPlayerSound(p, ModSounds.FLY);
                NetUtil.msg(p, "🛫 Teleported to " + worldKeyStr + " @ " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            });
        });
    }
}
