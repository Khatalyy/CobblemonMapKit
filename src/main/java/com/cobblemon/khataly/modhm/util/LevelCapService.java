package com.cobblemon.khataly.modhm.util;

import com.cobblemon.khataly.modhm.config.LevelCapConfig;
import com.cobblemon.khataly.modhm.config.PlayerLevelCapProgress;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Set;

public final class LevelCapService {
    private LevelCapService() {}

    /** Effective cap = max(baseCap, qualsiasi cap sbloccato dal player via LABEL). */
    public static int getEffectiveCap(ServerPlayerEntity player) {
        int cap = LevelCapConfig.getBaseCap();
        // labelLower -> cap
        Map<String, Integer> labelsCaps = LevelCapConfig.getAllLabelsWithCaps();
        // set di labelLower applicate al player
        Set<String> applied = PlayerLevelCapProgress.getApplied(player.getUuid());

        for (String labelLower : applied) {
            Integer v = labelsCaps.get(labelLower);
            if (v != null && v > cap) cap = v;
        }
        return cap;
    }

    /** true se (solo per la fase di cattura) gli shiny ignorano il cap. */
    public static boolean isShinyBypassOnCapture() {
        return LevelCapConfig.isBypassIfShiny();
    }

    public static boolean isAtOrOverCap(int currentLevel, int cap) {
        return currentLevel >= cap;
    }
}
