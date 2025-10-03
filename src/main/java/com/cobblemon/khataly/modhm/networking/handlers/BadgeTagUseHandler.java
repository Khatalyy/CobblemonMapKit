package com.cobblemon.khataly.modhm.networking.handlers;

import com.cobblemon.khataly.modhm.HMMod;
import com.cobblemon.khataly.modhm.item.custom.BadgeCaseItem;
import com.cobblemon.khataly.modhm.item.custom.BadgeItem;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.sound.SoundEvents;

import java.util.Optional;

public final class BadgeTagUseHandler {
    private BadgeTagUseHandler() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(stack);
            if (stack.isEmpty()) return TypedActionResult.pass(stack);

            // Evita doppio handling per le tue BadgeItem
            if (stack.getItem() instanceof BadgeItem) return TypedActionResult.pass(stack);

            // Non è una medaglia? (controllo tag)
            if (!Registries.ITEM.getEntry(stack.getItem()).isIn(BadgeCaseItem.BADGE_TAG))
                return TypedActionResult.pass(stack);

            // Trova il case
            ItemStack caseStack = findCaseStack(sp);
            if (caseStack.isEmpty() || !(caseStack.getItem() instanceof BadgeCaseItem)) {
                sp.sendMessage(Text.translatable("msg."+ HMMod.MOD_ID +".badge_case.missing"), true);
                return TypedActionResult.pass(stack);
            }

            // ⬇️ Blocco se pieno
            if (BadgeCaseItem.isFull(caseStack)) {
                sp.sendMessage(Text.translatable("msg."+ HMMod.MOD_ID +".badge_case.full"), true);
                return TypedActionResult.pass(stack);
            }

            Identifier id = Registries.ITEM.getId(stack.getItem());
            boolean added = BadgeCaseItem.addBadge(caseStack, id);
            if (!added) return TypedActionResult.pass(stack);

            // consuma e apri GUI con animazione
            stack.decrement(1);
            var data  = BadgeCaseItem.readBadgesDataAndDecay(caseStack);
            int total = BadgeCaseItem.totalCountOrDefault(data.size());
            BadgeBoxHandler.sendOpen(sp, hand, data, total, Optional.of(id));
            sp.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 1f, 1f);

            return TypedActionResult.success(stack);
        });
    }

    private static ItemStack findCaseStack(ServerPlayerEntity sp) {
        var main = sp.getMainHandStack(); if (main.getItem() instanceof BadgeCaseItem) return main;
        var off  = sp.getOffHandStack();  if (off.getItem()  instanceof BadgeCaseItem) return off;
        for (int i = 0; i < sp.getInventory().size(); i++) {
            var s = sp.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() instanceof BadgeCaseItem) return s;
        }
        return ItemStack.EMPTY;
    }
}
