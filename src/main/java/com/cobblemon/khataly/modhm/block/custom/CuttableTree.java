package com.cobblemon.khataly.modhm.block.custom;

import com.cobblemon.khataly.modhm.block.entity.ModBlockEntities;
import com.cobblemon.khataly.modhm.block.entity.custom.BreakableRockEntity;
import com.cobblemon.khataly.modhm.block.entity.custom.CuttableTreeEntity;
import com.cobblemon.khataly.modhm.sound.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class CuttableTree extends BlockWithEntity implements BlockEntityProvider {

    public static final MapCodec<BreakableRock> CODEC = BreakableRock.createCodec(BreakableRock::new);
    private static final VoxelShape SHAPE =
            Block.createCuboidShape(0, 0, 0, 16, 32, 16);

    public CuttableTree(Settings settings) {
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
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CuttableTreeEntity(pos, state);
    }
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null; // server-side
        if (type != ModBlockEntities.CUTTABLE_TREE_BE) return null;

        return (world1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof CuttableTreeEntity cuttableTreeEntity) {
                cuttableTreeEntity.tick();
            }
        };
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            if(world.getBlockEntity(pos) instanceof CuttableTreeEntity cuttableTreeEntity) {
                player.openHandledScreen(cuttableTreeEntity);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.FAIL;
    }

}
