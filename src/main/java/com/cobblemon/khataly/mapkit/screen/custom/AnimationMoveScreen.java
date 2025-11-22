package com.cobblemon.khataly.mapkit.screen.custom;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class AnimationMoveScreen extends Screen {
    private final FloatingState floatingState = new FloatingState();
    private final RenderablePokemon pokemon;
    private final List<SpeedLine> lines = new ArrayList<>();
    private final Random random = new Random();
    private int timer = 0;

    private static class SpeedLine {
        int x, y;
        final int width = 50;
        final int height = 4;

        SpeedLine(int x, int y) {
            this.x = x;
            this.y = y;
        }

        void move() {
            this.x += 20;
        }

        boolean isOffScreen(int screenWidth) {
            return this.x > screenWidth;
        }
    }

    public AnimationMoveScreen(Text title, RenderablePokemon pokemon) {
        super(title);
        this.pokemon = pokemon;

        floatingState.setCurrentPose("stand");
        floatingState.setCurrentAspects(pokemon.getAspects());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int stripHeight = 120;
        int stripY = (this.height - stripHeight) / 2;

        // Gradient blu scuro → blu medio
        int topColor = 0xFF0A1A33;   // blu molto scuro
        int bottomColor = 0xFF1E3A66; // blu un po' più chiaro
        context.fillGradient(0, stripY, this.width, stripY + stripHeight, topColor, bottomColor);

        // Piccole "stelle" random che luccicano
        for (int i = 0; i < 20; i++) {
            int starX = random.nextInt(this.width);
            int starY = stripY + random.nextInt(stripHeight);
            int alpha = 150 + random.nextInt(105); // trasparenza variabile (150-255)
            int starColor = (alpha << 24) | 0xFFFFFF;
            context.fill(starX, starY, starX + 2, starY + 2, starColor);
        }

        // Disegna le linee di velocità (più veloci)
        for (SpeedLine line : lines) {
            context.fill(line.x, line.y, line.x + line.width, line.y + line.height, 0xFFFFFFFF);
        }

        // Centro della striscia blu
        int centerX = this.width / 2;
        int centerY = (this.height - stripHeight) / 2;

        float pokemonScale = 9.0f;
        context.getMatrices().push();

        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().translate(0, -stripHeight / 4f, 0);
        context.getMatrices().scale(pokemonScale, pokemonScale, pokemonScale);

        Quaternionf rotation = new Quaternionf()
                .rotateXYZ((float) Math.toRadians(15), (float) Math.toRadians(-30), 0);

        PokemonGuiUtilsKt.drawProfilePokemon(
                pokemon,
                context.getMatrices(),
                rotation,
                PoseType.STAND,
                floatingState,
                delta,
                9.0f,
                true,
                true,
                1f, 1f, 1f, 1f,
                0,0
        );

        context.getMatrices().pop();
        super.render(context, mouseX, mouseY, delta);
    }


    @Override
    public void tick() {
        timer++;

        if (timer % 2 == 0) {
            int offset = random.nextInt(60) - 30;
            lines.add(new SpeedLine(-50, this.height / 2 + offset));
        }

        Iterator<SpeedLine> it = lines.iterator();
        while (it.hasNext()) {
            SpeedLine line = it.next();
            line.move();
            if (line.isOffScreen(this.width)) {
                it.remove();
            }
        }

        if (timer > 40 && this.client != null) {
            this.client.setScreen(null);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Nessuno sfondo trasparente
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
