package com.cobblemon.khataly.mapkit.networking.packet;

import com.cobblemon.khataly.mapkit.CobblemonMapKitMod;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Server → Client packet carrying a RenderablePokemon for animation purposes.
 */
public record AnimationHMPacketS2C(RenderablePokemon pokemon) implements CustomPayload {

    // ATTENZIONE: lasciato invariato per compatibilità col protocollo esistente
    public static final Id<AnimationHMPacketS2C> ID =
            new Id<>(Identifier.of(CobblemonMapKitMod.MOD_ID, "animation_responce"));

    public static final PacketCodec<RegistryByteBuf, AnimationHMPacketS2C> CODEC =
            new PacketCodec<>() {

                // Coerente tra encode/decode: Identifier <-> String
                private final PacketCodec<ByteBuf, Identifier> speciesCodec =
                        PacketCodecs.string(32767).xmap(Identifier::tryParse, Identifier::toString);

                private final PacketCodec<ByteBuf, Integer> sizeCodec = PacketCodecs.VAR_INT;
                private final PacketCodec<ByteBuf, String> aspectCodec = PacketCodecs.STRING;

                @Override
                public AnimationHMPacketS2C decode(RegistryByteBuf buf) {
                    // 1) Species Identifier
                    Identifier speciesId = speciesCodec.decode(buf);
                    if (speciesId == null) {
                        throw new IllegalStateException("Missing species identifier");
                    }

                    // 2) Decodifica Species tramite codec NBT (senza Apache)
                    NbtElement nbt = NbtString.of(speciesId.toString());
                    Species species = Species.getBY_IDENTIFIER_CODEC()
                            .parse(NbtOps.INSTANCE, nbt)
                            .getOrThrow(err -> new IllegalStateException("Failed to decode Species: " + err));

                    // 3) Aspects
                    int size = sizeCodec.decode(buf);
                    if (size < 0) {
                        throw new IllegalStateException("Invalid aspects size: " + size);
                    }

                    Set<String> aspects = new HashSet<>(size);
                    for (int i = 0; i < size; i++) {
                        aspects.add(aspectCodec.decode(buf));
                    }

                    return new AnimationHMPacketS2C(new RenderablePokemon(species, aspects, ItemStack.EMPTY));
                }

                @Override
                public void encode(RegistryByteBuf buf, AnimationHMPacketS2C packet) {
                    // 1) Species Identifier
                    speciesCodec.encode(buf, packet.pokemon().getSpecies().getResourceIdentifier());

                    // 2) Aspects
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
