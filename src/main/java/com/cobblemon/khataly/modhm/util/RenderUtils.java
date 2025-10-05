package com.cobblemon.khataly.modhm.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

public class RenderUtils {

    /** Bordi: resta il path che sai già che funziona. */
    public static void drawOutlineBox(MatrixStack matrices, Box box, float r, float g, float b, float a) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);

        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        WorldRenderer.drawBox(matrices, buf, box, r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * Filled semitrasparente: emettiamo 6 quad usando solo POSITION+COLOR.
     * IMPORTANTE: dopo averla chiamata, fai providers.draw() nel caller.
     */
    public static void drawFilledBox(MatrixStack matrices, VertexConsumerProvider providers, Box box,
                                     float rf, float gf, float bf, float af) {

        // Layer debug adatto a position+color e alpha
        VertexConsumer vc = providers.getBuffer(RenderLayer.getDebugFilledBox());

        Matrix4f m = matrices.peek().getPositionMatrix();

        int r = (int)(rf * 255f);
        int g = (int)(gf * 255f);
        int b = (int)(bf * 255f);
        int a = (int)(af * 255f);

        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        // Ogni faccia: 4 vertici in senso orario

        // BOTTOM
        v(vc, m, x1, y1, z1, r, g, b, a);
        v(vc, m, x2, y1, z1, r, g, b, a);
        v(vc, m, x2, y1, z2, r, g, b, a);
        v(vc, m, x1, y1, z2, r, g, b, a);

        // TOP
        v(vc, m, x1, y2, z1, r, g, b, a);
        v(vc, m, x1, y2, z2, r, g, b, a);
        v(vc, m, x2, y2, z2, r, g, b, a);
        v(vc, m, x2, y2, z1, r, g, b, a);

        // NORTH
        v(vc, m, x1, y1, z1, r, g, b, a);
        v(vc, m, x1, y2, z1, r, g, b, a);
        v(vc, m, x2, y2, z1, r, g, b, a);
        v(vc, m, x2, y1, z1, r, g, b, a);

        // SOUTH
        v(vc, m, x1, y1, z2, r, g, b, a);
        v(vc, m, x2, y1, z2, r, g, b, a);
        v(vc, m, x2, y2, z2, r, g, b, a);
        v(vc, m, x1, y2, z2, r, g, b, a);

        // WEST
        v(vc, m, x1, y1, z1, r, g, b, a);
        v(vc, m, x1, y1, z2, r, g, b, a);
        v(vc, m, x1, y2, z2, r, g, b, a);
        v(vc, m, x1, y2, z1, r, g, b, a);

        // EAST
        v(vc, m, x2, y1, z1, r, g, b, a);
        v(vc, m, x2, y2, z1, r, g, b, a);
        v(vc, m, x2, y2, z2, r, g, b, a);
        v(vc, m, x2, y1, z2, r, g, b, a);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                          int r, int g, int b, int a) {
        vc.vertex(m, x, y, z).color(r, g, b, a);
    }
}
