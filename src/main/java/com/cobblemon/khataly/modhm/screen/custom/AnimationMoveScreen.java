package com.cobblemon.khataly.modhm.screen.custom;

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
            this.x += 12;
        }

        boolean isOffScreen(int screenWidth) {
            return this.x > screenWidth;
        }
    }

    public AnimationMoveScreen(Text title, RenderablePokemon pokemon) {
        super(title);
        this.pokemon = pokemon;

        // Inizializza il FloatingState correttamente
        floatingState.setCurrentPose("stand");
        floatingState.setCurrentAspects(pokemon.getAspects());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Sfondo blu centrale
        int stripHeight = 120;
        int stripY = (this.height - stripHeight) / 2;
        context.fill(0, stripY, this.width, stripY + stripHeight, 0xFF3A80D9);

        // Disegna le linee di velocità
        for (SpeedLine line : lines) {
            context.fill(line.x, line.y, line.x + line.width, line.y + line.height, 0xFFFFFFFF);
        }

        // Posizione e scala per centrare il Pokémon
        int centerX = this.width / 2;
        int centerY = this.height / 2 + 10; // offset verticale per centrare meglio
        float pokemonScale = 4.5f; // scala sufficiente per rendere visibile qualsiasi modello

        // Renderizza il Pokémon
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(pokemonScale, pokemonScale, 1f);

        PokemonGuiUtilsKt.drawProfilePokemon(
                pokemon,                             // RenderablePokemon completo
                context.getMatrices(),
                new Quaternionf().rotateXYZ(0f, (float) Math.toRadians(180), 0f),
                PoseType.STAND,
                floatingState,
                delta,
                5f,   // scale qui può rimanere 1.0f perché scalato sopra
                true,   // applyProfileTransform
                true,   // applyBaseScale
                1f, 1f, 1f, 1f
        );

        context.getMatrices().pop();

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        timer++;

        // Genera linee di velocità ogni 2 tick
        if (timer % 2 == 0) {
            int offset = random.nextInt(60) - 30;
            lines.add(new SpeedLine(-50, this.height / 2 + offset));
        }

        // Aggiorna e rimuove linee fuori schermo
        Iterator<SpeedLine> it = lines.iterator();
        while (it.hasNext()) {
            SpeedLine line = it.next();
            line.move();
            if (line.isOffScreen(this.width)) {
                it.remove();
            }
        }

        // Chiudi lo schermo dopo 40 tick (~2 secondi)
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
