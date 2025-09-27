package com.cobblemon.khataly.modhm;

import com.cobblemon.khataly.modhm.block.ModBlocks;
import com.cobblemon.khataly.modhm.block.entity.ModBlockEntities;
import com.cobblemon.khataly.modhm.block.renderer.UltraHolePortalRenderer;
import com.cobblemon.khataly.modhm.event.client.ClientEventHandler;
import com.cobblemon.khataly.modhm.screen.ModScreenHandlers;
import com.cobblemon.khataly.modhm.screen.custom.CutScreen;
import com.cobblemon.khataly.modhm.screen.custom.RockClimbScreen;
import com.cobblemon.khataly.modhm.screen.custom.RockSmashScreen;
import com.cobblemon.khataly.modhm.screen.custom.StrengthScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class HMModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Registrazione schermate
        HandledScreens.register(ModScreenHandlers.ROCK_SMASH_SCREEN_HANDLER, RockSmashScreen::new);
        HandledScreens.register(ModScreenHandlers.CUT_SCREEN_HANDLER, CutScreen::new);
        HandledScreens.register(ModScreenHandlers.STRENGHT_SCREEN_HANDLER, StrengthScreen::new);
        HandledScreens.register(ModScreenHandlers.ROCKCLIMB_SCREEN_HANDLER, RockClimbScreen::new);

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CLIMBABLE_ROCK, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ULTRAHOLE_PORTAL, RenderLayer.getCutout());

        BlockEntityRendererFactories.register(ModBlockEntities.ULTRAHOLE_ROCK_BE, UltraHolePortalRenderer::new);
        ClientEventHandler.register();
    }
}
