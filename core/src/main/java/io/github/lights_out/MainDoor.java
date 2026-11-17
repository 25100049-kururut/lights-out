package io.github.lights_out;

import com.badlogic.gdx.math.Rectangle;

/** One of the main doors that must be locked. Clicking it opens the lock minigame. */
public class MainDoor {
    private final String roomId;
    private final String name;
    private final Rectangle rect;   // click area (world units)
    private boolean done = false;

    private MainDoor(String roomId, String name, Rectangle rect) {
        this.roomId = roomId;
        this.name = name;
        this.rect = rect;
    }

    /** Positions are pixels in the 1920x953 room picture. */
    public static MainDoor fromImage(String roomId, String name, float x1, float y1, float x2, float y2) {
        float sx = Main.WORLD_WIDTH / 1920f;
        float sy = Main.WORLD_HEIGHT / 953f;
        return new MainDoor(roomId, name,
            new Rectangle(x1 * sx, (953f - y2) * sy, (x2 - x1) * sx, (y2 - y1) * sy));
    }

    public boolean contains(float x, float y) { return rect.contains(x, y); }

    public String getRoomId() { return roomId; }
    public String getName() { return name; }
    public Rectangle getRect() { return rect; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
}
