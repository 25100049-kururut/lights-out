package io.github.lights_out;


import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;


/** The screen that shows what is inside a drawer. Items are dark, can be dragged, and some are sharp. */
public class DrawerView {
    private static final float PANEL_X = 190f;
    private static final float PANEL_Y = 150f;
    private static final float PANEL_W = 900f;
    private static final float PANEL_H = 420f;


    private static final float ITEM_DARKNESS = 0.6f; // lower = darker items = harder to find
    private static final float HURT_TIME = 0.4f;     // how long the red flash lasts


    private final Array<Item> drawer0 = new Array<>();
    private final Array<Item> drawer1 = new Array<>();
    private final Item flashlight;
    private final Texture pixel;


    private boolean loaded = false;
    private int open = -1;       // -1 = closed, 0 or 1 = which drawer is open
    private Item dragged = null; // the item being dragged right now
    private float grabDX, grabDY;
    private float hurtTimer = 0f;


    /** flashlightDrawer: 0 or 1, the drawer the flashlight is buried in. */
    public DrawerView(int flashlightDrawer) {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();


        Item f = buildBuriedDrawer(flashlightDrawer == 0 ? drawer0 : drawer1);
        buildDecoyDrawer(flashlightDrawer == 0 ? drawer1 : drawer0);
        flashlight = f;
    }


    /**
     * The flashlight is at the bottom of the pile. The sharp things lie on top of it,
     * and the harmless pills and key cover the only part that is free of sharp things.
     * Move the pills and the key away to reach the flashlight safely.
     * Item(name, file, dangerous?, x, y, size, rotation). Items are drawn in this order.
     */
    private Item buildBuriedDrawer(Array<Item> list) {
        Item f = new Item("flashlight", "flashlight.png", false, 640, 380, 240, 0);
        list.add(f);
        list.add(new Item("knife", "knife.png", true, 700, 442, 340, 225));
        list.add(new Item("scissors", "scissors.png", true, 749, 307, 260, 270));
        list.add(new Item("screwdriver", "screwdriver.png", true, 646, 312, 310, 180));
        list.add(new Item("pills", "med_pills.png", false, 615, 383, 170, 30));
        list.add(new Item("key", "key_1.png", false, 606, 390, 140, 60));
        return f;
    }


    /** The other drawer: no flashlight, just a few things. */
    private void buildDecoyDrawer(Array<Item> list) {
        list.add(new Item("fork", "fork.png", true, 800, 330, 230, 60));
        list.add(new Item("key", "key_2.png", false, 460, 400, 150, 25));
        list.add(new Item("key", "key_3.png", false, 600, 280, 150, -40));
    }


    private void ensureLoaded() {
        if (loaded) return;
        for (Item it : drawer0) it.load();
        for (Item it : drawer1) it.load();
        loaded = true;
    }


    private Array<Item> current() {
        return open == 0 ? drawer0 : drawer1;
    }


    public void open(int drawer) {
        ensureLoaded();
        open = drawer;
        dragged = null;
    }


    public void close() {
        open = -1;
        dragged = null;
    }


    public boolean isOpen() { return open >= 0; }
    public boolean isDragging() { return dragged != null; }


    public void takeFlashlight() { flashlight.setVisible(false); }


    /** Call when the player gets hurt: flashes the screen red. */
    public void hurt() { hurtTimer = HURT_TIME; }


    public void update(float delta) {
        if (hurtTimer > 0f) hurtTimer = Math.max(0f, hurtTimer - delta);
    }


    public boolean insidePanel(float x, float y) {
        return x >= PANEL_X - 12 && x <= PANEL_X + PANEL_W + 12
            && y >= PANEL_Y - 12 && y <= PANEL_Y + PANEL_H + 12;
    }


    /** The item under the mouse (the top-most one), or null. */
    public Item itemAt(float x, float y) {
        if (open < 0) return null;
        Array<Item> list = current();
        for (int i = list.size - 1; i >= 0; i--) {
            Item it = list.get(i);
            if (it.contains(x, y)) return it;
        }
        return null;
    }


    /** Picks up the item under the mouse and brings it to the front. Returns it (or null). */
    public Item grab(float x, float y) {
        Item it = itemAt(x, y);
        if (it == null) return null;
        dragged = it;
        grabDX = it.getX() - x;
        grabDY = it.getY() - y;
        Array<Item> list = current();
        list.removeValue(it, true);
        list.add(it);
        return it;
    }


    public void dragTo(float x, float y) {
        if (dragged == null) return;
        float nx = MathUtils.clamp(x + grabDX, PANEL_X + 20, PANEL_X + PANEL_W - 20);
        float ny = MathUtils.clamp(y + grabDY, PANEL_Y + 20, PANEL_Y + PANEL_H - 20);
        dragged.setPosition(nx, ny);
    }


    public void release() { dragged = null; }


    public void render(SpriteBatch batch, BitmapFont font) {
        if (open < 0) return;
        batch.begin();


        // dim everything behind
        batch.setColor(0f, 0f, 0f, 0.82f);
        batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);


        // drawer frame and inside
        batch.setColor(0.22f, 0.15f, 0.10f, 1f);
        batch.draw(pixel, PANEL_X - 12, PANEL_Y - 12, PANEL_W + 24, PANEL_H + 24);
        batch.setColor(0.09f, 0.09f, 0.11f, 1f);
        batch.draw(pixel, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);


        // items
        for (Item it : current()) it.draw(batch, ITEM_DARKNESS);


        // red flash when hurt
        if (hurtTimer > 0f) {
            batch.setColor(0.8f, 0f, 0f, 0.4f * (hurtTimer / HURT_TIME));
            batch.draw(pixel, 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
        }
        batch.setColor(Color.WHITE);


        // labels (above the drawer, so the dialogue bar never covers them)
        font.getData().setScale(1.5f);
        font.setColor(0.75f, 0.75f, 0.75f, 1f);
        font.draw(batch, "Searching the drawer...", PANEL_X, PANEL_Y + PANEL_H + 48);
        font.getData().setScale(1.1f);
        font.setColor(0.55f, 0.55f, 0.55f, 1f);
        font.draw(batch, "Drag items to move them. Sharp things hurt!   Click outside or press ESC to close.",
            PANEL_X, PANEL_Y + PANEL_H + 26);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);


        batch.end();
    }


    public void dispose() {
        pixel.dispose();
        for (Item it : drawer0) it.dispose();
        for (Item it : drawer1) it.dispose();
    }
}
