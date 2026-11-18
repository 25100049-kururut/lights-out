package io.github.lights_out;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * All the sound of the game in one place: the looping storm ambience (rain, wind, a low horror drone),
 * thunder, random creepy noises, the heartbeat, and short sound effects.
 *
 * One SoundManager lives in Main, so the ambience keeps playing when the screen changes.
 * A missing sound file is simply skipped (the game never crashes because of audio).
 */
public class SoundManager {
    private static final String[] THUNDER = {"thunder_1.wav", "thunder_2.wav", "thunder_3.wav"};
    private static final String[] CREEPY = {"sfx_creak", "sfx_thump", "sfx_sting"};
    private static final String[] LIGHTNING = {"sfx_lightning_1", "sfx_lightning_2"};
    private static final String[] EFFECTS = {
        // general
        "sfx_power_down", "sfx_creak", "sfx_thump", "sfx_heartbeat", "sfx_sting", "sfx_click", "sfx_flashlight",
        "sfx_bell", "sfx_ding", "sfx_lightning_1", "sfx_lightning_2",
        // task 1: drawers and sharp things
        "sfx_drawer", "sfx_drawer_close", "sfx_clatter", "sfx_ouch", "sfx_cut", "sfx_hurt",
        // task 2: windows
        "sfx_howl", "sfx_gust", "sfx_window_step", "sfx_shut",
        // task 3: leaks
        "sfx_drip", "sfx_plink", "sfx_splash",
        // task 4: plugs
        "sfx_spark", "sfx_unplug", "sfx_zap",
        // task 5: doors
        "sfx_key_turn", "sfx_jam", "sfx_rattle", "sfx_lock"
    };

    // loops: 0 = rain, 1 = wind, 2 = horror drone, 3 = calm creepy music box, 4 = tense horror music
    private static final String[] LOOP_FILES = {
        "rain_loop.wav", "wind_loop.wav", "drone_loop.wav", "music_menu_loop.wav", "music_tense_loop.wav"
    };
    private static final float[] LOOP_MAX = {0.45f, 0.8f, 0.7f, 0.85f, 0.85f};  // loudest each loop can get
    private static final float FADE_SPEED = 0.5f;                 // how fast volumes glide to their target (per second)

    private static final float CREEPY_MIN = 14f;                  // seconds between random creepy noises
    private static final float CREEPY_MAX = 32f;

    private final Music[] loops = new Music[LOOP_FILES.length];
    private final float[] volume = new float[LOOP_FILES.length];  // volume right now
    private final float[] target = new float[LOOP_FILES.length];  // volume it is moving toward
    private final Sound[] thunders = new Sound[THUNDER.length];
    private final Map<String, Sound> effects = new HashMap<>();

    private float master = 1f;
    private boolean muted = false;
    private boolean ambienceOn = false;

    private boolean horrorEvents = false;
    private float creepyTimer = MathUtils.random(CREEPY_MIN, CREEPY_MAX);
    private int lastThunder = -1;

    private float heartIntensity = 0f;   // 0 = no heartbeat, 1 = fast and loud
    private float heartTimer = 0f;

    public SoundManager() {
        int missing = 0;
        for (int i = 0; i < LOOP_FILES.length; i++) {
            if (!exists(LOOP_FILES[i])) { missing++; continue; }
            loops[i] = Gdx.audio.newMusic(Gdx.files.internal(LOOP_FILES[i]));
            loops[i].setLooping(true);
            loops[i].setVolume(0f);
        }
        for (int i = 0; i < THUNDER.length; i++) {
            if (!exists(THUNDER[i])) { missing++; continue; }
            thunders[i] = Gdx.audio.newSound(Gdx.files.internal(THUNDER[i]));
        }
        for (String name : EFFECTS) {
            if (!exists(name + ".wav")) { missing++; continue; }
            effects.put(name, Gdx.audio.newSound(Gdx.files.internal(name + ".wav")));
        }
        // tell the developer in the console if the sound files were not copied into assets/
        if (missing > 0) {
            Gdx.app.error("SoundManager", missing + " sound file(s) are missing from the assets folder. "
                + "Copy all the .wav files into assets/ or the game will be silent.");
        } else {
            Gdx.app.log("SoundManager", "All sound files loaded.");
        }
    }

    private boolean exists(String file) {
        boolean found = Gdx.files.internal(file).exists();
        if (!found) Gdx.app.error("SoundManager", "Missing sound file: " + file);
        return found;
    }

    /** Starts the rain, wind, drone and music loops (silent at first, they fade in). Safe to call many times. */
    public void startAmbience() {
        if (ambienceOn) return;
        ambienceOn = true;
        for (Music m : loops) if (m != null) m.play();
    }

    /** How loud each ambience layer should be (0 to 1). The volumes glide there smoothly. */
    public void setMood(float rain, float wind, float drone) {
        target[0] = MathUtils.clamp(rain, 0f, 1f);
        target[1] = MathUtils.clamp(wind, 0f, 1f);
        target[2] = MathUtils.clamp(drone, 0f, 1f);
    }

    /** How loud the two music tracks should be (0 to 1): the calm creepy music box and the tense horror music. */
    public void setMusic(float calm, float tense) {
        target[3] = MathUtils.clamp(calm, 0f, 1f);
        target[4] = MathUtils.clamp(tense, 0f, 1f);
    }

    /** Turns the random creepy noises (creaks, thumps, a strange shimmer) on or off. */
    public void setHorrorEvents(boolean on) {
        horrorEvents = on;
    }

    /** 0 = no heartbeat. Closer to 1 = faster and louder. */
    public void setHeartbeat(float intensity) {
        heartIntensity = MathUtils.clamp(intensity, 0f, 1f);
    }

    /** Plays a thunder clap. volume: 0 to 1 (far away = quiet). */
    public void thunder(float vol) {
        int n = THUNDER.length;
        int pick = MathUtils.random(n - 1);
        if (pick == lastThunder) pick = (pick + 1) % n;   // never the same clap twice in a row
        lastThunder = pick;
        if (thunders[pick] != null) {
            thunders[pick].play(vol * master, MathUtils.random(0.9f, 1.08f), MathUtils.random(-0.3f, 0.3f));
        }
    }

    /** The crack of a lightning strike. Plays at the same moment as the flash. */
    public void lightning(float vol) {
        Sound s = effects.get(LIGHTNING[MathUtils.random(LIGHTNING.length - 1)]);
        if (s != null) s.play(vol * master, MathUtils.random(0.92f, 1.08f), MathUtils.random(-0.3f, 0.3f));
    }

    /** Plays a short sound effect by name, e.g. play("sfx_hurt"). */
    public void play(String name) {
        play(name, 1f);
    }

    public void play(String name, float vol) {
        Sound s = effects.get(name);
        if (s != null) s.play(vol * master);
    }

    /** Same, with a pitch (1 = normal, 0.5 to 2) and a pan (-1 = left, 0 = middle, 1 = right). */
    public void play(String name, float vol, float pitch, float pan) {
        Sound s = effects.get(name);
        if (s != null) s.play(vol * master, MathUtils.clamp(pitch, 0.5f, 2f), MathUtils.clamp(pan, -1f, 1f));
    }

    public void toggleMute() {
        muted = !muted;
        master = muted ? 0f : 1f;
    }

    public boolean isMuted() { return muted; }

    /** Call once per frame (Main does it). */
    public void update(float delta) {
        // glide each loop toward its target volume
        for (int i = 0; i < loops.length; i++) {
            float step = FADE_SPEED * delta;
            if (volume[i] < target[i]) volume[i] = Math.min(target[i], volume[i] + step);
            else volume[i] = Math.max(target[i], volume[i] - step);
            if (loops[i] != null) loops[i].setVolume(volume[i] * LOOP_MAX[i] * master);
        }

        // random creepy noises
        if (horrorEvents) {
            creepyTimer -= delta;
            if (creepyTimer <= 0f) {
                creepyTimer = MathUtils.random(CREEPY_MIN, CREEPY_MAX);
                Sound s = effects.get(CREEPY[MathUtils.random(CREEPY.length - 1)]);
                if (s != null) {
                    s.play(MathUtils.random(0.25f, 0.55f) * master, MathUtils.random(0.8f, 1.1f), MathUtils.random(-0.8f, 0.8f));
                }
            }
        }

        // heartbeat when the player is in danger
        if (heartIntensity > 0.01f) {
            heartTimer -= delta;
            if (heartTimer <= 0f) {
                heartTimer = 1.15f - 0.55f * heartIntensity;
                Sound s = effects.get("sfx_heartbeat");
                if (s != null) s.play((0.35f + 0.65f * heartIntensity) * master);
            }
        } else {
            heartTimer = 0f;
        }
    }

    public void dispose() {
        for (Music m : loops) if (m != null) m.dispose();
        for (Sound s : thunders) if (s != null) s.dispose();
        for (Sound s : effects.values()) s.dispose();
    }
}
