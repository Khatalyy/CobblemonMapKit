package com.cobblemon.khataly.mapkit.entity.model;

import com.cobblemon.khataly.mapkit.CobblemonMapKitMod;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

public class ModModelLayers {
    public static final EntityModelLayer BICYCLE =
            new EntityModelLayer( Identifier.of(CobblemonMapKitMod.MOD_ID, "bicycle"), "main");
}
