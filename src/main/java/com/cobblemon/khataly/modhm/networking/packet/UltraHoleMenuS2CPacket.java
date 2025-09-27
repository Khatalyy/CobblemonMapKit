package com.cobblemon.khataly.modhm.networking.packet;

import com.cobblemon.khataly.modhm.HMMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record UltraHoleMenuS2CPacket(
        UUID pokemonId,
        boolean canUltraHole
) implements CustomPayload {

    public static final Id<UltraHoleMenuS2CPacket> ID =
            new Id<>(Identifier.of(HMMod.MOD_ID, "show_ultrahole_menu_s2c"));

    // Codec manuale per UUID (stessa logica di UltraHoleMenuS2CPacket)
    private static final PacketCodec<RegistryByteBuf, UUID> UUID_CODEC = new PacketCodec<>() {
        @Override
        public UUID decode(RegistryByteBuf buf) {
            long most = buf.readLong();
            long least = buf.readLong();
            return new UUID(most, least);
        }

        @Override
        public void encode(RegistryByteBuf buf, UUID uuid) {
            buf.writeLong(uuid.getMostSignificantBits());
            buf.writeLong(uuid.getLeastSignificantBits());
        }
    };

    public static final PacketCodec<RegistryByteBuf, UltraHoleMenuS2CPacket> CODEC = PacketCodec.tuple(
            UUID_CODEC, UltraHoleMenuS2CPacket::pokemonId,
            PacketCodecs.BOOL, UltraHoleMenuS2CPacket::canUltraHole,
            UltraHoleMenuS2CPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Factory per creare il pacchetto lato server */
    public static UltraHoleMenuS2CPacket fromServerData(UUID pokemonId, boolean canUltraHole) {
        return new UltraHoleMenuS2CPacket(pokemonId, canUltraHole);
    }
}
