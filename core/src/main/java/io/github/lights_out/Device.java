package io.github.lights_out;

import com.badlogic.gdx.math.Rectangle;

/** A device that must be unplugged. Clicking the device (or its outlet) opens the minigame. */
public class Device {
    private final String roomId;
    private final String name;
    private final Rectangle rect;   // where the device is in the room picture (the click area)
    private Rectangle outlet = null; // where its outlet is (optional)
    private boolean done = false;

    public Device(String roomId, String name, float x, float y, float w, float h) {
        this.roomId = roomId;
        this.name = name;
        this.rect = new Rectangle(x, y, w, h);
    }

    /** Adds a click area for the wall outlet this device is plugged into. */
    public void setOutlet(float x, float y, float w, float h) {
        outlet = new Rectangle(x, y, w, h);
    }

    /** True if the point is on the device or on its outlet. */
    public boolean contains(float px, float py) {
        return rect.contains(px, py) || (outlet != null && outlet.contains(px, py));
    }

    public String getRoomId() { return roomId; }
    public String getName() { return name; }
    public Rectangle getRect() { return rect; }
    public Rectangle getOutlet() { return outlet; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
}
