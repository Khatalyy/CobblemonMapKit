package com.cobblemon.khataly.modhm.block.entity.custom;

import com.cobblemon.khataly.modhm.block.entity.ModBlockEntities;
import com.cobblemon.khataly.modhm.screen.custom.CutScreenHandler;
import com.cobblemon.khataly.modhm.sound.ModSounds;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

public class CuttableTreeEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
    public CuttableTreeEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CUTTABLE_TREE_BE, pos, state);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return this.pos;
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("CuttableTree");
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new CutScreenHandler(syncId, playerInventory, this.pos);
    }
    // Metodo tick che chiameremo dal Block
    private boolean hasPlayed = false;
    public void tick() {
        if (world == null) return;

        Box box = new Box(pos).expand(0.5, 0.5, 0.5);
        boolean playerInside = !world.getEntitiesByClass(PlayerEntity.class, box, p -> true).isEmpty();

        if (playerInside && !hasPlayed) {
            for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, box, p -> true)) {
                world.playSound(null, pos, ModSounds.WALL_BUMP, SoundCategory.BLOCKS, 1.0f, 1.0f);
            }
            hasPlayed = true;
        } else if (!playerInside) {
            hasPlayed = false;
        }
    }


    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }
}
