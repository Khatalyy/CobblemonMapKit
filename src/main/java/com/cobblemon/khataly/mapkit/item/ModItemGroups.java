package com.cobblemon.khataly.mapkit.item;

import com.cobblemon.khataly.mapkit.CobblemonMapKitMod;
import com.cobblemon.khataly.mapkit.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {

    public static final ItemGroup MODHM_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(CobblemonMapKitMod.MOD_ID,"mapkit_group"),
            FabricItemGroup.builder().icon(() -> new ItemStack(ModBlocks.BREAKABLE_ROCK))
                    .displayName(Text.translatable("itemgroup.mapkit.mapkit_group"))
                    .entries((displayContext, entries) -> {
                        entries.add(ModBlocks.BREAKABLE_ROCK);
                        entries.add(ModBlocks.CUTTABLE_TREE);
                        entries.add(ModBlocks.MOVABLE_ROCK);
                        entries.add(ModBlocks.CLIMBABLE_ROCK);
                        entries.add(ModBlocks.DIRECTIONAL_PANEL_BLOCK);
                        entries.add(ModItems.BADGE_CASE);
                        entries.add(ModItems.FIRE_BADGE);
                        entries.add(ModItems.WATER_BADGE);
                        entries.add(ModItems.THUNDER_BADGE);
                        entries.add(ModItems.GRASS_BADGE);
                        entries.add(ModItems.DRACO_BADGE);
                        entries.add(ModItems.GHOST_BADGE);
                        entries.add(ModItems.FAIRY_BADGE);
                        entries.add(ModItems.STEEL_BADGE);
                        entries.add(ModItems.BUG_BADGE);
                        entries.add(ModItems.DARK_BADGE);
                        entries.add(ModItems.ROCK_BADGE);
                        entries.add(ModItems.PSYCHIC_BADGE);
                        entries.add(ModItems.EARTH_BADGE);
                        entries.add(ModItems.ICE_BADGE);
                        entries.add(ModItems.POISON_BADGE);
                        entries.add(ModItems.FIGHTING_BADGE);
                        entries.add(ModItems.NORMAL_BADGE);
                        entries.add(ModItems.FLYING_BADGE);
                        entries.add(ModItems.GRASS_WAND);
                        entries.add(ModItems.RUNNING_SHOES);
                        entries.add(ModItems.BICYCLE);
                        entries.add(ModItems.WAILMER_WATERING_CAN);
                    }).build());

    public static void registerItemGroups(){
        CobblemonMapKitMod.LOGGER.info("Registering Item Groups for: " + CobblemonMapKitMod.MOD_ID);
    }

}
