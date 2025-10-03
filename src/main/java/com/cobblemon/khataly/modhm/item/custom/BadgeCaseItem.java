package com.cobblemon.khataly.modhm.item.custom;

import com.cobblemon.khataly.modhm.HMMod;
import com.cobblemon.khataly.modhm.networking.handlers.BadgeBoxHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.*;

/**
 * Badge Case che salva una lista di badge con livello di lucidatura (shine) e timestamp ultimo update.
 * Formato NBT (CUSTOM_DATA):
 * Badges: List of Compound { id:String, shine:int(0..100), last:long(ms) }
 */
public class BadgeCaseItem extends Item {

    /** data/<modid>/tags/item/badges.json */
    public static final TagKey<Item> BADGE_TAG =
            TagKey.of(RegistryKeys.ITEM, Identifier.of(HMMod.MOD_ID, "badges"));

    /** Cap fisso come la griglia 2x4 */
    public static final int MAX_SLOTS = 8;

    private static final String NBT_BADGES = "Badges";
    private static final String KEY_ID   = "id";
    private static final String KEY_SH   = "shine";
    private static final String KEY_LAST = "last";

    /** Decadimento offline: ~20 minuti per passare da 100 → 0 */
    private static final long  FULL_DECAY_MS = 20L * 60L * 1000L;
    private static final float DECAY_PER_SEC = 100f / (FULL_DECAY_MS / 1000f);

    /** DTO immutabile per un badge nel case. */
    public record BadgeData(Identifier id, int shine, long last) {}

    public BadgeCaseItem(Settings settings) { super(settings); }

    /** Click destro → apri GUI (server → client) */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack caseStack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            // Applica decadimento “offline” quando apri
            List<BadgeData> data = readBadgesDataAndDecay(caseStack);
            int total = totalCountOrDefault(data.size());
            BadgeBoxHandler.sendOpen(sp, hand, data, total, Optional.empty());
        }
        return TypedActionResult.success(caseStack, world.isClient);
    }

    /** Tooltip: conta + (in ADVANCED) elenco con % shine */
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        List<BadgeData> data  = readBadgesDataAndDecay(stack);
        int total = 8;
        long collected = data.stream().filter(d -> d.id() != null).count();

        tooltip.add(Text.translatable("tooltip." + HMMod.MOD_ID + ".badge_case.count", collected, total));

        if (type == TooltipType.ADVANCED) {
            for (BadgeData d : data) {
                if (d.id() == null) continue;
                Item item = Registries.ITEM.get(d.id());
                MutableText name = new ItemStack(item).getName().copy();
                tooltip.add(Text.literal("• ").append(name).append(Text.literal("  (" + d.shine() + "%)")));
            }
        } else {
            tooltip.add(Text.translatable("tooltip." + HMMod.MOD_ID + ".badge_case.hint"));
        }
    }

    /* ===================== API pubblica (usata da handlers / utility) ===================== */

    /** Ritorna true se il box ha già 8 badge. */
    public static boolean isFull(ItemStack caseStack) {
        int count = 0;
        for (BadgeData b : readBadgesData(caseStack)) { // no decay, no scritture
            if (b.id() != null) count++;
            if (count >= MAX_SLOTS) return true;
        }
        return false;
    }

    /** Inserisce un badge se non già presente (shine=0) e se non è pieno. */
    public static boolean addBadge(ItemStack caseStack, Identifier id) {
        // hard cap
        if (isFull(caseStack)) return false;

        long now = System.currentTimeMillis();
        List<BadgeData> data = readBadgesDataAndDecay(caseStack);
        boolean already = data.stream().anyMatch(b -> id.equals(b.id()));
        if (already) return false;

        data.add(new BadgeData(id, 0, now));
        writeBadgesData(caseStack, data);
        return true;
    }

    /** Lucida un badge: +amount (clamp 1..10 lato call, qui clamp 0..100) e aggiorna last=now. */
    public static boolean polish(ItemStack caseStack, Identifier id, int amount) {
        long now = System.currentTimeMillis();
        List<BadgeData> data = readBadgesDataAndDecay(caseStack);
        boolean changed = false;
        for (int i = 0; i < data.size(); i++) {
            BadgeData b = data.get(i);
            if (id.equals(b.id())) {
                int newSh = Math.min(100, Math.max(0, b.shine() + amount));
                if (newSh != b.shine()) changed = true;
                data.set(i, new BadgeData(b.id(), newSh, now));
                break;
            }
        }
        if (changed) writeBadgesData(caseStack, data);
        return changed;
    }

    /** Rimuove un badge se presente. */
    public static boolean remove(ItemStack caseStack, Identifier id) {
        List<BadgeData> data = readBadgesDataAndDecay(caseStack);
        boolean removed = data.removeIf(b -> id.equals(b.id()));
        if (removed) writeBadgesData(caseStack, data);
        return removed;
    }

    /** Ritorna i dati applicando decadimento (e scrivendo eventuali cambi nel NBT). */
    public static List<BadgeData> readBadgesDataAndDecay(ItemStack stack) {
        List<BadgeData> list = readBadgesDataRaw(stack);
        long now = System.currentTimeMillis();
        boolean changed = false;
        ArrayList<BadgeData> out = new ArrayList<>(list.size());

        for (BadgeData b : list) {
            if (b.id() == null || b.shine() <= 0) { out.add(b); continue; }
            long elapsedMs = Math.max(0, now - Math.max(0, b.last()));
            if (elapsedMs <= 0) { out.add(b); continue; }
            int drop = Math.max(0, Math.round((elapsedMs / 1000f) * DECAY_PER_SEC));
            int newSh = Math.max(0, b.shine() - drop);
            if (newSh != b.shine()) changed = true;
            out.add(new BadgeData(b.id(), newSh, now)); // avanza “last”
        }
        if (changed) writeBadgesData(stack, out);
        return out;
    }

    /** SOLO LETTURA: ritorna una copia dei dati senza applicare decadimento e senza scrivere NBT. */
    public static List<BadgeData> readBadgesData(ItemStack stack) {
        return new ArrayList<>(readBadgesDataRaw(stack));
    }

    /** SOLO GLI ID dei badge presenti, senza decadimento. */
    public static Set<Identifier> readBadges(ItemStack stack) {
        LinkedHashSet<Identifier> out = new LinkedHashSet<>();
        for (BadgeData b : readBadgesDataRaw(stack)) {
            if (b.id() != null) out.add(b.id());
        }
        return out;
    }

    /** Per tooltips/fallback: tenta la size del tag dei badge, altrimenti usa fallback. */
    public static int totalCountOrDefault(int fallback) {
        return Registries.ITEM.getEntryList(BADGE_TAG).map(RegistryEntryList::size).orElse(fallback);
    }

    /* ===================== NBT (low-level) ===================== */

    private static List<BadgeData> readBadgesDataRaw(ItemStack stack) {
        NbtCompound tag = readCustomTag(stack);
        ArrayList<BadgeData> out = new ArrayList<>();
        if (!tag.contains(NBT_BADGES, NbtElement.LIST_TYPE)) return out;

        NbtList list = tag.getList(NBT_BADGES, NbtElement.COMPOUND_TYPE);

        // Compat col vecchio formato (lista di stringhe)
        if (list.isEmpty() || list.getHeldType() == NbtElement.STRING_TYPE) {
            long now = System.currentTimeMillis();
            NbtList newList = new NbtList();
            for (int i = 0; i < list.size(); i++) {
                Identifier id = Identifier.tryParse(list.getString(i));
                if (id == null) continue;
                out.add(new BadgeData(id, 0, now));
                NbtCompound c = new NbtCompound();
                c.putString(KEY_ID, id.toString());
                c.putInt(KEY_SH, 0);
                c.putLong(KEY_LAST, now);
                newList.add(c);
            }
            tag.put(NBT_BADGES, newList);
            writeCustomTag(stack, tag);
            return out;
        }

        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            Identifier id = c.contains(KEY_ID) ? Identifier.tryParse(c.getString(KEY_ID)) : null;
            int sh  = c.contains(KEY_SH)   ? Math.max(0, Math.min(100, c.getInt(KEY_SH))) : 0;
            long ls = c.contains(KEY_LAST) ? c.getLong(KEY_LAST) : System.currentTimeMillis();
            if (id != null) out.add(new BadgeData(id, sh, ls));
        }
        return out;
    }

    public static void writeBadgesData(ItemStack stack, List<BadgeData> data) {
        NbtCompound tag = readCustomTag(stack);
        NbtList list = new NbtList();
        for (BadgeData b : data) {
            if (b.id() == null) continue;
            NbtCompound c = new NbtCompound();
            c.putString(KEY_ID, b.id().toString());
            c.putInt(KEY_SH, Math.max(0, Math.min(100, b.shine())));
            c.putLong(KEY_LAST, b.last());
            list.add(c);
        }
        tag.put(NBT_BADGES, list);
        writeCustomTag(stack, tag);
    }

    private static NbtCompound readCustomTag(ItemStack stack) {
        NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        return comp.copyNbt();
    }

    private static void writeCustomTag(ItemStack stack, NbtCompound newTag) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, tag -> tag.copyFrom(newTag));
    }
}
