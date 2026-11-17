package io.github.lights_out;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class Main extends Game {
    public static final float WORLD_WIDTH = 1280;
    public static final float WORLD_HEIGHT = 720;

    public SpriteBatch batch;
    public BitmapFont font;
    public SoundManager audio;   // shared by every screen, so the storm never restarts

    @Override
    public void create() {
        batch = new SpriteBatch();
        font = new BitmapFont();
        audio = new SoundManager();
        audio.startAmbience();
        setScreen(new MainMenuScreen(this));
    }

    @Override
    public void render() {
        super.render();   // draws the current screen
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) audio.toggleMute();   // M = mute / unmute
        audio.update(Gdx.graphics.getDeltaTime());
    }

    @Override
    public void dispose() {
        super.dispose();
        audio.dispose();
        batch.dispose();
        font.dispose();
    }
}
