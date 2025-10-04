package com.cobblemon.khataly.modhm.item;

import com.cobblemon.khataly.modhm.HMMod;
import com.cobblemon.khataly.modhm.item.custom.BadgeCaseItem;
import com.cobblemon.khataly.modhm.item.custom.BadgeItem;
import com.cobblemon.khataly.modhm.item.custom.GrassWandItem;
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

    public static final Item GHOST_BADGE = registerItem("ghost_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    public static final Item DRACO_BADGE = registerItem("draco_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    public static final Item FAIRY_BADGE = registerItem("fairy_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    public static final Item STEEL_BADGE = registerItem("steel_badge",
            new BadgeItem(new Item.Settings().maxCount(1)));

    // === PORTA MEDAGLIE (semplice item, unico) ===
    public static final Item BADGE_CASE = registerItem("badge_case",
            new BadgeCaseItem(new Item.Settings().maxCount(1)));

    private static Item registerItem(String name, Item item){
        return Registry.register(Registries.ITEM, Identifier.of(HMMod.MOD_ID, name), item);
    }

    public static void registerModItems(){
        HMMod.LOGGER.info("Registering Mod Items for " + HMMod.MOD_ID);
    }
}
