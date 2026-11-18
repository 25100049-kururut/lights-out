package io.github.lights_out;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

/** The jumpscare: black screen, then the face slams in, shakes, comes closer, and fades out. */
public class Jumpscare {
    private static final String[] IMAGE_FILES = {"jumpscare_1.png", "jumpscare.png"}; // the first one that exists is used

    private static final float PRE_DELAY = 0.7f; // black screen before the scare (the sound builds up here, then the hit)
    private static final float SCARE_TIME = 1.9f;  // how long the face stays on screen
    private static final float FADE_TIME = 0.6f;   // fade to black at the end
    private static final float FLASH_TIME = 0.12f; // white flash at the start
    private static final float SHAKE = 16f;        // how hard the face shakes
    private static final float ZOOM = 0.3f;        // how much closer the face comes (0.3 = 30% bigger)
    private static final boolean COVER_SCREEN = false; // false = the whole picture fits on screen
    // true = the picture fills the screen (the edges get cut off)

    private final Texture image;
    private final Texture pixel;
    private Sound sound = null;      // optional scream (jumpscare.ogg / .wav / .mp3 in assets)

    private boolean active = false;
    private boolean soundPlayed = false;
    private float t = 0f;

    public Jumpscare() {
        String file = "jumpscare.png";
        for (String f : IMAGE_FILES) {
            if (Gdx.files.internal(f).exists()) {
                file = f;
                break;
            }
        }
        image = new Texture(Gdx.files.internal(file), true);
        image.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        String[] names = {"jumpscare.ogg", "jumpscare.wav", "jumpscare.mp3"};
        for (String n : names) {
            if (Gdx.files.internal(n).exists()) {
                sound = Gdx.audio.newSound(Gdx.files.internal(n));
                break;
            }
        }
    }

    public void start() {
        active = true;
        soundPlayed = false;
        t = 0f;
    }

    public boolean isActive() { return active; }

    public void update(float delta) {
        if (!active) return;
        t += delta;
        if (!soundPlayed) {   // the sound starts at once: first the rising dread, then the scream on the hit
            soundPlayed = true;
            if (sound != null) sound.play(1f);
        }
        if (t >= PRE_DELAY + SCARE_TIME + FADE_TIME) active = false;
    }

    public void render(SpriteBatch batch) {
        if (!active) return;
        batch.begin();

        // black screen
        batch.setColor(0f, 0f, 0f, 1f);
        batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);

        float s = t - PRE_DELAY;
        if (s >= 0f) {
            float progress = MathUtils.clamp(s / SCARE_TIME, 0f, 1f);

            // the picture fits the screen (or fills it), then slowly comes closer
            float scaleW = Main.WORLD_WIDTH / image.getWidth();
            float scaleH = Main.WORLD_HEIGHT / image.getHeight();
            float base = COVER_SCREEN ? Math.max(scaleW, scaleH) : Math.min(scaleW, scaleH);
            float scale = base * (1f + ZOOM * progress);
            float imgW = image.getWidth() * scale;
            float imgH = image.getHeight() * scale;

            float shake = SHAKE * (1f - 0.6f * progress);
            float sx = MathUtils.random(-shake, shake);
            float sy = MathUtils.random(-shake, shake);

            batch.setColor(Color.WHITE);
            batch.draw(image, (Main.WORLD_WIDTH - imgW) / 2f + sx, (Main.WORLD_HEIGHT - imgH) / 2f + sy, imgW, imgH);

            // white flash at the start
            if (s < FLASH_TIME) {
                batch.setColor(1f, 1f, 1f, 1f - s / FLASH_TIME);
                batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
            }

            // fade to black at the end
            if (s > SCARE_TIME) {
                batch.setColor(0f, 0f, 0f, MathUtils.clamp((s - SCARE_TIME) / FADE_TIME, 0f, 1f));
                batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
            }
        }

        batch.setColor(Color.WHITE);
        batch.end();
    }

    public void dispose() {
        image.dispose();
        pixel.dispose();
        if (sound != null) sound.dispose();
    }
}
