package com.cobblemon.khataly.modhm.block.custom;

import com.cobblemon.khataly.modhm.block.entity.ModBlockEntities;
import com.cobblemon.khataly.modhm.block.entity.custom.BreakableRockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class BreakableRock extends BlockWithEntity implements BlockEntityProvider {

    public static final MapCodec<BreakableRock> CODEC = BreakableRock.createCodec(BreakableRock::new);
    private static final VoxelShape SHAPE =
            Block.createCuboidShape(0, 0, 0, 16, 16, 16);

    public BreakableRock(Settings settings) {
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
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            if (world.getBlockEntity(pos) instanceof BreakableRockEntity breakableRockEntity) {
                player.openHandledScreen(breakableRockEntity);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.FAIL;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BreakableRockEntity(pos, state);
    }

    // Attiva il ticking per il BlockEntity così da rilevare collisioni laterali
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null; // server-side
        if (type != ModBlockEntities.BREAKABLE_ROCK_BE) return null;

        return (world1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof BreakableRockEntity rockEntity) {
                rockEntity.tick();
            }
        };
    }

}
