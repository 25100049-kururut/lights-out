package io.github.lights_out;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;

/** One room: its picture, its doors, and which rooms its screen edges lead to. */
public class Room {
    private final String id;
    private final String displayName;
    private final String texturePath;
    private final float brightness;
    private final Array<Hotspot> hotspots = new Array<>();

    private String leftTarget;   // room reached by clicking the left side of the screen
    private String rightTarget;  // room reached by clicking the right side of the screen
    private Texture texture;

    public Room(String id, String displayName, String texturePath, float brightness) {
        this.id = id;
        this.displayName = displayName;
        this.texturePath = texturePath;
        this.brightness = brightness;
    }

    public void load() {
        texture = new Texture(texturePath);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    public void dispose() {
        if (texture != null) texture.dispose();
    }

    public void addHotspot(Hotspot h) { hotspots.add(h); }

    public Hotspot hotspotAt(float x, float y) {
        for (Hotspot h : hotspots) {
            if (h.contains(x, y)) return h;
        }
        return null;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public float getBrightness() { return brightness; }
    public Texture getTexture() { return texture; }

    public String getLeftTarget() { return leftTarget; }
    public String getRightTarget() { return rightTarget; }
    public void setLeftTarget(String id) { leftTarget = id; }
    public void setRightTarget(String id) { rightTarget = id; }
}
