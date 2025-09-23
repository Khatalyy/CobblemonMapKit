package com.cobblemon.khataly.modhm.networking;

import com.cobblemon.khataly.modhm.block.ModBlocks;
import com.cobblemon.khataly.modhm.config.ModConfig;
import com.cobblemon.khataly.modhm.networking.packet.*;
import com.cobblemon.khataly.modhm.sound.ModSounds;
import com.cobblemon.khataly.modhm.util.PartyUtils;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ModNetworking {
    private static final float ROCK_SMASH_POKEMON_ENCOUNTER_CHANCE = 0.25f;

    private static final Map<BlockPos, TimedBlock> blocksToRestore = new ConcurrentHashMap<>();
    private static final Map<BlockPos, BlockPos> currentToOriginal = new ConcurrentHashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger("ModNetworking");

    public static void registerPackets() {
        PayloadTypeRegistry.playS2C().register(AnimationHMPacketS2C.ID, AnimationHMPacketS2C.CODEC);

        PayloadTypeRegistry.playC2S().register(RockSmashPacketC2S.ID, RockSmashPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(CutPacketC2S.ID, CutPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(StrengthPacketC2S.ID, StrengthPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(RockClimbPacketC2S.ID, RockClimbPacketC2S.CODEC);

        PayloadTypeRegistry.playC2S().register(FlyPacketC2S.ID, FlyPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(FlyMenuC2SPacket.ID, FlyMenuC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(FlyMenuS2CPacket.ID, FlyMenuS2CPacket.CODEC);

        PayloadTypeRegistry.playC2S().register(FlashPacketC2S.ID, FlashPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(FlashMenuC2SPacket.ID, FlashMenuC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(FlashMenuS2CPacket.ID, FlashMenuS2CPacket.CODEC);

        PayloadTypeRegistry.playC2S().register(TeleportPacketC2S.ID, TeleportPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(TeleportMenuC2SPacket.ID, TeleportMenuC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(TeleportMenuS2CPacket.ID, TeleportMenuS2CPacket.CODEC);

        registerC2SPackets();
    }

    public static void registerC2SPackets() {
        registerRockSmashHandler();
        registerCutHandler();
        registerRockClimbHandler();
        registerStrengthHandler();
        registerFlyHandler();
        registerTeleportHandler();
        registerFlashHandler();
    }

    private static void registerTeleportHandler() {
        ServerPlayNetworking.registerGlobalReceiver(TeleportPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                // Controlla che abbia un Pokémon che conosce Teleport
                if (!PartyUtils.hasMove(player,"teleport")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Teleport!"), false);
                    return;
                }
                RenderablePokemon renderablePokemon = PartyUtils.getRenderPokemonByMove(player, "teleport");
                if (renderablePokemon != null) {
                    ServerPlayNetworking.send(player, new AnimationHMPacketS2C(renderablePokemon));
                }

                // Posizione spawn personalizzata (letto o respawn point)
                BlockPos spawnPos = player.getSpawnPointPosition();
                ServerWorld spawnWorld = null;

                if (spawnPos != null) {
                    spawnWorld = Objects.requireNonNull(player.getServer()).getWorld(player.getSpawnPointDimension());
                }

                // Se non ha letto, usa spawn del mondo
                if (spawnPos == null || spawnWorld == null) {
                    spawnWorld = Objects.requireNonNull(player.getServer()).getOverworld();
                    spawnPos = spawnWorld.getSpawnPos();
                }

                // Esegui teletrasporto
                player.teleport(
                        spawnWorld,
                        spawnPos.getX() + 0.5,
                        spawnPos.getY() + 1,
                        spawnPos.getZ() + 0.5,
                        player.getYaw(),
                        player.getPitch()
                );
                player.playSoundToPlayer(ModSounds.TELEPORT,SoundCategory.PLAYERS,1,1);
                player.sendMessage(Text.literal("✨ Teleported to your spawn point!"), false);
            });
        });
    }

    private static void registerFlashHandler() {
        ServerPlayNetworking.registerGlobalReceiver(FlashPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                // 🔹 Verifica se ha un Pokémon con Flash
                if (!PartyUtils.hasMove(player,"flash")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Flash!"), false);
                    return;
                }

                // 🔹 Controlla se ha già Night Vision
                if (player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION)) {
                    player.sendMessage(Text.literal("❗ Flash is already active!"), false);
                    return;
                }

                // Mostra animazione
                RenderablePokemon renderablePokemon = PartyUtils.getRenderPokemonByMove(player, "flash");
                if (renderablePokemon != null) {
                    ServerPlayNetworking.send(player, new AnimationHMPacketS2C(renderablePokemon));
                }

                int durationSeconds = ModConfig.FLASH_DURATION;
                int durationTicks = durationSeconds * 20;

                // 🔹 Applica Night Vision lato server
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.NIGHT_VISION,
                        durationTicks,
                        0,
                        false,
                        false
                ));
                player.playSoundToPlayer(ModSounds.FLASH,SoundCategory.PLAYERS,1,1);
                player.sendMessage(Text.literal("✨ Flash activated! You can see clearly for " + durationSeconds + " seconds."), false);
            });
        });
    }

    private static void registerFlyHandler() {
        ServerPlayNetworking.registerGlobalReceiver(FlyPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!PartyUtils.hasMove(player,"fly")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Fly!"), false);
                    return;
                }
                BlockPos targetPos = payload.pos();
                RenderablePokemon renderablePokemon = PartyUtils.getRenderPokemonByMove(player, "fly");
                if (renderablePokemon != null) {
                    ServerPlayNetworking.send(player, new AnimationHMPacketS2C(renderablePokemon));
                }

                player.teleport((ServerWorld) player.getWorld(),
                        targetPos.getX() + 0.5,
                        targetPos.getY(),
                        targetPos.getZ() + 0.5,
                        player.getYaw(),
                        player.getPitch()
                );
                player.playSoundToPlayer(ModSounds.FLY,SoundCategory.PLAYERS,1,1);
                player.sendMessage(Text.literal("🛫 Teleported to " + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ()), false);
            });
        });
    }
    // Mappa dei giocatori che stanno scalando -> posizione iniziale della scalata
    private static final Map<ServerPlayerEntity, BlockPos> playersClimbing = new ConcurrentHashMap<>();
    // Conta i tick trascorsi dall’inizio della scalata
    private static final Map<ServerPlayerEntity, Integer> climbingTicks = new ConcurrentHashMap<>();
    // Giocatori che hanno già sentito il suono
    private static final Set<ServerPlayerEntity> playersPlayingSound = ConcurrentHashMap.newKeySet();

    // Registrazione pacchetto Rock Climb
    private static void registerRockClimbHandler() {
        ServerPlayNetworking.registerGlobalReceiver(RockClimbPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!PartyUtils.hasMove(player, "rockclimb")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Rock Climb!"), false);
                    return;
                }

                BlockPos startPos = payload.pos();
                BlockState startState = player.getWorld().getBlockState(startPos);
                if (startState.isAir() || !startState.isOf(ModBlocks.CLIMBABLE_ROCK)) {
                    player.sendMessage(Text.literal("⚠️ This block cannot be climbed!"), false);
                    return;
                }

                // Animazione Pokémon
                RenderablePokemon renderablePokemon = PartyUtils.getRenderPokemonByMove(player, "rockclimb");
                if (renderablePokemon != null) {
                    ServerPlayNetworking.send(player, new AnimationHMPacketS2C(renderablePokemon));
                }

                // Inizia scalata
                playersClimbing.put(player, startPos);
                climbingTicks.put(player, 0);
            });
        });
    }




    private static void registerRockSmashHandler() {
        ServerPlayNetworking.registerGlobalReceiver(RockSmashPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                BlockPos pos = payload.pos();

                if (!PartyUtils.hasMove(player,"rocksmash")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Rock Smash!"), false);
                    return;
                }

                BlockState originalState = player.getWorld().getBlockState(pos);
                if (originalState.isAir()) {
                    player.sendMessage(Text.literal("⚠️ There's nothing to break here!"), false);
                    return;
                }

                if (blocksToRestore.containsKey(pos)) {
                    player.sendMessage(Text.literal("⏳ The block has already been smashed, wait for it to return!"), false);
                    return;
                }




                player.sendMessage(Text.literal("💥 you used Rock Smash!"), false);
                player.playSoundToPlayer(ModSounds.BREAKABLE_ROCK,SoundCategory.PLAYERS,1,1);

                player.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState());
                blocksToRestore.put(pos, new TimedBlock(originalState, ModConfig.ROCKSMASH_RESPAWN * 20, null));




                player.networkHandler.sendPacket(new ParticleS2CPacket(
                        ParticleTypes.CLOUD,
                        true,
                        (float) pos.getX() + 0.5f,
                        (float) pos.getY() + 0.5f,
                        (float) pos.getZ() + 0.5f,
                        0.3f, 0.3f, 0.3f,
                        0.1f,
                        20
                ));

                LOGGER.info("Block Rock removed at {}, restore timer started", pos);

                if (player.getWorld().random.nextFloat() < ROCK_SMASH_POKEMON_ENCOUNTER_CHANCE) {
                    spawnWildPokemonAttack(player);
                }else {
                    // Recupera il Pokémon del giocatore che conosce la mossa Rock Smash
                    RenderablePokemon renderablePokemon = PartyUtils.getRenderPokemonByMove(player, "rocksmash");
                    if (renderablePokemon != null) {
                        ServerPlayNetworking.send(player, new AnimationHMPacketS2C(renderablePokemon));
                    }
                }
            });
        });
    }

    private static void spawnWildPokemonAttack(ServerPlayerEntity player) {
        Species species = PokemonSpecies.INSTANCE.getByName("geodude");
        if (species == null) {
            LOGGER.warn("Pokemon spec non found!");
            return;
        }

        Pokemon pokemon = new Pokemon();
        pokemon.setSpecies(species);
        pokemon.setLevel(10);
        pokemon.getMoveSet().setMove(2, Objects.requireNonNull(Moves.INSTANCE.getByName("rockthrow")).create());
        pokemon.getMoveSet().setMove(3, Objects.requireNonNull(Moves.INSTANCE.getByName("rockthrow")).create());
        pokemon.getMoveSet().setMove(1, Objects.requireNonNull(Moves.INSTANCE.getByName("selfdestruct")).create());

        EntityType<PokemonEntity> type = CobblemonEntities.POKEMON;
        PokemonEntity pokemonEntity = new PokemonEntity(player.getWorld(), pokemon, type);
        pokemonEntity.setPokemon(pokemon);

        BlockPos spawnPos = player.getBlockPos().add(1, 0, 0);
        pokemonEntity.refreshPositionAndAngles(spawnPos, 0, 0);
        player.getWorld().spawnEntity(pokemonEntity);

        BattleBuilder.INSTANCE.pve(player, pokemonEntity);
    }

    private static void registerCutHandler() {
        ServerPlayNetworking.registerGlobalReceiver(CutPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                BlockPos pos = payload.pos();

                if (!PartyUtils.hasMove(player,"cut")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Cut!"), false);
                    return;
                }

                BlockState originalState = player.getWorld().getBlockState(pos);
                if (originalState.isAir()) {
                    player.sendMessage(Text.literal("⚠️ There's nothing to cut here!"), false);
                    return;
                }

                if (blocksToRestore.containsKey(pos)) {
                    player.sendMessage(Text.literal("⏳ The block has already been cut, wait for it to return!"), false);
                    return;
                }

                RenderablePokemon renderablePokemon = PartyUtils.getRenderPokemonByMove(player, "cut");
                if (renderablePokemon != null) {
                    ServerPlayNetworking.send(player, new AnimationHMPacketS2C(renderablePokemon));
                }
                player.sendMessage(Text.literal("💥 you used Cut!"), false);
                player.playSoundToPlayer(ModSounds.CUTTABLE_TREE,SoundCategory.PLAYERS,1,1);

                player.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState());
                blocksToRestore.put(pos, new TimedBlock(originalState, ModConfig.CUT_RESPAWN * 20, null));


                player.networkHandler.sendPacket(new ParticleS2CPacket(
                        ParticleTypes.CHERRY_LEAVES,
                        true,
                        (float) pos.getX() + 0.5f,
                        (float) pos.getY() + 0.5f,
                        (float) pos.getZ() + 0.5f,
                        0.3f, 0.3f, 0.3f,
                        0.1f,
                        20
                ));

                LOGGER.info("Block removed at {}, restore timer started", pos);
            });
        });
    }

    private static void registerStrengthHandler() {
        ServerPlayNetworking.registerGlobalReceiver(StrengthPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                BlockPos clickedPos = payload.pos();

                if (!PartyUtils.hasMove(player,"strength")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Strength!"), false);
                    return;
                }

                BlockPos originalPos = currentToOriginal.getOrDefault(clickedPos, clickedPos);
                TimedBlock timedBlock = blocksToRestore.get(originalPos);
                BlockPos currentPos = (timedBlock != null && timedBlock.movedTo != null)
                        ? timedBlock.movedTo
                        : originalPos;

                BlockState blockState = player.getWorld().getBlockState(currentPos);
                if (blockState.isAir()) {
                    player.sendMessage(Text.literal("⚠️ There's nothing to move here!"), false);
                    return;
                }

                BlockPos targetPos = currentPos.offset(player.getHorizontalFacing());
                if (!player.getWorld().getBlockState(targetPos).isAir()) {
                    player.sendMessage(Text.literal("⛔ Cannot push the block there!"), false);
                    player.playSoundToPlayer(ModSounds.WALL_BUMP,SoundCategory.PLAYERS,1,1);
                    return;
                }

                RenderablePokemon renderablePokemon = PartyUtils.getRenderPokemonByMove(player, "strength");
                if (renderablePokemon != null) {
                    ServerPlayNetworking.send(player, new AnimationHMPacketS2C(renderablePokemon));
                }
                player.sendMessage(Text.literal("💥 you used Strength!"), false);
                player.playSoundToPlayer(ModSounds.MOVABLE_ROCK,SoundCategory.PLAYERS,1,1);
                // Rimuovi blocco attuale e aggiorna mappature
                player.getWorld().setBlockState(currentPos, Blocks.AIR.getDefaultState());
                currentToOriginal.remove(currentPos);

                // Metti blocco nella nuova posizione
                player.getWorld().setBlockState(targetPos, blockState);
                currentToOriginal.put(targetPos, originalPos);

                // Aggiorna o crea TimedBlock (non cerchiamo più l'entity immediatamente)
                if (timedBlock != null) {
                    timedBlock.movedTo = targetPos;
                    timedBlock.ticksLeft = ModConfig.STRENGTH_RESPAWN * 20;
                } else {
                    TimedBlock tb = new TimedBlock(blockState, ModConfig.STRENGTH_RESPAWN * 20, targetPos);
                    // lasciamo tb.fallingEntity null per ora; tick() gestirà la rimozione cercando sotto movedTo
                    blocksToRestore.put(originalPos, tb);
                }

            });
        });
    }



    public static void tick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();

        // --- 1️⃣ Gestione blocchi da ripristinare ---
        blocksToRestore.entrySet().removeIf(entry -> {
            BlockPos originalPos = entry.getKey();
            TimedBlock timedBlock = entry.getValue();

            // decremento timer
            timedBlock.ticksLeft--;
            if (timedBlock.ticksLeft > 0) return false;

            // rimuovi FallingBlockEntity se ancora vivo
            if (timedBlock.fallingEntity != null && timedBlock.fallingEntity.isAlive()) {
                timedBlock.fallingEntity.discard();
            }

            // Se il blocco è stato spostato, cerca di rimuoverlo da movedTo
            if (timedBlock.movedTo != null && !timedBlock.movedTo.equals(originalPos)) {
                BlockPos moved = timedBlock.movedTo;
                BlockState stateAtMoved = world.getBlockState(moved);

                if (stateAtMoved.isOf(timedBlock.blockState.getBlock())) {
                    world.setBlockState(moved, Blocks.AIR.getDefaultState());
                    currentToOriginal.remove(moved);
                } else {
                    final int maxSearch = 64;
                    BlockPos scan = moved.down();
                    int steps = 0;

                    while (scan.getY() >= world.getBottomY() && steps < maxSearch) {
                        BlockState s = world.getBlockState(scan);
                        if (s.isOf(timedBlock.blockState.getBlock())) {
                            world.setBlockState(scan, Blocks.AIR.getDefaultState());
                            currentToOriginal.remove(scan);
                            break;
                        }
                        scan = scan.down();
                        steps++;
                    }
                    currentToOriginal.remove(moved);
                }
            }

            // Ripristina blocco originale
            world.setBlockState(originalPos, timedBlock.blockState);
            currentToOriginal.remove(originalPos);

            LOGGER.info("Block restored at {}", originalPos);
            return true; // rimuovi dalla mappa blocksToRestore
        });

        // --- 2️⃣ Gestione scalata giocatori ---
        playersClimbing.forEach((player, startPos) -> {
            if (!player.isAlive()) {
                playersClimbing.remove(player);
                climbingTicks.remove(player);
                playersPlayingSound.remove(player);
                return;
            }

            // Incrementa tick di scalata
            climbingTicks.put(player, climbingTicks.getOrDefault(player, 0) + 1);

            // Calcola dinamicamente l’ultimo blocco CLIMBABLE_ROCK sopra startPos
            BlockPos lastBlockPos = startPos;
            int maxClimbHeight = 20;
            for (int i = 1; i <= maxClimbHeight; i++) {
                BlockPos nextPos = startPos.up(i);
                if (world.getBlockState(nextPos).isOf(ModBlocks.CLIMBABLE_ROCK)) {
                    lastBlockPos = nextPos;
                } else {
                    break;
                }
            }

            // Salto finale: un blocco sopra l’ultimo + altezza giocatore
            lastBlockPos = lastBlockPos.up(2);

            // Direzione verso target
            double dx = lastBlockPos.getX() + 0.5 - player.getX();
            double dy = lastBlockPos.getY() - player.getY();
            double dz = lastBlockPos.getZ() + 0.5 - player.getZ();
            double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

            if (distance < 0.1) {
                // Arrivato in cima
                player.setVelocity(0, 0, 0);
                player.velocityModified = true;
                player.sendMessage(Text.literal("🧗 You climbed to the top of the rock!"), false);

                playersClimbing.remove(player);
                climbingTicks.remove(player);
                playersPlayingSound.remove(player);
                return;
            }

            // Normalizza direzione e applica velocità
            double speed = 0.15;
            double vx = dx / distance * speed;
            double vy = dy / distance * speed;
            double vz = dz / distance * speed;

            player.setVelocity(vx, vy, vz);
            player.velocityModified = true;

            // Avvia suono solo dopo un ritardo di 4 tick
            int tickDelay = 4;
            if (climbingTicks.get(player) >= tickDelay && !playersPlayingSound.contains(player)) {
                player.playSoundToPlayer(ModSounds.CLIMBABLE_ROCK, SoundCategory.PLAYERS, 1f, 1f);
                playersPlayingSound.add(player);
            }
        });
    }




    private static class TimedBlock {
        final BlockState blockState;
        BlockPos movedTo;
        int ticksLeft;
        FallingBlockEntity fallingEntity; // 🔽 collegamento al blocco caduto

        TimedBlock(BlockState blockState, int ticksLeft, BlockPos movedTo) {
            this.blockState = blockState;
            this.ticksLeft = ticksLeft;
            this.movedTo = movedTo;
        }
    }
}
