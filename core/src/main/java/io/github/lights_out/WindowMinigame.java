package io.github.lights_out;


import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;


/** The "click when the line is in the green" minigame for closing a window. */
public class WindowMinigame {
    public enum Result { NONE, SUCCESS, FAIL }


    // The window in the center of the screen (where the CLOSED window sits)
    private static final float WIN_X = 565f, WIN_Y = 265f, WIN_W = 150f, WIN_H = 335f;
    // The white circle behind the window (center and size)
    private static final float CIRCLE_X = 640f, CIRCLE_Y = 432f, CIRCLE_SIZE = 440f;
    private static final float CIRCLE_ALPHA = 0.2f;  // how visible the circle is (0 to 1)
    // The bar and button, below the window
    private static final float BAR_X = 410f, BAR_Y = 185f, BAR_W = 340f, BAR_H = 24f;
    private static final float BTN_X = 770f, BTN_Y = 179f, BTN_W = 100f, BTN_H = 36f;


    private static final float GREEN_START = 0.30f;  // size of the green part at the start (0 to 1)
    private static final float GREEN_SHRINK = 0.60f; // after a hit the green becomes this much of its size (smaller = shrinks more)
    private static final float GREEN_MIN = 0.04f;    // the green never gets smaller than this
    private static final float LINE_SPEED = 0.8f;    // how fast the line moves. Bigger = harder
    private static final float FLASH_TIME = 0.35f;   // how long the bar flashes after a click
    private static final float FINISH_DELAY = 1.0f;  // pause after the window closes, before leaving


    private final Texture pixel;
    private final Texture circle;
    private final GlyphLayout layout = new GlyphLayout();


    private GameWindow target;
    private boolean active = false;
    private boolean finishing = false;
    private float finishTimer = 0f;


    private float linePos = 0f;   // 0 to 1 along the bar
    private float lineDir = 1f;
    private float greenStart = 0.4f;
    private float greenWidth = GREEN_START;
    private final float[] sizeHistory = new float[16]; // the sizes the green had before each shrink
    private int historyCount = 0;
    private float flash = 0f;
    private boolean flashGood = true;


    public WindowMinigame() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();


        // soft white circle
        int size = 256;
        Pixmap cp = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float c = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = (float) Math.sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c;
                float a = MathUtils.clamp((1f - d) / 0.12f, 0f, 1f); // soft edge
                cp.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a));
            }
        }
        circle = new Texture(cp);
        circle.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        cp.dispose();
    }


    public void open(GameWindow window) {
        target = window;
        active = true;
        finishing = false;
        linePos = 0f;
        lineDir = 1f;
        flash = 0f;
        greenWidth = GREEN_START;
        historyCount = 0;
        greenStart = MathUtils.random(0.04f, 1f - greenWidth - 0.04f);
    }


    public void close() {
        active = false;
        finishing = false;
        target = null;
    }


    public boolean isActive() { return active; }
    public GameWindow getTarget() { return target; }


    public boolean buttonContains(float x, float y) {
        return active && !finishing
            && x >= BTN_X && x <= BTN_X + BTN_W && y >= BTN_Y && y <= BTN_Y + BTN_H;
    }


    /**
     * The player pressed the button.
     * A hit closes the window a step and makes the green smaller.
     * A miss opens the window a step more and brings the green back to its previous (bigger) size.
     * The green moves to a new spot after every click.
     */
    public Result press() {
        if (!active || finishing || target == null) return Result.NONE;


        boolean good = linePos >= greenStart && linePos <= greenStart + greenWidth;
        if (good) {
            target.closeStep();
            if (historyCount < sizeHistory.length) sizeHistory[historyCount++] = greenWidth; // remember this size
            greenWidth = Math.max(GREEN_MIN, greenWidth * GREEN_SHRINK);
        } else {
            target.openStep();
            if (historyCount > 0) greenWidth = sizeHistory[--historyCount]; // go back to the size it had before
        }


        flash = FLASH_TIME;
        flashGood = good;
        moveGreen();


        if (target.isClosed()) {
            finishing = true;
            finishTimer = FINISH_DELAY;
        }
        return good ? Result.SUCCESS : Result.FAIL;
    }


    private void moveGreen() {
        float old = greenStart;
        float max = 1f - greenWidth - 0.04f;
        for (int i = 0; i < 20; i++) {
            greenStart = MathUtils.random(0.04f, max);
            if (Math.abs(greenStart - old) >= 0.25f) break; // make sure it really moves
        }
    }


    public void update(float delta) {
        if (!active) return;
        if (flash > 0f) flash = Math.max(0f, flash - delta);


        if (finishing) {
            finishTimer -= delta;
            if (finishTimer <= 0f) close();
            return;
        }


        linePos += lineDir * LINE_SPEED * delta;
        if (linePos >= 1f) { linePos = 1f; lineDir = -1f; }
        else if (linePos <= 0f) { linePos = 0f; lineDir = 1f; }
    }


    public void render(SpriteBatch batch, BitmapFont font, WindowFrames frames, float mouseX, float mouseY) {
        if (!active || target == null) return;
        batch.begin();


        // dim everything behind
        batch.setColor(0f, 0f, 0f, 0.85f);
        batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);


        // faint white circle behind the window, so the black window can be seen
        batch.setColor(1f, 1f, 1f, CIRCLE_ALPHA);
        batch.draw(circle, CIRCLE_X - CIRCLE_SIZE / 2f, CIRCLE_Y - CIRCLE_SIZE / 2f, CIRCLE_SIZE, CIRCLE_SIZE);


        // title
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
        layout.setText(font, "Close the window!");
        font.draw(batch, "Close the window!", (Main.WORLD_WIDTH - layout.width) / 2f, 700);


        // the window, in the center
        frames.draw(batch, target.getShown(), WIN_X, WIN_Y, WIN_W, WIN_H, 1f);


        // bar: border, background, green part
        batch.setColor(0.4f, 0.35f, 0.35f, 1f);
        batch.draw(pixel, BAR_X - 3, BAR_Y - 3, BAR_W + 6, BAR_H + 6);
        batch.setColor(0.08f, 0.08f, 0.1f, 1f);
        batch.draw(pixel, BAR_X, BAR_Y, BAR_W, BAR_H);
        batch.setColor(0.15f, 0.75f, 0.25f, 1f);
        batch.draw(pixel, BAR_X + greenStart * BAR_W, BAR_Y, greenWidth * BAR_W, BAR_H);


        // flash after a click (green = hit, red = miss)
        if (flash > 0f) {
            if (flashGood) batch.setColor(0.3f, 1f, 0.4f, 0.5f * flash / FLASH_TIME);
            else batch.setColor(1f, 0.1f, 0.1f, 0.5f * flash / FLASH_TIME);
            batch.draw(pixel, BAR_X, BAR_Y, BAR_W, BAR_H);
        }


        // the moving line
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(pixel, BAR_X + linePos * BAR_W - 2f, BAR_Y - 6f, 4f, BAR_H + 12f);


        // the button beside the bar
        boolean hover = buttonContains(mouseX, mouseY);
        if (hover) batch.setColor(0.5f, 0.12f, 0.12f, 1f);
        else batch.setColor(0.25f, 0.25f, 0.28f, 1f);
        batch.draw(pixel, BTN_X, BTN_Y, BTN_W, BTN_H);
        batch.setColor(0.6f, 0.6f, 0.65f, 1f);
        batch.draw(pixel, BTN_X, BTN_Y, BTN_W, 2);
        batch.draw(pixel, BTN_X, BTN_Y + BTN_H - 2, BTN_W, 2);
        batch.draw(pixel, BTN_X, BTN_Y, 2, BTN_H);
        batch.draw(pixel, BTN_X + BTN_W - 2, BTN_Y, 2, BTN_H);
        batch.setColor(Color.WHITE);


        font.getData().setScale(1.3f);
        font.setColor(Color.WHITE);
        layout.setText(font, "CLOSE");
        font.draw(batch, "CLOSE", BTN_X + (BTN_W - layout.width) / 2f,
            BTN_Y + (BTN_H + layout.height) / 2f);


        // hint
        font.getData().setScale(1.1f);
        font.setColor(0.65f, 0.65f, 0.65f, 1f);
        String hint = "Press CLOSE (or SPACE) when the line is in the green.   ESC = leave";
        layout.setText(font, hint);
        font.draw(batch, hint, (Main.WORLD_WIDTH - layout.width) / 2f, 166);


        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }


    public void dispose() {
        pixel.dispose();
        circle.dispose();
    }
}



