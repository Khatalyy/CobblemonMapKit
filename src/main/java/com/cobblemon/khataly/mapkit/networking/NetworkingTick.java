package com.cobblemon.khataly.mapkit.networking;

import com.cobblemon.khataly.mapkit.networking.manager.ClimbManager;
import com.cobblemon.khataly.mapkit.networking.manager.RestoreManager;
import net.minecraft.server.MinecraftServer;

public class NetworkingTick {
    public static void tick(MinecraftServer server) {
        RestoreManager.get().tick(server);
        ClimbManager.get().tick();
    }
}
