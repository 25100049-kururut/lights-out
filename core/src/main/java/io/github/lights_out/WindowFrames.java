package io.github.lights_out;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

/** The 5 window pictures (window_0 = closed ... window_4 = wide open). Fades smoothly between them. */
public class WindowFrames {
    private static final int FRAMES = 5;

    // Measured from the window pictures (pixels): the whole picture, and the CLOSED window inside it
    private static final float CANVAS_W = 1536f;
    private static final float CANVAS_H = 2048f;
    private static final float CLOSED_X = 423f;
    private static final float CLOSED_TOP = 311f;
    private static final float CLOSED_W = 690f;
    private static final float CLOSED_H = 1529f;

    private final Texture[] tex = new Texture[FRAMES];

    public WindowFrames() {
        for (int i = 0; i < FRAMES; i++) {
            tex[i] = new Texture(Gdx.files.internal("window_" + i + ".png"), true); // true = smooth when shrunk
            tex[i].setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        }
    }

    /**
     * Draws a window. (x, y, w, h) is where the CLOSED window sits. The open shutters
     * swing out past that box. level: 0 = closed ... 4 = wide open (decimals blend two pictures).
     * shade: 1 = normal, lower = darker.
     */
    public void draw(SpriteBatch batch, float level, float x, float y, float w, float h, float shade) {
        float sx = w / CLOSED_W;
        float sy = h / CLOSED_H;
        float left = x - CLOSED_X * sx;
        float bottom = y - (CANVAS_H - (CLOSED_TOP + CLOSED_H)) * sy;
        float dw = CANVAS_W * sx;
        float dh = CANVAS_H * sy;

        int lo = MathUtils.clamp((int) Math.floor(level), 0, FRAMES - 1);
        int hi = Math.min(FRAMES - 1, lo + 1);
        float frac = level - lo;

        batch.setColor(shade, shade, shade, 1f);
        batch.draw(tex[lo], left, bottom, dw, dh);
        if (hi != lo && frac > 0.01f) {
            batch.setColor(shade, shade, shade, frac);
            batch.draw(tex[hi], left, bottom, dw, dh);
        }
        batch.setColor(Color.WHITE);
    }

    public void dispose() {
        for (Texture t : tex) t.dispose();
    }
}
