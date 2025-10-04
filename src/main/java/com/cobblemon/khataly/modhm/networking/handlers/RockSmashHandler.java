package com.cobblemon.khataly.modhm.networking.handlers;

import com.cobblemon.khataly.modhm.config.ModConfig;
import com.cobblemon.khataly.modhm.networking.manager.RestoreManager;
import com.cobblemon.khataly.modhm.networking.packet.RockSmashPacketC2S;
import com.cobblemon.khataly.modhm.networking.util.NetUtil;
import com.cobblemon.khataly.modhm.sound.ModSounds;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class RockSmashHandler {
    private RockSmashHandler() {}
    private static final Logger LOGGER = LoggerFactory.getLogger("RockSmashHandler");
    private static final float ENCOUNTER_CHANCE = 0.25f;

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(RockSmashPacketC2S.ID, (payload, ctx) -> {
            ServerPlayerEntity p = ctx.player();
            ctx.server().execute(() -> {
                BlockPos pos = payload.pos();

                if (!NetUtil.requireMove(p, "rocksmash", "❌ No Pokémon in your party knows Rock Smash!")) return;
                if (!NetUtil.requireItem(p, ModConfig.ROCKSMASH.item, ModConfig.ROCKSMASH.message)) return;

                BlockState original = p.getWorld().getBlockState(pos);
                if (original.isAir()) {
                    NetUtil.msg(p, "⚠️ There's nothing to break here!");
                    return;
                }
                if (RestoreManager.get().isBusy(pos)) {
                    NetUtil.msg(p, "⏳ The block has already been smashed, wait for it to return!");
                    return;
                }

                NetUtil.msg(p, "💥 you used Rock Smash!");
                NetUtil.playPlayerSound(p, ModSounds.BREAKABLE_ROCK);

                p.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState());
                RestoreManager.get().addTimed(pos, original, ModConfig.ROCKSMASH_RESPAWN);

                NetUtil.sendParticles(p, ParticleTypes.CLOUD, pos, 0.3f, 0.3f, 0.3f, 0.1f, 20);
                LOGGER.info("Block Rock removed at {}, restore timer started", pos);

                if (p.getWorld().random.nextFloat() < ENCOUNTER_CHANCE) {
                    spawnWildPokemonAttack(p);
                } else {
                    NetUtil.sendAnimation(p, "rocksmash");
                }
            });
        });
    }

    private static void spawnWildPokemonAttack(ServerPlayerEntity player) {
        Species species = PokemonSpecies.INSTANCE.getByName("geodude");
        if (species == null) {
            LOGGER.warn("Pokemon spec not found!");
            return;
        }

        Pokemon pokemon = new Pokemon();
        pokemon.setSpecies(species);
        pokemon.setLevel(10);
        pokemon.initializeMoveset(true);

        PokemonEntity pokemonEntity = new PokemonEntity(player.getWorld(), pokemon, CobblemonEntities.POKEMON);
        pokemonEntity.setPokemon(pokemon);

        BlockPos spawnPos = player.getBlockPos().add(1, 0, 0);
        pokemonEntity.refreshPositionAndAngles(spawnPos, 0, 0);
        player.getWorld().spawnEntity(pokemonEntity);

        BattleBuilder.INSTANCE.pve(player, pokemonEntity);
    }
}
