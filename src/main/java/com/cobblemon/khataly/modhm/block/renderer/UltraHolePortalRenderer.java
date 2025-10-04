package com.cobblemon.khataly.modhm.block.renderer;

import com.cobblemon.khataly.modhm.block.entity.custom.UltraHolePortalEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.render.OverlayTexture;
import org.joml.Vector3f;

import java.util.Random;

public class UltraHolePortalRenderer implements BlockEntityRenderer<UltraHolePortalEntity> {

    private static final Random RANDOM = new Random();

    // ====== PALETTE TIPO SCREENSHOT (bianco -> ciano -> rosa) ======
    // Core quasi bianco
    private static final float CORE_V = 1.00f; // value
    private static final float CORE_S = 0.05f; // sat

    // Mid ciano
    private static final float CYAN_H = 0.55f; // ~ciano
    private static final float CYAN_S = 0.35f;
    private static final float CYAN_V = 1.00f;

    // Outer rosa/magenta
    private static final float PINK_H = 0.88f; // ~rosa/magenta
    private static final float PINK_S = 0.45f;
    private static final float PINK_V = 1.00f;

    // Leggera “respirazione” di tinta
    private static final float HUE_WOBBLE = 0.015f;

    // Noise field per micro-variante
    private static final float NOISE_FREQ   = 1.6f;
    private static final float NOISE_SPEEDX = 0.20f;
    private static final float NOISE_SPEEDY = -0.15f;
    private static final int   NOISE_OCTAVES= 4;
    private static final float NOISE_GAIN   = 0.55f;
    private static final float NOISE_LACUNARITY = 2.1f;

    // Profondità/mesh disco
    private static final int DEPTH_LAYERS   = 10;
    private static final float LAYER_Z_STEP = 0.12f;
    private static final float LAYER_RADIUS_SHRINK = 0.07f;
    private static final int RING_STEPS     = 128;
    private static final int RADIAL_BANDS   = 10;

    // Particelle (tema pastello)
    private static final float RING_BASE_HUE = 0.76f; // non più usato direttamente, ma lasciato se serve

    public UltraHolePortalRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(UltraHolePortalEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;

        BlockPos pos = entity.getPos();

        // ---- Timeline ----
        int growDuration = 200;
        int stableDuration = 1200;
        int shrinkDuration = 200;
        float maxRadiusFinal = 1.5f;
        float minRadius = 0.05f;

        // ---- Age ----
        int localAge = entity.getAge() + 1;
        entity.setAge(localAge);
        double time = localAge + tickDelta;

        // ---- Radius + sound ----
        float radius;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (localAge < growDuration) {
            float progress = (localAge + tickDelta) / (float) growDuration;
            radius = maxRadiusFinal * progress;
            if (mc.player != null) {
                float volume = 0.2f + 0.8f * progress;
                float pitch  = 0.5f + progress;
                mc.getSoundManager().play(PositionedSoundInstance.master(
                        SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, volume, pitch));
            }
        } else if (localAge < growDuration + stableDuration) {
            radius = maxRadiusFinal;
        } else if (localAge < growDuration + stableDuration + shrinkDuration) {
            int shrinkAge = localAge - (growDuration + stableDuration);
            float progress = 1.0f - (shrinkAge + tickDelta) / (float) shrinkDuration;
            radius = Math.max(maxRadiusFinal * progress, minRadius);
        } else {
            radius = minRadius;
        }

        // ---- Ambience durante fase STABILE (ping ogni ~2s) ----
        if (localAge >= growDuration && localAge < growDuration + stableDuration) {
            if ((localAge % 40) == 0) {
                float vol   = 0.25f;
                float pitch = 0.90f + (RANDOM.nextFloat() - 0.5f) * 0.08f;
                world.playSound(
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_BEACON_AMBIENT,
                        net.minecraft.sound.SoundCategory.AMBIENT,
                        vol, pitch, false
                );
            }
        }

        // =========================================================
        //        SUPERFICIE INTERNA (GRADIENT PASTELLO)
        // =========================================================
        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);

        BakedModelManager modelManager = mc.getBlockRenderManager().getModels().getModelManager();
        Sprite white = modelManager.getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                .getSprite(Identifier.of("minecraft", "block/white_concrete"));

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getTranslucent());

        for (int layer = 0; layer < DEPTH_LAYERS; layer++) {
            float zOffset  = -LAYER_Z_STEP * layer;
            float baseOuterR = Math.max(0.05f, radius * (1.0f - layer * LAYER_RADIUS_SHRINK));
            if (baseOuterR <= 1e-3f) continue;

            // alpha leggermente decrescente
            int alpha = Math.max(60, 235 - layer * 18);

            for (int b = 0; b < RADIAL_BANDS; b++) {
                float t0 = (float)b / RADIAL_BANDS;
                float t1 = (float)(b + 1) / RADIAL_BANDS;

                float rInner = baseOuterR * t0;
                float rOuter = baseOuterR * t1;

                for (int i = 0; i < RING_STEPS; i++) {
                    double a1 = (2 * Math.PI * i / RING_STEPS);
                    double a2 = (2 * Math.PI * (i + 1) / RING_STEPS);

                    float x1o = (float)(Math.cos(a1) * rOuter);
                    float y1o = (float)(Math.sin(a1) * rOuter);
                    float x2o = (float)(Math.cos(a2) * rOuter);
                    float y2o = (float)(Math.sin(a2) * rOuter);

                    float x1i = (float)(Math.cos(a1) * rInner);
                    float y1i = (float)(Math.sin(a1) * rInner);
                    float x2i = (float)(Math.cos(a2) * rInner);
                    float y2i = (float)(Math.sin(a2) * rInner);

                    // ---------------- COLORE PASTELLO TIPO SCREENSHOT ----------------
                    float tRadOuter = (rOuter / (baseOuterR + 1e-6f));
                    float tRadInner = (rInner / (baseOuterR + 1e-6f));

                    float nx1o = (x1o / baseOuterR) * NOISE_FREQ + (float)time * NOISE_SPEEDX;
                    float ny1o = (y1o / baseOuterR) * NOISE_FREQ + (float)time * NOISE_SPEEDY;
                    float nx1i = (x1i / baseOuterR) * NOISE_FREQ + (float)time * NOISE_SPEEDX;
                    float ny1i = (y1i / baseOuterR) * NOISE_FREQ + (float)time * NOISE_SPEEDY;

                    float fOuter = fbm(nx1o, ny1o);
                    float fInner = fbm(nx1i, ny1i);

                    int aOuter = Math.max(140, alpha);
                    int aInner = Math.max(120, alpha - 10);

                    int colOuter = pastelPortalColor(tRadOuter, fOuter, (float)time, aOuter);
                    int colInner = pastelPortalColor(tRadInner, fInner, (float)time, aInner);
                    // ------------------------------------------------------------------

                    // UV block atlas (centro 0.5,0.5)
                    float u1o = white.getFrameU((x1o / (baseOuterR * 2f)) + 0.5f);
                    float v1o = white.getFrameV((y1o / (baseOuterR * 2f)) + 0.5f);
                    float u2o = white.getFrameU((x2o / (baseOuterR * 2f)) + 0.5f);
                    float v2o = white.getFrameV((y2o / (baseOuterR * 2f)) + 0.5f);

                    float u1i = white.getFrameU((x1i / (baseOuterR * 2f)) + 0.5f);
                    float v1i = white.getFrameV((y1i / (baseOuterR * 2f)) + 0.5f);
                    float u2i = white.getFrameU((x2i / (baseOuterR * 2f)) + 0.5f);
                    float v2i = white.getFrameV((y2i / (baseOuterR * 2f)) + 0.5f);

                    putVertexUV(buffer, matrices, x1o, y1o, zOffset, colOuter, light, u1o, v1o);
                    putVertexUV(buffer, matrices, x2o, y2o, zOffset, colOuter, light, u2o, v2o);
                    putVertexUV(buffer, matrices, x2i, y2i, zOffset, colInner, light, u2i, v2i);
                    putVertexUV(buffer, matrices, x1i, y1i, zOffset, colInner, light, u1i, v1i);
                }
            }
        }
        matrices.pop();

        // =========================================================
        //                 PARTICELLE (ciano/rosa)
        // =========================================================
        {
            int depthLayersParticles = 10;
            int baseSegments = 48;
            float spawnChance = 0.75f;
            float swirlSpeed = 0.10f;
            float inwardSpeedBase = 0.06f;
            float baseDepthPull = 0.02f;
            float jitter = 0.008f;

            float inwardSpeed = inwardSpeedBase;
            float depthPull   = baseDepthPull;

            if (localAge >= growDuration + stableDuration && localAge < growDuration + stableDuration + shrinkDuration) {
                int shrinkAge = localAge - (growDuration + stableDuration);
                float t = (shrinkAge + tickDelta) / (float)shrinkDuration;
                inwardSpeed += 0.04f * t;
                depthPull   += 0.06f * t;
            }

            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 0.5;
            double cz = pos.getZ() + 0.5;

            for (int layer = 0; layer < depthLayersParticles; layer++) {
                float zOffset = -0.12f * layer;
                float layerRadius = Math.max(0.05f, radius * (1.0f - layer * 0.07f));
                double rotation = time * 0.02 * (layer % 2 == 0 ? 1 : -1);
                int segmentsParticles = Math.max(18, (int)(baseSegments * (0.5f + layerRadius)));

                for (int i = 0; i < segmentsParticles; i++) {
                    if (RANDOM.nextFloat() > spawnChance) continue;

                    double angle = (2 * Math.PI * i / segmentsParticles) + rotation;

                    double localX = Math.cos(angle) * layerRadius;
                    double localY = Math.sin(angle) * layerRadius;

                    double px = cx + localX + (RANDOM.nextDouble() - 0.5) * jitter;
                    double py = cy + localY + (RANDOM.nextDouble() - 0.5) * jitter;
                    double pz = cz + zOffset;

                    double nx = -localX / Math.max(1e-6, layerRadius);
                    double ny = -localY / Math.max(1e-6, layerRadius);

                    double txv = -Math.sin(angle);
                    double tyv =  Math.cos(angle);

                    double vx = nx * inwardSpeed + txv * swirlSpeed + (RANDOM.nextDouble() - 0.5) * jitter;
                    double vy = ny * inwardSpeed + tyv * swirlSpeed + (RANDOM.nextDouble() - 0.5) * jitter;
                    double vz = -depthPull + (RANDOM.nextDouble() - 0.5) * (jitter * 0.5);

                    // alterna ciano ↔ rosa
                    boolean cyanBand = (i + layer) % 2 == 0;
                    float h = cyanBand ? CYAN_H : PINK_H;
                    Vector3f cBright = hsvToVec((h + (float)Math.sin(time*0.3 + i*0.2) * 0.02f + 1f) % 1f,
                            cyanBand ? 0.35f : 0.45f,
                            1.00f);
                    Vector3f cDark   = hsvToVec(h, 0.15f, 0.30f);
                    float scale = 0.9f + RANDOM.nextFloat() * 0.6f;

                    world.addParticle(new DustColorTransitionParticleEffect(cBright, cDark, scale), px, py, pz, vx, vy, vz);

                    if (RANDOM.nextFloat() < 0.05f) {
                        world.addParticle(ParticleTypes.END_ROD, px, py, pz, vx * 0.45, vy * 0.45, vz * 0.45);
                    }
                }
            }
        }
    }

    // -------------------- noise & utils --------------------
    private static float fbm(float x, float y) {
        float amp = 1.0f;
        float freq = 1.0f;
        float sum = 0.0f;
        float norm = 0.0f;
        for (int o = 0; o < UltraHolePortalRenderer.NOISE_OCTAVES; o++) {
            sum  += amp * smoothNoise(x * freq, y * freq);
            norm += amp;
            amp  *= UltraHolePortalRenderer.NOISE_GAIN;
            freq *= UltraHolePortalRenderer.NOISE_LACUNARITY;
        }
        return sum / Math.max(1e-6f, norm); // 0..1
    }

    private static float smoothNoise(float x, float y) {
        int x0 = (int)Math.floor(x);
        int y0 = (int)Math.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        float sx = x - x0;
        float sy = y - y0;

        float n00 = hash2(x0, y0);
        float n10 = hash2(x1, y0);
        float n01 = hash2(x0, y1);
        float n11 = hash2(x1, y1);

        float ix0 = lerp(n00, n10, smoothstep(sx));
        float ix1 = lerp(n01, n11, smoothstep(sx));
        return lerp(ix0, ix1, smoothstep(sy));
    }

    private static float hash2(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return (h & 0x7fffffff) / 2147483647f;
    }

    private static float smoothstep(float t) { return t * t * (3f - 2f * t); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static void putVertexUV(VertexConsumer buf, MatrixStack matrices,
                                    float x, float y, float z, int argb, int light,
                                    float u, float v) {
        buf.vertex(matrices.peek().getPositionMatrix(), x, y, z)
                .color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0f, 0f, 1f);
    }

    /** Blend HSV con wrapping dell'hue. t in [0..1] */
    private static int hsvMixARGB(float h1, float s1, float v1, float h2, float s2, float v2, float t, int a) {
        float dh = ((h2 - h1 + 1f + 0.5f) % 1f) - 0.5f; // cammino più corto sull'anello
        float h = (h1 + dh * t + 1f) % 1f;
        float s = s1 + (s2 - s1) * t;
        float v = v1 + (v2 - v1) * t;
        return hsvToARGB(h, s, v, a);
    }

    /** Colore del portale: centro bianco, poi ciano, poi rosa. noise fa “respirare” la tinta. */
    private static int pastelPortalColor(float tRad, float noise01, float time, int alpha) {
        float wob = (float)Math.sin(time * 0.6 + tRad * 6.0) * HUE_WOBBLE;
        if (tRad < 0.15f) {
            float s = Math.max(0f, CORE_S + (noise01 - 0.5f) * 0.05f);
            return hsvToARGB((CYAN_H + wob + 1f) % 1f, s, CORE_V, alpha);
        } else if (tRad < 0.60f) {
            float u = smoothstep((tRad - 0.15f) / 0.45f);
            float h2 = (CYAN_H + wob + (noise01 - 0.5f) * 0.02f + 1f) % 1f;
            return hsvMixARGB(CYAN_H + wob, 0.10f, 1.00f, h2, CYAN_S, CYAN_V, u, alpha);
        } else {
            float u = smoothstep((tRad - 0.60f) / 0.40f);
            float h1 = (CYAN_H + wob + (noise01 - 0.5f) * 0.02f + 1f) % 1f;
            float h2 = (PINK_H - wob + (noise01 - 0.5f) * 0.02f + 1f) % 1f;
            return hsvMixARGB(h1, CYAN_S, CYAN_V, h2, PINK_S, PINK_V, u, alpha);
        }
    }

    private static Vector3f hsvToVec(float h, float s, float v) {
        int rgb = hsvToRGB(h, s, v);
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        return new Vector3f(r, g, b);
    }

    private static int hsvToARGB(float h, float s, float v, int a) {
        int rgb = hsvToRGB(h, s, v);
        return (a << 24) | rgb;
    }

    private static int hsvToRGB(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f;
        int i = (int)Math.floor(h * 6f);
        float f = h * 6f - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        float r=0, g=0, b=0;
        switch (i % 6) {
            case 0 -> { r=v; g=t; b=p; }
            case 1 -> { r=q; g=v; b=p; }
            case 2 -> { r=p; g=v; b=t; }
            case 3 -> { r=p; g=q; b=v; }
            case 4 -> { r=t; g=p; b=v; }
            case 5 -> { r=v; g=p; b=q; }
        }
        int ri = (int)(r * 255f);
        int gi = (int)(g * 255f);
        int bi = (int)(b * 255f);
        return (ri << 16) | (gi << 8) | bi;
    }
}
