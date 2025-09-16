package com.cobblemon.khataly.modhm.networking.packet;

import com.cobblemon.khataly.modhm.HMMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;


public record FlashMenuS2CPacket(boolean canFlash) implements CustomPayload {
    public static final Id<FlashMenuS2CPacket> ID =
            new Id<>(net.minecraft.util.Identifier.of(HMMod.MOD_ID, "show_flash_menu_s2c"));

    public static final PacketCodec<RegistryByteBuf, FlashMenuS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, FlashMenuS2CPacket::canFlash,
            FlashMenuS2CPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
