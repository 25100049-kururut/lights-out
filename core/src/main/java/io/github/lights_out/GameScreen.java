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
import com.badlogic.gdx.utils.Array;
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

    private static final int START_OPEN = 4;           // how open the windows start (4 = wide open = 4 hits each)

    // What shows at the top-left for each task (big line, small line)
    private static final String[] OBJECTIVE = {
        "Find the Flashlight", "Close the windows", "Stop the leaks", "Unplug the devices",
        "Lock the front and back doors", "Stop the rain water", "Store the emergency kit", "Get to the storage room"
    };
    private static final String[] OBJECTIVE_INFO = {
        "in the Kitchen drawers", "every window in the house", "use the buckets", "around the house",
        "find the keys first", "coming in under the door", "and some supplies", "before time runs out"
    };

    // What you say when you grab something sharp
    private static final String[] OUCH = {
        "Ouch! That's sharp!",
        "Ah! I cut myself. Careful...",
        "Ow! Maybe I should move the other things first."
    };

    // What you say when you miss the green
    private static final String[] MISS = {
        "No! The wind pushed it open more!",
        "Too slow! It's opening wider!",
        "Come on, focus... it's swinging open!"
    };

    // What you say when a water drop is missed
    private static final String[] SPILL = {
        "Ugh! It spilled on the floor!",
        "No! I missed it!",
        "The floor is getting soaked!"
    };

    private final Main game;
    private final FitViewport viewport = new FitViewport(Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final GlyphLayout layout = new GlyphLayout();
    private final Map<String, Room> rooms = new HashMap<>();
    private final DrawerView drawers;
    private final DialogueBox dialogue = new DialogueBox();

    private final WindowFrames windowFrames = new WindowFrames();
    private final Array<GameWindow> windows = new Array<>();
    private final WindowMinigame minigame = new WindowMinigame();
    private boolean windowsActive = false;

    private final LeakMinigame leakGame = new LeakMinigame();
    private final Array<Leak> leaks = new Array<>();

    private Room current;
    private String pendingRoom = null;
    private boolean fadingOut = false;
    private float fade = 0f;
    private float leftReveal = 0f;   // 0 = shadow, 1 = shadow gone and light on
    private float rightReveal = 0f;
    private float time = 0f;

    private int currentTask;
    private boolean flashlightFound = false;
    private float lightLevel = 0f;   // 0 = rooms as they were, 1 = lighter (flashlight found)
    private int mistakes = 0;        // counts mistakes (the entity will use this later)
    private int ouchIndex = 0;
    private int missIndex = 0;
    private int spillIndex = 0;

    public GameScreen(Main game, int startTask) {
        this.game = game;
        this.currentTask = Math.min(startTask, OBJECTIVE.length - 1);

        // the flashlight is buried in a random drawer (0 or 1)
        drawers = new DrawerView(MathUtils.random(1));

        buildRooms();

        if (startTask >= 1) {
            flashlightFound = true;
            lightLevel = 1f;
            drawers.takeFlashlight();
            windowsActive = true;
            for (GameWindow w : windows) {
                w.setLevel(startTask >= 2 ? 0 : START_OPEN);
                w.snap();
            }
        }
        if (startTask >= 3) {
            for (Leak l : leaks) l.setDone(true);
        }

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

        // Kitchen: left door -> storage 1, left AND right side of screen -> living room, two drawers
        Room kitchen = new Room("kitchen", "Kitchen", "temp_kitchen.png", 1f);
        kitchen.addHotspot(Hotspot.fromImage(445, 265, 570, 615, "storage1"));
        kitchen.addHotspot(Hotspot.actionFromImage(715, 458, 840, 506, "drawer0"));
        kitchen.addHotspot(Hotspot.actionFromImage(843, 458, 1025, 506, "drawer1"));
        kitchen.setLeftTarget("living");
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

        // The windows that must be closed. These are the windows already in the room pictures:
        // room, x, y, width, height (the click area on the window)
        windows.add(new GameWindow("bedroom", 842, 325, 123, 276));
        windows.add(new GameWindow("living", 412, 352, 173, 200));
        windows.add(new GameWindow("living", 702, 352, 179, 200));
        windows.add(new GameWindow("kitchen", 568, 412, 130, 64));

        // The puddles (leaks): room, x of the middle, y of the middle, width
        leaks.add(new Leak("kitchen", 520, 195, 120));
        leaks.add(new Leak("living", 707, 187, 125));
        leaks.add(new Leak("storage1", 347, 161, 115));
        leaks.add(new Leak("storage2", 567, 168, 115));
    }

    private void goTo(String roomId) {
        pendingRoom = roomId;
        fadingOut = true;
    }

    private int closedCount() {
        int n = 0;
        for (GameWindow w : windows) if (w.isClosed()) n++;
        return n;
    }

    private int leaksLeft() {
        int n = 0;
        for (Leak l : leaks) if (!l.isDone()) n++;
        return n;
    }

    /** A window of the current room under the mouse. wantClosed: look for closed or open ones. */
    private GameWindow windowAt(float x, float y, boolean wantClosed) {
        if (!windowsActive) return null;
        for (GameWindow w : windows) {
            if (w.getRoomId().equals(current.getId()) && w.isClosed() == wantClosed && w.contains(x, y)) {
                return w;
            }
        }
        return null;
    }

    /** A puddle (or the bucket that replaced it) of the current room under the mouse. */
    private Leak leakAt(float x, float y) {
        if (currentTask < 2) return null;
        for (Leak l : leaks) {
            if (l.getRoomId().equals(current.getId()) && l.contains(x, y)) return l;
        }
        return null;
    }

    /** The player grabbed an item inside a drawer. */
    private void onGrab(Item item) {
        if (item.getName().equals("flashlight")) {
            drawers.takeFlashlight();
            drawers.close();
            flashlightFound = true;
            currentTask = Math.max(currentTask, 1);

            // the storm blows the windows open: task 2 begins
            windowsActive = true;
            for (GameWindow w : windows) {
                w.setLevel(START_OPEN);
                w.snap();
            }

            dialogue.say("Found it! Come on, turn on...",
                "The battery is almost dead. It barely lights anything.",
                "The windows are rattling... I have to close every one!");
        } else if (item.isHazard()) {
            mistakes++;
            drawers.hurt();
            dialogue.say(OUCH[ouchIndex % OUCH.length]);
            ouchIndex++;
        }
        // harmless items (keys, pills) can be moved with no penalty
    }

    /** The player pressed the CLOSE button in the window minigame. */
    private void onWindowPress() {
        GameWindow w = minigame.getTarget();
        WindowMinigame.Result result = minigame.press();

        if (result == WindowMinigame.Result.SUCCESS) {
            if (w != null && w.isClosed()) {
                if (closedCount() == windows.size) {
                    currentTask = Math.max(currentTask, 2);
                    dialogue.say("That's all of them. The wind can't get in now.",
                        "...Wait. What's that dripping sound? There are puddles everywhere!");
                } else {
                    dialogue.say("That one's shut. " + (windows.size - closedCount()) + " more to go.");
                }
            }
        } else if (result == WindowMinigame.Result.FAIL) {
            mistakes++;
            dialogue.say(MISS[missIndex % MISS.length]);
            missIndex++;
        }
    }

    @Override
    public void render(float delta) {
        // ESC closes the minigame or the drawer first, otherwise goes back to the menu
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (leakGame.isActive()) {
                leakGame.close();
            } else if (minigame.isActive()) {
                minigame.close();
            } else if (drawers.isOpen()) {
                drawers.close();
            } else {
                game.setScreen(new MainMenuScreen(game));
                return;
            }
        }

        time += delta;
        viewport.apply();
        Vector2 mouse = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));

        dialogue.update(delta);
        drawers.update(delta);
        minigame.update(delta);
        leakGame.update(delta, mouse.x);
        for (GameWindow w : windows) w.update(delta);
        if (flashlightFound && lightLevel < 1f) {
            lightLevel = Math.min(1f, lightLevel + delta * LIGHT_SPEED);
        }

        // missed drops in the leak minigame: the fail effect
        int misses = leakGame.takeMisses();
        if (misses > 0) {
            mistakes += misses;
            dialogue.say(SPILL[spillIndex % SPILL.length]);
            spillIndex++;
        }

        // the 20th drop was caught: this leak is stopped
        if (leakGame.takeFinished()) {
            Leak l = leakGame.getTarget();
            if (l != null) l.setDone(true);
            if (leaksLeft() == 0) {
                currentTask = Math.max(currentTask, 3);
                dialogue.say("That's the last leak. The floor is finally safe.",
                    "I should unplug everything before the power comes back.");
            } else {
                dialogue.say("Leak stopped. " + leaksLeft() + " more to go.");
            }
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
        boolean windowOpen = minigame.isActive();
        boolean leakOpen = leakGame.isActive();
        boolean roomActive = !busy && !drawerOpen && !windowOpen && !leakOpen;

        boolean inside = mouse.x >= 0 && mouse.x <= Main.WORLD_WIDTH
            && mouse.y >= 0 && mouse.y <= Main.WORLD_HEIGHT;

        Hotspot hs = (roomActive && inside) ? current.hotspotAt(mouse.x, mouse.y) : null;
        boolean free = roomActive && inside && hs == null;
        GameWindow openWindow = free ? windowAt(mouse.x, mouse.y, false) : null;  // an open window
        GameWindow shutWindow = free ? windowAt(mouse.x, mouse.y, true) : null;   // an already closed window
        Leak hoverLeak = free ? leakAt(mouse.x, mouse.y) : null;
        boolean overLeft = roomActive && inside && current.getLeftTarget() != null && mouse.x < EDGE_ZONE;
        boolean overRight = roomActive && inside && current.getRightTarget() != null
            && mouse.x > Main.WORLD_WIDTH - EDGE_ZONE;
        Item hoverItem = (drawerOpen && !busy && inside) ? drawers.itemAt(mouse.x, mouse.y) : null;
        boolean overButton = windowOpen && !busy && minigame.buttonContains(mouse.x, mouse.y);

        // the shadow lifts and the light grows as the mouse gets closer to that side
        float leftAim = overLeft ? 1f - mouse.x / EDGE_ZONE : 0f;
        float rightAim = overRight ? 1f - (Main.WORLD_WIDTH - mouse.x) / EDGE_ZONE : 0f;
        float smooth = Math.min(1f, delta * 6f);
        leftReveal += (leftAim - leftReveal) * smooth;
        rightReveal += (rightAim - rightReveal) * smooth;

        boolean overPuddle = hoverLeak != null && !hoverLeak.isDone();
        Gdx.graphics.setSystemCursor(
            (hs != null || openWindow != null || overPuddle || overLeft || overRight
                || hoverItem != null || drawers.isDragging() || overButton)
                ? Cursor.SystemCursor.Hand
                : Cursor.SystemCursor.Arrow);

        // clicks and dragging
        if (windowOpen && !busy) {
            boolean pressed = Gdx.input.justTouched() && overButton;
            if (pressed || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) onWindowPress();
        } else if (leakOpen) {
            // nothing to click: the mouse moves the bucket
        } else if (drawerOpen && !busy) {
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
            } else if (openWindow != null) {
                minigame.open(openWindow);
            } else if (shutWindow != null) {
                dialogue.say("This window is already closed.");
            } else if (hoverLeak != null) {
                if (hoverLeak.isDone()) dialogue.say("I already put a bucket here.");
                else leakGame.open(hoverLeak);
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

        // the puddles (a small bucket replaces a puddle once its leak is stopped)
        if (currentTask >= 2) {
            for (Leak l : leaks) {
                if (!l.getRoomId().equals(current.getId())) continue;
                if (l.isDone()) leakGame.drawBucketSmall(game.batch, l, b);
                else leakGame.drawPuddle(game.batch, l, b, time);
            }
        }
        game.batch.setColor(Color.WHITE);
        game.batch.end();

        // edge shadows (and faint light) on the sides that lead somewhere
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        if (!drawerOpen && !windowOpen && !leakOpen) {
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

        // the open drawer and the minigames
        drawers.render(game.batch, game.font);
        minigame.render(game.batch, game.font, windowFrames, mouse.x, mouse.y);
        leakGame.render(game.batch, game.font);

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

        String info;
        if (currentTask == 1) info = "Windows closed: " + closedCount() + " of " + windows.size;
        else if (currentTask == 2) info = "Leaks stopped: " + (leaks.size - leaksLeft()) + " of " + leaks.size;
        else info = OBJECTIVE_INFO[currentTask];
        game.font.getData().setScale(1.2f);
        game.font.setColor(0.85f, 0.85f, 0.85f, 1f);
        game.font.draw(game.batch, info, 30 + indent, 658);

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
        windowFrames.dispose();
        minigame.dispose();
        leakGame.dispose();
        shapes.dispose();
    }
}
