package io.github.lights_out;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class OpeningScene implements Screen {

    // mode: 0 = lights on, 1 = pitch black (brownout), 2 = dark room
    private static class Line {
        final String speaker, text;
        final int mode;
        Line(String speaker, String text, int mode) {
            this.speaker = speaker;
            this.text = text;
            this.mode = mode;
        }
    }

    private final Main game;
    private final FitViewport viewport = new FitViewport(Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
    private final Texture bedroom = new Texture("bedroom.png");
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final GlyphLayout layout = new GlyphLayout();

    private final Line[] lines = {
        new Line("Radio", "...Typhoon signal number 3 has been raised over Central Visayas...", 0),
        new Line("You", "Just one more match, then I'll eat.", 0),
        new Line("Radio", "...prepare emergency kits and keep a flashlight within reach...", 0),
        new Line("You", "Ugh, it's raining again.", 0),
        new Line("You", "Last enemy. I'm so close to winning...", 0),
        new Line("", "The lights go out.", 1),
        new Line("You", "No, no, no! I was about to win!", 2),
        new Line("You", "Was that a brownout? The radio said something about a typhoon...", 2),
        new Line("You", "It's so dark. I need to find the flashlight... maybe in the kitchen.", 2)
    };

    private final Lightning lightning;   // flashes once the room is dark

    private int index = 0;
    private float charTimer = 0f;
    private final float charsPerSecond = 40f;

    public OpeningScene(Main game) {
        this.game = game;
        lightning = new Lightning(game.audio, 3f, 6f, 14f);

        // lights still on: just the storm outside, no horror yet
        game.audio.setMood(0.4f, 0.12f, 0f);
        game.audio.setHorrorEvents(false);
        game.audio.setHeartbeat(0f);
    }

    /** Called when a new line starts: the sound follows the mood of the scene. */
    private void onLineChanged() {
        int mode = lines[index].mode;
        if (mode == 1) {                       // the brownout
            game.audio.play("sfx_power_down");
            game.audio.thunder(0.9f);
            game.audio.setMood(0.4f, 0.12f, 0f);
        } else if (mode == 2) {                // dark room: the horror drone creeps in
            game.audio.setMood(0.6f, 0.35f, 0.55f);
        }
    }

    @Override
    public void render(float delta) {
        Line line = lines[index];
        charTimer += delta;
        int visible = Math.min(line.text.length(), (int) (charTimer * charsPerSecond));
        boolean finishedTyping = visible >= line.text.length();

        // click or SPACE: finish the line, or go to the next one
        if (Gdx.input.justTouched() || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (!finishedTyping) {
                charTimer = 9999f;
            } else if (index < lines.length - 1) {
                index++;
                charTimer = 0f;
                onLineChanged();
                return;
            } else {
                game.setScreen(new GameScreen(game, 0));
                return;
            }
        }

        ScreenUtils.clear(0, 0, 0, 1);
        viewport.apply();
        game.batch.setProjectionMatrix(viewport.getCamera().combined);
        shapes.setProjectionMatrix(viewport.getCamera().combined);

        // background brightness depends on the mode (lower = darker)
        float b = line.mode == 0 ? 1f : (line.mode == 1 ? 0f : 0.3f);
        game.batch.begin();
        game.batch.setColor(b, b, b, 1f);
        game.batch.draw(bedroom, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
        game.batch.setColor(Color.WHITE);
        game.batch.end();

        // lightning flashes (only once the room is dark)
        lightning.setEnabled(line.mode == 2);
        lightning.update(delta);
        lightning.draw(shapes, 0.45f);

        // dialogue bar
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0, 0, 0, 0.75f);
        shapes.rect(140, 30, 1000, 150);
        shapes.end();

        // text
        BitmapFont font = game.font;
        game.batch.begin();

        if (!line.speaker.isEmpty()) {
            font.getData().setScale(1.5f);
            if (line.speaker.equals("Radio")) font.setColor(1f, 0.6f, 0.2f, 1f);
            else font.setColor(0.5f, 0.8f, 1f, 1f);
            font.draw(game.batch, line.speaker.toUpperCase(), 170, 160);
        }

        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
        layout.setText(font, line.text.substring(0, visible), Color.WHITE, 940, Align.left, true);
        font.draw(game.batch, layout, 170, 130);

        if (finishedTyping) {
            font.getData().setScale(1.2f);
            font.setColor(0.7f, 0.7f, 0.7f, 1f);
            font.draw(game.batch, "Click to continue", 980, 55);
        }

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        game.batch.end();
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
    @Override public void show() { }
    @Override public void pause() { }
    @Override public void resume() { }

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        bedroom.dispose();
        shapes.dispose();
    }
}
