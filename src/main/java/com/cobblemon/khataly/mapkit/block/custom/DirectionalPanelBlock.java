package com.cobblemon.khataly.mapkit.block.custom;

import com.cobblemon.khataly.mapkit.sound.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class DirectionalPanelBlock extends HorizontalFacingBlock {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final MapCodec<DirectionalPanelBlock> CODEC = createCodec(DirectionalPanelBlock::new);
    private static final boolean ENABLED = true;
    public DirectionalPanelBlock(Settings settings) {
        super(settings);
        setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClient) {
            if (!ENABLED) {
                if(entity.isPlayer()){
                    entity.sendMessage(Text.literal("You are not authorized to use this mod. Please contact the owner for permission.")
                            .styled(s -> s.withColor(0xFF0000)));
                    return;
                }
            }
            Direction direction = state.get(FACING);
            Vec3d push = new Vec3d(
                    direction.getOffsetX() * 0.5,
                    0,
                    direction.getOffsetZ() * 0.5
            );

            // Applica la spinta a qualsiasi entità
            entity.addVelocity(push);
            entity.velocityModified = true;

            // Suono solo per il player (opzionale)
            if (entity instanceof PlayerEntity) {
                world.playSound(null, pos, ModSounds.DIRECTIONAL_PANEL_BLOCK, SoundCategory.BLOCKS, 0.05f, 1.0f);
            }
        }

        super.onSteppedOn(world, pos, state, entity);
    }
}

