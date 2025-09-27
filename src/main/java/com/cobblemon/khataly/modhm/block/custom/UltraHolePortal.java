package com.cobblemon.khataly.modhm.block.custom;

import com.cobblemon.khataly.modhm.block.entity.custom.UltraHolePortalEntity;
import com.cobblemon.khataly.modhm.block.entity.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class UltraHolePortal extends BlockWithEntity {
    public static final MapCodec<UltraHolePortal> CODEC = createCodec(UltraHolePortal::new);
    private static final VoxelShape SHAPE =
            Block.createCuboidShape(0, 0, 0, 16, 16, 0.5);
    public UltraHolePortal(Settings settings) {
        super(settings);
    }
    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }
    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL; // Usa modello normale
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new UltraHolePortalEntity(pos, state);
    }
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            // rimuove il blocco alla posizione cliccata
            world.removeBlock(pos, false);

            // ripiazza lo stesso blocco un blocco più in alto
            BlockPos newPos = pos.up(1);
            world.setBlockState(newPos, state, 3);

            // se serve ricrea anche la BlockEntity
            BlockEntity be = world.getBlockEntity(newPos);
            if (be instanceof UltraHolePortalEntity portal) {
                portal.markDirty();
            }
        }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null; // Solo server
        if (type != ModBlockEntities.ULTRAHOLE_ROCK_BE) return null;

        return (serverWorld, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof UltraHolePortalEntity portalEntity) {
                portalEntity.tick();
            }
        };
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof UltraHolePortalEntity portal) {
                portal.removePortal(); // rimuove il BlockEntity se il blocco è cambiato/distrutto
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
