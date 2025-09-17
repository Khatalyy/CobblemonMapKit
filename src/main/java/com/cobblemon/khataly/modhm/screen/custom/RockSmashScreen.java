package com.cobblemon.khataly.modhm.screen.custom;

import com.cobblemon.khataly.modhm.HMMod;
import com.cobblemon.khataly.modhm.networking.packet.FlashMenuS2CPacket;
import com.cobblemon.khataly.modhm.networking.packet.RockSmashPacketC2S;
import com.cobblemon.khataly.modhm.networking.packet.RockSmashPacketS2C;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class RockSmashScreen extends HandledScreen<RockSmashScreenHandler> {

    private static final Identifier GUI_TEXTURE =
            Identifier.of(HMMod.MOD_ID, "textures/gui/rocksmash/rocksmash_gui.png");

    public RockSmashScreen(RockSmashScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {

    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);
        context.drawTexture(GUI_TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);

        int buttonWidth = 50;
        int buttonHeight = 21;

        // YES
        int yesX = this.x + 30;
        int yesY = this.y + 114;
        if (mouseX >= yesX && mouseX <= yesX + buttonWidth &&
                mouseY >= yesY && mouseY <= yesY + buttonHeight) {
            context.fill(yesX, yesY, yesX + buttonWidth, yesY + buttonHeight, 0x66FFFFFF);
        }

        // NO
        int noX = this.x + 95;
        int noY = this.y + 114;
        if (mouseX >= noX && mouseX <= noX + buttonWidth &&
                mouseY >= noY && mouseY <= noY + buttonHeight) {
            context.fill(noX, noY, noX + buttonWidth, noY + buttonHeight, 0x66FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int buttonWidth = 50;
        int buttonHeight = 21;

        int yesX = x + 30;
        int yesY = y + 114;
        int noX = x + 95;
        int noY = y + 114;

        // YES
        if (mouseX >= yesX && mouseX <= yesX + buttonWidth &&
                mouseY >= yesY && mouseY <= yesY + buttonHeight) {

            // 1️⃣ Invia il pacchetto al server
            ClientPlayNetworking.send(new RockSmashPacketC2S(handler.getPos()));

            ClientPlayNetworking.registerGlobalReceiver(RockSmashPacketS2C.ID, (payload, context) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    mc.setScreen(new AnimationMoveScreen(Text.literal("AnimationMoveScreen"),payload.pokemon()));
                });
            });


            return true;
        }

        // NO
        if (mouseX >= noX && mouseX <= noX + buttonWidth &&
                mouseY >= noY && mouseY <= noY + buttonHeight) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.setScreen(null);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }




}
