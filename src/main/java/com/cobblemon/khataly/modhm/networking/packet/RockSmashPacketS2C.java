package com.cobblemon.khataly.modhm.networking.packet;

import com.cobblemon.khataly.modhm.HMMod;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.apache.commons.codec.DecoderException;

import java.util.HashSet;
import java.util.Set;

public record RockSmashPacketS2C(RenderablePokemon pokemon) implements CustomPayload {

    public static final Id<RockSmashPacketS2C> ID =
            new Id<>(Identifier.of(HMMod.MOD_ID, "rocksmash_responce"));

    public static final PacketCodec<RegistryByteBuf, RockSmashPacketS2C> CODEC =
            new PacketCodec<>() {
                private final PacketCodec<ByteBuf, Identifier> speciesCodec =
                        PacketCodecs.string(32767).xmap(Identifier::tryParse, Identifier::toString);
                private final PacketCodec<ByteBuf, Integer> sizeCodec = PacketCodecs.VAR_INT;
                private final PacketCodec<ByteBuf, String> aspectCodec = PacketCodecs.STRING;

                @Override
                public RockSmashPacketS2C decode(RegistryByteBuf buf) {
                    // Leggi l'identifier come stringa
                    String idString = PacketCodecs.STRING.decode(buf);
                    Identifier speciesId = Identifier.tryParse(idString);

                    // Convertilo in NbtString per il codec
                    assert speciesId != null;
                    NbtElement nbt = NbtString.of(speciesId.toString());

                    // Usa il codec per ottenere il Species
                    Species species = null;
                    try {
                        species = Species.getBY_IDENTIFIER_CODEC().parse(NbtOps.INSTANCE, nbt)
                                .getOrThrow(error -> new DecoderException("Failed to decode Species: " + error));
                    } catch (DecoderException e) {
                        throw new RuntimeException(e);
                    }

                    // Leggi gli aspects
                    int size = PacketCodecs.VAR_INT.decode(buf);
                    Set<String> aspects = new HashSet<>();
                    for (int i = 0; i < size; i++) {
                        aspects.add(PacketCodecs.STRING.decode(buf));
                    }

                    return new RockSmashPacketS2C(new RenderablePokemon(species, aspects));
                }


                @Override
                public void encode(RegistryByteBuf buf, RockSmashPacketS2C packet) {
                    speciesCodec.encode(buf, packet.pokemon().getSpecies().getResourceIdentifier());
                    Set<String> aspects = packet.pokemon().getAspects();
                    sizeCodec.encode(buf, aspects.size());
                    for (String aspect : aspects) {
                        aspectCodec.encode(buf, aspect);
                    }
                }
            };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
