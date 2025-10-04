package com.cobblemon.khataly.modhm.networking.packet;

import com.cobblemon.khataly.modhm.HMMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record PlaceGrassC2SPacket(BlockPos a, BlockPos b) implements CustomPayload {
    public static final CustomPayload.Id<PlaceGrassC2SPacket> ID =
            new CustomPayload.Id<>(Identifier.of(HMMod.MOD_ID, "place_grass"));

    public static final PacketCodec<RegistryByteBuf, PlaceGrassC2SPacket> CODEC =
            PacketCodec.tuple(
                    BlockPos.PACKET_CODEC, PlaceGrassC2SPacket::a,
                    BlockPos.PACKET_CODEC, PlaceGrassC2SPacket::b,
                    PlaceGrassC2SPacket::new
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
