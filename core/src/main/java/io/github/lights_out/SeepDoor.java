package io.github.lights_out;

import com.badlogic.gdx.math.Rectangle;

/** A door that rain water is seeping under. Clicking it (with the cloth) opens the "seal the gap" minigame. */
public class SeepDoor {
    private static final float PUDDLE_DROP = 60f;   // how far the click area reaches down onto the water (room picture pixels)

    private final String roomId;
    private final String name;
    private final Rectangle rect;      // click area: the door and the water in front of it (world units)
    private final float baseX, baseY;  // middle of the bottom edge of the door (world units)
    private final float doorWidth;
    private boolean done = false;

    private SeepDoor(String roomId, String name, Rectangle rect, float baseX, float baseY, float doorWidth) {
        this.roomId = roomId;
        this.name = name;
        this.rect = rect;
        this.baseX = baseX;
        this.baseY = baseY;
        this.doorWidth = doorWidth;
    }

    /** Positions are pixels in the 1920x953 room picture (the same numbers as the door's MainDoor). */
    public static SeepDoor fromImage(String roomId, String name, float x1, float y1, float x2, float y2) {
        float sx = Main.WORLD_WIDTH / 1920f;
        float sy = Main.WORLD_HEIGHT / 953f;
        Rectangle r = new Rectangle(x1 * sx, (953f - (y2 + PUDDLE_DROP)) * sy,
            (x2 - x1) * sx, (y2 - y1 + PUDDLE_DROP) * sy);
        return new SeepDoor(roomId, name, r, (x1 + x2) / 2f * sx, (953f - y2) * sy, (x2 - x1) * sx);
    }

    public boolean contains(float x, float y) { return rect.contains(x, y); }

    public String getRoomId() { return roomId; }
    public String getName() { return name; }
    public Rectangle getRect() { return rect; }
    public float getBaseX() { return baseX; }
    public float getBaseY() { return baseY; }
    public float getDoorWidth() { return doorWidth; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
}
