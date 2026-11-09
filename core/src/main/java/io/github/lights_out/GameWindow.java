package io.github.lights_out;

import com.badlogic.gdx.math.Rectangle;

/** One window in a room. level: 0 = closed ... 4 = wide open. */
public class GameWindow {
    public static final int MAX_LEVEL = 4;

    private final String roomId;
    private final Rectangle rect; // where the CLOSED window sits in the room (world units)
    private int level = 0;        // what the window really is
    private float shown = 0f;     // what is drawn (moves slowly toward level)

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

    /** Click area: the window, a bit wider and taller while the shutters are swung open. */
    public boolean contains(float px, float py) {
        float open = shown / MAX_LEVEL;
        float extraX = rect.width * 0.35f * open;
        float extraTop = rect.height * 0.10f * open;
        return px >= rect.x - extraX && px <= rect.x + rect.width + extraX
            && py >= rect.y && py <= rect.y + rect.height + extraTop;
    }
}
