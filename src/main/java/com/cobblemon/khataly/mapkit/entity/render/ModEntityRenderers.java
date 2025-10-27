package com.cobblemon.khataly.mapkit.entity.render;

import com.cobblemon.khataly.mapkit.entity.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ModEntityRenderers {
    public static void register() {
        EntityRendererRegistry.register(ModEntities.BICYCLE, BicycleRenderer::new);
    }
}
