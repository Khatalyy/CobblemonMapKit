package com.cobblemon.khataly.modhm.screen.custom;

import com.cobblemon.khataly.modhm.HMMod;
import com.cobblemon.khataly.modhm.networking.packet.badgebox.EjectBadgeC2SPacket;
import com.cobblemon.khataly.modhm.networking.packet.badgebox.PolishBadgeC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * BadgeCaseScreen – lucidatura lenta a “metri” + livelli stelle.
 */
public class BadgeCaseScreen extends Screen {

    private static final Identifier BG_TEX = Identifier.of(HMMod.MOD_ID, "textures/gui/badge_case.png");

    private List<ItemStack> badges;
    private List<Integer>   shines; // 0..100
    private int total;

    // pannello
    private final int panelW = 256;
    private final int panelH = 160;
    private int left, top;

    // griglia
    private final int rows = 2, cols = 4;
    private final float itemScale = 1.9f; // badge grandi
    private final int   slotSize  = 40;

    // polish (nuovo sistema a metri di sfregamento)
    private static final double POLISH_PIXELS_PER_SHINE   = 120.0; // ⬅️ aumenta per richiedere più strofinamenti
    private static final long   POLISH_PACKET_COOLDOWN_MS = 95L;   // ⬅️ anti-spam pacchetti
    private static final int    POLISH_PACKET_AMOUNT      = 1;     // manda +1% per volta

    private int  polishingSlot = -1;
    private double lastX, lastY;
    private long lastPolishSoundMs = 0;

    /** progresso locale per-slot (quanti “pixel sfregati” accumulati) */
    private final List<Double> polishAccum = new ArrayList<>();
    /** cooldown ultimo pacchetto per-slot */
    private final List<Long>   lastPacketSentMs = new ArrayList<>();

    // animazione inserimento “cinematica”
    private Identifier pendingAnimId = null;
    private CinematicInsert cinematic = null;
    private long screenShakeUntil = 0L;
    private final Random rnd = new Random();

    // particelle stelline “spark”
    private final List<Spark> sparks = new ArrayList<>();

    public BadgeCaseScreen(Hand handUsed, List<ItemStack> badges, List<Integer> shines, int total) {
        super(Text.translatable("item." + HMMod.MOD_ID + ".badge_case"));
        this.badges = new ArrayList<>(badges);
        this.shines = new ArrayList<>(shines);
        this.total  = Math.max(total, badges.size());
        ensurePerSlotArrays();
    }

    /** evita il “traballo” del braccio mentre la GUI è aperta */
    @Override public void tick() {
        var opts = MinecraftClient.getInstance().options;
        opts.attackKey.setPressed(false);
        opts.useKey.setPressed(false);
        super.tick();
    }

    /** chiamata dal client handler subito dopo setScreen */
    public void queueInsertAnimation(Identifier badgeId) { this.pendingAnimId = badgeId; }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - panelW) / 2;
        this.top  = (this.height - panelH) / 2;

        if (pendingAnimId != null) {
            startCinematicInsert(pendingAnimId);
            pendingAnimId = null;
        }
    }

    @Override public boolean shouldPause() { return false; }

    /* ====================== Layout ====================== */

    private int gridStartX(){ return left + (panelW - cols * slotSize) / 2; }
    private int gridStartY(){ return top  + (panelH - rows * slotSize) / 2 + 8; }

    private int[] slotBounds(int idx) {
        int r = idx / cols, c = idx % cols;
        int x0 = gridStartX() + c * slotSize;
        int y0 = gridStartY() + r * slotSize;
        return new int[]{x0, y0, x0 + slotSize, y0 + slotSize};
    }

    /** posizione centrata dell’item scalato */
    private int[] itemDrawPos(int idx) {
        int[] b = slotBounds(idx);
        int itemPx = Math.round(16 * itemScale);
        int x = b[0] + (slotSize - itemPx) / 2;
        int y = b[1] + (slotSize - itemPx) / 2;
        return new int[]{x, y};
    }

    private int indexOfBadgeId(Identifier id){
        for (int i=0;i<badges.size();i++){
            if (Registries.ITEM.getId(badges.get(i).getItem()).equals(id)) return i;
        }
        return -1;
    }

    /* =================== Input: polish/eject =================== */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (cinematic != null) return true;

        int idx = hoveredSlotIndex((int)mouseX, (int)mouseY);

        // Shift + Right Click => eject
        if (button == 1 && hasShiftDown() && idx >= 0 && idx < badges.size() && !badges.get(idx).isEmpty()) {
            var id = Registries.ITEM.getId(badges.get(idx).getItem());
            ClientPlayNetworking.send(new EjectBadgeC2SPacket(id));
            return true;
        }

        // Left click => inizia lucidatura
        if (button == 0 && idx >= 0 && idx < badges.size() && !badges.get(idx).isEmpty()) {
            polishingSlot = idx;
            lastX = mouseX; lastY = mouseY;
            return true;
        }

        // Left click fuori => chiudi
        if (button == 0 && (mouseX < left || mouseX > left+panelW || mouseY < top || mouseY > top+panelH)) {
            this.close(); return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (cinematic != null) return true;

        if (button == 0 && polishingSlot >= 0 && polishingSlot < badges.size()) {
            double dxl = mouseX - lastX, dyl = mouseY - lastY;
            double dist = Math.hypot(dxl, dyl);
            lastX = mouseX; lastY = mouseY;

            // accumula “metri” di sfregamento per questo slot
            double acc = polishAccum.get(polishingSlot) + dist;
            long   now = System.currentTimeMillis();
            long   lastPkt = lastPacketSentMs.get(polishingSlot);

            // quando superiamo la soglia, proviamo a mandare un +1 (rate-limited)
            while (acc >= POLISH_PIXELS_PER_SHINE) {
                if (now - lastPkt >= POLISH_PACKET_COOLDOWN_MS) {
                    acc -= POLISH_PIXELS_PER_SHINE;

                    var id = Registries.ITEM.getId(badges.get(polishingSlot).getItem());
                    ClientPlayNetworking.send(new PolishBadgeC2SPacket(id, POLISH_PACKET_AMOUNT));
                    lastPacketSentMs.set(polishingSlot, now);

                    // suono (rate-limited indipendente)
                    if (now - lastPolishSoundMs > 150 && MinecraftClient.getInstance().player != null) {
                        MinecraftClient.getInstance().player.playSound(SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, 0.3f, 1.1f);
                        lastPolishSoundMs = now;
                    }

                    // feedback locale: +1% (coerente con amount=1)
                    int curr = polishingSlot < shines.size() ? shines.get(polishingSlot) : 0;
                    if (polishingSlot < shines.size()) shines.set(polishingSlot, Math.min(100, curr + POLISH_PACKET_AMOUNT));
                    spawnSlotSparks(polishingSlot, 2);
                } else {
                    // ancora in cooldown: esci dal while per non “bruciare” acc
                    break;
                }
                // aggiorna now/lastPkt per loop
                now = System.currentTimeMillis();
                lastPkt = lastPacketSentMs.get(polishingSlot);
            }

            polishAccum.set(polishingSlot, acc);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) polishingSlot = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int hoveredSlotIndex(int mx, int my){
        for (int i=0;i<Math.min(total, rows*cols);i++){
            int[] b = slotBounds(i);
            if (mx>=b[0] && mx<b[2] && my>=b[1] && my<b[3]) return i;
        }
        return -1;
    }

    /* ====================== Sync/Anim ====================== */

    /** chiamata dal S2C sync */
    public void applySync(List<ItemStack> newBadges, List<Integer> newShines, int total) {
        this.badges = new ArrayList<>(newBadges);
        this.shines = new ArrayList<>(newShines);
        this.total  = Math.max(total, newBadges.size());
        ensurePerSlotArrays();
    }

    /** avvia la cinematic “gigante al centro → spin → schianto nello slot” */
    private void startCinematicInsert(Identifier badgeId) {
        int idx = indexOfBadgeId(badgeId);
        if (idx < 0 || idx >= badges.size()) return;
        ItemStack st = badges.get(idx).copy();
        if (st.isEmpty()) return;

        // coord target slot
        int[] pos = itemDrawPos(idx);
        int targetX = pos[0];
        int targetY = pos[1];

        // centro schermo
        float giantScale = 3.4f;
        int giantPx = Math.round(16 * giantScale);
        int centerX = left + (panelW - giantPx) / 2;
        int centerY = top  + (panelH - giantPx) / 2 - 6;

        this.cinematic = new CinematicInsert(st, idx, centerX, centerY, giantScale, targetX, targetY);
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1f);
        }
    }

    /* =================== Render =================== */

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // shake globale
        ctx.getMatrices().push();
        if (System.currentTimeMillis() < screenShakeUntil) {
            float sx = (rnd.nextFloat() - 0.5f) * 2f;
            float sy = (rnd.nextFloat() - 0.5f) * 2f;
            ctx.getMatrices().translate(sx, sy, 0);
        }

        // pannello
        if (MinecraftClient.getInstance().getResourceManager().getResource(BG_TEX).isPresent()) {
            ctx.drawTexture(BG_TEX, left, top, 0, 0, panelW, panelH, panelW, panelH);
        } else {
            ctx.fill(left, top, left+panelW, top+panelH, 0xCC1E1E1E);
            ctx.fill(left+3, top+3, left+panelW-3, top+panelH-3, 0xFF2A2A2A);
        }

        // titolo
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("GYM BADGES"),
                left + panelW/2, top + 6, 0xFFE8E0C8);

        // griglia 2×4
        for (int i=0;i<Math.min(total, rows*cols);i++){
            int[] b = slotBounds(i);
            ctx.fill(b[0], b[1], b[2], b[3], 0x22000000);
            ctx.fill(b[0]+1, b[1]+1, b[2]-1, b[3]-1, 0x22000000);
        }

        // items statici
        int itemPx = Math.round(16 * itemScale);
        for (int i=0;i<Math.min(badges.size(), rows*cols);i++){
            var st = badges.get(i);
            if (st.isEmpty()) continue;
            int[] p = itemDrawPos(i);

            if (cinematic != null && i == cinematic.slotIndex) {
                // lo slot target lo lascio “vuoto”: la medaglia cinematica passa sopra
            } else {
                drawItemScaled(ctx, st, p[0], p[1]);
                int shine = (i < shines.size() ? shines.get(i) : 0);
                if (shine > 0) drawStarsLeveled(ctx, p[0], p[1], itemPx, shine);
            }
        }

        if (cinematic != null) {
            if (!cinematic.render(ctx)) {
                int finishedSlot = cinematic.slotIndex;
                cinematic = null;

                spawnSlotSparks(finishedSlot, 10);
                screenShakeUntil = System.currentTimeMillis() + 180;
                if (client != null && client.player != null) {
                    client.player.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 0.9f, 1.1f);
                }
            }
        }

        // sparks
        renderSparks(ctx);

        // tooltip
        int hov = hoveredSlotIndex(mouseX, mouseY);
        if (hov >= 0 && hov < badges.size() && !badges.get(hov).isEmpty() && cinematic == null) {
            var stack = badges.get(hov);
            assert this.client != null;
            List<Text> lines = new ArrayList<>(getTooltipFromItem(this.client, stack));
            lines.add(Text.translatable("tooltip."+HMMod.MOD_ID+".badge_case.remove_hint"));
            lines.add(Text.translatable("tooltip."+HMMod.MOD_ID+".badge_case.polish_hint"));
            ctx.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
        }

        ctx.getMatrices().pop();
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    /* =================== Helpers disegno =================== */

    private void drawItemScaled(DrawContext ctx, ItemStack st, int x, int y) {
        ctx.getMatrices().push();
        ctx.getMatrices().scale(itemScale, itemScale, 1f);
        int dx = Math.round(x / itemScale);
        int dy = Math.round(y / itemScale);
        ctx.drawItem(st, dx, dy);
        ctx.drawItemInSlot(this.textRenderer, st, dx, dy);
        ctx.getMatrices().pop();
    }

    /** stelle a livelli – niente overlay bianco */
    private void drawStarsLeveled(DrawContext ctx, int x, int y, int size, int shine) {
        // livelli
        final int level =
                (shine < 25) ? 0 :
                        (shine < 50) ? 1 :
                                (shine < 75) ? 2 :
                                        (shine < 90) ? 3 : 4;

        if (level == 0) return;

        int baseCount  = switch (level) { case 1 -> 2; case 2 -> 3; case 3 -> 4; default -> 6; };
        int extraFromShine = Math.max(0, (shine - 25) / 15); // aggiustino progressivo
        int stars = Math.min(10, baseCount + extraFromShine);

        int color = switch (level) {
            case 1 -> 0xFFEEF7FF; // bianco freddo tenue
            case 2 -> 0xFFFFFFFF; // bianco pieno
            case 3 -> 0xFFFFE3A0; // caldo
            default -> 0xFFFFC84A; // dorato
        };

        int alpha = 200; // opacità
        int argb  = (alpha << 24) | (color & 0xFFFFFF);

        long tick = System.currentTimeMillis() / 100;
        for (int i = 0; i < stars; i++) {
            if (((tick + i) % 3) != 0) continue; // “intermittenti”

            int sx = x + 3 + rnd.nextInt(Math.max(1, size - 6));
            int sy = y + 3 + rnd.nextInt(Math.max(1, size - 6));

            // dimensione cresce col livello
            int s = (level >= 4) ? 2 : (level >= 3 ? 2 : 1);

            // piccola “croce” scintillante
            ctx.fill(sx,   sy,   sx + s,   sy + s,   argb);
            ctx.fill(sx+2, sy,   sx+2+s,   sy + s,   argb);
            ctx.fill(sx+1, sy-1, sx+1+s-1, sy + s+1, argb);
        }
    }

    private void spawnSlotSparks(int slotIndex, int count) {
        int[] p = itemDrawPos(slotIndex);
        int size = Math.round(16 * itemScale);
        for (int i = 0; i < count; i++) {
            sparks.add(Spark.create(p[0] + size/2f, p[1] + size/2f));
        }
    }

    private void renderSparks(DrawContext ctx) {
        long now = System.currentTimeMillis();
        sparks.removeIf(s -> {
            float t = (now - s.t0) / (float)s.lifeMs;
            if (t >= 1f) return true;
            float x = s.x + s.vx * t;
            float y = s.y + s.vy * t + 0.5f * s.g * t * t;
            int a = (int)(255 * (1f - t));
            int c = (a << 24) | 0xFFFFFF;
            ctx.fill((int)x, (int)y, (int)x+1, (int)y+1, c);
            return false;
        });
    }

    /* =================== Classi animazione =================== */

    private static float easeOutCubic(float t){ return 1f - (float)Math.pow(1f - t, 3); }
    private static float easeInOutCubic(float t){
        return t < 0.5f ? 4f*t*t*t : 1f - (float)Math.pow(-2f*t + 2f, 3)/2f;
    }

    private static class Spark {
        final float x, y, vx, vy, g;
        final long t0;
        final long lifeMs;
        private Spark(float x, float y, float vx, float vy, float g, long lifeMs) {
            this.x=x; this.y=y; this.vx=vx; this.vy=vy; this.g=g; this.lifeMs=lifeMs; this.t0=System.currentTimeMillis();
        }
        static Spark create(float x, float y) {
            Random r = new Random();
            float ang = (float)(r.nextFloat() * Math.PI * 2);
            float spd = 1.5f + r.nextFloat()*2.0f;
            return new Spark(x, y, (float)Math.cos(ang)*spd, (float)Math.sin(ang)*spd, 1.2f, 350 + r.nextInt(200));
        }
    }

    private class CinematicInsert {
        final ItemStack stack;
        final int slotIndex;
        final int targetX, targetY;
        final int centerX, centerY;
        final float bigScale;

        final long tStart = System.currentTimeMillis();
        final long dIn = 250;
        final long dSpin = 300;
        final long dOut = 320;

        CinematicInsert(ItemStack st, int slotIndex, int centerX, int centerY, float bigScale, int targetX, int targetY) {
            this.stack = st;
            this.slotIndex = slotIndex;
            this.centerX = centerX;
            this.centerY = centerY;
            this.bigScale = bigScale;
            this.targetX = targetX;
            this.targetY = targetY;
        }

        boolean render(DrawContext ctx) {
            long now = System.currentTimeMillis();
            long dt = now - tStart;

            if (dt <= dIn) {
                float t = easeOutCubic(dt / (float)dIn);
                float startX = left + panelW/2f - 8;
                float startY = top + panelH + 24;
                float x = lerp(startX, centerX, t);
                float y = lerp(startY, centerY, t);
                float s = lerp(itemScale, bigScale, t);
                float rot = (float)Math.toRadians(20 * t);
                drawItemAt(ctx, stack, x, y, s, rot);
                return true;

            } else if (dt <= dIn + dSpin) {
                float t = easeInOutCubic((dt - dIn) / (float)dSpin);
                float rot = (float)Math.toRadians(720 * t);
                drawItemAt(ctx, stack, centerX, centerY, bigScale, rot);
                return true;

            } else if (dt <= dIn + dSpin + dOut) {
                float t = easeOutCubic((dt - dIn - dSpin) / (float)dOut);
                float cx = (centerX + targetX) / 2f;
                float cy = Math.min(centerY, targetY) - 30;
                float[] p = quadBezier(centerX, centerY, cx, cy, targetX, targetY, t);
                float x = p[0], y = p[1];
                float s = lerp(bigScale, itemScale, t);
                float rot = (float)Math.toRadians(45 * (1f - t));
                drawItemAt(ctx, stack, x, y, s, rot);

                if (t > 0.95f && now - screenShakeUntil > 300) {
                    spawnSlotSparks(slotIndex, 6);
                    screenShakeUntil = now + 140;
                }
                return true;

            } else {
                return false;
            }
        }

        private float lerp(float a, float b, float t) { return a + (b - a) * t; }
        private float[] quadBezier(float x0, float y0, float cx, float cy, float x1, float y1, float t) {
            float u = 1f - t;
            float x = u*u*x0 + 2*u*t*cx + t*t*x1;
            float y = u*u*y0 + 2*u*t*cy + t*t*y1;
            return new float[]{x, y};
        }
    }

    /** draw con pivot al centro (rotazione) e scala */
    private void drawItemAt(DrawContext ctx, ItemStack st, float x, float y, float scale, float rotZ) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 300f);
        ctx.getMatrices().translate(8, 8, 0);
        ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation(rotZ));
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.getMatrices().translate(-8, -8, 0);
        ctx.drawItem(st, 0, 0);
        ctx.drawItemInSlot(this.textRenderer, st, 0, 0);
        ctx.getMatrices().pop();
    }

    /* ===== per-slot arrays ===== */

    private void ensurePerSlotArrays() {
        int n = badges.size();
        while (polishAccum.size() < n) polishAccum.add(0.0);
        while (lastPacketSentMs.size() < n) lastPacketSentMs.add(0L);
        if (polishAccum.size() > n) polishAccum.subList(n, polishAccum.size()).clear();
        if (lastPacketSentMs.size() > n) lastPacketSentMs.subList(n, lastPacketSentMs.size()).clear();
    }
}
