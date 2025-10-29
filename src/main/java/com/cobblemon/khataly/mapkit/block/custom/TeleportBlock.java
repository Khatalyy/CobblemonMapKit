package com.cobblemon.khataly.mapkit.block.custom;

import com.cobblemon.khataly.mapkit.block.entity.custom.TeleportBlockEntity;
import com.cobblemon.khataly.mapkit.networking.manager.TeleportAnimationManager;
import com.cobblemon.khataly.mapkit.util.TeleportPairRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class TeleportBlock extends BlockWithEntity implements BlockEntityProvider {

    public static final MapCodec<TeleportBlock> CODEC = createCodec(TeleportBlock::new);
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;

    private static final VoxelShape FULL_CUBE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

    public TeleportBlock(Settings settings) {
        super(settings); // keep opaque/solid settings
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override public MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }
    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return FULL_CUBE; }
    @Override public VoxelShape getCollisionShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return FULL_CUBE; }

    /* Trigger when you WALK ON the top face of the cube */
    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (world.isClient) return;
        if (!(world instanceof ServerWorld sw)) return;
        if (!(entity instanceof ServerPlayerEntity sp)) return;
        if (sp.isSpectator()) return;

        // Anti-loop: ignore while player is still on the arrival block or within time suppress window
        if (TeleportAnimationManager.shouldIgnoreStep(sp, sw, pos)) return;

        var be = sw.getBlockEntity(pos);
        if (!(be instanceof TeleportBlockEntity tbe)) return;

        String id = tbe.getPairId();
        if (id == null) {
            sp.sendMessage(Text.literal("§eTeleport block has no pair ID."), true);
            return;
        }

        var registry = TeleportPairRegistry.get(sw);
        var target = registry.getOther(id, pos, sw.getRegistryKey());
        if (target == null) {
            sp.sendMessage(Text.literal("§eThis teleport block has no paired destination."), true);
            return;
        }

        ServerWorld targetWorld = sw.getServer().getWorld(target.dimension());
        if (targetWorld == null) {
            sp.sendMessage(Text.literal("§cTarget dimension is not loaded."), true);
            return;
        }

        // Animation + teleport: lands ABOVE the paired block (safe position search handled in manager)
        TeleportAnimationManager.queueTeleport(sp, targetWorld, target.pos());
    }

    /* Auto-pairing on place */
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!world.isClient && world instanceof ServerWorld sw) {
            var reg = TeleportPairRegistry.get(sw);
            String id = reg.addTeleport(sw, pos);
            BlockEntity be = sw.getBlockEntity(pos);
            if (be instanceof TeleportBlockEntity tbe) tbe.setPairId(id);
        }
    }

    @Nullable @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TeleportBlockEntity(pos, state);
    }
}
