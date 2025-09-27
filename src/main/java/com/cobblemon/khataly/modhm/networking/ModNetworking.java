package com.cobblemon.khataly.modhm.networking;

import com.cobblemon.khataly.modhm.block.ModBlocks;
import com.cobblemon.khataly.modhm.block.custom.ClimbableRock;
import com.cobblemon.khataly.modhm.config.ModConfig;
import com.cobblemon.khataly.modhm.networking.packet.*;
import com.cobblemon.khataly.modhm.sound.ModSounds;
import com.cobblemon.khataly.modhm.util.PlayerUtils;
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
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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

        PayloadTypeRegistry.playC2S().register(UltraHolePacketC2S.ID, UltraHolePacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(UltraHoleMenuC2SPacket.ID, UltraHoleMenuC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(UltraHoleMenuS2CPacket.ID, UltraHoleMenuS2CPacket.CODEC);

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
        registerUltraHoleHandler();
    }

    private static void registerUltraHoleHandler() {
        ServerPlayNetworking.registerGlobalReceiver(UltraHolePacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {

                // Lista delle mosse richieste per usare UltraHole
                List<String> ultraHoleMoves = List.of("sunsteelstrike", "moongeistbeam");
                List<String> knownMoves = ultraHoleMoves.stream()
                        .filter(move -> PlayerUtils.hasMove(player, move))
                        .toList();

                if (knownMoves.isEmpty()) {
                    player.sendMessage(Text.literal("❌ None of your Pokémon know the moves required to open an UltraHole!"), false);
                    player.sendMessage(Text.literal("Required moves: Sunsteel Strike or Moongeist Beam"), false);
                    return;
                }

                if (ModConfig.ULTRAHOLE.item != null &&
                        !PlayerUtils.hasRequiredItem(player, ModConfig.ULTRAHOLE.item)) {
                    player.sendMessage(Text.literal(ModConfig.ULTRAHOLE.message), false);
                    return;
                }

                // --- Determina la dimensione di destinazione ---
                String destDimensionId = ModConfig.ULTRAHOLE_SETTINGS.destinationDimension;

                // Se il giocatore è già nella destinazione, torna nell'Overworld
                if (player.getWorld().getRegistryKey().getValue().toString().equals(destDimensionId)) {
                    destDimensionId = "minecraft:overworld";
                }

                Identifier dimIdentifier = Identifier.tryParse(destDimensionId);
                if (dimIdentifier == null) {
                    player.sendMessage(Text.literal("❌ Invalid dimension ID in config!"), false);
                    return;
                }

                RegistryKey<World> targetWorldKey = RegistryKey.of(RegistryKeys.WORLD, dimIdentifier);
                ServerWorld targetWorld = Objects.requireNonNull(player.getServer()).getWorld(targetWorldKey);
                if (targetWorld == null) {
                    player.sendMessage(Text.literal("❌ Target dimension not found!"), false);
                    return;
                }

                // Prendi la prima mossa valida
                String chosenMove = knownMoves.getFirst();
                RenderablePokemon renderablePokemon = PlayerUtils.getRenderPokemonByMove(player, chosenMove);
                if (renderablePokemon != null) {
                    ServerPlayNetworking.send(player, new AnimationHMPacketS2C(renderablePokemon));
                }

                // --- Teletrasporto alle coordinate configurate ---
                double x = ModConfig.ULTRAHOLE_SETTINGS.x;
                double y = ModConfig.ULTRAHOLE_SETTINGS.y;
                double z = ModConfig.ULTRAHOLE_SETTINGS.z;

                player.teleport(targetWorld, x, y, z, player.getYaw(), player.getPitch());
            });
        });
    }




    private static void registerTeleportHandler() {
        ServerPlayNetworking.registerGlobalReceiver(TeleportPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                // Controlla che abbia un Pokémon che conosce Teleport
                if (!PlayerUtils.hasMove(player,"teleport")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Teleport!"), false);
                    return;
                }
                if (ModConfig.TELEPORT.item != null &&
                        !PlayerUtils.hasRequiredItem(player, ModConfig.TELEPORT.item)) {
                    // usa il messaggio personalizzato
                    player.sendMessage(Text.literal(ModConfig.TELEPORT.message), false);
                    return;
                }
                RenderablePokemon renderablePokemon = PlayerUtils.getRenderPokemonByMove(player, "teleport");
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
                if (!PlayerUtils.hasMove(player,"flash")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Flash!"), false);
                    return;
                }
                if (ModConfig.FLASH.item != null &&
                        !PlayerUtils.hasRequiredItem(player, ModConfig.FLASH.item)) {
                    // usa il messaggio personalizzato
                    player.sendMessage(Text.literal(ModConfig.FLASH.message), false);
                    return;
                }

                // 🔹 Controlla se ha già Night Vision
                if (player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION)) {
                    player.sendMessage(Text.literal("❗ Flash is already active!"), false);
                    return;
                }

                // Mostra animazione
                RenderablePokemon renderablePokemon = PlayerUtils.getRenderPokemonByMove(player, "flash");
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
                if (!PlayerUtils.hasMove(player,"fly")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Fly!"), false);
                    return;
                }
                if (ModConfig.FLY.item != null &&
                        !PlayerUtils.hasRequiredItem(player, ModConfig.FLY.item)) {
                    // usa il messaggio personalizzato
                    player.sendMessage(Text.literal(ModConfig.FLY.message), false);
                    return;
                }
                BlockPos targetPos = payload.pos();
                RenderablePokemon renderablePokemon = PlayerUtils.getRenderPokemonByMove(player, "fly");
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
    // --- Variabili globali per la scalata ---
    private static final Map<ServerPlayerEntity, BlockPos> playersClimbing = new ConcurrentHashMap<>();
    private static final Map<ServerPlayerEntity, Integer> climbingTicks = new ConcurrentHashMap<>();
    private static final Map<ServerPlayerEntity, Set<BlockPos>> visitedClimbBlocks = new ConcurrentHashMap<>();
    private static final Map<ServerPlayerEntity, BlockPos> climbingTargets = new ConcurrentHashMap<>();
    private static final Set<ServerPlayerEntity> playersPlayingSound = ConcurrentHashMap.newKeySet();

    // --- Registrazione pacchetto Rock Climb ---
    private static void registerRockClimbHandler() {
        ServerPlayNetworking.registerGlobalReceiver(RockClimbPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!PlayerUtils.hasMove(player, "rockclimb")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Rock Climb!"), false);
                    return;
                }
                if (ModConfig.ROCKCLIMB.item != null &&
                        !PlayerUtils.hasRequiredItem(player, ModConfig.ROCKCLIMB.item)) {
                    // usa il messaggio personalizzato
                    player.sendMessage(Text.literal(ModConfig.ROCKCLIMB.message), false);
                    return;
                }
                BlockPos startPos = payload.pos();
                BlockState startState = player.getWorld().getBlockState(startPos);
                if (startState.isAir() || !startState.isOf(ModBlocks.CLIMBABLE_ROCK)) {
                    player.sendMessage(Text.literal("⚠️ This block cannot be climbed!"), false);
                    return;
                }

                // Animazione Pokémon
                RenderablePokemon renderablePokemon = PlayerUtils.getRenderPokemonByMove(player, "rockclimb");
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

                if (!PlayerUtils.hasMove(player,"rocksmash")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Rock Smash!"), false);
                    return;
                }
                if (ModConfig.ROCKSMASH.item != null &&
                        !PlayerUtils.hasRequiredItem(player, ModConfig.ROCKSMASH.item)) {
                    // usa il messaggio personalizzato
                    player.sendMessage(Text.literal(ModConfig.ROCKSMASH.message), false);
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
                    RenderablePokemon renderablePokemon = PlayerUtils.getRenderPokemonByMove(player, "rocksmash");
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

                if (!PlayerUtils.hasMove(player,"cut")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Cut!"), false);
                    return;
                }
                if (ModConfig.CUT.item != null &&
                        !PlayerUtils.hasRequiredItem(player, ModConfig.CUT.item)) {
                    // usa il messaggio personalizzato
                    player.sendMessage(Text.literal(ModConfig.CUT.message), false);
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

                RenderablePokemon renderablePokemon = PlayerUtils.getRenderPokemonByMove(player, "cut");
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

                if (!PlayerUtils.hasMove(player,"strength")) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Strength!"), false);
                    return;
                }
                if (ModConfig.STRENGTH.item != null &&
                        !PlayerUtils.hasRequiredItem(player, ModConfig.STRENGTH.item)) {
                    // usa il messaggio personalizzato
                    player.sendMessage(Text.literal(ModConfig.STRENGTH.message), false);
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

                RenderablePokemon renderablePokemon = PlayerUtils.getRenderPokemonByMove(player, "strength");
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

            timedBlock.ticksLeft--;
            if (timedBlock.ticksLeft > 0) return false;

            if (timedBlock.fallingEntity != null && timedBlock.fallingEntity.isAlive()) {
                timedBlock.fallingEntity.discard();
            }

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

            world.setBlockState(originalPos, timedBlock.blockState);
            currentToOriginal.remove(originalPos);

            LOGGER.info("Block restored at {}", originalPos);
            return true;
        });

        // --- 2️⃣ Gestione scalata giocatori (su e giù) ---
        playersClimbing.forEach((player, startPos) -> {
            if (!player.isAlive()) {
                // Cleanup
                playersClimbing.remove(player);
                climbingTicks.remove(player);
                playersPlayingSound.remove(player);
                climbingTargets.remove(player);
                visitedClimbBlocks.remove(player);
                return;
            }

            climbingTicks.put(player, climbingTicks.getOrDefault(player, 0) + 1);

            // Inizializza set di blocchi visitati
            visitedClimbBlocks.putIfAbsent(player, new HashSet<>());
            Set<BlockPos> visited = visitedClimbBlocks.get(player);
            visited.add(startPos);

            // Prendi target o calcolane uno nuovo
            BlockPos target = climbingTargets.get(player);
            if (target == null) {
                target = findNextClimbStep(world, startPos, visited);
                if (target == null) {
                    player.sendMessage(Text.literal("🧗 No climbable blocks!"), false);
                    playersClimbing.remove(player);
                    climbingTicks.remove(player);
                    playersPlayingSound.remove(player);
                    climbingTargets.remove(player);
                    visitedClimbBlocks.remove(player);
                    return;
                }
                climbingTargets.put(player, target);
                visited.add(target);
            }

            // Movimento verso target
            double dx = target.getX() + 0.5 - player.getX();
            double dy = target.getY() + 1.0 - player.getY();
            double dz = target.getZ() + 0.5 - player.getZ();
            double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

            if (distance < 0.2) {
                // Raggiunto target: calcola prossimo passo
                BlockPos next = findNextClimbStep(world, target, visited);
                if (next == null) {
                    // Fine scalata, boost finale su o giù
                    double finalBoost = dy > 0 ? 0.2 : -0.2;
                    player.setVelocity(0, finalBoost, 0);
                    player.velocityModified = true;

                    // Messaggio basato sulla direzione
                    if (dy > 0) {
                        player.sendMessage(Text.literal("🧗 You climbed up!"), false);
                    } else {
                        player.sendMessage(Text.literal("🧗 You climbed down!"), false);
                    }

                    // Cleanup
                    playersClimbing.remove(player);
                    climbingTicks.remove(player);
                    playersPlayingSound.remove(player);
                    climbingTargets.remove(player);
                    visitedClimbBlocks.remove(player);
                    return;
                }
                climbingTargets.put(player, next);
                visited.add(next);
                return;
            }

            // Muovi verso target
            double speed = 0.15;
            double vx = dx / distance * speed;
            double vy = dy / distance * speed;
            double vz = dz / distance * speed;
            player.setVelocity(vx, vy, vz);
            player.velocityModified = true;

            // Suono dopo delay
            int tickDelay = 4;
            if (climbingTicks.get(player) >= tickDelay && !playersPlayingSound.contains(player)) {
                player.playSoundToPlayer(ModSounds.CLIMBABLE_ROCK, SoundCategory.PLAYERS, 1f, 1f);
                playersPlayingSound.add(player);
            }
        });
    }

    // --- Funzione per trovare il prossimo blocco climbabile ---
    private static BlockPos findNextClimbStep(ServerWorld world, BlockPos from, Set<BlockPos> visited) {
        BlockState state = world.getBlockState(from);

        // --- 1️⃣ Controlla sopra (salita) ---
        BlockPos up = from.up();
        if (!visited.contains(up) && state.isOf(ModBlocks.CLIMBABLE_ROCK) && world.getBlockState(up).isOf(ModBlocks.CLIMBABLE_ROCK)) {
            return up;
        }

        if (state.isOf(ModBlocks.CLIMBABLE_ROCK)) {
            Direction facing = state.get(ClimbableRock.FACING);

            BlockPos upForward = up.offset(facing);
            if (!visited.contains(upForward) && world.getBlockState(upForward).isOf(ModBlocks.CLIMBABLE_ROCK))
                return upForward;

            BlockPos upBackward = up.offset(facing.getOpposite());
            if (!visited.contains(upBackward) && world.getBlockState(upBackward).isOf(ModBlocks.CLIMBABLE_ROCK))
                return upBackward;
        }

        // --- 2️⃣ Controlla sotto (discesa) ---
        BlockPos down = from.down();
        if (!visited.contains(down) && world.getBlockState(down).isOf(ModBlocks.CLIMBABLE_ROCK)) return down;

        if (state.isOf(ModBlocks.CLIMBABLE_ROCK)) {
            Direction facing = state.get(ClimbableRock.FACING);

            BlockPos downForward = down.offset(facing);
            if (!visited.contains(downForward) && world.getBlockState(downForward).isOf(ModBlocks.CLIMBABLE_ROCK))
                return downForward;

            BlockPos downBackward = down.offset(facing.getOpposite());
            if (!visited.contains(downBackward) && world.getBlockState(downBackward).isOf(ModBlocks.CLIMBABLE_ROCK))
                return downBackward;
        }

        // --- 3️⃣ Controlla vicini orizzontali ---
        Direction[] horizontals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction dir : horizontals) {
            BlockPos n = from.offset(dir);
            if (!visited.contains(n) && world.getBlockState(n).isOf(ModBlocks.CLIMBABLE_ROCK)) return n;
        }

        // Nessun blocco climbabile trovato
        return null;
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
