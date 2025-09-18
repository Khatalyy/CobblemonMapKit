package com.cobblemon.khataly.modhm.block.custom;

import com.cobblemon.khataly.modhm.block.entity.ModBlockEntities;
import com.cobblemon.khataly.modhm.block.entity.custom.MovableRockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class MovableRock extends BlockWithEntity implements BlockEntityProvider {
    public static final MapCodec<MovableRock> CODEC = createCodec(MovableRock::new);

    private static final VoxelShape SHAPE =
            Block.createCuboidShape(0, 0, 0, 16, 16, 16);

    public MovableRock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null; // server-side
        if (type != ModBlockEntities.MOVABLE_ROCK) return null;

        return (world1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof MovableRockEntity movableRockEntity) {
                movableRockEntity.tick();
            }
        };
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            if (world.getBlockEntity(pos) instanceof MovableRockEntity movableRockEntity) {
                player.openHandledScreen(movableRockEntity);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.FAIL;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MovableRockEntity(pos, state);
    }

    // 🔽 Gravità
    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        world.scheduleBlockTick(pos, this, 2);
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (canFallThrough(world.getBlockState(pos.down())) && pos.getY() >= world.getBottomY()) {
            // Rimuovi BlockEntity
            BlockEntity be = world.getBlockEntity(pos);
            world.removeBlockEntity(pos);

            // Crea FallingBlockEntity
            FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, pos, state);

            if (be instanceof MovableRockEntity rockEntity) {
                falling.blockEntityData = rockEntity.createNbtWithIdentifyingData(world.getRegistryManager());
            }

            // 🔽 Marca l’entità per riconoscerla dopo
            falling.addCommandTag("movable_rock");
            falling.addCommandTag("origin_" + pos.toShortString());
        }
    }

    private boolean canFallThrough(BlockState state) {
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty()) return true;
        if (state.isIn(BlockTags.FIRE)) return true;
        return false;
    }
}
