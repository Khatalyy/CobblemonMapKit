package com.cobblemon.khataly.modhm.networking.packet;

import com.cobblemon.khataly.modhm.HMMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;


public record UltraHolePacketC2S(BlockPos pos) implements CustomPayload {
    public static final Identifier ID_RAW = Identifier.of(HMMod.MOD_ID, "ultrahole_request");
    public static final Id<UltraHolePacketC2S> ID = new Id<>(ID_RAW);

    public static final PacketCodec<RegistryByteBuf, UltraHolePacketC2S> CODEC =
            PacketCodec.tuple(BlockPos.PACKET_CODEC, UltraHolePacketC2S::pos, UltraHolePacketC2S::new);


    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

