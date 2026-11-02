package io.github.lights_out;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayDeque;

/** Dialogue bar at the bottom of the screen, styled like the cutscene. Lines type out one by one. */
public class DialogueBox {
    private static final float CHARS_PER_SECOND = 40f; // same typing speed as the cutscene
    private static final float HOLD_TIME = 2.2f;       // seconds a finished line stays on screen

    private final Texture pixel;
    private final GlyphLayout layout = new GlyphLayout();
    private final ArrayDeque<String> queue = new ArrayDeque<>();

    private String text = "";
    private float charTimer = 0f;
    private float holdTimer = 0f;
    private boolean active = false;

    public DialogueBox() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
    }

    /** Shows one or more lines, one after another. Replaces whatever is showing. */
    public void say(String... lines) {
        queue.clear();
        for (String l : lines) queue.add(l);
        next();
    }

    private void next() {
        String line = queue.poll();
        if (line == null) {
            active = false;
            return;
        }
        text = line;
        charTimer = 0f;
        holdTimer = 0f;
        active = true;
    }

    public void update(float delta) {
        if (!active) return;
        charTimer += delta;
        if ((int) (charTimer * CHARS_PER_SECOND) >= text.length()) {
            holdTimer += delta;
            if (holdTimer >= HOLD_TIME) next();
        }
    }

    /** Call while the batch is running (between begin and end). */
    public void render(SpriteBatch batch, BitmapFont font) {
        if (!active) return;
        int visible = Math.min(text.length(), (int) (charTimer * CHARS_PER_SECOND));
        String shown = text.substring(0, visible);

        // dark bar, same look as the cutscene
        batch.setColor(0f, 0f, 0f, 0.75f);
        batch.draw(pixel, 140, 30, 1000, 115);
        batch.setColor(Color.WHITE);

        // speaker name (blue, like "YOU" in the cutscene)
        font.getData().setScale(1.5f);
        font.setColor(0.5f, 0.8f, 1f, 1f);
        font.draw(batch, "YOU", 170, 135);

        // the line itself
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
        layout.setText(font, shown, Color.WHITE, 940, Align.left, true);
        font.draw(batch, layout, 170, 105);

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    public void dispose() {
        pixel.dispose();
    }
}
