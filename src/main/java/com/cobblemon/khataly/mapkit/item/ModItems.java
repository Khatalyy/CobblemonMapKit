package com.cobblemon.khataly.mapkit.item;

import com.cobblemon.khataly.mapkit.CobblemonMapKitMod;
import com.cobblemon.khataly.mapkit.item.custom.*;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item GRASS_WAND = registerItem("grass_wand",
            new GrassWandItem(new Item.Settings().maxCount(1)));

    // === MEDAGLIE (semplici, non impilabili) ===
    public static final Item FIRE_BADGE = registerItem("fire_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    public static final Item WATER_BADGE = registerItem("water_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    public static final Item THUNDER_BADGE = registerItem("thunder_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    public static final Item GRASS_BADGE = registerItem("grass_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    public static final Item RUNNING_SHOES = registerItem("running_shoes",
            new RunningShoesItem(
                    ArmorMaterials.CHAIN,           // puoi fare un materiale custom più avanti
                    ArmorItem.Type.BOOTS,
                    new Item.Settings().maxCount(1),
                    0.50                              // +50% velocità
            ));

    public static final Item BICYCLE = registerItem("bicycle",
            new BicycleItem(new Item.Settings().maxCount(1)));

    public static final Item GHOST_BADGE = registerItem("ghost_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    public static final Item DRACO_BADGE = registerItem("draco_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    public static final Item FAIRY_BADGE = registerItem("fairy_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    public static final Item STEEL_BADGE = registerItem("steel_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    public static final Item BUG_BADGE = registerItem("bug_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));
    public static final Item DARK_BADGE = registerItem("dark_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));
    public static final Item EARTH_BADGE = registerItem("earth_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));
    public static final Item ROCK_BADGE = registerItem("rock_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));
    public static final Item PSYCHIC_BADGE = registerItem("psychic_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));
    public static final Item ICE_BADGE = registerItem("ice_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));
    public static final Item POISON_BADGE = registerItem("poison_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));
    public static final Item NORMAL_BADGE = registerItem("normal_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));
    public static final Item FLYING_BADGE = registerItem("flying_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));
    public static final Item FIGHTING_BADGE = registerItem("fighting_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    // === PORTA MEDAGLIE (semplice item, unico) ===
    public static final Item BADGE_CASE = registerItem("badge_case",
            new BadgeCaseItem(new Item.Settings().maxCount(1)));

    private static Item registerItem(String name, Item item){
        return Registry.register(Registries.ITEM, Identifier.of(CobblemonMapKitMod.MOD_ID, name), item);
    }

    public static void registerModItems(){
        CobblemonMapKitMod.LOGGER.info("Registering Mod Items for " + CobblemonMapKitMod.MOD_ID);
    }
}
