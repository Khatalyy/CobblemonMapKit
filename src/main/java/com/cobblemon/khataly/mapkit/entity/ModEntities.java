package com.cobblemon.khataly.mapkit.entity;

import com.cobblemon.khataly.mapkit.CobblemonMapKitMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModEntities {
    public static EntityType<BicycleEntity> BICYCLE;

    public static void register() {
        BICYCLE = Registry.register(
                Registries.ENTITY_TYPE,
                CobblemonMapKitMod.id("bicycle"),
                EntityType.Builder
                        .<BicycleEntity>create(BicycleEntity::new, SpawnGroup.MISC)
                        .dimensions(1.2f, 1.2f)    // width, height
                        .maxTrackingRange(64)
                        .trackingTickInterval(1)
                        .build()
        );
        FabricDefaultAttributeRegistry.register(ModEntities.BICYCLE, BicycleEntity.createAttributes());
    }
}
