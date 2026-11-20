package io.github.lights_out;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/**
 * Sealing a leaking door.
 *
 * Hold the mouse on the gap under the door and drag the cloth along the glowing line to block it.
 * Mistakes (each one costs a chance):
 *   - dragging outside the line
 *   - dragging sloppily and skipping spots (the skipped spots flash red, go back over them)
 * When every spot of the gap is covered, the door is sealed (bottomdoor_towel.png).
 */
public class TowelMinigame {
    // The door picture (3000x1687) and where it is drawn (world units)
    private static final float IMG_W = 1100f;
    private static final float IMG_H = IMG_W * 1687f / 3000f;
    private static final float IMG_X = (Main.WORLD_WIDTH - IMG_W) / 2f;
    private static final float IMG_Y = 20f;

    // The gap to trace: its two ends as fractions of the door picture (u = across, v = down)
    private static final float LINE_U0 = 0.107f, LINE_V0 = 0.473f;
    private static final float LINE_U1 = 0.900f, LINE_V1 = 0.470f;

    // Rules
    private static final float TOLERANCE = 22f;        // how far from the line you may drag (world units)
    private static final float REENTER = 0.8f;         // after leaving the line you must come back this close (x TOLERANCE)
    private static final int BINS = 60;                // the gap is split in this many spots, every spot must be covered
    private static final float MAX_JUMP = 60f;         // the cloth may move this far in one frame; more = skipped spots
    private static final float FAIL_COOLDOWN = 0.7f;   // pause between two mistakes (so one slip is one mistake)
    private static final float FLASH_TIME = 0.45f;
    private static final float GAP_SHOW_TIME = 2.2f;   // how long the skipped spots flash red
    private static final float FINISH_DELAY = 1.8f;    // pause after the door is sealed

    // Look
    private static final float STRIP_W = 20f;          // thickness of the cloth strip on the gap
    private static final float CLOTH_SIZE = 170f;      // the cloth that follows the mouse

    public static final int FAIL_OUTSIDE = 0;
    public static final int FAIL_SKIPPED = 1;

    // The crumpled cloth on the floor and the cloth in the hand (parts of the pictures that are not empty)
    private static final int[] FLOOR_CLOTH = {29, 692, 1488, 736};
    private static final int[] HAND_CLOTH = {82, 387, 1285, 1279};

    private enum Phase { DRAW, FINISH }

    private final Texture pixel;
    private final Texture dry, water, sealed;
    private final Texture clothFloorTex, clothHandTex;
    private final TextureRegion clothFloor, clothHand;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final GlyphLayout layout = new GlyphLayout();

    // the line in world units
    private final float ax, ay, bx, by, len, dx, dy;

    private final boolean[] covered = new boolean[BINS];
    private int coveredCount = 0;

    private SeepDoor target;
    private boolean active = false;
    private Phase phase = Phase.DRAW;

    private boolean dragging = false;   // the cloth is held on the gap
    private boolean outside = false;    // the cloth left the line (waiting for it to come back)
    private float lastT = -1f;          // where along the line the cloth was last frame (-1 = nowhere)
    private boolean strokeFailed = false;
    private float clothX, clothY;     // the cloth in the hand
    private boolean hasMouse = false;

    private float failCooldown = 0f;
    private float flash = 0f;
    private float shake = 0f;
    private float gapTimer = 0f;
    private float statusTimer = 0f;
    private String statusText = "";
    private boolean statusGood = false;
    private float finishTimer = 0f;
    private float time = 0f;

    private int failsPending = 0;
    private int lastFailReason = FAIL_OUTSIDE;
    private boolean finishedEvent = false;

    public TowelMinigame() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        dry = load("bottomdoor.png");
        water = load("bottomdoor_water.png");
        sealed = load("bottomdoor_towel.png");
        clothFloorTex = load("cloth_not_picked_up.png");
        clothHandTex = load("cloth_picked_up.png");
        clothFloor = new TextureRegion(clothFloorTex, FLOOR_CLOTH[0], FLOOR_CLOTH[1], FLOOR_CLOTH[2], FLOOR_CLOTH[3]);
        clothHand = new TextureRegion(clothHandTex, HAND_CLOTH[0], HAND_CLOTH[1], HAND_CLOTH[2], HAND_CLOTH[3]);

        ax = IMG_X + LINE_U0 * IMG_W;
        ay = IMG_Y + (1f - LINE_V0) * IMG_H;
        bx = IMG_X + LINE_U1 * IMG_W;
        by = IMG_Y + (1f - LINE_V1) * IMG_H;
        len = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
        dx = (bx - ax) / len;
        dy = (by - ay) / len;
    }

    private Texture load(String file) {
        Texture t = new Texture(Gdx.files.internal(file), true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        return t;
    }

    public void open(SeepDoor door) {
        target = door;
        active = true;
        phase = Phase.DRAW;
        for (int i = 0; i < BINS; i++) covered[i] = false;
        coveredCount = 0;
        dragging = false;
        outside = false;
        lastT = -1f;
        strokeFailed = false;
        failCooldown = 0f;
        flash = 0f;
        shake = 0f;
        gapTimer = 0f;
        statusTimer = 0f;
        failsPending = 0;
        finishedEvent = false;
        hasMouse = false;
    }

    public void close() {
        active = false;
        target = null;
        dragging = false;
    }

    public boolean isActive() { return active; }
    public SeepDoor getTarget() { return target; }
    public int getFailReason() { return lastFailReason; }

    /** How many mistakes were made since the last time this was asked. */
    public int takeFails() {
        int f = failsPending;
        failsPending = 0;
        return f;
    }

    /** True once, when the gap is completely covered. */
    public boolean takeFinished() {
        boolean f = finishedEvent;
        finishedEvent = false;
        return f;
    }

    private void say(String text, boolean good, float seconds) {
        statusText = text;
        statusGood = good;
        statusTimer = seconds;
    }

    private void fail(int reason) {
        if (failCooldown > 0f) return;   // one slip = one mistake
        failCooldown = FAIL_COOLDOWN;
        failsPending++;
        lastFailReason = reason;
        flash = FLASH_TIME;
        shake = 0.4f;
        if (reason == FAIL_OUTSIDE) say("OFF THE LINE!", false, 1.2f);
        else say("YOU SKIPPED A SPOT!", false, 1.4f);
    }

    /** How far along the line (0 to len) the point is, and how far from the line it is. */
    private float alongLine(float x, float y) {
        return MathUtils.clamp((x - ax) * dx + (y - ay) * dy, 0f, len);
    }

    private float distFromLine(float x, float y) {
        float t = alongLine(x, y);
        float px = ax + dx * t, py = ay + dy * t;
        return (float) Math.sqrt((x - px) * (x - px) + (y - py) * (y - py));
    }

    private int binOf(float t) {
        return MathUtils.clamp((int) (t / len * BINS), 0, BINS - 1);
    }

    /** Covers every spot between two places on the line. */
    private void cover(float t0, float t1) {
        int a = binOf(Math.min(t0, t1)), b = binOf(Math.max(t0, t1));
        for (int i = a; i <= b; i++) {
            if (!covered[i]) {
                covered[i] = true;
                coveredCount++;
            }
        }
    }

    /** True if there are uncovered spots between covered ones (the player skipped them). */
    private boolean hasSkippedSpots() {
        int first = -1, last = -1;
        for (int i = 0; i < BINS; i++) {
            if (covered[i]) {
                if (first < 0) first = i;
                last = i;
            }
        }
        if (first < 0) return false;
        for (int i = first; i <= last; i++) if (!covered[i]) return true;
        return false;
    }

    private void endStroke() {
        // let go with spots skipped behind the cloth: a sloppy drag
        if (!strokeFailed && hasSkippedSpots()) {
            fail(FAIL_SKIPPED);
            strokeFailed = true;
        }
        if (hasSkippedSpots()) gapTimer = GAP_SHOW_TIME;
    }

    public void update(float delta, float mx, float my, boolean clicked, boolean down) {
        if (!active) return;
        time += delta;
        clothX = mx;
        clothY = my;
        hasMouse = true;
        if (flash > 0f) flash = Math.max(0f, flash - delta);
        if (shake > 0f) shake = Math.max(0f, shake - delta);
        if (failCooldown > 0f) failCooldown = Math.max(0f, failCooldown - delta);
        if (gapTimer > 0f) gapTimer = Math.max(0f, gapTimer - delta);
        if (statusTimer > 0f) statusTimer = Math.max(0f, statusTimer - delta);

        if (phase == Phase.FINISH) {
            finishTimer -= delta;
            if (finishTimer <= 0f) close();
            return;
        }

        if (!down) {
            if (dragging) endStroke();
            dragging = false;
            outside = false;
            lastT = -1f;
            return;
        }

        float t = alongLine(mx, my);
        float dist = distFromLine(mx, my);

        if (!dragging) {
            // pressing on the gap (or dragging onto it) starts a stroke
            if (dist <= TOLERANCE) {
                dragging = true;
                outside = false;
                strokeFailed = false;
                lastT = t;
                cover(t, t);
            }
        } else if (outside) {
            // the cloth left the line: it only counts again once it is back close to the gap
            if (dist <= TOLERANCE * REENTER) {
                outside = false;
                lastT = t;
                cover(t, t);
            }
        } else if (dist > TOLERANCE) {
            outside = true;
            lastT = -1f;
            fail(FAIL_OUTSIDE);
        } else {
            float jump = Math.abs(t - lastT);
            float maxJump = MAX_JUMP * Math.max(1f, delta * 60f);
            if (jump <= maxJump) {
                cover(lastT, t);
            } else {
                cover(t, t);            // the spots in between stay uncovered
                gapTimer = GAP_SHOW_TIME;
                strokeFailed = true;
                fail(FAIL_SKIPPED);
            }
            lastT = t;
        }

        if (coveredCount >= BINS) {
            phase = Phase.FINISH;
            finishTimer = FINISH_DELAY;
            finishedEvent = true;
            dragging = false;
            say("SEALED!", true, 99f);
        }
    }

    /** The cloth on the storage room floor (centre x, y and width). */
    public void drawFloorCloth(SpriteBatch batch, float cx, float cy, float width, float shade) {
        float h = width * clothFloor.getRegionHeight() / clothFloor.getRegionWidth();
        batch.setColor(shade, shade, shade, 1f);
        batch.draw(clothFloor, cx - width / 2f, cy - h / 2f, width, h);
        batch.setColor(Color.WHITE);
    }

    private float lineX(float t) { return ax + dx * t; }
    private float lineY(float t) { return ay + dy * t; }

    private void centered(SpriteBatch batch, BitmapFont font, String text, float cx, float y) {
        layout.setText(font, text);
        font.draw(batch, text, cx - layout.width / 2f, y);
    }

    public void render(SpriteBatch batch, BitmapFont font) {
        if (!active) return;
        shapes.setProjectionMatrix(batch.getProjectionMatrix());
        float progress = (float) coveredCount / BINS;
        boolean done = phase == Phase.FINISH;

        // 1) backdrop and the door picture
        batch.begin();
        batch.setColor(0f, 0f, 0f, 0.92f);
        batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);

        float sx = shake > 0f ? MathUtils.random(-1f, 1f) * shake * 14f : 0f;
        float sy = shake > 0f ? MathUtils.random(-1f, 1f) * shake * 14f : 0f;
        batch.setColor(0.06f, 0.07f, 0.09f, 1f);
        batch.draw(pixel, IMG_X - 10f + sx, IMG_Y - 10f + sy, IMG_W + 20f, IMG_H + 20f);

        float tint = 1f - 0.5f * (flash > 0f ? flash / FLASH_TIME : 0f);
        if (done) {
            batch.setColor(1f, 1f, 1f, 1f);
            batch.draw(sealed, IMG_X + sx, IMG_Y + sy, IMG_W, IMG_H);
        } else {
            batch.setColor(1f, tint, tint, 1f);
            batch.draw(dry, IMG_X + sx, IMG_Y + sy, IMG_W, IMG_H);
            batch.setColor(1f, tint, tint, 1f - 0.45f * progress);   // the water slows down as the gap closes
            batch.draw(water, IMG_X + sx, IMG_Y + sy, IMG_W, IMG_H);
        }
        batch.setColor(Color.WHITE);
        batch.end();

        // 2) the line and the cloth strip
        if (!done) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            float binLen = len / BINS;
            float pulse = 0.5f + 0.5f * MathUtils.sin(time * 6f);
            boolean blinkRed = gapTimer > 0f && MathUtils.sin(time * 16f) > 0f;

            int first = -1, last = -1;
            for (int i = 0; i < BINS; i++) {
                if (covered[i]) {
                    if (first < 0) first = i;
                    last = i;
                }
            }

            for (int i = 0; i < BINS; i++) {
                float t0 = i * binLen, t1 = (i + 1) * binLen;
                float x0 = lineX(t0) + sx, y0 = lineY(t0) + sy, x1 = lineX(t1) + sx, y1 = lineY(t1) + sy;
                if (covered[i]) {
                    shapes.setColor(0.18f, 0.18f, 0.2f, 1f);
                    shapes.rectLine(x0, y0, x1, y1, STRIP_W + 5f);
                    shapes.setColor(0.62f, 0.62f, 0.64f, 1f);
                    shapes.rectLine(x0, y0, x1, y1, STRIP_W);
                } else if (blinkRed && i > first && i < last) {
                    shapes.setColor(1f, 0.2f, 0.15f, 0.9f);
                    shapes.rectLine(x0, y0, x1, y1, STRIP_W);
                } else if (i % 2 == 0) {
                    // the dashed guide
                    shapes.setColor(1f, 0.85f, 0.2f, 0.45f + 0.4f * pulse);
                    shapes.rectLine(x0, y0, x1, y1, 5f);
                }
            }

            // the two ends
            shapes.setColor(0.3f, 0.9f, 0.45f, 0.5f + 0.4f * pulse);
            shapes.circle(ax + sx, ay + sy, 9f, 20);
            shapes.setColor(1f, 0.85f, 0.2f, 0.5f + 0.4f * pulse);
            shapes.circle(bx + sx, by + sy, 9f, 20);
            shapes.end();
        }

        // 3) texts, the cloth in the hand and the flash
        batch.begin();
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
        centered(batch, font, "Seal the " + (target != null ? target.getName() : "door") + "!",
            Main.WORLD_WIDTH / 2f, 706);

        font.getData().setScale(1.1f);
        font.setColor(0.65f, 0.65f, 0.65f, 1f);
        centered(batch, font, done ? "The water is stopped."
                : "Hold the mouse and drag the cloth along the gap. Stay on the line, skip nothing!   ESC = leave",
            Main.WORLD_WIDTH / 2f, 676);

        if (statusTimer > 0f) {
            font.getData().setScale(2f);
            if (statusGood) font.setColor(0.5f, 1f, 0.6f, 1f);
            else font.setColor(1f, 0.3f, 0.2f, 1f);
            centered(batch, font, statusText, Main.WORLD_WIDTH / 2f, 625);
        }

        font.getData().setScale(1.1f);
        font.setColor(0.55f, 0.55f, 0.6f, 1f);
        centered(batch, font, "GAP  " + (int) (progress * 100f) + "%", Main.WORLD_WIDTH / 2f, 18f);

        if (!done && hasMouse) {
            float w = CLOTH_SIZE;
            float h = w * clothHand.getRegionHeight() / clothHand.getRegionWidth();
            batch.setColor(1f, 1f, 1f, dragging ? 1f : 0.8f);
            batch.draw(clothHand, clothX - w / 2f, clothY - h * 0.55f, w, h);
        }

        if (flash > 0f) {
            batch.setColor(1f, 0.35f, 0.15f, 0.5f * flash / FLASH_TIME);
            batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
        }
        batch.setColor(Color.WHITE);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    public void dispose() {
        pixel.dispose();
        dry.dispose();
        water.dispose();
        sealed.dispose();
        clothFloorTex.dispose();
        clothHandTex.dispose();
        shapes.dispose();
    }
}
