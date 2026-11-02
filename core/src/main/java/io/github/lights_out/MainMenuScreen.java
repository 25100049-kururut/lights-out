package io.github.lights_out;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class MainMenuScreen implements Screen {
    // Buttons
    private static final float BUTTON_SCALE = 0.42f;
    private static final float BUTTON_X = 90f;
    private static final float[] BUTTON_Y = {266f, 181f, 96f}; // Play, Settings, Quit (from the bottom)

    // Title (x, y from bottom, width). Height follows the picture's shape.
    private static final float TITLE_X = 166f;
    private static final float TITLE_Y = 415f;
    private static final float TITLE_WIDTH = 433f;

    // Flashlight glow (world position of the lens, size, strength)
    private static final float GLOW_X = 840f;
    private static final float GLOW_Y = 78f;
    private static final float GLOW_SIZE = 200f;     // bigger = wider glow
    private static final float GLOW_STRENGTH = 0.35f; // 0 to 1, higher = brighter

    // Lightning
    private static final float DARK_LEVEL = 0.35f;
    private static final float MIN_WAIT = 4f;
    private static final float MAX_WAIT = 9f;
    private static final float FLASH_LENGTH = 0.8f;

    private final Main game;
    private final FitViewport viewport = new FitViewport(Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
    private final Preferences prefs = Gdx.app.getPreferences("lightsout");

    private final Texture background = new Texture("menu_bg.png");
    private final Texture title = new Texture("title_text.png");
    private final Texture glow;
    private final String[] names = {"play", "settings", "quit"};
    private final Texture[] normal = new Texture[3];
    private final Texture[] selectedTex = new Texture[3];
    private final Rectangle[] areas = new Rectangle[3];

    private int selected = 0;
    private float messageTimer = 0f;
    private float time = 0f;

    private float nextStrike = 2.5f;
    private float flashTime = -1f;

    public MainMenuScreen(Main game) {
        this.game = game;
        background.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        title.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        glow = makeGlowTexture(256);
        for (int i = 0; i < 3; i++) {
            normal[i] = new Texture(names[i] + "_normal.png");
            selectedTex[i] = new Texture(names[i] + "_selected.png");
            normal[i].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            selectedTex[i].setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            areas[i] = new Rectangle(BUTTON_X, BUTTON_Y[i],
                selectedTex[i].getWidth() * BUTTON_SCALE,
                selectedTex[i].getHeight() * BUTTON_SCALE);
        }
    }

    /** Makes a soft round glow (bright in the middle, fading to nothing at the edge). */
    private Texture makeGlowTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float c = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = Math.min(1f, (float) Math.sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c);
                float a = (1f - d) * (1f - d);
                pm.drawPixel(x, y, Color.rgba8888(1f, 0.95f, 0.8f, a));
            }
        }
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    private float flashStrength() {
        if (flashTime < 0) return 0f;
        float t = flashTime;
        if (t < 0.08f) return 1f;
        if (t < 0.18f) return 0.15f;
        if (t < 0.30f) return 0.85f;
        return Math.max(0f, 1f - (t - 0.30f) / (FLASH_LENGTH - 0.30f));
    }

    @Override
    public void render(float delta) {
        time += delta;
        if (messageTimer > 0) messageTimer -= delta;

        // lightning timing
        if (flashTime >= 0) {
            flashTime += delta;
            if (flashTime > FLASH_LENGTH) flashTime = -1f;
        } else {
            nextStrike -= delta;
            if (nextStrike <= 0) {
                flashTime = 0f;
                nextStrike = MathUtils.random(MIN_WAIT, MAX_WAIT);
            }
        }

        viewport.apply();
        Vector2 mouse = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));

        boolean overButton = false;
        for (int i = 0; i < 3; i++) {
            if (areas[i].contains(mouse)) {
                selected = i;
                overButton = true;
            }
        }
        Gdx.graphics.setSystemCursor(overButton
            ? Cursor.SystemCursor.Hand
            : Cursor.SystemCursor.Arrow);

        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) selected = (selected + 1) % 3;
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) selected = (selected + 2) % 3;

        boolean clicked = Gdx.input.justTouched() && overButton;
        boolean enter = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
        if (clicked || enter) {
            choose(selected);
            return;
        }

        // background brightness: dark, lifted by the lightning
        float flash = flashStrength();
        float b = DARK_LEVEL + (1.15f - DARK_LEVEL) * flash;
        float r = Math.min(1f, b * 0.92f);
        float g = Math.min(1f, b * 0.97f);
        float bl = Math.min(1f, b);

        ScreenUtils.clear(0, 0, 0, 1);
        game.batch.setProjectionMatrix(viewport.getCamera().combined);
        game.batch.begin();

        // 1. background
        game.batch.setColor(r, g, bl, 1f);
        game.batch.draw(background, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);

        // 2. flashlight glow (added on top, so it stays bright even when the room is dark)
        game.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        float flicker = 1f + 0.04f * MathUtils.sin(time * 3f);
        float strength = Math.min(1f, GLOW_STRENGTH * flicker);
        game.batch.setColor(1f, 1f, 1f, strength);
        float big = GLOW_SIZE * flicker;
        game.batch.draw(glow, GLOW_X - big / 2f, GLOW_Y - big / 2f, big, big);
        game.batch.setColor(1f, 1f, 1f, strength * 0.6f);
        float small = GLOW_SIZE * 0.4f;
        game.batch.draw(glow, GLOW_X - small / 2f, GLOW_Y - small / 2f, small, small);
        game.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // 3. title (slightly dimmer between flashes)
        float tb = 0.8f + 0.2f * flash;
        game.batch.setColor(tb, tb, tb, 1f);
        float titleHeight = TITLE_WIDTH * title.getHeight() / title.getWidth();
        game.batch.draw(title, TITLE_X, TITLE_Y, TITLE_WIDTH, titleHeight);
        game.batch.setColor(Color.WHITE);

        // 4. buttons
        for (int i = 0; i < 3; i++) {
            Texture t = (i == selected) ? selectedTex[i] : normal[i];
            float w = t.getWidth() * BUTTON_SCALE;
            float h = t.getHeight() * BUTTON_SCALE;
            game.batch.draw(t, BUTTON_X, BUTTON_Y[i], w, h);
        }

        if (messageTimer > 0) {
            game.font.setColor(Color.WHITE);
            game.font.getData().setScale(1.8f);
            game.font.draw(game.batch, "(Settings are coming soon...)", 90, 60);
            game.font.getData().setScale(1f);
        }
        game.batch.end();
    }

    private void choose(int index) {
        if (index == 0) {
            int done = prefs.getInteger("tasksDone", 0);
            if (done > 0) game.setScreen(new GameScreen(game, done));
            else game.setScreen(new OpeningScene(game));
        } else if (index == 1) {
            messageTimer = 2.5f;
        } else {
            Gdx.app.exit();
        }
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
    @Override public void show() { }
    @Override public void pause() { }
    @Override public void resume() { }

    @Override
    public void hide() {
        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
        dispose();
    }

    @Override
    public void dispose() {
        background.dispose();
        title.dispose();
        glow.dispose();
        for (int i = 0; i < 3; i++) {
            normal[i].dispose();
            selectedTex[i].dispose();
        }
    }
}
