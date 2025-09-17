package com.cobblemon.khataly.modhm.networking;

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
        ModConfig.load(); // Carica la configurazione all’avvio

        PayloadTypeRegistry.playC2S().register(RockSmashPacketC2S.ID, RockSmashPacketC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(RockSmashPacketS2C.ID, RockSmashPacketS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(CutPacketC2S.ID, CutPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(StrengthPacketC2S.ID, StrengthPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(FlyPacketC2S.ID, FlyPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(TeleportPacketC2S.ID, TeleportPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(FlyMenuC2SPacket.ID, FlyMenuC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(FlyMenuS2CPacket.ID, FlyMenuS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(FlashMenuC2SPacket.ID, FlashMenuC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(FlashMenuS2CPacket.ID, FlashMenuS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(TeleportMenuC2SPacket.ID, TeleportMenuC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(TeleportMenuS2CPacket.ID, TeleportMenuS2CPacket.CODEC);
        registerC2SPackets();
    }

    public static void registerC2SPackets() {
        registerShowFlyMenuHandler();
        registerShowTeleportMenuHandler();
        registerRockSmashHandler();
        registerCutHandler();
        registerStrengthHandler();
        registerFlyHandler();
        registerShowFlashMenuHandler();
        registerTeleportHandler();
    }

    private static void registerShowTeleportMenuHandler() {
        ServerPlayNetworking.registerGlobalReceiver(TeleportMenuC2SPacket.ID, (payload, context) -> {
            System.out.println("Receiver TeleportMenuC2SPacket registrato");
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                // ✅ Usa payload.pokemonId() perché è un record
                boolean cantp = PartyUtils.pokemonHasTeleportInParty(player, payload.pokemonId());
                System.out.println(cantp + " variabile bool tp nel pokemon");

                // Invia pacchetto S2C
                ServerPlayNetworking.send(player, new TeleportMenuS2CPacket(cantp));
            });
        });
    }

    private static void registerTeleportHandler() {
        ServerPlayNetworking.registerGlobalReceiver(TeleportPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                // Controlla che abbia un Pokémon che conosce Teleport
                if (!PartyUtils.hasTeleport(player)) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Teleport!"), false);
                    return;
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

                player.sendMessage(Text.literal("✨ Teleported to your spawn point!"), false);
            });
        });
    }

    private static void registerShowFlashMenuHandler() {
        ServerPlayNetworking.registerGlobalReceiver(FlashMenuC2SPacket.ID, (payload, context) -> {
            System.out.println("Receiver FlashMenuC2SPacket registrato");
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                // ✅ Usa payload.pokemonId() perché è un record
                boolean canFlash = PartyUtils.pokemonHasFlashInParty(player, payload.pokemonId());
                System.out.println(canFlash + " variabile bool flash nel pokemon");

                // Invia pacchetto S2C
                ServerPlayNetworking.send(player, new FlashMenuS2CPacket(canFlash));
            });
        });
    }

    private static void registerShowFlyMenuHandler() {
        ServerPlayNetworking.registerGlobalReceiver(FlyMenuC2SPacket.ID, (payload, context) -> {
            System.out.println("Receiver FlyMenuC2SPacket registrato");
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                // ✅ Usa payload.pokemonId() perché è un record
                boolean canFly = PartyUtils.pokemonHasFlyInParty(player, payload.pokemonId());
                System.out.println(canFly + " variabile bool fly nel pokemon");

                // Invia pacchetto S2C
                ServerPlayNetworking.send(player, new FlyMenuS2CPacket(canFly));
            });
        });
    }



    private static void registerFlyHandler() {
        ServerPlayNetworking.registerGlobalReceiver(FlyPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!PartyUtils.hasFly(player)) {
                    player.sendMessage(Text.literal("❌ No Pokémon in your party knows Fly!"), false);
                    return;
                }

                BlockPos targetPos = payload.pos();
                if (!player.getWorld().isChunkLoaded(targetPos)) {
                    player.sendMessage(Text.literal("⚠️ The target location is not loaded!"), false);
                    return;
                }

                player.teleport((ServerWorld) player.getWorld(),
                        targetPos.getX() + 0.5,
                        targetPos.getY(),
                        targetPos.getZ() + 0.5,
                        player.getYaw(),
                        player.getPitch()
                );

                player.sendMessage(Text.literal("🛫 Teleported to " + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ()), false);
            });
        });
    }

    private static void registerRockSmashHandler() {
        ServerPlayNetworking.registerGlobalReceiver(RockSmashPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                BlockPos pos = payload.pos();

                if (!PartyUtils.hasRockSmash(player)) {
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

                // Recupera il Pokémon del giocatore che conosce la mossa Rock Smash
                RenderablePokemon renderablePokemon = PartyUtils.getRenderPokemonByMove(player, "rocksmash");
                if (renderablePokemon != null) {
                    ServerPlayNetworking.send(player, new RockSmashPacketS2C(renderablePokemon));
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

                if (!PartyUtils.hasCut(player)) {
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

                LOGGER.info("Block removed at " + pos + ", restore timer started");
            });
        });
    }

    private static void registerStrengthHandler() {
        ServerPlayNetworking.registerGlobalReceiver(StrengthPacketC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                BlockPos clickedPos = payload.pos();

                if (!PartyUtils.hasStrength(player)) {
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

        blocksToRestore.entrySet().removeIf(entry -> {
            BlockPos originalPos = entry.getKey();
            TimedBlock timedBlock = entry.getValue();

            // decremento timer
            timedBlock.ticksLeft--;
            if (timedBlock.ticksLeft > 0) return false;

            // se esiste un FallingBlockEntity associato e ancora vivo -> lo rimuoviamo
            if (timedBlock.fallingEntity != null && timedBlock.fallingEntity.isAlive()) {
                timedBlock.fallingEntity.discard();
            }

            // Se il blocco è stato spostato, proviamo a cancellarlo: prima in movedTo,
            // poi cerchiamo sotto movedTo (fino a maxSearch blocchi) per trovare il masso caduto
            if (timedBlock.movedTo != null && !timedBlock.movedTo.equals(originalPos)) {
                BlockPos moved = timedBlock.movedTo;
                BlockState stateAtMoved = world.getBlockState(moved);

                // caso semplice: il blocco è ancora nella posizione movedTo
                if (stateAtMoved.isOf(timedBlock.blockState.getBlock())) {
                    world.setBlockState(moved, Blocks.AIR.getDefaultState());
                    currentToOriginal.remove(moved);
                } else {
                    // cerca in colonna verso il basso il primo blocco dello stesso tipo (range limitata)
                    final int maxSearch = 64; // puoi modificare questo limite
                    BlockPos scan = moved.down();
                    int steps = 0;
                    boolean removed = false;

                    while (scan.getY() >= world.getBottomY() && steps < maxSearch) {
                        BlockState s = world.getBlockState(scan);
                        if (s.isOf(timedBlock.blockState.getBlock())) {
                            // trovato il masso caduto -> rimuovilo
                            world.setBlockState(scan, Blocks.AIR.getDefaultState());
                            currentToOriginal.remove(scan);
                            removed = true;
                            break;
                        }
                        // se trovi un blocco solido diverso dalla aria potresti voler fermarti;
                        // qui continuiamo a scendere finché trovi lo stesso blocco o superiamo maxSearch
                        if(!s.isAir()) {
                            // continua comunque, può essere che sia atterrato su una panca non identica
                        }
                        scan = scan.down();
                        steps++;
                    }

                    // rimuovi la mappatura stale per la posizione movedTo (evita riferimenti obsoleti)
                    currentToOriginal.remove(moved);
                }
            }

            // ripristina l'originale sempre
            world.setBlockState(originalPos, timedBlock.blockState);
            currentToOriginal.remove(originalPos);

            LOGGER.info("Block restored at " + originalPos);
            return true; // rimuovi dalla mappa blocksToRestore
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
