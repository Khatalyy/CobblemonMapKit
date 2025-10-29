package com.cobblemon.khataly.mapkit;

import com.cobblemon.khataly.mapkit.block.ModBlocks;
import com.cobblemon.khataly.mapkit.block.entity.ModBlockEntities;
import com.cobblemon.khataly.mapkit.block.renderer.UltraHolePortalRenderer;
import com.cobblemon.khataly.mapkit.entity.ModEntities;
import com.cobblemon.khataly.mapkit.entity.model.BicycleEntityModel;
import com.cobblemon.khataly.mapkit.entity.model.ModModelLayers;
import com.cobblemon.khataly.mapkit.entity.render.BicycleRenderer;
import com.cobblemon.khataly.mapkit.entity.render.ModEntityRenderers;
import com.cobblemon.khataly.mapkit.event.client.ClientEventHandler;
import com.cobblemon.khataly.mapkit.item.ModItems;
import com.cobblemon.khataly.mapkit.networking.handlers.BadgeBoxClientHandler;
import com.cobblemon.khataly.mapkit.networking.packet.RotatePlayerS2CPacket;
import com.cobblemon.khataly.mapkit.networking.util.ClientAnimationState;
import com.cobblemon.khataly.mapkit.networking.util.GrassNetworkingInit;
import com.cobblemon.khataly.mapkit.screen.ModScreenHandlers;
import com.cobblemon.khataly.mapkit.screen.custom.CutScreen;
import com.cobblemon.khataly.mapkit.screen.custom.RockClimbScreen;
import com.cobblemon.khataly.mapkit.screen.custom.RockSmashScreen;
import com.cobblemon.khataly.mapkit.screen.custom.StrengthScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class CobblemonMapKitModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Registrazione schermate
        HandledScreens.register(ModScreenHandlers.ROCK_SMASH_SCREEN_HANDLER, RockSmashScreen::new);
        HandledScreens.register(ModScreenHandlers.CUT_SCREEN_HANDLER, CutScreen::new);
        HandledScreens.register(ModScreenHandlers.STRENGHT_SCREEN_HANDLER, StrengthScreen::new);
        HandledScreens.register(ModScreenHandlers.ROCKCLIMB_SCREEN_HANDLER, RockClimbScreen::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CLIMBABLE_ROCK, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ULTRAHOLE_PORTAL, RenderLayer.getCutout());
        BadgeBoxClientHandler.register();
        BlockEntityRendererFactories.register(ModBlockEntities.ULTRAHOLE_ROCK_BE, UltraHolePortalRenderer::new);
        ClientEventHandler.register();
        GrassNetworkingInit.registerReceivers();

        ArmorRenderer invisibleBoots = (matrices, vertexConsumers, stack, entity, slot, light, model) -> {
            // Intenzionalmente vuoto: non renderizzare niente
        };
        ArmorRenderer.register(invisibleBoots, ModItems.RUNNING_SHOES);

        ModEntityRenderers.register();
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.BICYCLE, BicycleEntityModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.BICYCLE, BicycleRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(RotatePlayerS2CPacket.ID, (payload, context) -> {
            float total = payload.totalRotation();
            int ticks = Math.max(1, payload.durationTicks());
            context.client().execute(() -> {
                ClientAnimationState.rotationPerTick = total / ticks;
                ClientAnimationState.ticksRemaining = ticks;
            });
        });

        // Applica la rotazione OGNI TICK client (lineare)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (ClientAnimationState.ticksRemaining > 0 && client.player != null) {
                float delta = ClientAnimationState.rotationPerTick;
                var p = client.player;
                p.setYaw(p.getYaw() + delta);
                p.setHeadYaw(p.getHeadYaw() + delta); // (consigliato) sincronizza la testa
                ClientAnimationState.ticksRemaining--;
                if (ClientAnimationState.ticksRemaining == 0) {
                    ClientAnimationState.rotationPerTick = 0f;
                }
            }
        });
    }
}
