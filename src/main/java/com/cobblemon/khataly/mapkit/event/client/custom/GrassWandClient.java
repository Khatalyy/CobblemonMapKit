package com.cobblemon.khataly.mapkit.event.client.custom;

import com.cobblemon.khataly.mapkit.item.ModItems;
import com.cobblemon.khataly.mapkit.networking.packet.grasszones.PlaceGrassC2SPacket;
import com.cobblemon.khataly.mapkit.networking.packet.grasszones.RequestZonesC2SPacket;
import com.cobblemon.khataly.mapkit.networking.packet.grasszones.GrassZonesSyncS2CPacket;
import com.cobblemon.khataly.mapkit.util.GrassZonesClientCache;
import com.cobblemon.khataly.mapkit.util.RenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

@Environment(EnvType.CLIENT)
public class GrassWandClient {
    private static BlockPos startPos = null;
    private static BlockPos curPos = null;
    private static boolean selecting = false;

    // refresh zones every ~1s while holding the wand
    private static int zonesRefreshCooldown = 0;

    public static void register() {
        // Tick: handle selection + on-demand zones request when holding wand
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            boolean holdingWand = isHoldingWand(client);

            // Request zones snapshot every 20 ticks while holding the wand
            if (holdingWand) {
                if (zonesRefreshCooldown <= 0) {
                    ClientPlayNetworking.send(new RequestZonesC2SPacket());
                    zonesRefreshCooldown = 20; // ~1 second
                } else {
                    zonesRefreshCooldown--;
                }
            } else {
                zonesRefreshCooldown = 0;
            }

            // Press & hold selection (uses "using item" while holding the wand)
            boolean using = holdingWand && client.player.isUsingItem();

            if (using) {
                if (!selecting) {
                    startPos = rayBlock(client);
                    selecting = startPos != null;
                }
                curPos = rayBlock(client);
            } else {
                // on release: send once
                if (selecting && startPos != null && curPos != null) {
                    ClientPlayNetworking.send(new PlaceGrassC2SPacket(startPos, curPos));
                }
                selecting = false;
                startPos = null;
                curPos = null;
            }
        });

        // Rendering: draw saved zones (yellow) + current selection (blue)
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            var mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;

            MatrixStack matrices = ctx.matrixStack();
            var providers = ctx.consumers();
            if (providers == null || matrices == null) return;

            var cam = ctx.camera();
            double camX = cam.getPos().x;
            double camY = cam.getPos().y;
            double camZ = cam.getPos().z;

            // 1) Draw all saved zones (YELLOW) when holding the wand
            if (isHoldingWand(mc)) {
                var worldKeyStr = mc.world.getRegistryKey().getValue().toString();

                for (GrassZonesSyncS2CPacket.ZoneDto z : GrassZonesClientCache.getZones()) {
                    if (!z.worldKey().equals(worldKeyStr)) continue;

                    double minX = Math.min(z.minX(), z.maxX());
                    double maxX = Math.max(z.minX(), z.maxX()) + 1;
                    double minZ = Math.min(z.minZ(), z.maxZ());
                    double maxZ = Math.max(z.minZ(), z.maxZ()) + 1;

                    // NUOVO: range verticale completo
                    double minY = Math.min(z.minY(), z.maxY());
                    double maxY = Math.max(z.minY(), z.maxY()) + 1;

                    Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ)
                            .offset(-camX, -camY, -camZ);

                    // Yellow overlay (transparent fill + strong outline)
                    RenderUtils.drawFilledBox(matrices, providers, box, 1f, 1f, 0f, 0.12f);
                    RenderUtils.drawOutlineBox(matrices, box, 1f, 0.9f, 0f, 0.95f);
                }
            }

            // 2) Draw current selection (BLUE), if any
            if (selecting && startPos != null && curPos != null) {
                // Box 3D dai due corner selezionati (+1 su max per coprire il voxel intero)
                double minX = Math.min(startPos.getX(), curPos.getX());
                double minY = Math.min(startPos.getY(), curPos.getY());
                double minZ = Math.min(startPos.getZ(), curPos.getZ());
                double maxX = Math.max(startPos.getX(), curPos.getX()) + 1;
                double maxY = Math.max(startPos.getY(), curPos.getY()) + 1;
                double maxZ = Math.max(startPos.getZ(), curPos.getZ()) + 1;

                Box sel = new Box(minX, minY, minZ, maxX, maxY, maxZ)
                        .offset(-camX, -camY, -camZ);

                // Blue overlay for the live selection
                RenderUtils.drawFilledBox(matrices, providers, sel, 0f, 0.4f, 1f, 0.25f);
                RenderUtils.drawOutlineBox(matrices, sel, 0f, 0.6f, 1f, 0.9f);
            }
        });
    }

    /** True if the player is holding the GRASS_WAND in main or off hand. */
    private static boolean isHoldingWand(MinecraftClient client) {
        assert client.player != null;
        ItemStack main = client.player.getMainHandStack();
        ItemStack off  = client.player.getOffHandStack();
        return (!main.isEmpty() && main.getItem() == ModItems.GRASS_WAND)
                || (!off.isEmpty() && off.getItem() == ModItems.GRASS_WAND);
    }

    private static BlockPos rayBlock(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof BlockHitResult bhr) return bhr.getBlockPos();
        return null; // no selection if you are not targeting a block
    }
}
