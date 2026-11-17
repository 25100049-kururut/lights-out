package io.github.lights_out;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/**
 * Random lightning. Each strike makes a flash on screen (getFlash / draw) and, a moment later,
 * a thunder clap. Sound is slower than light, so the thunder arrives after the flash:
 * a short delay means the storm is close, a long delay means it is far away.
 */
public class Lightning {
    private final SoundManager audio;
    private final float minWait;
    private final float maxWait;

    private float wait;               // seconds until the next strike
    private float t = -1f;            // time since the flash started (-1 = no flash right now)
    private float duration = 0.8f;
    private float strength = 1f;      // some strikes are brighter than others
    private int pattern = 0;          // which flash shape (0, 1 or 2)

    private boolean thunderPending = false;
    private float thunderTimer = 0f;
    private float thunderVolume = 1f;

    private boolean enabled = true;

    /** firstWait: seconds before the first strike. Then one strike every minWait to maxWait seconds. */
    public Lightning(SoundManager audio, float firstWait, float minWait, float maxWait) {
        this.audio = audio;
        this.wait = firstWait;
        this.minWait = minWait;
        this.maxWait = maxWait;
    }

    /** While disabled, no new strikes start (a flash that is already happening finishes). */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void update(float delta) {
        // the thunder arrives after the flash
        if (thunderPending) {
            thunderTimer -= delta;
            if (thunderTimer <= 0f) {
                thunderPending = false;
                audio.thunder(thunderVolume);
            }
        }

        if (t >= 0f) {
            t += delta;
            if (t > duration) t = -1f;
            return;
        }
        if (!enabled) return;

        wait -= delta;
        if (wait <= 0f) strike();
    }

    private void strike() {
        pattern = MathUtils.random(2);
        strength = MathUtils.random(0.6f, 1f);
        duration = pattern == 0 ? 0.8f : (pattern == 1 ? 0.45f : 1.0f);
        t = 0f;
        wait = MathUtils.random(minWait, maxWait);

        float distance = MathUtils.random();              // 0 = right above the house, 1 = far away
        thunderTimer = 0.08f + distance * 2.8f;
        thunderVolume = MathUtils.lerp(1f, 0.35f, distance);
        thunderPending = true;
    }

    /** How bright the flash is right now: 0 (none) to 1 (brightest). */
    public float getFlash() {
        if (t < 0f) return 0f;
        float f;
        if (pattern == 0) {            // flash, flicker, flash, fade
            if (t < 0.08f) f = 1f;
            else if (t < 0.18f) f = 0.15f;
            else if (t < 0.30f) f = 0.85f;
            else f = 1f - (t - 0.30f) / (duration - 0.30f);
        } else if (pattern == 1) {     // one quick crack
            if (t < 0.06f) f = 1f;
            else f = 1f - (t - 0.06f) / (duration - 0.06f);
        } else {                       // three flickers
            if (t < 0.05f) f = 0.9f;
            else if (t < 0.12f) f = 0.1f;
            else if (t < 0.20f) f = 1f;
            else if (t < 0.27f) f = 0.15f;
            else if (t < 0.36f) f = 0.7f;
            else f = 0.7f * (1f - (t - 0.36f) / (duration - 0.36f));
        }
        return MathUtils.clamp(f, 0f, 1f) * strength;
    }

    /**
     * Draws the flash as a cold white-blue light over the whole screen.
     * The ShapeRenderer needs its projection matrix set already. maxAlpha: how bright the brightest flash is.
     */
    public void draw(ShapeRenderer shapes, float maxAlpha) {
        float f = getFlash();
        if (f < 0.01f) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);   // adds light
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.72f, 0.82f, 1f, f * maxAlpha);
        shapes.rect(0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }
}
