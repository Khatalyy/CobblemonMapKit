package com.cobblemon.khataly.modhm.networking.handlers;

import com.cobblemon.khataly.modhm.networking.packet.badgebox.OpenBadgeBoxS2CPacket;
import com.cobblemon.khataly.modhm.networking.packet.badgebox.SyncBadgeBoxS2CPacket;
import com.cobblemon.khataly.modhm.screen.custom.BadgeCaseScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;

public final class BadgeBoxClientHandler {
    private BadgeBoxClientHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(OpenBadgeBoxS2CPacket.ID, (payload, ctx) -> {
            ctx.client().execute(() -> {
                var mc = MinecraftClient.getInstance();
                if (mc.player == null) return;

                List<ItemStack> stacks = new ArrayList<>(((OpenBadgeBoxS2CPacket) payload).badges().size());
                List<Integer> shines = new ArrayList<>(((OpenBadgeBoxS2CPacket) payload).badges().size());
                ((OpenBadgeBoxS2CPacket) payload).badges().forEach(e -> {
                    Item it = Registries.ITEM.get(e.id());
                    stacks.add(new ItemStack(it));
                    shines.add(e.shine());
                });

                var screen = new BadgeCaseScreen(((OpenBadgeBoxS2CPacket) payload).handUsed(), stacks, shines, ((OpenBadgeBoxS2CPacket) payload).totalSlots());

                // *** IMPORTANTE: animazione PRIMA di setScreen ***
                ((OpenBadgeBoxS2CPacket) payload).animInsertedId().ifPresent(screen::queueInsertAnimation);

                mc.setScreen(screen);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncBadgeBoxS2CPacket.ID, (payload, ctx) -> {
            ctx.client().execute(() -> {
                if (MinecraftClient.getInstance().currentScreen instanceof BadgeCaseScreen screen) {

                    List<ItemStack> stacks = new ArrayList<>(((SyncBadgeBoxS2CPacket) payload).badges().size());
                    List<Integer> shines = new ArrayList<>(((SyncBadgeBoxS2CPacket) payload).badges().size());
                    ((SyncBadgeBoxS2CPacket) payload).badges().forEach(e -> {
                        Item it = Registries.ITEM.get(e.id());
                        stacks.add(new ItemStack(it));
                        shines.add(e.shine());
                    });

                    screen.applySync(stacks, shines, ((SyncBadgeBoxS2CPacket) payload).totalSlots());
                }
            });
        });
    }
}
