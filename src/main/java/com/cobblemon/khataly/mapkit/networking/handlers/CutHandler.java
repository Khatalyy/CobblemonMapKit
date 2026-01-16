package com.cobblemon.khataly.mapkit.networking.handlers;

import com.cobblemon.khataly.mapkit.config.HMConfig;
import com.cobblemon.khataly.mapkit.networking.manager.RestoreManager;
import com.cobblemon.khataly.mapkit.networking.packet.cut.CutPacketC2S;
import com.cobblemon.khataly.mapkit.networking.util.NetUtil;
import com.cobblemon.khataly.mapkit.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CutHandler {
    private CutHandler() {}
    private static final Logger LOGGER = LoggerFactory.getLogger("CutHandler");

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CutPacketC2S.ID, (payload, ctx) -> {
            ServerPlayerEntity p = ctx.player();
            ctx.server().execute(() -> {
                ServerWorld w = (ServerWorld) p.getWorld();
                BlockPos pos = payload.pos();

                if (!NetUtil.requireMove(p, "cut", "❌ No Pokémon in your party knows Cut!")) return;
                if (!NetUtil.requireItem(p, HMConfig.CUT.item, HMConfig.CUT.message)) return;

                BlockState original = w.getBlockState(pos);
                if (original.isAir()) {
                    NetUtil.msg(p, "⚠️ There's nothing to cut here!");
                    return;
                }

                if (RestoreManager.get().isBusy(w, pos)) {
                    NetUtil.msg(p, "⏳ The block has already been cut, wait for it to return!");
                    return;
                }

                NetUtil.sendAnimation(p, "cut");
                NetUtil.msg(p, "💥 you used Cut!");
                NetUtil.playPlayerSound(p, ModSounds.CUTTABLE_TREE);

                w.setBlockState(pos, Blocks.AIR.getDefaultState());
                RestoreManager.get().addTimed(w, pos, original, HMConfig.CUT_RESPAWN);

                NetUtil.sendParticles(p, ParticleTypes.CHERRY_LEAVES, pos, 0.3f, 0.3f, 0.3f, 0.1f, 20);
                LOGGER.info("Block removed at {}, restore timer started", pos);
            });
        });
    }
}
