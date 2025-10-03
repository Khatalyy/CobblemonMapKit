package com.cobblemon.khataly.modhm.item.custom;

import com.cobblemon.khataly.modhm.HMMod;
import com.cobblemon.khataly.modhm.networking.handlers.BadgeBoxHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;

public class BadgeItem extends Item {
    public BadgeItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack badgeStack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof net.minecraft.server.network.ServerPlayerEntity sp)) {
            return TypedActionResult.success(badgeStack, world.isClient);
        }

        // trova il case ovunque
        ItemStack caseStack = BadgeInventory.findCaseStack(sp);
        if (caseStack.isEmpty() || !(caseStack.getItem() instanceof BadgeCaseItem)) {
            sp.sendMessage(Text.translatable("msg." + HMMod.MOD_ID + ".badge_case.missing"), true);
            return TypedActionResult.pass(badgeStack);
        }

        // ⬇️ Blocco se pieno
        if (BadgeCaseItem.isFull(caseStack)) {
            sp.sendMessage(Text.translatable("msg." + HMMod.MOD_ID + ".badge_case.full"), true);
            return TypedActionResult.pass(badgeStack);
        }

        Identifier badgeId = Registries.ITEM.getId(badgeStack.getItem());
        boolean added = BadgeCaseItem.addBadge(caseStack, badgeId);
        if (added) badgeStack.decrement(1);

        List<BadgeCaseItem.BadgeData> data = BadgeCaseItem.readBadgesDataAndDecay(caseStack);
        int total = Registries.ITEM.getEntryList(BadgeCaseItem.BADGE_TAG)
                .map(RegistryEntryList::size).orElse(data.size());

        Hand caseHand = BadgeInventory.handIfHeld(sp);
        if (caseHand == null) caseHand = Hand.MAIN_HAND;

        // apri GUI con animazione del badge inserito
        BadgeBoxHandler.sendOpen(sp, caseHand, data, total, Optional.of(badgeId));
        return TypedActionResult.success(badgeStack, false);
    }

    static final class BadgeInventory {
        static ItemStack findCaseStack(net.minecraft.server.network.ServerPlayerEntity sp) {
            var main = sp.getMainHandStack();
            if (main.getItem() instanceof BadgeCaseItem) return main;
            var off = sp.getOffHandStack();
            if (off.getItem() instanceof BadgeCaseItem) return off;
            for (int i = 0; i < sp.getInventory().size(); i++) {
                var s = sp.getInventory().getStack(i);
                if (!s.isEmpty() && s.getItem() instanceof BadgeCaseItem) return s;
            }
            return ItemStack.EMPTY;
        }
        static Hand handIfHeld(net.minecraft.server.network.ServerPlayerEntity sp) {
            if (sp.getMainHandStack().getItem() instanceof BadgeCaseItem) return Hand.MAIN_HAND;
            if (sp.getOffHandStack().getItem() instanceof BadgeCaseItem) return Hand.OFF_HAND;
            return null;
        }
    }
}
