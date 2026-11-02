package io.github.lights_out;

import com.badlogic.gdx.math.Rectangle;

/** A clickable area. It either leads to another room, or triggers an action (like opening a drawer). */
public class Hotspot {
    private final Rectangle area;
    private final String targetRoom; // room to go to (or null)
    private final String action;     // action name, e.g. "drawer0" (or null)

    public Hotspot(Rectangle area, String targetRoom) {
        this(area, targetRoom, null);
    }

    private Hotspot(Rectangle area, String targetRoom, String action) {
        this.area = area;
        this.targetRoom = targetRoom;
        this.action = action;
    }

    public boolean contains(float x, float y) {
        return area.contains(x, y);
    }

    public String getTargetRoom() { return targetRoom; }
    public String getAction() { return action; }

    private static Rectangle rectFromImage(float x1, float y1, float x2, float y2) {
        float sx = Main.WORLD_WIDTH / 1920f;
        float sy = Main.WORLD_HEIGHT / 953f;
        return new Rectangle(x1 * sx, (953f - y2) * sy, (x2 - x1) * sx, (y2 - y1) * sy);
    }

    /** A door/area that leads to another room. Positions are pixels in the 1920x953 room picture. */
    public static Hotspot fromImage(float x1, float y1, float x2, float y2, String target) {
        return new Hotspot(rectFromImage(x1, y1, x2, y2), target, null);
    }

    /** An area that triggers an action. Positions are pixels in the 1920x953 room picture. */
    public static Hotspot actionFromImage(float x1, float y1, float x2, float y2, String action) {
        return new Hotspot(rectFromImage(x1, y1, x2, y2), null, action);
    }
}
