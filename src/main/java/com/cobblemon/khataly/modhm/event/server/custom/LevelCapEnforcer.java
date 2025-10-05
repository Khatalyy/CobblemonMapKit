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
 * Level cap enforcement (2025-10):
 *  - EXP: blocco/riduzione fino al cap (battaglie, caramelle, ecc.).
 *  - Capture PRE-hit: se target sopra cap e senza bypass (shiny/master) → blocca la cattura.
 *    Se bypass (shiny/master), tagga la UUID dell'entità per saltare il clamp in GAINED.
 *  - Captured POST: nessun clamp (evita desync/NPE client).
 *  - Gained: clamp solo qui se sopra cap e la config lo richiede, salvo bypass shiny/master/tag.
 *  - LevelUp: safety clamp.
 */
public final class LevelCapEnforcer {

    private LevelCapEnforcer() {}

    /** Finestra extra di safety per il bypass MasterBall (fallback). */
    private static final long BYPASS_WINDOW_MS = 20000L;

    /** playerUUID -> deadline ms per bypass master (fallback temporale) */
    private static final Map<UUID, Long> recentMasterBypass = new ConcurrentHashMap<>();

    /** playerUUID -> set delle UUID delle entità catturate con bypass (tag forte, senza scadenza) */
    private static final Map<UUID, Set<UUID>> captureBypass = new ConcurrentHashMap<>();

    // =========================================================
    //                        REGISTER
    // =========================================================
    public static void register() {
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.HIGHEST, e -> { onExperienceGainedPre(e); return Unit.INSTANCE; });
        CobblemonEvents.EXPERIENCE_CANDY_USE_PRE.subscribe(Priority.HIGHEST, e -> { onExperienceCandyPre(e); return Unit.INSTANCE; });
        CobblemonEvents.LEVEL_UP_EVENT.subscribe(Priority.NORMAL, e -> { onLevelUpPost(e); return Unit.INSTANCE; });

        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGHEST, e -> { onPokeballHit(e); return Unit.INSTANCE; });
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, e -> { onPokemonCaptured(e); return Unit.INSTANCE; }); // no-op (solo per completezza/log futuro)

        CobblemonEvents.POKEMON_GAINED.subscribe(Priority.NORMAL, e -> { onPokemonGained(e); return Unit.INSTANCE; });
    }

    // =========================================================
    //                           EXP
    // =========================================================
    private static void onExperienceGainedPre(ExperienceGainedPreEvent event) {
        if (!LevelCapConfig.isEnabled()) return;
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
        if (!LevelCapConfig.isEnabled()) return;
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
        if (!LevelCapConfig.isEnabled()) return;
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

    // =========================================================
    //                       CAPTURE (PRE)
    // =========================================================
    private static void onPokeballHit(ThrownPokeballHitEvent event) {
        if (!LevelCapConfig.isEnabled()) return;

        var pokeBall = event.getPokeBall();
        var targetEntity = event.getPokemon();
        var ownerEntity = pokeBall.getOwner();
        if (!(ownerEntity instanceof ServerPlayerEntity player)) return;

        int lvl = getPokemonLevel(targetEntity);
        int cap = LevelCapService.getEffectiveCap(player);

        boolean shinyAllowed  = LevelCapConfig.isBypassIfShiny() && isPokemonShiny(targetEntity);
        boolean masterAllowed = LevelCapConfig.isBypassOnMasterBall() && isMasterBall(pokeBall);

        if (lvl > cap && !(shinyAllowed || masterAllowed)) {
            cancel(event);
            notify(player, "§cCapture blocked: level " + lvl + " above cap " + cap + ".");
            return;
        }

        // Se è stato consentito (shiny/master) e sopra cap → tagga la UUID dell'entità
        if (lvl > cap) {
            UUID targetId = getEntityUuid(targetEntity);
            if (targetId != null) {
                captureBypass.computeIfAbsent(player.getUuid(), k -> ConcurrentHashMap.newKeySet()).add(targetId);
            }
            if (masterAllowed) {
                // Fallback temporale: se per qualsiasi motivo non combaciano le UUID, teniamo anche una finestra di 20s.
                recentMasterBypass.put(player.getUuid(), System.currentTimeMillis() + BYPASS_WINDOW_MS);
                notify(player, "§aCapture bypass (Master Ball): level " + lvl + " allowed.");
            } else {
                notify(player, "§aCapture bypass (Shiny Pokémon): level " + lvl + " allowed.");
            }
        }
    }

    // =========================================================
    //                     CAPTURED (POST)
    // =========================================================
    private static void onPokemonCaptured(PokemonCapturedEvent event) {
        if (!LevelCapConfig.isEnabled()) return;
        // Intenzionalmente nessuna logica: il clamp al catturato è spostato su GAINED
        // per evitare desync/NPE lato client durante gli update.
    }

    // =========================================================
    //                  GAINED (post-capture o altro)
    // =========================================================
    private static void onPokemonGained(PokemonGainedEvent event) {
        if (!LevelCapConfig.isEnabled()) return;

        Object pokemon = event.getPokemon();
        ServerPlayerEntity owner = eventPlayerOrOwner(event, pokemon);
        if (owner == null) return;

        int lvl = getPokemonLevel(pokemon);
        int cap = LevelCapService.getEffectiveCap(owner);

        // Valuta i bypass:
        // 1) tag forte (UUID del Pokémon catturato con bypass)
        UUID monId = getPokemonUuid(pokemon);
        boolean taggedBypass = false;
        if (monId != null) {
            Set<UUID> set = captureBypass.getOrDefault(owner.getUuid(), Collections.emptySet());
            if (set.remove(monId)) {
                taggedBypass = true;
            }
            // pulizia struttura se vuota
            if (set.isEmpty()) {
                captureBypass.remove(owner.getUuid());
            }
        }

        // 2) bypass shiny da config
        boolean shinyBypass  = LevelCapConfig.isBypassIfShiny() && isPokemonShiny(pokemon);

        // 3) fallback temporale master
        boolean masterBypass = consumeMasterWindow(owner.getUuid());

        boolean skipClamp = taggedBypass || shinyBypass || masterBypass;

        if (lvl > cap && !skipClamp) {
            if (LevelCapConfig.isClampGainedOverCap()) {
                MinecraftServer server = owner.getServer();
                if (server != null) {
                    server.execute(() -> {
                        if (setPokemonLevel(pokemon, cap)) {
                            notify(owner, "§cPokémon level clamped to " + cap + ".");
                        }
                    });
                }
            } else {
                notify(owner, "§ePokémon gained over cap (" + lvl + " > " + cap + ") allowed by config; no clamp applied.");
            }
        }
    }

    // =========================================================
    //                         UTIL
    // =========================================================
    private static boolean consumeMasterWindow(UUID playerId) {
        Long until = recentMasterBypass.get(playerId);
        long now = System.currentTimeMillis();
        if (until != null && now <= until) {
            recentMasterBypass.remove(playerId);
            return true;
        }
        if (until != null) recentMasterBypass.remove(playerId);
        return false;
    }

    private static UUID getEntityUuid(Object entity) {
        if (entity == null) return null;
        try {
            Object r = entity.getClass().getMethod("getUuid").invoke(entity);
            if (r instanceof UUID u) return u;
        } catch (ReflectiveOperationException ignored) {}
        try {
            Object r = entity.getClass().getMethod("getUUID").invoke(entity);
            if (r instanceof UUID u) return u;
        } catch (ReflectiveOperationException ignored) {}
        return null;
    }

    private static UUID getPokemonUuid(Object obj) {
        Object mon = unwrapMon(obj);
        if (mon == null) return null;
        try {
            Object r = mon.getClass().getMethod("getUuid").invoke(mon);
            if (r instanceof UUID u) return u;
        } catch (ReflectiveOperationException ignored) {}
        try {
            Object r = mon.getClass().getMethod("getUUID").invoke(mon);
            if (r instanceof UUID u) return u;
        } catch (ReflectiveOperationException ignored) {}
        return null;
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

    // =========================================================
    //               Master Ball detection robusta
    // =========================================================
    private static boolean isMasterBall(Object pokeBallEntity) {
        if (!LevelCapConfig.isBypassOnMasterBall() || pokeBallEntity == null) return false;

        String quick = firstNonNull(
                tryString(pokeBallEntity, "getBallId"),
                tryString(pokeBallEntity, "getBallIdentifier"),
                tryString(pokeBallEntity, "getPokeBallId"),
                tryString(pokeBallEntity, "getIdentifier"),
                tryString(pokeBallEntity, "getName")
        );
        if (isCobblemonMasterBallId(quick)) return true;

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

        ItemStack stack = tryGetPokeballItemStack(pokeBallEntity);
        if (stack != null && !stack.isEmpty()) {
            String itemId = itemIdString(stack.getItem());
            if (isCobblemonMasterBallId(itemId)) return true;
            if (isCobblemonMasterBallId(String.valueOf(stack.getItem()))) return true;
        }

        String cls = pokeBallEntity.getClass().getName();
        if (isCobblemonMasterBallId(cls)) return true;
        String simple = pokeBallEntity.getClass().getSimpleName();
        if (isCobblemonMasterBallId(simple)) return true;
        String ts = String.valueOf(pokeBallEntity);
        return isCobblemonMasterBallId(ts);
    }

    private static boolean isCobblemonMasterBallId(String s) {
        if (s == null || s.isBlank()) return false;
        String v = s.toLowerCase(Locale.ROOT).trim();
        return v.equals("cobblemon:master_ball")
                || v.endsWith(":master_ball")
                || v.contains("master_ball")
                || v.equals("master ball")
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
                switch (v) {
                    case null -> {
                        continue;
                    }
                    case CharSequence charSequence -> {
                        if (isCobblemonMasterBallId(v.toString())) return true;
                    }
                    case Item item -> {
                        String id = itemIdString(item);
                        if (isCobblemonMasterBallId(id)) return true;
                    }
                    case ItemStack st -> {
                        if (!st.isEmpty() && isCobblemonMasterBallId(itemIdString(st.getItem()))) return true;
                    }
                    case Enum<?> en -> {
                        if (isCobblemonMasterBallId(en.name())) return true;
                    }
                    default -> {
                        if (isCobblemonMasterBallId(String.valueOf(v))) return true;
                    }
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

    // =========================================================
    //                       EXP helpers
    // =========================================================
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
