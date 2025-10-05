package com.cobblemon.khataly.modhm.event.client.custom;

import com.cobblemon.khataly.modhm.item.ModItems;
import com.cobblemon.khataly.modhm.networking.packet.PlaceGrassC2SPacket;
import com.cobblemon.khataly.modhm.util.RenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

@Environment(EnvType.CLIENT)
public class GrassWandClient {
    private static BlockPos startPos = null;
    private static BlockPos curPos = null;
    private static boolean selecting = false;

    public static void register() {
        // Logica press&hold + invio packet al rilascio
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            boolean holdingWand = client.player.getActiveItem() != null
                    && client.player.getActiveItem().getItem() == ModItems.GRASS_WAND;

            boolean using = holdingWand && client.player.isUsingItem();

            if (using) {
                if (!selecting) {
                    startPos = rayBlock(client);
                    selecting = startPos != null;
                }
                curPos = rayBlock(client);
            } else {
                // rilascio: invia packet una volta
                if (selecting && startPos != null && curPos != null) {
                    ClientPlayNetworking.send(new PlaceGrassC2SPacket(startPos, curPos));
                }
                selecting = false;
                startPos = null;
                curPos = null;
            }
        });

        // Overlay blu semitrasparente
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            if (!selecting || startPos == null || curPos == null) return;

            MatrixStack matrices = ctx.matrixStack();
            var cam = ctx.camera();
            double camX = cam.getPos().x;
            double camY = cam.getPos().y;
            double camZ = cam.getPos().z;

            // Box allineata ai blocchi (+1 sugli estremi per coprire tutto il voxel)
            double minX = Math.min(startPos.getX(), curPos.getX());
            double minY = Math.min(startPos.getY(), curPos.getY());
            double minZ = Math.min(startPos.getZ(), curPos.getZ());
            double maxX = Math.max(startPos.getX(), curPos.getX()) + 1;
            double maxY = Math.max(startPos.getY(), curPos.getY()) + 1;
            double maxZ = Math.max(startPos.getZ(), curPos.getZ()) + 1;

            // Se vuoi solo XZ su un piano Y fisso, blocca minY/maxY a (y, y+1)
            // qui la teniamo 1-di-spessore intorno al piano selezionato:
            // prendi il piano più basso dei due
            Box box = new Box(minX, minY, minZ, maxX, minY + 1, maxZ)
                    .offset(-camX, -camY, -camZ);

            var providers = ctx.consumers();
            if (providers == null) return;
            assert matrices != null;
            RenderUtils.drawFilledBox(matrices, providers, box, 0f, 0.4f, 1f, 0.25f);
            RenderUtils.drawOutlineBox(matrices, box, 0f, 0.6f, 1f, 0.9f);
        });
    }

    private static BlockPos rayBlock(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof BlockHitResult bhr) return bhr.getBlockPos();
        return null; // niente selezione se non colpisci blocchi
    }
}
