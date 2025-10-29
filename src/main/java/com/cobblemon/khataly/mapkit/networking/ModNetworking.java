package com.cobblemon.khataly.mapkit.networking;

import com.cobblemon.khataly.mapkit.networking.handlers.*;
import com.cobblemon.khataly.mapkit.networking.packet.*;
import com.cobblemon.khataly.mapkit.networking.packet.badgebox.EjectBadgeC2SPacket;
import com.cobblemon.khataly.mapkit.networking.packet.badgebox.OpenBadgeBoxS2CPacket;
import com.cobblemon.khataly.mapkit.networking.packet.badgebox.SyncBadgeBoxS2CPacket;
import com.cobblemon.khataly.mapkit.networking.packet.badgebox.PolishBadgeC2SPacket;
import com.cobblemon.khataly.mapkit.networking.packet.cut.CutPacketC2S;
import com.cobblemon.khataly.mapkit.networking.packet.flash.FlashMenuC2SPacket;
import com.cobblemon.khataly.mapkit.networking.packet.flash.FlashMenuS2CPacket;
import com.cobblemon.khataly.mapkit.networking.packet.flash.FlashPacketC2S;
import com.cobblemon.khataly.mapkit.networking.packet.fly.FlyMenuC2SPacket;
import com.cobblemon.khataly.mapkit.networking.packet.fly.FlyMenuS2CPacket;
import com.cobblemon.khataly.mapkit.networking.packet.fly.FlyPacketC2S;
import com.cobblemon.khataly.mapkit.networking.packet.grasszones.GrassZonesSyncS2CPacket;
import com.cobblemon.khataly.mapkit.networking.packet.grasszones.PlaceGrassC2SPacket;
import com.cobblemon.khataly.mapkit.networking.packet.grasszones.RequestZonesC2SPacket;
import com.cobblemon.khataly.mapkit.networking.packet.rockclimb.RockClimbPacketC2S;
import com.cobblemon.khataly.mapkit.networking.packet.rocksmash.RockSmashPacketC2S;
import com.cobblemon.khataly.mapkit.networking.packet.strength.StrengthPacketC2S;
import com.cobblemon.khataly.mapkit.networking.packet.teleport.TeleportMenuC2SPacket;
import com.cobblemon.khataly.mapkit.networking.packet.teleport.TeleportMenuS2CPacket;
import com.cobblemon.khataly.mapkit.networking.packet.teleport.TeleportPacketC2S;
import com.cobblemon.khataly.mapkit.networking.packet.ultrahole.UltraHoleMenuC2SPacket;
import com.cobblemon.khataly.mapkit.networking.packet.ultrahole.UltraHoleMenuS2CPacket;
import com.cobblemon.khataly.mapkit.networking.packet.ultrahole.UltraHolePacketC2S;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;

public class ModNetworking {

    public static void registerPackets() {
        PayloadTypeRegistry.playC2S().register(PlaceGrassC2SPacket.ID, PlaceGrassC2SPacket.CODEC);
        // ======= tuoi pacchetti esistenti =======
        PayloadTypeRegistry.playS2C().register(AnimationHMPacketS2C.ID, AnimationHMPacketS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(FlyMenuS2CPacket.ID,     FlyMenuS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(FlashMenuS2CPacket.ID,   FlashMenuS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(TeleportMenuS2CPacket.ID, TeleportMenuS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(UltraHoleMenuS2CPacket.ID, UltraHoleMenuS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(RotatePlayerS2CPacket.ID, RotatePlayerS2CPacket.CODEC);

        PayloadTypeRegistry.playC2S().register(RockSmashPacketC2S.ID,   RockSmashPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(CutPacketC2S.ID,         CutPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(StrengthPacketC2S.ID,    StrengthPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(RockClimbPacketC2S.ID,   RockClimbPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(FlyPacketC2S.ID,         FlyPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(FlyMenuC2SPacket.ID,     FlyMenuC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(FlashPacketC2S.ID,       FlashPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(FlashMenuC2SPacket.ID,   FlashMenuC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(TeleportPacketC2S.ID,    TeleportPacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(TeleportMenuC2SPacket.ID, TeleportMenuC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(UltraHolePacketC2S.ID,   UltraHolePacketC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(UltraHoleMenuC2SPacket.ID, UltraHoleMenuC2SPacket.CODEC);

        // ======= BadgeBox =======
        PayloadTypeRegistry.playS2C().register(OpenBadgeBoxS2CPacket.ID,  OpenBadgeBoxS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncBadgeBoxS2CPacket.ID,  SyncBadgeBoxS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(PolishBadgeC2SPacket.ID,   PolishBadgeC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(EjectBadgeC2SPacket.ID,   EjectBadgeC2SPacket.CODEC);

        PayloadTypeRegistry.playS2C().register(GrassZonesSyncS2CPacket.ID, GrassZonesSyncS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestZonesC2SPacket.ID,   RequestZonesC2SPacket.CODEC);

        // Receiver server: risponde con lo snapshot
        ServerPlayNetworking.registerGlobalReceiver(
                RequestZonesC2SPacket.ID,
                (payload, ctx) -> ctx.server().execute(() -> {
                    var pkt = new GrassZonesSyncS2CPacket(GrassZonesSyncS2CPacket.buildDtos());
                    ServerPlayNetworking.send(ctx.player(), pkt);
                })
        );


        // ======= Handlers =======
        RockSmashHandler.register();
        CutHandler.register();
        StrengthHandler.register();
        RockClimbHandler.register();
        FlyHandler.register();
        TeleportHandler.register();
        FlashHandler.register();
        UltraHoleHandler.register();
        BadgeBoxHandler.register();
        GrassWandHandler.register();
    }

    public static void tick(MinecraftServer server) {
        NetworkingTick.tick(server);
    }
}
