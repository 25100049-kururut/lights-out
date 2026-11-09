package io.github.lights_out;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

/** An object you can find in a drawer. Clicks only count on the visible part of the picture. */
public class Item {
    private static final int MAX_PIXELS = 300; // pictures are shrunk to this size when loaded

    private final String name;
    private final String file;
    private final boolean hazard;
    private final float cx, cy;       // center position on screen
    private final float longSide;     // size of the item's longest side on screen
    private final float rotation;     // degrees

    private Texture texture;
    private Pixmap hitMap;
    private int w, h;
    private float scale;
    private boolean visible = true;

    public Item(String name, String file, boolean hazard,
                float cx, float cy, float longSide, float rotation) {
        this.name = name;
        this.file = file;
        this.hazard = hazard;
        this.cx = cx;
        this.cy = cy;
        this.longSide = longSide;
        this.rotation = rotation;
    }

    /** Loads the picture, shrinks it, and crops away the empty space around it. */
    public void load() {
        Pixmap big = new Pixmap(Gdx.files.internal(file));
        float s = Math.min(1f, (float) MAX_PIXELS / Math.max(big.getWidth(), big.getHeight()));
        int sw = Math.max(1, Math.round(big.getWidth() * s));
        int sh = Math.max(1, Math.round(big.getHeight() * s));

        Pixmap small = new Pixmap(sw, sh, Pixmap.Format.RGBA8888);
        small.setBlending(Pixmap.Blending.None);
        small.setFilter(Pixmap.Filter.BiLinear);
        small.drawPixmap(big, 0, 0, big.getWidth(), big.getHeight(), 0, 0, sw, sh);
        big.dispose();

        int minX = sw, minY = sh, maxX = -1, maxY = -1;
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                if ((small.getPixel(x, y) & 0xff) > 40) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) { minX = 0; minY = 0; maxX = sw - 1; maxY = sh - 1; }

        w = maxX - minX + 1;
        h = maxY - minY + 1;
        hitMap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        hitMap.setBlending(Pixmap.Blending.None);
        hitMap.drawPixmap(small, 0, 0, minX, minY, w, h);
        small.dispose();

        texture = new Texture(hitMap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        scale = longSide / Math.max(w, h);
    }

    /** shade: 1 = normal, lower = darker. */
    public void draw(SpriteBatch batch, float shade) {
        if (!visible || texture == null) return;
        float dw = w * scale;
        float dh = h * scale;
        batch.setColor(shade, shade, shade, 1f);
        batch.draw(texture, cx - dw / 2f, cy - dh / 2f, dw / 2f, dh / 2f, dw, dh,
            1f, 1f, rotation, 0, 0, w, h, false, false);
    }

    /** True if the point (x, y) is on a visible part of the item. */
    public boolean contains(float x, float y) {
        if (!visible || hitMap == null) return false;
        float dx = x - cx;
        float dy = y - cy;
        float r = rotation * MathUtils.degreesToRadians;
        float cos = MathUtils.cos(r);
        float sin = MathUtils.sin(r);
        float lx = dx * cos + dy * sin;
        float ly = -dx * sin + dy * cos;
        int px = (int) (lx / scale + w / 2f);
        int py = (int) (h / 2f - ly / scale);
        if (px < 0 || py < 0 || px >= w || py >= h) return false;
        return (hitMap.getPixel(px, py) & 0xff) > 40;
    }

    public String getName() { return name; }
    public boolean isHazard() { return hazard; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public void dispose() {
        if (texture != null) texture.dispose();
        if (hitMap != null) hitMap.dispose();
    }
}
