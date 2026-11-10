package io.github.lights_out;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

/** The "move the bucket and catch the water drops" minigame for stopping a leak. */
public class LeakMinigame {
    public static final int CATCHES_NEEDED = 20;   // drops to catch for one puddle

    // Play area (world units)
    private static final float FIELD_X0 = 250f, FIELD_X1 = 1030f;
    private static final float FLOOR_Y = 170f;     // where the bucket stands and drops splash
    private static final float DRIP_Y = 600f;      // where drops start (the ceiling)
    private static final float[] DRIP_X = {330f, 480f, 640f, 800f, 950f};
    private static final float[] DROP_HEIGHT = {64f, 60f, 52f, 48f, 40f, 36f, 34f, 30f}; // size of water_drop_1 ... 8

    // Bucket
    private static final float BUCKET_H = 190f;
    private static final float MOUTH_FROM_TOP = 0.43f; // how far down the picture the opening is
    private static final float MOUTH_HALF = 0.40f;     // half width of the catching area (share of bucket width)
    private static final float BODY_HALF = 0.46f;      // half width of the whole bucket

    // Difficulty
    private static final float BASE_SPEED = 200f;      // how fast the first drops fall
    private static final float SPEED_PER_CATCH = 18f;  // drops fall this much faster after every catch
    private static final float START_SPAWN = 1.0f;     // seconds between drops at the start
    private static final float MIN_SPAWN = 0.6f;       // fastest rate of new drops
    private static final float SPAWN_FASTER = 0.02f;   // new drops come this much sooner after every catch

    private static final float FLASH_TIME = 0.4f;      // red flash after a miss
    private static final float FINISH_DELAY = 1.3f;    // pause after the 20th catch

    private static class Drop {
        Texture tex;
        float x, y, w, h, speed;   // y = the bottom of the drop
        boolean passed;
    }

    private static class Splash {
        float x, y, vx, vy, life;
    }

    private final Texture pixel;
    private final Texture bucket;
    private final Texture puddle;
    private final Texture[] drops = new Texture[8];
    private final GlyphLayout layout = new GlyphLayout();
    private final Array<Drop> falling = new Array<>();
    private final Array<Splash> splashes = new Array<>();

    private Leak target;
    private boolean active = false;
    private boolean finishing = false;
    private float finishTimer = 0f;

    private int caught = 0;
    private float spawnTimer = 0f;
    private float bucketX = (FIELD_X0 + FIELD_X1) / 2f;
    private float prevBucketX = bucketX;
    private float tilt = 0f;
    private float flash = 0f;
    private float time = 0f;
    private int missesPending = 0;
    private boolean finishedEvent = false;

    public LeakMinigame() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        bucket = new Texture(com.badlogic.gdx.Gdx.files.internal("leak_bucket.png"), true);
        bucket.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        puddle = new Texture(com.badlogic.gdx.Gdx.files.internal("water_puddle.png"), true);
        puddle.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        for (int i = 0; i < drops.length; i++) {
            drops[i] = new Texture(com.badlogic.gdx.Gdx.files.internal("water_drop_" + (i + 1) + ".png"), true);
            drops[i].setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        }
    }

    public void open(Leak leak) {
        target = leak;
        active = true;
        finishing = false;
        finishedEvent = false;
        caught = 0;
        spawnTimer = 0.8f;
        bucketX = (FIELD_X0 + FIELD_X1) / 2f;
        prevBucketX = bucketX;
        tilt = 0f;
        flash = 0f;
        missesPending = 0;
        falling.clear();
        splashes.clear();
    }

    public void close() {
        active = false;
        finishing = false;
        target = null;
        falling.clear();
        splashes.clear();
    }

    public boolean isActive() { return active; }
    public Leak getTarget() { return target; }

    /** How many drops were missed since the last time this was asked. */
    public int takeMisses() {
        int m = missesPending;
        missesPending = 0;
        return m;
    }

    /** True once, when the 20th drop has been caught. */
    public boolean takeFinished() {
        boolean f = finishedEvent;
        finishedEvent = false;
        return f;
    }

    private float bucketWidth() {
        return BUCKET_H * bucket.getWidth() / bucket.getHeight();
    }

    private void spawn() {
        Drop d = new Drop();
        int idx = MathUtils.random(DROP_HEIGHT.length - 1);
        d.tex = drops[idx];
        d.h = DROP_HEIGHT[idx];
        d.w = d.h * d.tex.getWidth() / d.tex.getHeight();
        d.x = DRIP_X[MathUtils.random(DRIP_X.length - 1)] + MathUtils.random(-8f, 8f);
        d.y = DRIP_Y - d.h;                           // starts just under the ceiling
        d.speed = BASE_SPEED + SPEED_PER_CATCH * caught; // later drops fall faster
        falling.add(d);
    }

    private void splash(float x, float y) {
        for (int i = 0; i < 7; i++) {
            Splash s = new Splash();
            s.x = x;
            s.y = y;
            s.vx = MathUtils.random(-130f, 130f);
            s.vy = MathUtils.random(110f, 260f);
            s.life = 0.45f;
            splashes.add(s);
        }
    }

    private void miss(float x, float y) {
        splash(x, y);
        missesPending++;
        flash = FLASH_TIME;
    }

    public void update(float delta, float mouseX) {
        if (!active) return;
        time += delta;
        if (flash > 0f) flash = Math.max(0f, flash - delta);

        // splash pieces
        for (int i = splashes.size - 1; i >= 0; i--) {
            Splash s = splashes.get(i);
            s.life -= delta;
            s.vy -= 900f * delta;
            s.x += s.vx * delta;
            s.y += s.vy * delta;
            if (s.life <= 0f) splashes.removeIndex(i);
        }

        if (finishing) {
            finishTimer -= delta;
            if (finishTimer <= 0f) close();
            return;
        }

        // the bucket follows the mouse
        float bw = bucketWidth();
        float targetX = MathUtils.clamp(mouseX, FIELD_X0 + bw / 2f, FIELD_X1 - bw / 2f);
        bucketX += (targetX - bucketX) * Math.min(1f, 20f * delta);
        if (delta > 0f) {
            float vx = (bucketX - prevBucketX) / delta;
            float wantTilt = -MathUtils.clamp(vx * 0.004f, -8f, 8f);
            tilt += (wantTilt - tilt) * Math.min(1f, 10f * delta);
        }
        prevBucketX = bucketX;

        // new drops
        spawnTimer -= delta;
        if (spawnTimer <= 0f) {
            spawn();
            spawnTimer = Math.max(MIN_SPAWN, START_SPAWN - SPAWN_FASTER * caught);
        }

        // falling drops
        float mouthY = FLOOR_Y + BUCKET_H * (1f - MOUTH_FROM_TOP);
        for (int i = falling.size - 1; i >= 0; i--) {
            Drop d = falling.get(i);
            float prevY = d.y;
            d.y -= d.speed * delta;

            if (!d.passed && prevY >= mouthY && d.y < mouthY) {
                d.passed = true;
                float dx = Math.abs(d.x - bucketX);
                if (dx <= bw * MOUTH_HALF) {            // caught!
                    falling.removeIndex(i);
                    caught++;
                    if (caught >= CATCHES_NEEDED) {
                        finishing = true;
                        finishTimer = FINISH_DELAY;
                        finishedEvent = true;
                        falling.clear();
                        return;
                    }
                    continue;
                } else if (dx <= bw * BODY_HALF) {      // hit the rim of the bucket
                    falling.removeIndex(i);
                    miss(d.x, mouthY);
                    continue;
                }
            }

            if (d.y <= FLOOR_Y) {                       // fell on the floor
                falling.removeIndex(i);
                miss(d.x, FLOOR_Y);
            }
        }
    }

    /** Draws a puddle in a room. */
    public void drawPuddle(SpriteBatch batch, Leak leak, float shade, float t) {
        float pulse = 0.9f + 0.1f * MathUtils.sin(t * 2f);
        batch.setColor(shade, shade, shade, pulse);
        batch.draw(puddle, leak.getX() - leak.getWidth() / 2f, leak.getY() - leak.getHeight() / 2f,
            leak.getWidth(), leak.getHeight());
        batch.setColor(Color.WHITE);
    }

    /** Draws the small bucket that replaces a puddle once its leak is stopped. */
    public void drawBucketSmall(SpriteBatch batch, Leak leak, float shade) {
        float h = leak.getWidth() * 0.7f;
        float w = h * bucket.getWidth() / bucket.getHeight();
        batch.setColor(shade, shade, shade, 1f);
        batch.draw(bucket, leak.getX() - w / 2f, leak.getY() - leak.getHeight() * 0.3f, w, h);
        batch.setColor(Color.WHITE);
    }

    public void render(SpriteBatch batch, BitmapFont font) {
        if (!active) return;
        batch.begin();

        // dim everything behind
        batch.setColor(0f, 0f, 0f, 0.88f);
        batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);

        // play area
        batch.setColor(0.06f, 0.07f, 0.09f, 1f);
        batch.draw(pixel, FIELD_X0 - 10, FLOOR_Y - 50, FIELD_X1 - FIELD_X0 + 20, DRIP_Y + 30 - (FLOOR_Y - 50));

        // ceiling with the drip points
        batch.setColor(0.03f, 0.03f, 0.04f, 1f);
        batch.draw(pixel, FIELD_X0 - 10, DRIP_Y, FIELD_X1 - FIELD_X0 + 20, 30);
        for (int i = 0; i < DRIP_X.length; i++) {
            float glint = 0.55f + 0.35f * MathUtils.sin(time * 3f + i * 1.7f);
            batch.setColor(1f, 1f, 1f, glint);
            batch.draw(drops[7], DRIP_X[i] - 7f, DRIP_Y - 8f, 14f, 20f);
        }

        // floor
        batch.setColor(0.12f, 0.12f, 0.15f, 1f);
        batch.draw(pixel, FIELD_X0 - 10, FLOOR_Y - 50, FIELD_X1 - FIELD_X0 + 20, 50);
        batch.setColor(0.3f, 0.3f, 0.35f, 1f);
        batch.draw(pixel, FIELD_X0 - 10, FLOOR_Y - 3, FIELD_X1 - FIELD_X0 + 20, 3);

        // falling drops
        batch.setColor(Color.WHITE);
        for (Drop d : falling) {
            batch.draw(d.tex, d.x - d.w / 2f, d.y, d.w, d.h);
        }

        // the bucket
        float bw = bucketWidth();
        batch.draw(bucket, bucketX - bw / 2f, FLOOR_Y, bw / 2f, 0f, bw, BUCKET_H,
            1f, 1f, tilt, 0, 0, bucket.getWidth(), bucket.getHeight(), false, false);

        // splashes
        for (Splash s : splashes) {
            batch.setColor(0.55f, 0.7f, 0.85f, MathUtils.clamp(s.life / 0.45f, 0f, 1f));
            batch.draw(pixel, s.x - 2.5f, s.y - 2.5f, 5f, 5f);
        }

        // red flash after a miss
        if (flash > 0f) {
            batch.setColor(0.8f, 0f, 0f, 0.35f * flash / FLASH_TIME);
            batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
        }
        batch.setColor(Color.WHITE);

        // title
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
        layout.setText(font, "Catch the water!");
        font.draw(batch, "Catch the water!", (Main.WORLD_WIDTH - layout.width) / 2f, 708);

        // hint
        font.getData().setScale(1.1f);
        font.setColor(0.65f, 0.65f, 0.65f, 1f);
        String hint = "Move the mouse to move the bucket. Catch " + CATCHES_NEEDED + " drops.   ESC = leave";
        layout.setText(font, hint);
        font.draw(batch, hint, (Main.WORLD_WIDTH - layout.width) / 2f, 680);

        // progress bar and counter
        float barX = 440f, barY = 645f, barW = 400f, barH = 14f;
        batch.setColor(0.3f, 0.3f, 0.35f, 1f);
        batch.draw(pixel, barX - 2, barY - 2, barW + 4, barH + 4);
        batch.setColor(0.05f, 0.05f, 0.08f, 1f);
        batch.draw(pixel, barX, barY, barW, barH);
        batch.setColor(0.3f, 0.6f, 0.9f, 1f);
        batch.draw(pixel, barX, barY, barW * caught / CATCHES_NEEDED, barH);
        batch.setColor(Color.WHITE);

        font.getData().setScale(1.3f);
        font.setColor(Color.WHITE);
        font.draw(batch, caught + " / " + CATCHES_NEEDED, barX + barW + 15f, barY + 14f);

        // message when done
        if (finishing) {
            font.getData().setScale(2.5f);
            font.setColor(0.5f, 0.85f, 1f, 1f);
            layout.setText(font, "Leak stopped!");
            font.draw(batch, "Leak stopped!", (Main.WORLD_WIDTH - layout.width) / 2f, 420);
        }

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    public void dispose() {
        pixel.dispose();
        bucket.dispose();
        puddle.dispose();
        for (Texture t : drops) t.dispose();
    }
}
