package io.github.lights_out;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/**
 * Locking a main door.
 *
 * Phase 1: spam CLICK or SPACE to fill the bar before it empties completely (empty = jumpscare).
 * Phase 2: a dial with a spinning red line. Press the letter in the middle while the line is
 *          inside the white zone. Do it CHECKS_NEEDED times to lock the door.
 *          A mistake gives the fail effect and sends you back to phase 1 (fill the bar again).
 *
 * The doorknob picture changes as the checks are passed (see FRAME_FOR_SUCCESS).
 */
public class DoorMinigame {
    // Layout (world units)
    private static final float KNOB_CX = 360f, KNOB_CY = 360f, KNOB_SIZE = 480f;  // the doorknob picture
    private static final float PANEL_CX = 900f;                                    // middle of the right side
    private static final float BAR_W = 520f, BAR_H = 54f, BAR_Y = 345f;            // the spam bar
    private static final float DIAL_CY = 385f, DIAL_R = 150f;                      // the dial
    private static final float PIPS_Y = 215f;                                      // the 3 progress dots

    // Rules
    private static final int CHECKS_NEEDED = 3;                // letters to get right in a row
    /** Which doorknob picture (0 to 2) is shown after 0, 1, 2 and 3 checks passed. */
    private static final int[] FRAME_FOR_SUCCESS = {0, 1, 2, 2};

    // Phase 1: the spam bar
    private static final float START_LEVEL = 0.4f;             // how full the bar starts (0 to 1)
    private static final float PRESS_ADD = 0.07f;              // how much each click / SPACE adds
    private static final float DRAIN_BASE = 0.22f;             // how fast the bar empties (per second)
    private static final float DRAIN_PER_SUCCESS = 0.05f;      // ... and how much faster after each passed check
    private static final float GRACE_TIME = 0.8f;              // the bar waits this long before it starts emptying

    // Phase 2: the dial (angles are degrees clockwise from the top)
    private static final float NEEDLE_SPEED = 220f;            // degrees per second
    private static final float NEEDLE_PER_SUCCESS = 30f;       // faster after each passed check
    private static final float ZONE_WIDTH = 62f;               // size of the white zone
    private static final float ZONE_SHRINK = 8f;               // the zone gets smaller after each passed check
    private static final float ZONE_MIN_WIDTH = 30f;
    private static final float ZONE_MIN_START = 110f;          // the zone is never too close to the start
    private static final float ZONE_MAX_START = 250f;
    private static final float TOLERANCE = 4f;                 // forgiving degrees after the end of the zone
    private static final float CHECK_DELAY = 0.9f;             // pause before the line starts moving

    private static final float FAIL_PAUSE = 0.7f;              // pause after a mistake, then the bar comes back
    private static final float FLASH_TIME = 0.45f;
    private static final float FINISH_DELAY = 1.3f;            // pause after the door is locked

    public static final int FAIL_WRONG_KEY = 0;
    public static final int FAIL_TOO_EARLY = 1;
    public static final int FAIL_TOO_SLOW = 2;

    private enum Phase { MASH, CHECK, PAUSE, FINISH, LOST }

    private final Texture pixel;
    private final Texture[] knob = new Texture[3];
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final GlyphLayout layout = new GlyphLayout();

    private MainDoor target;
    private boolean active = false;
    private Phase phase = Phase.MASH;

    private int successes = 0;
    private float level = START_LEVEL;
    private float grace = GRACE_TIME;
    private float kick = 0f;           // little bump of the bar when you click

    private float needle = 0f;
    private float zoneStart = 150f;
    private float zoneWidth = ZONE_WIDTH;
    private int targetKey = Input.Keys.E;
    private float checkDelay = 0f;

    private float pauseTimer = 0f;
    private float finishTimer = 0f;
    private float flash = 0f;
    private boolean flashGood = false;
    private float shake = 0f;
    private float pop = 0f;
    private float statusTimer = 0f;
    private boolean statusGood = false;
    private String statusText = "";
    private float time = 0f;

    private int failsPending = 0;
    private int lastFailReason = FAIL_WRONG_KEY;
    private boolean finishedEvent = false;
    private boolean drainedEvent = false;

    public DoorMinigame() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        for (int i = 0; i < knob.length; i++) {
            knob[i] = new Texture(Gdx.files.internal("doorknob_" + (i + 1) + ".png"), true);
            knob[i].setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        }
    }

    public void open(MainDoor door) {
        target = door;
        active = true;
        successes = 0;
        startMash();
        flash = 0f;
        shake = 0f;
        pop = 0f;
        statusTimer = 0f;
        failsPending = 0;
        finishedEvent = false;
        drainedEvent = false;
    }

    public void close() {
        active = false;
        target = null;
    }

    public boolean isActive() { return active; }
    public MainDoor getTarget() { return target; }
    public int getFailReason() { return lastFailReason; }

    /** How many mistakes were made since the last time this was asked. */
    public int takeFails() {
        int f = failsPending;
        failsPending = 0;
        return f;
    }

    /** True once, when the door has been locked. */
    public boolean takeFinished() {
        boolean f = finishedEvent;
        finishedEvent = false;
        return f;
    }

    /** True once, when the bar ran completely empty (jumpscare!). */
    public boolean takeDrained() {
        boolean d = drainedEvent;
        drainedEvent = false;
        return d;
    }

    private void startMash() {
        phase = Phase.MASH;
        level = START_LEVEL;
        grace = GRACE_TIME;
        kick = 0f;
    }

    private void startCheck() {
        phase = Phase.CHECK;
        needle = 0f;
        checkDelay = CHECK_DELAY;
        targetKey = Input.Keys.A + MathUtils.random(25);
        zoneWidth = Math.max(ZONE_MIN_WIDTH, ZONE_WIDTH - ZONE_SHRINK * successes);
        zoneStart = MathUtils.random(ZONE_MIN_START, ZONE_MAX_START);
    }

    private void say(String text, boolean good, float seconds) {
        statusText = text;
        statusGood = good;
        statusTimer = seconds;
    }

    private void fail(int reason) {
        failsPending++;
        lastFailReason = reason;
        flash = FLASH_TIME;
        flashGood = false;
        shake = 0.45f;
        say(reason == FAIL_WRONG_KEY ? "WRONG KEY!" : reason == FAIL_TOO_EARLY ? "TOO EARLY!" : "TOO SLOW!",
            false, 1.4f);
        phase = Phase.PAUSE;
        pauseTimer = FAIL_PAUSE;
    }

    private void success() {
        successes++;
        pop = 1f;
        flash = FLASH_TIME * 0.6f;
        flashGood = true;
        if (successes >= CHECKS_NEEDED) {
            phase = Phase.FINISH;
            finishTimer = FINISH_DELAY;
            finishedEvent = true;
            say("LOCKED!", true, 99f);
        } else {
            say("Nice! " + (CHECKS_NEEDED - successes) + " more", true, 1.2f);
            startCheck();
        }
    }

    /** A letter key pressed this frame (the right one wins if several were pressed), or -1. */
    private int pressedLetter() {
        int other = -1;
        for (int k = Input.Keys.A; k <= Input.Keys.Z; k++) {
            if (Gdx.input.isKeyJustPressed(k)) {
                if (k == targetKey) return k;
                other = k;
            }
        }
        return other;
    }

    public void update(float delta, boolean clicked) {
        if (!active) return;
        time += delta;
        if (flash > 0f) flash = Math.max(0f, flash - delta);
        if (shake > 0f) shake = Math.max(0f, shake - delta);
        if (pop > 0f) pop = Math.max(0f, pop - delta * 3.5f);
        if (kick > 0f) kick = Math.max(0f, kick - delta * 8f);
        if (statusTimer > 0f) statusTimer = Math.max(0f, statusTimer - delta);

        switch (phase) {
            case MASH: {
                if (grace > 0f) grace -= delta;
                else level -= (DRAIN_BASE + DRAIN_PER_SUCCESS * successes) * delta;

                if (clicked || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                    level = Math.min(1f, level + PRESS_ADD);
                    kick = 1f;
                }

                if (level >= 1f) {
                    startCheck();
                } else if (level <= 0f) {
                    level = 0f;
                    phase = Phase.LOST;
                    drainedEvent = true;
                }
                break;
            }
            case CHECK: {
                if (checkDelay > 0f) {
                    checkDelay -= delta;
                    break;
                }
                needle += (NEEDLE_SPEED + NEEDLE_PER_SUCCESS * successes) * delta;

                int key = pressedLetter();
                if (key != -1) {
                    if (key != targetKey) fail(FAIL_WRONG_KEY);
                    else if (needle < zoneStart) fail(FAIL_TOO_EARLY);
                    else if (needle <= zoneStart + zoneWidth + TOLERANCE) success();
                    else fail(FAIL_TOO_SLOW);
                } else if (needle > zoneStart + zoneWidth + TOLERANCE) {
                    fail(FAIL_TOO_SLOW);   // the line passed the zone without a key press
                }
                break;
            }
            case PAUSE: {
                pauseTimer -= delta;
                if (pauseTimer <= 0f) startMash();   // fill the bar again
                break;
            }
            case FINISH: {
                finishTimer -= delta;
                if (finishTimer <= 0f) close();
                break;
            }
            default:
                break;
        }
    }

    private void roundRect(float x, float y, float w, float h, float r) {
        shapes.rect(x + r, y, w - 2f * r, h);
        shapes.rect(x, y + r, w, h - 2f * r);
        shapes.circle(x + r, y + r, r, 16);
        shapes.circle(x + w - r, y + r, r, 16);
        shapes.circle(x + r, y + h - r, r, 16);
        shapes.circle(x + w - r, y + h - r, r, 16);
    }

    private void centered(SpriteBatch batch, BitmapFont font, String text, float cx, float y) {
        layout.setText(font, text);
        font.draw(batch, text, cx - layout.width / 2f, y);
    }

    public void render(SpriteBatch batch, BitmapFont font) {
        if (!active) return;
        shapes.setProjectionMatrix(batch.getProjectionMatrix());
        float barX = PANEL_CX - BAR_W / 2f;

        // 1) backdrop, doorknob, spam bar
        batch.begin();
        batch.setColor(0f, 0f, 0f, 0.92f);
        batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);

        float size = KNOB_SIZE * (1f + 0.06f * MathUtils.sin(pop * MathUtils.PI));
        float sx = shake > 0f ? MathUtils.random(-1f, 1f) * shake * 28f : 0f;
        float sy = shake > 0f ? MathUtils.random(-1f, 1f) * shake * 28f : 0f;
        float tint = 1f - 0.5f * (flash > 0f && !flashGood ? flash / FLASH_TIME : 0f);
        batch.setColor(1f, tint, tint, 1f);
        batch.draw(knob[FRAME_FOR_SUCCESS[Math.min(successes, FRAME_FOR_SUCCESS.length - 1)]],
            KNOB_CX - size / 2f + sx, KNOB_CY - size / 2f + sy, size, size);

        if (phase == Phase.MASH) {
            float pulse = level < 0.3f ? 0.5f + 0.5f * MathUtils.sin(time * 14f) : 0f;
            float bump = kick * 4f;
            batch.setColor(0.35f + 0.5f * pulse, 0.35f * (1f - pulse), 0.4f * (1f - pulse), 1f);
            batch.draw(pixel, barX - 5f, BAR_Y - 5f - bump, BAR_W + 10f, BAR_H + 10f + 2f * bump);
            batch.setColor(0.05f, 0.05f, 0.07f, 1f);
            batch.draw(pixel, barX, BAR_Y - bump, BAR_W, BAR_H + 2f * bump);
            batch.setColor(MathUtils.lerp(1f, 0.25f, level), MathUtils.lerp(0.15f, 0.9f, level), 0.2f, 1f);
            batch.draw(pixel, barX, BAR_Y - bump, BAR_W * level, BAR_H + 2f * bump);
        }
        batch.setColor(Color.WHITE);
        batch.end();

        // 2) progress dots and the dial
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < CHECKS_NEEDED; i++) {
            float px = PANEL_CX + (i - (CHECKS_NEEDED - 1) / 2f) * 56f;
            shapes.setColor(0.4f, 0.4f, 0.45f, 1f);
            shapes.circle(px, PIPS_Y, 15f, 24);
            if (i < successes) shapes.setColor(0.3f, 0.9f, 0.45f, 1f);
            else shapes.setColor(0.07f, 0.07f, 0.09f, 1f);
            shapes.circle(px, PIPS_Y, 11f, 24);
        }

        if (phase == Phase.CHECK || phase == Phase.PAUSE) {
            float cx = PANEL_CX, cy = DIAL_CY, r = DIAL_R;
            shapes.setColor(0.35f, 0.35f, 0.4f, 1f);
            shapes.circle(cx, cy, r + 6f, 72);
            shapes.setColor(0.07f, 0.07f, 0.09f, 1f);
            shapes.circle(cx, cy, r, 72);

            // the white zone (libGDX arcs go counter-clockwise from the right, our angles clockwise from the top)
            shapes.setColor(0.92f, 0.92f, 0.92f, 1f);
            shapes.arc(cx, cy, r - 2f, 90f - (zoneStart + zoneWidth), zoneWidth, 40);
            shapes.setColor(0.07f, 0.07f, 0.09f, 1f);
            shapes.circle(cx, cy, r * 0.70f, 72);

            // the red line
            float dx = MathUtils.sinDeg(needle), dy = MathUtils.cosDeg(needle);
            shapes.setColor(0.95f, 0.1f, 0.1f, 1f);
            shapes.rectLine(cx + dx * r * 0.70f, cy + dy * r * 0.70f, cx + dx * (r + 8f), cy + dy * (r + 8f), 8f);

            // white rounded square for the letter
            float s = r * 0.78f;
            shapes.setColor(0.96f, 0.96f, 0.96f, 1f);
            roundRect(cx - s / 2f, cy - s / 2f, s, s, s * 0.18f);
        }
        shapes.end();

        // 3) texts and the flash
        batch.begin();
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
        centered(batch, font, "Lock the " + (target != null ? target.getName() : "door") + "!",
            Main.WORLD_WIDTH / 2f, 706);

        font.getData().setScale(1.1f);
        font.setColor(0.65f, 0.65f, 0.65f, 1f);
        String hint = (phase == Phase.MASH || phase == Phase.LOST)
            ? "Spam CLICK or SPACE to push the bolt in. Don't let the bar run out!   ESC = leave"
            : "Press the letter when the red line is in the white zone!   ESC = leave";
        centered(batch, font, hint, Main.WORLD_WIDTH / 2f, 676);

        if (phase == Phase.MASH) {
            font.getData().setScale(1.8f);
            font.setColor(1f, 1f, 1f, 1f);
            centered(batch, font, "PUSH THE BOLT IN!", PANEL_CX, BAR_Y + BAR_H + 70f);
            font.getData().setScale(1.4f);
            float a = 0.6f + 0.4f * MathUtils.sin(time * 10f);
            font.setColor(1f, 0.85f, 0.3f, a);
            centered(batch, font, "CLICK  /  SPACE", PANEL_CX, BAR_Y - 22f);
        } else if (phase == Phase.CHECK || phase == Phase.PAUSE) {
            font.getData().setScale(5f);
            font.setColor(0.05f, 0.05f, 0.05f, 1f);
            String letter = String.valueOf((char) ('A' + (targetKey - Input.Keys.A)));
            layout.setText(font, letter);
            font.draw(batch, letter, PANEL_CX - layout.width / 2f, DIAL_CY + layout.height / 2f);
        }

        if (statusTimer > 0f) {
            font.getData().setScale(2f);
            if (statusGood) font.setColor(0.5f, 1f, 0.6f, 1f);
            else font.setColor(1f, 0.3f, 0.2f, 1f);
            centered(batch, font, statusText, PANEL_CX, 625);
        }

        font.getData().setScale(1.1f);
        font.setColor(0.55f, 0.55f, 0.6f, 1f);
        centered(batch, font, "LOCK  " + successes + " / " + CHECKS_NEEDED, PANEL_CX, PIPS_Y - 30f);

        if (flash > 0f) {
            if (flashGood) batch.setColor(0.3f, 1f, 0.5f, 0.22f * flash / (FLASH_TIME * 0.6f));
            else batch.setColor(1f, 0.35f, 0.15f, 0.5f * flash / FLASH_TIME);
            batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
        }
        batch.setColor(Color.WHITE);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    public void dispose() {
        pixel.dispose();
        for (Texture t : knob) t.dispose();
        shapes.dispose();
    }
}
