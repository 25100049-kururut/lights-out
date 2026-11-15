package io.github.lights_out;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

/** Pull the plug out of the outlet at the right speed (like the card swipe task). */
public class UnplugMinigame {
    // Layout (world units)
    private static final float OUTLET_CX = 310f;      // middle of the outlet
    private static final float OUTLET_Y = 175f;       // bottom of the outlet
    private static final float OUTLET_H = 430f;       // height of the outlet
    private static final float SOCKET_X = 0.572f;     // where the socket is on the outlet picture (share of its width)
    private static final float SOCKET_Y = 0.607f;     // ... and share of its height from the top
    private static final float PLUG_W = 300f;         // drawn width of the plug
    private static final float PLUG_ASPECT = 0.5816f; // height / width of plug_cut.png
    private static final float TIP_FROM_TOP = 0.255f; // where the prongs are on the plug picture (share of its height from the top)
    private static final float PULL_DISTANCE = 560f;  // how far the plug must be pulled
    private static final float RAIL_Y = 190f;         // where the rail under the plug is

    // Timing: the pull must take between MIN_TIME and MAX_TIME seconds
    private static final float MIN_TIME = 0.45f;      // faster than this = too fast
    private static final float MAX_TIME = 1.2f;       // slower than this = too slow
    private static final float START_MOVE = 8f;       // the timer starts when the plug has moved this far

    private static final float FLASH_TIME = 0.45f;    // flash after a fail
    private static final float LOCK_TIME = 0.5f;      // pause before you can grab the plug again
    private static final float FINISH_DELAY = 1.1f;   // pause after the plug comes out
    private static final float RETURN_SPEED = 2200f;  // how fast the plug snaps back

    private static class Spark {
        float x, y, vx, vy, life;
    }

    private final Texture pixel;
    private final Texture outlet;
    private final Texture plug;
    private final GlyphLayout layout = new GlyphLayout();
    private final Array<Spark> sparks = new Array<>();
    private final float outletW, plugH, socketX, socketY;

    private Device target;
    private boolean active = false;
    private boolean finishing = false;
    private boolean grabbed = false;
    private boolean returning = false;
    private boolean timing = false;
    private boolean finishedEvent = false;
    private boolean lastTooFast = false;
    private boolean statusGood = false;
    private String statusText = "";
    private float finishTimer = 0f;
    private float flash = 0f;
    private float time = 0f;
    private float lock = 0f;
    private float statusTimer = 0f;
    private float progress = 0f;    // how far the plug is pulled (0 = plugged in)
    private float grabOffset = 0f;
    private float timer = 0f;       // how long the pull has taken
    private int zapsPending = 0;

    public UnplugMinigame() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        outlet = new Texture(Gdx.files.internal("outlet_cut.png"), true);
        outlet.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        plug = new Texture(Gdx.files.internal("plug_cut.png"), true);
        plug.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);

        outletW = OUTLET_H * outlet.getWidth() / outlet.getHeight();
        plugH = PLUG_W * PLUG_ASPECT;
        socketX = OUTLET_CX + (SOCKET_X - 0.5f) * outletW;
        socketY = OUTLET_Y + (1f - SOCKET_Y) * OUTLET_H;
    }

    public void open(Device device) {
        target = device;
        active = true;
        finishing = false;
        grabbed = false;
        returning = false;
        timing = false;
        finishedEvent = false;
        flash = 0f;
        lock = 0f;
        statusTimer = 0f;
        progress = 0f;
        timer = 0f;
        zapsPending = 0;
        sparks.clear();
    }

    public void close() {
        active = false;
        finishing = false;
        grabbed = false;
        returning = false;
        timing = false;
        target = null;
        sparks.clear();
    }

    public boolean isActive() { return active; }
    public boolean isGrabbed() { return grabbed; }
    public Device getTarget() { return target; }
    public boolean wasTooFast() { return lastTooFast; }

    /** True if the mouse is on the plug (so it can be grabbed). */
    public boolean isOverPlug(float x, float y) {
        if (!active || finishing || returning) return false;
        float tipX = socketX + progress;
        float bottom = socketY - (1f - TIP_FROM_TOP) * plugH;
        return x >= tipX && x <= tipX + PLUG_W && y >= bottom && y <= bottom + plugH;
    }

    /** How many times the player failed since the last time this was asked. */
    public int takeZaps() {
        int z = zapsPending;
        zapsPending = 0;
        return z;
    }

    /** True once, when the plug has been pulled out correctly. */
    public boolean takeFinished() {
        boolean f = finishedEvent;
        finishedEvent = false;
        return f;
    }

    private void spark(float x, float y, float vx, float vy, float life) {
        Spark s = new Spark();
        s.x = x;
        s.y = y;
        s.vx = vx;
        s.vy = vy;
        s.life = life;
        sparks.add(s);
    }

    /** The pull was too fast or too slow: sparks, flash, and the plug goes back into the outlet. */
    private void fail(boolean tooFast) {
        zapsPending++;
        lastTooFast = tooFast;
        flash = FLASH_TIME;
        statusText = tooFast ? "TOO FAST!" : "TOO SLOW!";
        statusGood = false;
        statusTimer = 1.4f;

        for (int i = 0; i < 18; i++) {
            float ang = MathUtils.random(360f);
            float speed = MathUtils.random(80f, 280f);
            spark(socketX, socketY, MathUtils.cosDeg(ang) * speed, MathUtils.sinDeg(ang) * speed,
                MathUtils.random(0.25f, 0.55f));
        }

        grabbed = false;
        timing = false;
        returning = true;
        lock = LOCK_TIME;
    }

    private void succeed() {
        finishing = true;
        finishTimer = FINISH_DELAY;
        finishedEvent = true;
        grabbed = false;
        timing = false;
        statusText = "Unplugged!";
        statusGood = true;
        statusTimer = 99f;
    }

    public void update(float delta, float mx, float my, boolean pressed, boolean held) {
        if (!active) return;
        time += delta;
        if (flash > 0f) flash = Math.max(0f, flash - delta);
        if (lock > 0f) lock = Math.max(0f, lock - delta);
        if (statusTimer > 0f) statusTimer = Math.max(0f, statusTimer - delta);

        for (int i = sparks.size - 1; i >= 0; i--) {
            Spark s = sparks.get(i);
            s.life -= delta;
            s.vy -= 300f * delta;
            s.x += s.vx * delta;
            s.y += s.vy * delta;
            if (s.life <= 0f) sparks.removeIndex(i);
        }

        if (finishing) {
            finishTimer -= delta;
            if (finishTimer <= 0f) close();
            return;
        }

        // the wet outlet crackles a little
        if (MathUtils.random() < 0.08f) {
            spark(socketX + MathUtils.random(-14f, 14f), socketY + MathUtils.random(-26f, 26f),
                MathUtils.random(-25f, 25f), MathUtils.random(10f, 50f), 0.25f);
        }

        // the plug is snapping back into the outlet
        if (returning) {
            progress = Math.max(0f, progress - RETURN_SPEED * delta);
            if (progress <= 0f) returning = false;
            return;
        }

        if (!grabbed) {
            if (pressed && lock <= 0f && isOverPlug(mx, my)) {
                grabbed = true;
                grabOffset = progress - (mx - socketX);  // so the plug doesn't jump when grabbed
                timing = false;
                timer = 0f;
            }
            return;
        }

        // let go before the end: the plug springs back, no penalty
        if (!held) {
            grabbed = false;
            timing = false;
            returning = true;
            return;
        }

        float wanted = MathUtils.clamp(mx - socketX + grabOffset, 0f, PULL_DISTANCE);
        if (wanted > progress) progress = wanted;        // the plug only moves outward

        if (!timing && progress > START_MOVE) {
            timing = true;
            timer = 0f;
        }
        if (timing) {
            timer += delta;
            if (progress >= PULL_DISTANCE) {
                if (timer < MIN_TIME) fail(true);        // yanked it out too fast
                else succeed();
            } else if (timer > MAX_TIME) {
                fail(false);                             // took too long
            }
        }
    }

    public void render(SpriteBatch batch, BitmapFont font) {
        if (!active) return;
        batch.begin();

        // dim everything behind
        batch.setColor(0f, 0f, 0f, 0.9f);
        batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);

        // the outlet
        batch.setColor(Color.WHITE);
        batch.draw(outlet, OUTLET_CX - outletW / 2f, OUTLET_Y, outletW, OUTLET_H);

        // the rail under the plug: how far to pull
        float endX = socketX + PULL_DISTANCE;
        batch.setColor(0.22f, 0.22f, 0.27f, 1f);
        batch.draw(pixel, socketX, RAIL_Y - 3f, PULL_DISTANCE, 6f);
        batch.setColor(0.35f, 0.8f, 0.5f, 1f);
        batch.draw(pixel, socketX, RAIL_Y - 3f, progress, 6f);
        batch.setColor(0.6f, 0.6f, 0.65f, 1f);
        batch.draw(pixel, socketX - 2f, RAIL_Y - 10f, 4f, 20f);
        batch.setColor(0.3f, 0.95f, 0.45f, 1f);
        batch.draw(pixel, endX - 2f, RAIL_Y - 12f, 4f, 24f);

        // the plug (its prongs are at the left, the tip sits in the socket)
        float tipX = socketX + progress;
        batch.setColor(Color.WHITE);
        batch.draw(plug, tipX, socketY - (1f - TIP_FROM_TOP) * plugH, PLUG_W, plugH);

        // sparks
        for (Spark s : sparks) {
            batch.setColor(1f, 0.8f, 0.3f, MathUtils.clamp(s.life / 0.3f, 0f, 1f));
            batch.draw(pixel, s.x - 2f, s.y - 2f, 4f, 4f);
        }

        // flash after a fail
        if (flash > 0f) {
            batch.setColor(1f, 0.35f, 0.15f, 0.5f * flash / FLASH_TIME);
            batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
        }
        batch.setColor(Color.WHITE);

        // texts
        String title = "Unplug the " + (target != null ? target.getName() : "device") + "!";
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
        layout.setText(font, title);
        font.draw(batch, title, (Main.WORLD_WIDTH - layout.width) / 2f, 706);

        font.getData().setScale(1.1f);
        font.setColor(0.65f, 0.65f, 0.65f, 1f);
        String hint = "Hold the plug and pull it out in one smooth motion. Not too fast, not too slow!   ESC = leave";
        layout.setText(font, hint);
        font.draw(batch, hint, (Main.WORLD_WIDTH - layout.width) / 2f, 676);

        if (statusTimer > 0f) {
            font.getData().setScale(2f);
            if (statusGood) font.setColor(0.5f, 1f, 0.6f, 1f);
            else font.setColor(1f, 0.3f, 0.2f, 1f);
            layout.setText(font, statusText);
            font.draw(batch, statusText, (Main.WORLD_WIDTH - layout.width) / 2f, 640);
        }

        font.getData().setScale(1.1f);
        font.setColor(0.55f, 0.55f, 0.6f, 1f);
        font.draw(batch, "PULL  >>>", socketX, RAIL_Y - 22f);

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    public void dispose() {
        pixel.dispose();
        outlet.dispose();
        plug.dispose();
    }
}
