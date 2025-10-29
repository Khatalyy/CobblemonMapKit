package com.cobblemon.khataly.mapkit.networking.util;

public class ClientAnimationState {
    public static float rotationPerTick = 0f;
    public static int ticksRemaining = 0;

    /** opzionale: reset pulito */
    public static void reset() {
        rotationPerTick = 0f;
        ticksRemaining = 0;
    }
}
