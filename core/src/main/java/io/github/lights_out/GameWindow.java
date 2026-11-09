package io.github.lights_out;


import com.badlogic.gdx.math.Rectangle;


/** One window in a room. level: 0 = closed ... 4 = wide open. */
public class GameWindow {
    public static final int MAX_LEVEL = 4;


    private final String roomId;
    private final Rectangle rect; // where the window is in the room picture (the click area)
    private int level = 0;        // what the window really is
    private float shown = 0f;     // what the minigame draws (moves slowly toward level)


    public GameWindow(String roomId, float x, float y, float w, float h) {
        this.roomId = roomId;
        this.rect = new Rectangle(x, y, w, h);
    }


    public void setLevel(int level) { this.level = Math.max(0, Math.min(MAX_LEVEL, level)); }
    public void snap() { shown = level; }   // jump straight to the level, no animation


    public int getLevel() { return level; }
    public boolean isClosed() { return level == 0; }
    public void closeStep() { setLevel(level - 1); }
    public void openStep() { setLevel(level + 1); }


    public void update(float delta) {
        float step = 2.5f * delta;
        if (shown < level) shown = Math.min(level, shown + step);
        else shown = Math.max(level, shown - step);
    }


    public float getShown() { return shown; }
    public String getRoomId() { return roomId; }
    public Rectangle getRect() { return rect; }


    /** True if the point is on the window in the room picture. */
    public boolean contains(float px, float py) {
        return rect.contains(px, py);
    }
}
