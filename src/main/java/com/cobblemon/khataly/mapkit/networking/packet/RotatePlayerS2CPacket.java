package com.cobblemon.khataly.mapkit.networking.packet;

import com.cobblemon.khataly.mapkit.CobblemonMapKitMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Ordina al client una rotazione fluida della camera:
 * totalRotation (gradi, può essere anche negativo) in durationTicks tick.
 */
public record RotatePlayerS2CPacket(float totalRotation, int durationTicks) implements CustomPayload {

    // Sostituisci Mapkit.MOD_ID con il tuo MOD_ID se differente
    public static final Identifier ID_RAW = Identifier.of(CobblemonMapKitMod.MOD_ID, "rotate_player");
    public static final CustomPayload.Id<RotatePlayerS2CPacket> ID = new CustomPayload.Id<>(ID_RAW);

    public static final PacketCodec<RegistryByteBuf, RotatePlayerS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, RotatePlayerS2CPacket::totalRotation,
            PacketCodecs.VAR_INT, RotatePlayerS2CPacket::durationTicks,
            RotatePlayerS2CPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
