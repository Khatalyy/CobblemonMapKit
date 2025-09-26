package com.cobblemon.khataly.modhm.screen.custom;

import com.cobblemon.khataly.modhm.networking.packet.FlyMenuS2CPacket;
import com.cobblemon.khataly.modhm.networking.packet.FlyPacketC2S;
import com.cobblemon.khataly.modhm.widget.SimpleButton;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public class FlyTargetListScreen extends Screen {

    private final List<FlyMenuS2CPacket.FlyTargetEntry> targets;

    public FlyTargetListScreen(MutableText title, List<FlyMenuS2CPacket.FlyTargetEntry> targets) {
        super(title);
        this.targets = targets;
    }

    @Override
    protected void init() {
        int y = 40;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        // Dimensione attuale del player
        RegistryKey<World> currentWorld = client.world.getRegistryKey();

        // Aggiunge un bottone per ogni target nella stessa dimensione
        for (FlyMenuS2CPacket.FlyTargetEntry entry : targets) {
            // Converte la stringa del worldKey in un RegistryKey
            RegistryKey<World> targetWorld = RegistryKey.of(RegistryKeys.WORLD,  Identifier.of(entry.worldKey()));

            if (!targetWorld.equals(currentWorld)) {
                continue; // salta se non è la stessa dimensione
            }

            String name = entry.name();
            BlockPos pos = entry.pos();

            this.addDrawableChild(new SimpleButton(
                    this.width / 2 - 100,
                    y,
                    200,
                    20,
                    Text.literal(name + " @ " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                    button -> {
                        // Invia al server la scelta del target
                        ClientPlayNetworking.send(new FlyPacketC2S(pos));
                        MinecraftClient.getInstance().setScreen(null);
                    }
            ));
            y += 25;
        }

        // Bottone chiudi
        this.addDrawableChild(new SimpleButton(
                this.width / 2 - 100,
                this.height - 40,
                200,
                20,
                Text.literal("Close"),
                button -> MinecraftClient.getInstance().setScreen(null)
        ));
    }
}
