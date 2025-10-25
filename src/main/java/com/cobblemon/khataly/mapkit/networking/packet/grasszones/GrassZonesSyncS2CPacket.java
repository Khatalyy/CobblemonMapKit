package com.cobblemon.khataly.mapkit.networking.packet.grasszones;

import com.cobblemon.khataly.mapkit.CobblemonMapKitMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record GrassZonesSyncS2CPacket(List<GrassZonesSyncS2CPacket.ZoneDto> zones)
        implements CustomPayload {

    public static final CustomPayload.Id<GrassZonesSyncS2CPacket> ID =
            new CustomPayload.Id<>(Identifier.of(CobblemonMapKitMod.MOD_ID, "zones_sync_s2c"));

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    private static <T> PacketCodec<RegistryByteBuf, T> lift(PacketCodec<ByteBuf, T> base) {
        return new PacketCodec<>() {
            @Override public T decode(RegistryByteBuf buf) { return base.decode(buf); }
            @Override public void encode(RegistryByteBuf buf, T value) { base.encode(buf, value); }
        };
    }

    private static final PacketCodec<RegistryByteBuf, UUID>    UUID_CODEC   = lift(Uuids.PACKET_CODEC);
    private static final PacketCodec<RegistryByteBuf, String>  STRING_CODEC = lift(PacketCodecs.STRING);
    private static final PacketCodec<RegistryByteBuf, Integer> VARINT_CODEC = lift(PacketCodecs.VAR_INT);

    public static final PacketCodec<RegistryByteBuf, GrassZonesSyncS2CPacket> CODEC =
            PacketCodec.tuple(ZoneDto.LIST_CODEC, GrassZonesSyncS2CPacket::zones, GrassZonesSyncS2CPacket::new);

    /** NUOVO: include minY e maxY (range verticale) */
    public static record ZoneDto(UUID id, String worldKey,
                                 int minX, int minZ, int maxX, int maxZ,
                                 int minY, int maxY) {

        public static final PacketCodec<RegistryByteBuf, ZoneDto> CODEC = new PacketCodec<>() {
            @Override public ZoneDto decode(RegistryByteBuf buf) {
                UUID id       = UUID_CODEC.decode(buf);
                String worldKey = STRING_CODEC.decode(buf);
                int minX      = VARINT_CODEC.decode(buf);
                int minZ      = VARINT_CODEC.decode(buf);
                int maxX      = VARINT_CODEC.decode(buf);
                int maxZ      = VARINT_CODEC.decode(buf);
                int minY      = VARINT_CODEC.decode(buf);
                int maxY      = VARINT_CODEC.decode(buf);
                return new ZoneDto(id, worldKey, minX, minZ, maxX, maxZ, minY, maxY);
            }
            @Override public void encode(RegistryByteBuf buf, ZoneDto z) {
                UUID_CODEC.encode(buf, z.id());
                STRING_CODEC.encode(buf, z.worldKey());
                VARINT_CODEC.encode(buf, z.minX());
                VARINT_CODEC.encode(buf, z.minZ());
                VARINT_CODEC.encode(buf, z.maxX());
                VARINT_CODEC.encode(buf, z.maxZ());
                VARINT_CODEC.encode(buf, z.minY());
                VARINT_CODEC.encode(buf, z.maxY());
            }
        };

        public static final PacketCodec<RegistryByteBuf, List<ZoneDto>> LIST_CODEC =
                CODEC.collect(PacketCodecs.toList());
    }

    /** Costruisce i DTO leggendo dal config aggiornato (minY/maxY). */
    public static List<ZoneDto> buildDtos() {
        List<ZoneDto> out = new ArrayList<>();
        for (var z : com.cobblemon.khataly.mapkit.config.GrassZonesConfig.getAll()) {
            out.add(new ZoneDto(
                    z.id(),
                    z.worldKey().getValue().toString(),
                    z.minX(), z.minZ(), z.maxX(), z.maxZ(),
                    z.minY(), z.maxY()
            ));
        }
        return out;
    }
}
