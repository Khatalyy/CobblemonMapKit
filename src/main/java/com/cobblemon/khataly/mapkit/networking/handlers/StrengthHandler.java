package com.cobblemon.khataly.mapkit.networking.handlers;

import com.cobblemon.khataly.mapkit.config.HMConfig;
import com.cobblemon.khataly.mapkit.networking.manager.RestoreManager;
import com.cobblemon.khataly.mapkit.networking.packet.strength.StrengthPacketC2S;
import com.cobblemon.khataly.mapkit.networking.util.NetUtil;
import com.cobblemon.khataly.mapkit.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class StrengthHandler {
    private StrengthHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(StrengthPacketC2S.ID, (payload, ctx) -> {
            ServerPlayerEntity p = ctx.player();
            ctx.server().execute(() -> {
                ServerWorld w = (ServerWorld) p.getWorld();
                BlockPos clickedPos = payload.pos();

                if (!NetUtil.requireMove(p, "strength", "❌ No Pokémon in your party knows Strength!")) return;
                if (!NetUtil.requireItem(p, HMConfig.STRENGTH.item, HMConfig.STRENGTH.message)) return;

                // Risolvi l'originale tenendo conto degli alias (nella stessa dimensione)
                BlockPos originalPos = RestoreManager.get().resolveOriginal(w, clickedPos);
                RestoreManager.TimedBlock tb = RestoreManager.get().getTimed(w, originalPos);
                BlockPos currentPos = (tb != null && tb.movedTo != null) ? tb.movedTo : originalPos;

                BlockState state = w.getBlockState(currentPos);
                if (state.isAir()) {
                    NetUtil.msg(p, "⚠️ There's nothing to move here!");
                    return;
                }

                BlockPos targetPos = currentPos.offset(p.getHorizontalFacing());
                if (!w.getBlockState(targetPos).isAir()) {
                    NetUtil.msg(p, "⛔ Cannot push the block there!");
                    NetUtil.playPlayerSound(p, ModSounds.WALL_BUMP);
                    return;
                }

                NetUtil.sendAnimation(p, "strength");
                NetUtil.msg(p, "💥 you used Strength!");
                NetUtil.playPlayerSound(p, ModSounds.MOVABLE_ROCK);

                // Sposta il blocco
                w.setBlockState(currentPos, Blocks.AIR.getDefaultState());
                RestoreManager.get().forgetAlias(w, currentPos);

                w.setBlockState(targetPos, state);
                RestoreManager.get().registerMove(w, originalPos, targetPos, state, HMConfig.STRENGTH_RESPAWN);
            });
        });
    }
}
