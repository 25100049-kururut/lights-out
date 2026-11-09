package io.github.lights_out;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntArray;


/** An object you can find in a drawer. Clicks only count on the visible part of the picture. */
public class Item {
    private static final int MAX_PIXELS = 300;       // pictures are shrunk to this size when loaded
    private static final float SPECK_LIMIT = 0.02f;  // pieces smaller than this share of the main shape are erased


    private final String name;
    private final String file;
    private final boolean hazard;
    private final float longSide;     // size of the item's longest side on screen
    private final float rotation;     // degrees
    private float cx, cy;             // center position on screen (changes when dragged)


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


    /** Loads the picture, shrinks it, removes stray specks, and crops away the empty space. */
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


        removeSpecks(small, sw, sh);


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


    /** Erases tiny pieces that are not connected to the main shape (stray dots and lines). */
    private void removeSpecks(Pixmap pm, int width, int height) {
        int[] label = new int[width * height];   // 0 = not part of any piece (yet)
        IntArray sizes = new IntArray();         // sizes.get(i) = pixel count of piece i + 1
        IntArray stack = new IntArray();


        for (int start = 0; start < width * height; start++) {
            int sx = start % width;
            int sy = start / width;
            if (label[start] != 0 || (pm.getPixel(sx, sy) & 0xff) <= 40) continue;


            int id = sizes.size + 1;
            int count = 0;
            stack.clear();
            stack.add(start);
            label[start] = id;
            while (stack.size > 0) {
                int p = stack.pop();
                count++;
                int px = p % width;
                int py = p / width;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = px + dx;
                        int ny = py + dy;
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                        int n = ny * width + nx;
                        if (label[n] != 0 || (pm.getPixel(nx, ny) & 0xff) <= 40) continue;
                        label[n] = id;
                        stack.add(n);
                    }
                }
            }
            sizes.add(count);
        }


        int biggest = 0;
        for (int i = 0; i < sizes.size; i++) biggest = Math.max(biggest, sizes.get(i));


        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int id = label[y * width + x];
                if (id != 0 && sizes.get(id - 1) < biggest * SPECK_LIMIT) {
                    pm.drawPixel(x, y, 0); // fully transparent
                }
            }
        }
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


    public float getX() { return cx; }
    public float getY() { return cy; }
    public void setPosition(float x, float y) { cx = x; cy = y; }


    public void dispose() {
        if (texture != null) texture.dispose();
        if (hitMap != null) hitMap.dispose();
    }
}
