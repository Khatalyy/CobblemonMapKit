package com.cobblemon.khataly.modhm.event.server.custom;

import com.cobblemon.khataly.modhm.config.LevelCapConfig;
import com.cobblemon.khataly.modhm.util.LevelCapService;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedPreEvent;
import com.cobblemon.mod.common.api.events.pokemon.LevelUpEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonGainedEvent;
import com.cobblemon.mod.common.api.events.pokemon.interaction.ExperienceCandyUseEvent;

import kotlin.Unit;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Level cap enforcement:
 *  - EXP: blocco/riduzione fino al cap per qualsiasi fonte (battaglie, caramelle S/XS/L/XL ecc.).
 *  - Cattura: shiny/master possono bypassare se abilitati (pre-hit). Post-capture clamp se necessario.
 *  - Clamp di sicurezza su LevelUp e su Pokémon ottenuti fuori dalla cattura.
 *
 * Colori:
 *   - §c rosso = bloccato/limitato/clamp
 *   - §a verde = permesso/bypass/trim sicuro
 */
public final class LevelCapEnforcer {

    private LevelCapEnforcer() {}

    // === Bypass window per ricordare una cattura consentita da Master/Shiny ===
    private static final long BYPASS_WINDOW_MS = 5000L;
    private static final Map<UUID, Long> recentMasterBypass = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> recentShinyBypass  = new ConcurrentHashMap<>();

    public static void register() {
        // EXP enforcement (copre anche le caramelle tramite l'evento EXP pre)
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.HIGHEST, e -> { onExperienceGainedPre(e); return Unit.INSTANCE; });
        // Block upfront se già al cap; trimming avviene nell'evento EXP
        CobblemonEvents.EXPERIENCE_CANDY_USE_PRE.subscribe(Priority.HIGHEST, e -> { onExperienceCandyPre(e); return Unit.INSTANCE; });
        // Clamp di sicurezza
        CobblemonEvents.LEVEL_UP_EVENT.subscribe(Priority.NORMAL, e -> { onLevelUpPost(e); return Unit.INSTANCE; });

        // Gate cattura e clamp post-capture
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGHEST, e -> { onPokeballHit(e); return Unit.INSTANCE; });
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, e -> { onPokemonCaptured(e); return Unit.INSTANCE; });

        // Altri modi di ottenere Pokémon (trade, reward, ecc.)
        CobblemonEvents.POKEMON_GAINED.subscribe(Priority.NORMAL, e -> { onPokemonGained(e); return Unit.INSTANCE; });
    }

    // ==================== EXP ====================

    private static void onExperienceGainedPre(ExperienceGainedPreEvent event) {
        Object pokemon = event.getPokemon();
        ServerPlayerEntity owner = eventPlayerOrOwner(event, pokemon);
        if (owner == null) return;

        int currentLevel = getPokemonLevel(pokemon);
        int cap = LevelCapService.getEffectiveCap(owner);

        if (LevelCapService.isAtOrOverCap(currentLevel, cap)) {
            cancel(event);
            notify(owner, "§cEXP blocked: Pokémon is at level cap " + cap + ".");
            return;
        }

        Integer incomingExp = getExpAmountFromAnyExpEvent(event);
        if (incomingExp == null || incomingExp <= 0) return;

        Integer expToCap = getRemainingExpToReachLevel(pokemon, cap);
        if (expToCap == null) return;

        if (expToCap <= 0) {
            cancel(event);
            notify(owner, "§cEXP blocked: would exceed the level cap " + cap + ".");
            return;
        }

        if (incomingExp > expToCap) {
            if (setExpAmountOnAnyExpEvent(event, expToCap)) {
                notify(owner, "§aEXP trimmed to reach level cap " + cap + " without exceeding it.");
            }
        }
    }

    private static void onExperienceCandyPre(ExperienceCandyUseEvent.Pre event) {
        Object pokemon = event.getPokemon();
        ServerPlayerEntity owner = eventPlayerOrOwner(event, pokemon);
        if (owner == null) return;

        int currentLevel = getPokemonLevel(pokemon);
        int cap = LevelCapService.getEffectiveCap(owner);

        if (LevelCapService.isAtOrOverCap(currentLevel, cap)) {
            cancel(event);
            notify(owner, "§cEXP candy blocked: Pokémon is at level cap " + cap + ".");
        }
    }

    private static void onLevelUpPost(LevelUpEvent event) {
        Object pokemon = event.getPokemon();
        ServerPlayerEntity owner = eventPlayerOrOwner(event, pokemon);
        if (owner == null) return;

        int lvl = getPokemonLevel(pokemon);
        int cap = LevelCapService.getEffectiveCap(owner);

        if (lvl > cap) {
            MinecraftServer server = owner.getServer();
            if (server != null) {
                server.execute(() -> {
                    if (setPokemonLevel(pokemon, cap)) {
                        notify(owner, "§cLevel up clamped to cap (" + cap + ").");
                    } else {
                        notify(owner, "§c[LevelCap][WARN] Pokémon exceeded cap (" + lvl + " > " + cap + ").");
                    }
                });
            } else {
                notify(owner, "§c[LevelCap][WARN] Pokémon exceeded cap (" + lvl + " > " + cap + ").");
            }
        }
    }

    // ==================== CAPTURE ====================

    private static void onPokeballHit(ThrownPokeballHitEvent event) {
        var pokeBall = event.getPokeBall();
        var targetEntity = event.getPokemon();
        var ownerEntity = pokeBall.getOwner();

        if (!(ownerEntity instanceof ServerPlayerEntity player)) return;

        int lvl = getPokemonLevel(targetEntity);
        int cap = LevelCapService.getEffectiveCap(player);

        boolean shinyAllowed  = isPokemonShiny(targetEntity) && LevelCapConfig.isBypassIfShiny();
        boolean masterAllowed = LevelCapConfig.isBypassOnMasterBall() && isMasterBall(pokeBall);

        if (lvl > cap && !(shinyAllowed || masterAllowed)) {
            cancel(event);
            notify(player, "§cCapture blocked: level " + lvl + " above cap " + cap + ".");
            return;
        }

        long until = System.currentTimeMillis() + BYPASS_WINDOW_MS;
        if (lvl > cap && masterAllowed) {
            recentMasterBypass.put(player.getUuid(), until);
            notify(player, "§aCapture bypass (Master Ball): level " + lvl + " allowed.");
        } else if (lvl > cap) {
            recentShinyBypass.put(player.getUuid(), until);
            notify(player, "§aCapture bypass (Shiny Pokémon): level " + lvl + " allowed.");
        }
    }

    private static void onPokemonCaptured(PokemonCapturedEvent event) {
        Object pokemon = event.getPokemon();
        ServerPlayerEntity owner = eventPlayerOrOwner(event, pokemon);
        if (owner == null) return;

        int lvl = getPokemonLevel(pokemon);
        int cap = LevelCapService.getEffectiveCap(owner);

        boolean masterBypass = consumeIfActive(recentMasterBypass, owner.getUuid());
        boolean shinyBypass  = consumeIfActive(recentShinyBypass,  owner.getUuid());
        boolean bypass = masterBypass || shinyBypass;

        if (lvl > cap && !bypass) {
            MinecraftServer server = owner.getServer();
            if (server != null) {
                server.execute(() -> {
                    if (setPokemonLevel(pokemon, cap)) {
                        notify(owner, "§cPokémon level clamped to " + cap + ".");
                    }
                });
            }
        }
    }

    // ==================== NON-CAPTURE GAINS ====================

    private static void onPokemonGained(PokemonGainedEvent event) {
        Object pokemon = event.getPokemon();
        ServerPlayerEntity owner = eventPlayerOrOwner(event, pokemon);
        if (owner == null) return;

        int lvl = getPokemonLevel(pokemon);
        int cap = LevelCapService.getEffectiveCap(owner);

        if (lvl > cap) {
            MinecraftServer server = owner.getServer();
            if (server != null) {
                server.execute(() -> {
                    if (setPokemonLevel(pokemon, cap)) {
                        notify(owner, "§cPokémon level clamped to " + cap + ".");
                    }
                });
            }
        }
    }

    // ==================== UTIL ====================

    private static boolean consumeIfActive(Map<UUID, Long> map, UUID id) {
        Long until = map.get(id);
        long now = System.currentTimeMillis();
        if (until != null && now <= until) {
            map.remove(id);
            return true;
        }
        if (until != null) map.remove(id);
        return false;
    }

    private static void cancel(Object cancelableEvent) {
        try { cancelableEvent.getClass().getMethod("cancel").invoke(cancelableEvent); return; }
        catch (ReflectiveOperationException ignored) {}
        try { cancelableEvent.getClass().getMethod("setCanceled", boolean.class).invoke(cancelableEvent, true); }
        catch (ReflectiveOperationException ignored) {}
    }

    private static void notify(ServerPlayerEntity player, String msg) {
        if (player != null) player.sendMessage(Text.literal(msg), false);
    }

    private static ServerPlayerEntity eventPlayerOrOwner(Object event, Object pokemon) {
        for (String name : new String[]{"getPlayer", "getReceiver", "getOwner", "getTrainer", "getServerPlayer"}) {
            ServerPlayerEntity p = tryGetPlayer(event, name);
            if (p != null) return p;
        }
        return tryGetPlayer(pokemon, "getOwnerPlayer");
    }

    private static ServerPlayerEntity tryGetPlayer(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object res = m.invoke(target);
            return (res instanceof ServerPlayerEntity) ? (ServerPlayerEntity) res : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object unwrapMon(Object obj) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod("getPokemon");
            Object inner = m.invoke(obj);
            if (inner != null) return inner;
        } catch (ReflectiveOperationException ignored) {}
        return obj;
    }

    private static boolean isPokemonShiny(Object obj) {
        Object mon = unwrapMon(obj);
        if (mon == null) return false;

        try {
            Object res = mon.getClass().getMethod("isShiny").invoke(mon);
            if (res instanceof Boolean) return (Boolean) res;
        } catch (ReflectiveOperationException ignored) {}

        try {
            var f = mon.getClass().getDeclaredField("shiny");
            f.setAccessible(true);
            Object v = f.get(mon);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (ReflectiveOperationException ignored) {}

        return false;
    }

    private static int getPokemonLevel(Object obj) {
        Object mon = unwrapMon(obj);
        if (mon == null) return 1;

        try {
            Object res = mon.getClass().getMethod("getLevel").invoke(mon);
            if (res instanceof Integer) return (Integer) res;
        } catch (ReflectiveOperationException ignored) {}

        try {
            Object stats = mon.getClass().getMethod("getStats").invoke(mon);
            if (stats != null) {
                Object res = stats.getClass().getMethod("getLevel").invoke(stats);
                if (res instanceof Integer) return (Integer) res;
            }
        } catch (ReflectiveOperationException ignored) {}

        return 1;
    }

    private static boolean setPokemonLevel(Object obj, int level) {
        Object mon = unwrapMon(obj);
        if (mon == null) return false;

        try {
            mon.getClass().getMethod("setLevel", int.class).invoke(mon, level);
            return true;
        } catch (ReflectiveOperationException ignored) {}

        try {
            Object stats = mon.getClass().getMethod("getStats").invoke(mon);
            if (stats != null) {
                stats.getClass().getMethod("setLevel", int.class).invoke(stats, level);
                return true;
            }
        } catch (ReflectiveOperationException ignored) {}

        return false;
    }

    // ---------- Master Ball detection (ROBUST, solo cobblemon:master_ball) ----------

    private static boolean isMasterBall(Object pokeBallEntity) {
        if (!LevelCapConfig.isBypassOnMasterBall() || pokeBallEntity == null) return false;

        // 0) tentativi diretti sul projectile
        String quick = firstNonNull(
                tryString(pokeBallEntity, "getBallId"),
                tryString(pokeBallEntity, "getBallIdentifier"),
                tryString(pokeBallEntity, "getPokeBallId"),
                tryString(pokeBallEntity, "getIdentifier"),
                tryString(pokeBallEntity, "getName")
        );
        if (isCobblemonMasterBallId(quick)) return true;

        // 1) oggetto/nome palla annidato
        Object ballObj = firstNonNullObj(
                tryObject(pokeBallEntity, "getBall"),
                tryObject(pokeBallEntity, "getPokeBall"),
                tryObject(pokeBallEntity, "getBallType"),
                tryObject(pokeBallEntity, "getType")
        );
        if (ballObj != null) {
            String bid = firstNonNull(
                    tryString(ballObj, "getIdentifier"),
                    tryString(ballObj, "getId"),
                    tryString(ballObj, "getName"),
                    String.valueOf(ballObj)
            );
            if (isCobblemonMasterBallId(bid)) return true;

            if (ballObj instanceof Enum<?>) {
                String en = ((Enum<?>) ballObj).name();
                if (isCobblemonMasterBallId(en)) return true;
            }

            if (scanObjectForMasterBall(ballObj)) return true;
        }

        // 2) itemstack del projectile
        ItemStack stack = tryGetPokeballItemStack(pokeBallEntity);
        if (stack != null && !stack.isEmpty()) {
            String itemId = itemIdString(stack.getItem());
            if (isCobblemonMasterBallId(itemId)) return true;
            if (isCobblemonMasterBallId(String.valueOf(stack.getItem()))) return true;
        }

        // 3) fallback: nomi di classe / toString
        String cls = pokeBallEntity.getClass().getName();
        if (isCobblemonMasterBallId(cls)) return true;
        String simple = pokeBallEntity.getClass().getSimpleName();
        if (isCobblemonMasterBallId(simple)) return true;
        String ts = String.valueOf(pokeBallEntity);
        return isCobblemonMasterBallId(ts);
    }

    /** vero se "cobblemon:master_ball" o stringhe/enum che contengono master_ball */
    private static boolean isCobblemonMasterBallId(String s) {
        if (s == null || s.isBlank()) return false;
        String v = s.toLowerCase(Locale.ROOT).trim();
        return v.equals("cobblemon:master_ball")
                || v.endsWith(":master_ball")
                || v.contains("master_ball")
                || v.equals("master ball")
                || v.equals("master_ball")
                || v.endsWith(".master_ball")
                || v.endsWith("$master_ball");
    }

    private static ItemStack tryGetPokeballItemStack(Object pokeBallEntity) {
        if (pokeBallEntity == null) return null;
        for (String mName : new String[]{"getItem", "getStack", "getItemStack", "getBallStack"}) {
            try {
                Method m = pokeBallEntity.getClass().getMethod(mName);
                Object res = m.invoke(pokeBallEntity);
                if (res instanceof ItemStack) return (ItemStack) res;
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static boolean scanObjectForMasterBall(Object obj) {
        try {
            Class<?> c = obj.getClass();
            for (var f : c.getDeclaredFields()) {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v == null) continue;

                if (v instanceof CharSequence) {
                    if (isCobblemonMasterBallId(v.toString())) return true;
                } else if (v instanceof Item) {
                    String id = itemIdString((Item) v);
                    if (isCobblemonMasterBallId(id)) return true;
                } else if (v instanceof ItemStack) {
                    ItemStack st = (ItemStack) v;
                    if (!st.isEmpty() && isCobblemonMasterBallId(itemIdString(st.getItem()))) return true;
                } else if (v instanceof Enum<?>) {
                    if (isCobblemonMasterBallId(((Enum<?>) v).name())) return true;
                } else {
                    if (isCobblemonMasterBallId(String.valueOf(v))) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static String itemIdString(Item item) {
        if (item == null) return "";
        try {
            return Registries.ITEM.getId(item).toString().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return item.getClass().getName().toLowerCase(Locale.ROOT);
        }
    }

    private static Object tryObject(Object target, String... methods) {
        if (target == null) return null;
        for (String name : methods) {
            try {
                Method m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static String tryString(Object target, String... methods) {
        Object o = tryObject(target, methods);
        return (o == null) ? null : String.valueOf(o);
    }

    // ======== EXP helpers ========

    private static Integer getExpAmountFromAnyExpEvent(Object ev) {
        for (String m : new String[]{"getExperience", "getExp", "getAmount", "getExperienceAmount"}) {
            try {
                Method mm = ev.getClass().getMethod(m);
                Object val = mm.invoke(ev);
                if (val instanceof Number) return ((Number) val).intValue();
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static boolean setExpAmountOnAnyExpEvent(Object ev, int newAmount) {
        for (String m : new String[]{"setExperience", "setExp", "setAmount", "setExperienceAmount"}) {
            try {
                Method mm = ev.getClass().getMethod(m, int.class);
                mm.invoke(ev, newAmount);
                return true;
            } catch (ReflectiveOperationException ignored) {}
        }
        return false;
    }

    private static Integer getRemainingExpToReachLevel(Object pokemonObj, int targetLevel) {
        Object mon = unwrapMon(pokemonObj);
        if (mon == null) return null;

        Integer currentLevel = tryInt(mon, "getLevel");
        if (currentLevel == null) {
            Object stats = tryObject(mon, "getStats");
            if (stats != null) currentLevel = tryInt(stats, "getLevel");
        }
        if (currentLevel == null) return null;

        if (currentLevel >= targetLevel) return 0;

        Integer currentExp = firstNonNull(
                tryInt(mon, "getExperience"),
                tryInt(mon, "getExp"),
                tryInt(mon, "getTotalExperience"),
                tryInt(tryObject(mon, "getStats"), "getExperience")
        );

        Integer expForTarget = firstNonNull(
                tryInt(mon, "getExperienceForLevel", targetLevel),
                tryInt(mon, "getExpForLevel", targetLevel),
                tryInt(tryObject(mon, "getStats"), "getExperienceForLevel", targetLevel),
                tryInt(tryObject(mon, "getStats"), "getExpForLevel", targetLevel)
        );

        if (currentExp != null && expForTarget != null) {
            int diff = expForTarget - currentExp;
            return Math.max(diff, 0);
        }

        int totalNeeded = 0;
        int from = currentLevel;
        while (from < targetLevel) {
            Integer toNext = firstNonNull(
                    tryInt(mon, "getExperienceToNextLevel"),
                    tryInt(mon, "getExpToNextLevel"),
                    tryInt(tryObject(mon, "getStats"), "getExperienceToNextLevel"),
                    tryInt(tryObject(mon, "getStats"), "getExpToNextLevel")
            );
            if (toNext == null) return null;
            totalNeeded += Math.max(0, toNext);
            from++;
        }
        return totalNeeded;
    }

    private static Integer tryInt(Object target, String method) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(method);
            Object res = m.invoke(target);
            return (res instanceof Number) ? ((Number) res).intValue() : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Integer tryInt(Object target, String method, int arg) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(method, int.class);
            Object res = m.invoke(target, arg);
            return (res instanceof Number) ? ((Number) res).intValue() : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // ======== piccoli helper ========

    @SafeVarargs
    private static <T> T firstNonNull(T... vals) {
        for (T v : vals) if (v != null) return v;
        return null;
    }

    private static Object firstNonNullObj(Object... arr) {
        for (Object o : arr) if (o != null) return o;
        return null;
    }
}
