package com.cobblemon.khataly.modhm.item;

import com.cobblemon.khataly.modhm.HMMod;
import com.cobblemon.khataly.modhm.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {

    public static final ItemGroup MODHM_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(HMMod.MOD_ID,"modhm_group"),
            FabricItemGroup.builder().icon(() -> new ItemStack(ModBlocks.BREAKABLE_ROCK))
                    .displayName(Text.translatable("itemgroup.modhm.modhm_group"))
                    .entries((displayContext, entries) -> {
                        entries.add(ModBlocks.BREAKABLE_ROCK);
                        entries.add(ModBlocks.CUTTABLE_TREE);
                        entries.add(ModBlocks.MOVABLE_ROCK);
                        entries.add(ModBlocks.CLIMBABLE_ROCK);
                        entries.add(ModItems.BADGE_CASE);
                        entries.add(ModItems.FIRE_BADGE);
                        entries.add(ModItems.WATER_BADGE);
                        entries.add(ModItems.THUNDER_BADGE);
                        entries.add(ModItems.GRASS_BADGE);
                        entries.add(ModItems.DRACO_BADGE);
                        entries.add(ModItems.GHOST_BADGE);
                        entries.add(ModItems.FAIRY_BADGE);
                        entries.add(ModItems.STEEL_BADGE);
                        entries.add(ModItems.GRASS_WAND);
                    }).build());

    public static void registerItemGroups(){
        HMMod.LOGGER.info("Registering Item Groups for: " + HMMod.MOD_ID);
    }

}
