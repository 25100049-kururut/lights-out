package io.github.lights_out;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.util.HashMap;
import java.util.Map;

public class GameScreen implements Screen {
    private static final float EDGE_ZONE = 110f;       // how close to the side the mouse must be (click area)
    private static final float SHADE_WIDTH = 230f;     // how far the edge shadow reaches into the room
    private static final float SHADE_STRENGTH = 0.6f;  // edge shadow darkness (0 to 1)
    private static final float LIGHT_STRENGTH = 0.12f; // faint light when the mouse reaches the side
    private static final float FADE_SPEED = 5f;        // room change fade (higher = faster)

    private static final float LIGHT_BOOST = 0.3f;     // how much lighter the rooms get after the flashlight
    private static final float LIGHT_SPEED = 0.5f;     // how fast the rooms get lighter

    // What shows at the top-left for each task (big line, small line)
    private static final String[] OBJECTIVE = {
        "Find the Flashlight", "Close the windows", "Stop the leaks", "Unplug the devices",
        "Lock the front and back doors", "Stop the rain water", "Store the emergency kit", "Get to the storage room"
    };
    private static final String[] OBJECTIVE_INFO = {
        "in the Kitchen drawers", "all of them", "use the buckets", "around the house",
        "find the keys first", "coming in under the door", "and some supplies", "before time runs out"
    };

    // What you say when you grab something sharp
    private static final String[] OUCH = {
        "Ouch! That's sharp!",
        "Ah! I cut myself. Careful...",
        "Ow! Maybe I should move the other things first."
    };

    private final Main game;
    private final FitViewport viewport = new FitViewport(Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final GlyphLayout layout = new GlyphLayout();
    private final Map<String, Room> rooms = new HashMap<>();
    private final DrawerView drawers;
    private final DialogueBox dialogue = new DialogueBox();

    private Room current;
    private String pendingRoom = null;
    private boolean fadingOut = false;
    private float fade = 0f;
    private float leftReveal = 0f;   // 0 = shadow, 1 = shadow gone and light on
    private float rightReveal = 0f;

    private int currentTask;
    private boolean flashlightFound = false;
    private float lightLevel = 0f;   // 0 = rooms as they were, 1 = lighter (flashlight found)
    private int mistakes = 0;        // counts times you got hurt (the entity will use this later)
    private int ouchIndex = 0;

    public GameScreen(Main game, int startTask) {
        this.game = game;
        this.currentTask = Math.min(startTask, OBJECTIVE.length - 1);

        // the flashlight is buried in a random drawer (0 or 1)
        drawers = new DrawerView(MathUtils.random(1));

        if (startTask >= 1) {
            flashlightFound = true;
            lightLevel = 1f;
            drawers.takeFlashlight();
        }

        buildRooms();
        current = rooms.get("bedroom");
    }

    private void addRoom(Room r) {
        r.load();
        rooms.put(r.getId(), r);
    }

    private void buildRooms() {
        // Bedroom: the door on the left leads to the living room
        Room bedroom = new Room("bedroom", "Bedroom", "bedroom.png", 0.55f);
        bedroom.addHotspot(new Hotspot(new Rectangle(0, 215, 150, 295), "living"));
        addRoom(bedroom);

        // Living room: right door -> bedroom, left side of screen -> kitchen
        Room living = new Room("living", "Living Room", "temp_living_room.png", 1f);
        living.addHotspot(Hotspot.fromImage(1510, 360, 1790, 880, "bedroom"));
        living.setLeftTarget("kitchen");
        addRoom(living);

        // Kitchen: left door -> storage 1, right side of screen -> living room, two drawers
        Room kitchen = new Room("kitchen", "Kitchen", "temp_kitchen.png", 1f);
        kitchen.addHotspot(Hotspot.fromImage(445, 265, 570, 615, "storage1"));
        kitchen.addHotspot(Hotspot.actionFromImage(715, 458, 840, 506, "drawer0"));
        kitchen.addHotspot(Hotspot.actionFromImage(843, 458, 1025, 506, "drawer1"));
        kitchen.setRightTarget("living");
        addRoom(kitchen);

        // Storage 1: left AND right side of screen -> storage 2
        Room storage1 = new Room("storage1", "Storage Room 1", "temp_storage_1.png", 1f);
        storage1.setLeftTarget("storage2");
        storage1.setRightTarget("storage2");
        addRoom(storage1);

        // Storage 2: left AND right side of screen -> storage 1, door -> kitchen
        Room storage2 = new Room("storage2", "Storage Room 2", "temp_storage_2.png", 1f);
        storage2.addHotspot(Hotspot.fromImage(1090, 248, 1220, 585, "kitchen"));
        storage2.setLeftTarget("storage1");
        storage2.setRightTarget("storage1");
        addRoom(storage2);
    }

    private void goTo(String roomId) {
        pendingRoom = roomId;
        fadingOut = true;
    }

    /** The player grabbed an item inside a drawer. */
    private void onGrab(Item item) {
        if (item.getName().equals("flashlight")) {
            drawers.takeFlashlight();
            drawers.close();
            flashlightFound = true;
            currentTask = Math.max(currentTask, 1);
            dialogue.say("Found it! Come on, turn on...",
                "The battery is almost dead. It barely lights anything.");
        } else if (item.isHazard()) {
            mistakes++;
            drawers.hurt();
            dialogue.say(OUCH[ouchIndex % OUCH.length]);
            ouchIndex++;
        }
        // harmless items (keys, pills) can be moved with no penalty
    }

    @Override
    public void render(float delta) {
        // ESC closes the drawer first, otherwise goes back to the menu
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (drawers.isOpen()) {
                drawers.close();
            } else {
                game.setScreen(new MainMenuScreen(game));
                return;
            }
        }

        dialogue.update(delta);
        drawers.update(delta);
        if (flashlightFound && lightLevel < 1f) {
            lightLevel = Math.min(1f, lightLevel + delta * LIGHT_SPEED);
        }

        // fade between rooms
        if (fadingOut) {
            fade += delta * FADE_SPEED;
            if (fade >= 1f) {
                fade = 1f;
                current = rooms.get(pendingRoom);
                fadingOut = false;
                leftReveal = 0f;
                rightReveal = 0f;
            }
        } else if (fade > 0f) {
            fade = Math.max(0f, fade - delta * FADE_SPEED);
        }
        boolean busy = fadingOut || fade > 0f;
        boolean drawerOpen = drawers.isOpen();
        boolean roomActive = !busy && !drawerOpen;

        viewport.apply();
        Vector2 mouse = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        boolean inside = mouse.x >= 0 && mouse.x <= Main.WORLD_WIDTH
            && mouse.y >= 0 && mouse.y <= Main.WORLD_HEIGHT;

        Hotspot hs = (roomActive && inside) ? current.hotspotAt(mouse.x, mouse.y) : null;
        boolean overLeft = roomActive && inside && current.getLeftTarget() != null && mouse.x < EDGE_ZONE;
        boolean overRight = roomActive && inside && current.getRightTarget() != null
            && mouse.x > Main.WORLD_WIDTH - EDGE_ZONE;
        Item hoverItem = (drawerOpen && !busy && inside) ? drawers.itemAt(mouse.x, mouse.y) : null;

        // the shadow lifts and the light grows as the mouse gets closer to that side
        float leftAim = overLeft ? 1f - mouse.x / EDGE_ZONE : 0f;
        float rightAim = overRight ? 1f - (Main.WORLD_WIDTH - mouse.x) / EDGE_ZONE : 0f;
        float smooth = Math.min(1f, delta * 6f);
        leftReveal += (leftAim - leftReveal) * smooth;
        rightReveal += (rightAim - rightReveal) * smooth;

        Gdx.graphics.setSystemCursor(
            (hs != null || overLeft || overRight || hoverItem != null || drawers.isDragging())
                ? Cursor.SystemCursor.Hand
                : Cursor.SystemCursor.Arrow);

        // clicks and dragging
        if (drawerOpen && !busy) {
            if (Gdx.input.justTouched()) {
                if (hoverItem != null) {
                    Item grabbed = drawers.grab(mouse.x, mouse.y);
                    if (grabbed != null) onGrab(grabbed);
                } else if (!drawers.insidePanel(mouse.x, mouse.y)) {
                    drawers.close();
                }
            }
            if (drawers.isDragging()) {
                if (Gdx.input.isTouched()) drawers.dragTo(mouse.x, mouse.y);
                else drawers.release();
            }
        } else if (!busy && Gdx.input.justTouched()) {
            if (hs != null) {
                if (hs.getAction() != null) {
                    drawers.open(hs.getAction().equals("drawer0") ? 0 : 1);
                } else {
                    goTo(hs.getTargetRoom());
                }
            } else if (overLeft) {
                goTo(current.getLeftTarget());
            } else if (overRight) {
                goTo(current.getRightTarget());
            }
        }

        ScreenUtils.clear(0, 0, 0, 1);
        game.batch.setProjectionMatrix(viewport.getCamera().combined);
        shapes.setProjectionMatrix(viewport.getCamera().combined);

        // room picture (a bit lighter once the flashlight is found)
        float b = current.getBrightness();
        game.batch.begin();
        game.batch.setColor(b, b, b, 1f);
        game.batch.draw(current.getTexture(), 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
        if (lightLevel > 0f) {
            game.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // adds light
            game.batch.setColor(b, b, b, LIGHT_BOOST * lightLevel);
            game.batch.draw(current.getTexture(), 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
            game.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        game.batch.setColor(Color.WHITE);
        game.batch.end();

        // edge shadows (and faint light) on the sides that lead somewhere
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        if (!drawerOpen) {
            if (current.getLeftTarget() != null) drawEdgeShade(true, leftReveal);
            if (current.getRightTarget() != null) drawEdgeShade(false, rightReveal);
        }

        // black fade while changing rooms
        if (fade > 0f) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, fade);
            shapes.rect(0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
            shapes.end();
        }

        // the open drawer
        drawers.render(game.batch, game.font);

        // text
        game.batch.begin();

        // measure how wide the "> " is, so the second line can start exactly under the first word
        game.font.getData().setScale(1.8f);
        layout.setText(game.font, "> Find");
        float withArrow = layout.width;
        layout.setText(game.font, "Find");
        float indent = withArrow - layout.width;

        game.font.setColor(Color.WHITE);
        game.font.draw(game.batch, "> " + OBJECTIVE[currentTask], 30, 690);

        game.font.getData().setScale(1.2f);
        game.font.setColor(0.85f, 0.85f, 0.85f, 1f);
        game.font.draw(game.batch, OBJECTIVE_INFO[currentTask], 30 + indent, 658);

        game.font.getData().setScale(1f);
        game.font.setColor(0.7f, 0.7f, 0.7f, 1f);
        game.font.draw(game.batch, "Task " + (currentTask + 1) + " of 8   |   "
            + current.getDisplayName() + "   |   ESC = menu", 30, 630);

        game.font.setColor(Color.WHITE);
        dialogue.render(game.batch, game.font);
        game.font.setColor(Color.WHITE);
        game.batch.end();
    }

    /** Dark shadow at the screen edge. As the mouse gets close, the shadow lifts and a faint light appears. */
    private void drawEdgeShade(boolean left, float reveal) {
        float h = Main.WORLD_HEIGHT;

        // shadow (fades away as the mouse gets close)
        float a = SHADE_STRENGTH * (1f - reveal);
        if (a >= 0.01f) {
            Color dark = new Color(0f, 0f, 0f, a);
            Color clear = new Color(0f, 0f, 0f, 0f);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            if (left) {
                shapes.rect(0, 0, SHADE_WIDTH, h, dark, clear, clear, dark);
            } else {
                shapes.rect(Main.WORLD_WIDTH - SHADE_WIDTH, 0, SHADE_WIDTH, h, clear, dark, dark, clear);
            }
            shapes.end();
        }

        // faint light (grows as the mouse gets close)
        float l = LIGHT_STRENGTH * reveal;
        if (l >= 0.005f) {
            float lightWidth = 120f;
            Color on = new Color(0.8f, 0.85f, 0.95f, l);
            Color off = new Color(0.8f, 0.85f, 0.95f, 0f);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive = light
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            if (left) {
                shapes.rect(0, 0, lightWidth, h, on, off, off, on);
            } else {
                shapes.rect(Main.WORLD_WIDTH - lightWidth, 0, lightWidth, h, off, on, on, off);
            }
            shapes.end();
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
    @Override public void show() { }
    @Override public void pause() { }
    @Override public void resume() { }

    @Override
    public void hide() {
        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
        dispose();
    }

    @Override
    public void dispose() {
        for (Room r : rooms.values()) r.dispose();
        drawers.dispose();
        dialogue.dispose();
        shapes.dispose();
    }
}
