package io.github.lights_out;

/** A puddle on the floor. Clicking it starts the "catch the water drops" minigame. */
public class Leak {
    private static final float PUDDLE_ASPECT = 461f / 598f; // height / width of water_puddle.png

    private final String roomId;
    private final float x, y;          // centre of the puddle (world units)
    private final float width, height;
    private boolean done = false;

    public Leak(String roomId, float x, float y, float width) {
        this.roomId = roomId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = width * PUDDLE_ASPECT;
    }

    public boolean contains(float px, float py) {
        return Math.abs(px - x) <= width / 2f + 12f && Math.abs(py - y) <= height / 2f + 12f;
    }

    public String getRoomId() { return roomId; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
}
