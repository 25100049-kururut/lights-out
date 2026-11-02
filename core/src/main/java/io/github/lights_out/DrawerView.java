package io.github.lights_out;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Array;

/** The screen that shows what is inside a drawer. Items are dark so they are hard to find. */
public class DrawerView {
    private static final float PANEL_X = 190f;
    private static final float PANEL_Y = 150f;
    private static final float PANEL_W = 900f;
    private static final float PANEL_H = 420f;

    private static final float ITEM_DARKNESS = 0.6f; // lower = darker items = harder to find

    private final Array<Item> drawer0 = new Array<>();
    private final Array<Item> drawer1 = new Array<>();
    private final Item flashlight;
    private final Texture pixel;

    private boolean loaded = false;
    private int open = -1; // -1 = closed, 0 or 1 = which drawer is open

    /** flashlightDrawer: 0 or 1, the drawer the flashlight is hidden in. */
    public DrawerView(int flashlightDrawer) {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        // Drawer 1 (left drawer): name, file, dangerous?, x, y, size, rotation
        drawer0.add(new Item("knife", "knife.png", true, 380, 360, 290, 20));
        drawer0.add(new Item("key", "key_1.png", false, 560, 300, 130, -35));
        drawer0.add(new Item("pills", "med_pills.png", false, 740, 410, 170, 10));
        drawer0.add(new Item("fork", "fork.png", true, 880, 300, 220, 70));

        // Drawer 2 (right drawer)
        drawer1.add(new Item("scissors", "scissors.png", true, 400, 380, 240, -15));
        drawer1.add(new Item("screwdriver", "screwdriver.png", true, 560, 330, 260, 60));
        drawer1.add(new Item("key", "key_2.png", false, 770, 430, 150, 30));
        drawer1.add(new Item("key", "key_3.png", false, 890, 290, 150, -50));

        // The flashlight hides in one of the two drawers
        if (flashlightDrawer == 0) {
            flashlight = new Item("flashlight", "flashlight.png", false, 640, 240, 220, -20);
            drawer0.add(flashlight);
        } else {
            flashlight = new Item("flashlight", "flashlight.png", false, 700, 240, 220, 15);
            drawer1.add(flashlight);
        }
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
    }

    public void close() { open = -1; }
    public boolean isOpen() { return open >= 0; }

    public void takeFlashlight() { flashlight.setVisible(false); }

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
        batch.setColor(Color.WHITE);

        // labels (both above the drawer, so the dialogue bar never covers them)
        font.getData().setScale(1.5f);
        font.setColor(0.75f, 0.75f, 0.75f, 1f);
        font.draw(batch, "Searching the drawer...", PANEL_X, PANEL_Y + PANEL_H + 45);
        font.getData().setScale(1.1f);
        font.setColor(0.55f, 0.55f, 0.55f, 1f);
        font.draw(batch, "Click outside the drawer or press ESC to close", PANEL_X + 400, PANEL_Y + PANEL_H + 42);
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
