package com.cobblemon.khataly.mapkit.item.custom;

import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.entity.EquipmentSlot;

public class WailmerWateringCanItem extends Item {

    // --- Tuning ---
    private static final int USE_TICKS_MAX = 72000;          // tieni premuto
    private static final int BONEMEAL_INTERVAL_TICKS = 8;    // ~0.4s
    private static final int GROW_ATTEMPTS_PER_PULSE = 18;
    private static final float GROW_CHANCE = 0.7f;
    private static final int RADIUS = 3;
    private static final boolean DAMAGE_ON_PULSE = true;

    public WailmerWateringCanItem(Settings settings) {
        super(settings);
    }

    // Firma nuova: (ItemStack, LivingEntity)
    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return USE_TICKS_MAX;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        // animazione continua “brush”, visivamente rende bene come irrigazione
        return UseAction.BRUSH;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        user.setCurrentHand(hand);

        if (!world.isClient) {
            world.playSound(null, user.getBlockPos(),
                    SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.PLAYERS, 0.6f, 1.2f);
        }
        return TypedActionResult.consume(stack);
    }

    // Firma nuova
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient) {
            world.playSound(null, user.getBlockPos(),
                    SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.PLAYERS, 0.35f, 1.7f);
        }
    }

    // Firma nuova
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return;

        // Particelle lato client
        if (world.isClient) {
            spawnWaterParticlesClient(world, player);
        }

        // Crescita lato server a impulsi
        if (!world.isClient) {
            int usedTicks = getMaxUseTime(stack, user) - remainingUseTicks;
            if (usedTicks % BONEMEAL_INTERVAL_TICKS == 0) {
                ServerWorld server = (ServerWorld) world;

                // raycast con la utility di Item e le firme nuove
                BlockHitResult hit = Item.raycast(world, player, RaycastContext.FluidHandling.NONE);
                BlockPos center = (hit != null && hit.getType() == HitResult.Type.BLOCK)
                        ? hit.getBlockPos()
                        : player.getBlockPos();

                Box area = new Box(center).expand(RADIUS);
                Random rng = server.getRandom();
                int successful = 0;

                for (int i = 0; i < GROW_ATTEMPTS_PER_PULSE; i++) {
                    BlockPos pos = randomPosInBox(rng, area);
                    BlockState state = server.getBlockState(pos);

                    if (state.getBlock() instanceof Fertilizable fert) {
                        if (fert.isFertilizable(server, pos, state)
                                && fert.canGrow(server, rng, pos, state)
                                && rng.nextFloat() < GROW_CHANCE) {

                            fert.grow(server, rng, pos, state);
                            successful++;

                            server.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                    6, 0.25, 0.25, 0.25, 0.02);
                        }
                    }
                }

                if (successful > 0) {
                    server.playSound(null, center, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                            SoundCategory.BLOCKS, 0.35f, 1.8f);
                    if (DAMAGE_ON_PULSE && !player.isCreative()) {
                        // Firma nuova: (amount, entity, slot)
                        stack.damage(1, player, EquipmentSlot.MAINHAND);
                    }
                }
            }
        }
    }

    // === Utils ===

    private static BlockPos randomPosInBox(Random rng, Box box) {
        int x = (int)Math.floor(box.minX) + rng.nextBetween(0, (int)Math.ceil(box.getLengthX()) - 1);
        int y = (int)Math.floor(box.minY) + rng.nextBetween(0, (int)Math.ceil(box.getLengthY()) - 1);
        int z = (int)Math.floor(box.minZ) + rng.nextBetween(0, (int)Math.ceil(box.getLengthZ()) - 1);
        return new BlockPos(x, y, z);
    }

    private static void spawnWaterParticlesClient(World world, PlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVector(player.getPitch(), player.getYaw());
        Vec3d origin = eye.add(dir.multiply(0.6));

        for (int i = 0; i < 8; i++) {
            double ox = origin.x + (world.random.nextDouble() - 0.5) * 0.2;
            double oy = origin.y - 0.2 + (world.random.nextDouble() * 0.1);
            double oz = origin.z + (world.random.nextDouble() - 0.5) * 0.2;

            double vx = dir.x * 0.1 + (world.random.nextDouble() - 0.5) * 0.02;
            double vy = -0.03 + (world.random.nextDouble() - 0.5) * 0.01;
            double vz = dir.z * 0.1 + (world.random.nextDouble() - 0.5) * 0.02;

            world.addParticle(ParticleTypes.SPLASH, ox, oy, oz, vx, vy, vz);
            world.addParticle(ParticleTypes.FALLING_WATER, ox, oy, oz, 0, -0.06, 0);
        }
    }
}
