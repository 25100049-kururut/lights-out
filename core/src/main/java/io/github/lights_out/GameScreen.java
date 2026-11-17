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
    private static final float SURGE_TIME = 120f;      // seconds for the surge meter to fill (then you lose)

    private static final int MAX_CHANCES = 10;         // how many mistakes you can make before the jumpscare (hidden from the player)
    private static final int CHANCES_PER_TASK = 2;     // chances you get back when you finish a task
    private static final int LOW_CHANCES = 3;          // at this many chances or fewer, the warnings start

    private static final float FLASH_BRIGHTNESS = 0.5f; // how bright the brightest lightning flash is (0 to 1)

    // What shows at the top-left for each task (big line, small line)
    private static final String[] OBJECTIVE = {
        "Find the Flashlight", "Close the windows", "Stop the leaks", "Unplug the devices",
        "Lock the front and back doors", "Stop the rain water", "Store the emergency kit", "Get to the storage room"
    };
    private static final String[] OBJECTIVE_INFO = {
        "in the Kitchen drawers", "every window in the house", "use the buckets", "before the power surges",
        "the living room and the kitchen", "coming in under the door", "and some supplies", "before time runs out"
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

    // What you say when you pull the plug too fast or too slow
    private static final String[] TOO_FAST = {
        "Whoa, too fast! It sparked!",
        "Don't yank it! Gently...",
        "Too quick! The outlet shocked me."
    };
    private static final String[] TOO_SLOW = {
        "Too slow! It's sparking!",
        "Don't hesitate! Pull it out smoothly!",
        "Come on, it's getting hot... faster!"
    };

    // What you say when a door lock check goes wrong
    private static final String[] DOOR_WRONG = {
        "Wrong key! The bolt jammed!",
        "No, not that one! It's stuck!",
        "Focus! The lock is jamming..."
    };
    private static final String[] DOOR_EARLY = {
        "Too early! The lock slipped!",
        "Wait for it... the bolt slid back!",
        "Not yet! Patience..."
    };
    private static final String[] DOOR_SLOW = {
        "Too slow! The bolt slid back!",
        "I hesitated... it unlocked again!",
        "Come on, faster! The lock is loosening!"
    };

    private final Main game;
    private final FitViewport viewport = new FitViewport(Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final GlyphLayout layout = new GlyphLayout();
    private final Map<String, Room> rooms = new HashMap<>();
    private final DrawerView drawers;
    private final DialogueBox dialogue = new DialogueBox();
    private final Jumpscare jumpscare = new Jumpscare();
    private final Lightning lightning;   // random lightning flashes (the thunder comes with them)

    private final WindowFrames windowFrames = new WindowFrames();
    private final Array<GameWindow> windows = new Array<>();
    private final WindowMinigame minigame = new WindowMinigame();
    private boolean windowsActive = false;

    private final LeakMinigame leakGame = new LeakMinigame();
    private final Array<Leak> leaks = new Array<>();

    private final UnplugMinigame unplugGame = new UnplugMinigame();
    private final Array<Device> devices = new Array<>();
    private float surge = 0f;          // 0 = empty, 1 = full (you lose)

    private final DoorMinigame doorGame = new DoorMinigame();
    private final Array<MainDoor> doors = new Array<>();

    private boolean gameOver = false;
    private float gameOverTimer = 0f;
    private String gameOverTitle = "THE POWER CAME BACK";
    private String gameOverText = "The surge was too strong. You lost.";

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
    private int mistakes = 0;        // counts every mistake (the entity will use this later)
    private int chances = MAX_CHANCES; // mistakes left before the jumpscare
    private boolean warnedLow = false;
    private int ouchIndex = 0;
    private int missIndex = 0;
    private int spillIndex = 0;
    private int plugIndex = 0;
    private int doorIndex = 0;

    public GameScreen(Main game, int startTask) {
        this.game = game;
        this.currentTask = Math.min(startTask, OBJECTIVE.length - 1);
        this.lightning = new Lightning(game.audio, 5f, 7f, 20f);
        game.audio.startAmbience();
        game.audio.setHorrorEvents(true);

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
        if (startTask >= 4) {
            for (Device d : devices) d.setDone(true);
        }
        if (startTask >= 5) {
            for (MainDoor d : doors) d.setDone(true);
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

        // The devices to unplug: room, name, x, y, width, height (the click area on the device).
        // setOutlet(x, y, width, height) adds a click area on the wall outlet it is plugged into.
        Device computer = new Device("bedroom", "computer", 1086, 320, 148, 100);
        computer.setOutlet(868, 296, 42, 26);
        devices.add(computer);

        devices.add(new Device("living", "TV", 905, 267, 70, 125));
        devices.add(new Device("living", "fan", 510, 315, 60, 80));

        Device kitchenFan = new Device("kitchen", "fan", 203, 180, 90, 300);
        kitchenFan.setOutlet(146, 205, 48, 40);
        devices.add(kitchenFan);

        // The main doors to lock: room, name, then the click area in the 1920x953 room picture
        doors.add(MainDoor.fromImage("living", "front door", 380, 294, 575, 585));
        doors.add(MainDoor.fromImage("kitchen", "back door", 1274, 267, 1476, 530));
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

    private int devicesLeft() {
        int n = 0;
        for (Device d : devices) if (!d.isDone()) n++;
        return n;
    }

    private int doorsLeft() {
        int n = 0;
        for (MainDoor d : doors) if (!d.isDone()) n++;
        return n;
    }

    /** A mistake: costs chances, and at 0 chances the jumpscare starts. line = what you say. */
    private void fail(int count, String line) {
        mistakes += count;
        chances = Math.max(0, chances - count);
        game.audio.play("sfx_thump", 0.8f);   // a dull thud somewhere in the house

        if (chances <= 0) {
            startJumpscare();
            return;
        }
        if (chances <= LOW_CHANCES && !warnedLow) {
            warnedLow = true;
            dialogue.say(line, "I'm running out of chances... Something is in the house.");
        } else {
            dialogue.say(line);
        }
    }

    /** A task was finished: you get some chances back (never more than the maximum). */
    private void gainChances() {
        chances = Math.min(MAX_CHANCES, chances + CHANCES_PER_TASK);
        game.audio.play("sfx_success", 0.6f);
        if (chances > LOW_CHANCES) warnedLow = false;
    }

    private void startJumpscare() {
        startJumpscare("IT FOUND YOU", "You made too many mistakes. It heard every one.");
    }

    private void startJumpscare(String title, String text) {
        drawers.close();
        minigame.close();
        leakGame.close();
        unplugGame.close();
        doorGame.close();
        game.audio.setMood(0.1f, 0.05f, 0f);   // the storm goes quiet: only the scream (jumpscare.wav)
        game.audio.setHorrorEvents(false);
        game.audio.setHeartbeat(0f);
        gameOver = true;
        gameOverTimer = 0f;
        gameOverTitle = title;
        gameOverText = text;
        jumpscare.start();
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

    /** A device (or its outlet) of the current room under the mouse, once the unplug task has started. */
    private Device deviceAt(float x, float y) {
        if (currentTask < 3) return null;
        for (Device d : devices) {
            if (d.getRoomId().equals(current.getId()) && d.contains(x, y)) return d;
        }
        return null;
    }

    /** A main door of the current room under the mouse, once the lock-the-doors task has started. */
    private MainDoor doorAt(float x, float y) {
        if (currentTask < 4) return null;
        for (MainDoor d : doors) {
            if (d.getRoomId().equals(current.getId()) && d.contains(x, y)) return d;
        }
        return null;
    }

    /** The player grabbed an item inside a drawer. */
    private void onGrab(Item item, float x, float y) {
        if (item.getName().equals("flashlight")) {
            drawers.takeFlashlight();
            drawers.close();
            game.audio.play("sfx_flashlight");
            flashlightFound = true;
            currentTask = Math.max(currentTask, 1);
            gainChances();

            // the storm blows the windows open: task 2 begins
            windowsActive = true;
            for (GameWindow w : windows) {
                w.setLevel(START_OPEN);
                w.snap();
            }

            dialogue.say("Found it! Come on, turn on...",
                "The battery is almost dead. It barely lights anything.",
                "The windows are rattling... I have to close every one!");
        } else if (item.isSharpAt(x, y)) {
            // only the sharp part (blade, tip, prongs) hurts; handles are safe
            drawers.hurt();
            game.audio.play("sfx_hurt");
            fail(1, OUCH[ouchIndex % OUCH.length]);
            ouchIndex++;
        }
        // harmless items (keys, pills) and the safe parts of sharp things (handles) can be moved with no penalty
    }

    /** The player pressed the CLOSE button in the window minigame. */
    private void onWindowPress() {
        GameWindow w = minigame.getTarget();
        WindowMinigame.Result result = minigame.press();

        if (result == WindowMinigame.Result.SUCCESS) {
            if (w != null && w.isClosed()) {
                game.audio.play("sfx_shut");
                if (closedCount() == windows.size) {
                    currentTask = Math.max(currentTask, 2);
                    gainChances();
                    dialogue.say("That's all of them. The wind can't get in now.",
                        "...Wait. What's that dripping sound? There are puddles everywhere!");
                } else {
                    dialogue.say("That one's shut. " + (windows.size - closedCount()) + " more to go.");
                }
            }
        } else if (result == WindowMinigame.Result.FAIL) {
            fail(1, MISS[missIndex % MISS.length]);
            missIndex++;
        }
    }

    @Override
    public void render(float delta) {
        // ESC closes the minigame or the drawer first, otherwise goes back to the menu
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (doorGame.isActive()) {
                doorGame.close();
            } else if (unplugGame.isActive()) {
                unplugGame.close();
            } else if (leakGame.isActive()) {
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

        // the game is lost (jumpscare or surge)
        if (gameOver) {
            renderGameOver(delta);
            return;
        }

        time += delta;
        lightning.update(delta);
        updateAudioMood();
        viewport.apply();
        Vector2 mouse = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));

        dialogue.update(delta);
        drawers.update(delta);
        minigame.update(delta);
        leakGame.update(delta, mouse.x);
        unplugGame.update(delta, mouse.x, mouse.y, Gdx.input.justTouched(), Gdx.input.isTouched());
        doorGame.update(delta, Gdx.input.justTouched());
        for (GameWindow w : windows) w.update(delta);
        if (flashlightFound && lightLevel < 1f) {
            lightLevel = Math.min(1f, lightLevel + delta * LIGHT_SPEED);
        }

        // the surge meter fills while the unplug task is going
        if (currentTask == 3) {
            surge += delta / SURGE_TIME;
            if (surge >= 1f) {
                surge = 1f;
                gameOver = true;
                gameOverTimer = 0f;
                gameOverTitle = "THE POWER CAME BACK";
                gameOverText = "The surge was too strong. You lost.";
                game.audio.play("sfx_zap");
                game.audio.play("sfx_power_down");
                game.audio.setMood(0.3f, 0.2f, 0f);
                game.audio.setHorrorEvents(false);
                game.audio.setHeartbeat(0f);
                drawers.close();
                minigame.close();
                leakGame.close();
                unplugGame.close();
                doorGame.close();
                return;
            }
        }

        // missed drops in the leak minigame: the fail effect
        int misses = leakGame.takeMisses();
        if (misses > 0) {
            game.audio.play("sfx_drip");
            fail(misses, SPILL[spillIndex % SPILL.length]);
            spillIndex++;
        }

        // the 20th drop was caught: this leak is stopped
        if (leakGame.takeFinished()) {
            Leak l = leakGame.getTarget();
            if (l != null) l.setDone(true);
            if (leaksLeft() == 0) {
                currentTask = Math.max(currentTask, 3);
                surge = 0f;
                gainChances();
                dialogue.say("That's the last leak. The floor is finally safe.",
                    "The power could surge back any second! I have to unplug everything, fast!");
            } else {
                dialogue.say("Leak stopped. " + leaksLeft() + " more to go.");
            }
        }

        // pulled the plug too fast or too slow: the fail effect
        int plugFails = unplugGame.takeZaps();
        if (plugFails > 0) {
            game.audio.play("sfx_zap");
            String[] lines = unplugGame.wasTooFast() ? TOO_FAST : TOO_SLOW;
            fail(plugFails, lines[plugIndex % lines.length]);
            plugIndex++;
        }

        // the plug came out: this device is unplugged
        if (unplugGame.takeFinished()) {
            Device d = unplugGame.getTarget();
            if (d != null) d.setDone(true);
            if (devicesLeft() == 0) {
                currentTask = Math.max(currentTask, 4);
                gainChances();
                dialogue.say("That's everything. No more sparks... for now.",
                    "Now I have to lock both main doors. One in the living room, one in the kitchen!");
            } else {
                dialogue.say("Unplugged. " + devicesLeft() + " more to go!");
            }
        }

        // the spam bar of the door lock ran completely empty: instant jumpscare
        if (doorGame.takeDrained()) {
            startJumpscare("THE DOOR WAS NEVER LOCKED", "The bolt slid back. It was already inside.");
        }

        // wrong letter, too early or too slow on the lock: the fail effect
        int doorFails = doorGame.takeFails();
        if (doorFails > 0) {
            String[] lines = doorGame.getFailReason() == DoorMinigame.FAIL_WRONG_KEY ? DOOR_WRONG
                : doorGame.getFailReason() == DoorMinigame.FAIL_TOO_EARLY ? DOOR_EARLY : DOOR_SLOW;
            fail(doorFails, lines[doorIndex % lines.length]);
            doorIndex++;
        }

        // the lock clicked: this door is locked
        if (doorGame.takeFinished()) {
            MainDoor d = doorGame.getTarget();
            if (d != null) d.setDone(true);
            game.audio.play("sfx_lock");
            if (doorsLeft() == 0) {
                currentTask = Math.max(currentTask, 5);
                gainChances();
                dialogue.say("Both doors are locked. Nothing is coming in that way.",
                    "...But I can hear rain water coming in under the door!");
            } else {
                dialogue.say("Door locked. " + doorsLeft() + " more to go!");
            }
        }

        // a mistake used up the last chance: the jumpscare starts
        if (gameOver) {
            renderGameOver(delta);
            return;
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
        boolean unplugOpen = unplugGame.isActive();
        boolean doorOpen = doorGame.isActive();
        boolean roomActive = !busy && !drawerOpen && !windowOpen && !leakOpen && !unplugOpen && !doorOpen;

        boolean inside = mouse.x >= 0 && mouse.x <= Main.WORLD_WIDTH
            && mouse.y >= 0 && mouse.y <= Main.WORLD_HEIGHT;

        Hotspot hs = (roomActive && inside) ? current.hotspotAt(mouse.x, mouse.y) : null;
        boolean free = roomActive && inside && hs == null;
        GameWindow openWindow = free ? windowAt(mouse.x, mouse.y, false) : null;  // an open window
        GameWindow shutWindow = free ? windowAt(mouse.x, mouse.y, true) : null;   // an already closed window
        Leak hoverLeak = free ? leakAt(mouse.x, mouse.y) : null;
        Device hoverDevice = free ? deviceAt(mouse.x, mouse.y) : null;
        MainDoor hoverDoor = free ? doorAt(mouse.x, mouse.y) : null;
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
        boolean overPlugged = hoverDevice != null && !hoverDevice.isDone();
        boolean overUnlocked = hoverDoor != null && !hoverDoor.isDone();
        boolean overPlug = unplugOpen && (unplugGame.isGrabbed() || unplugGame.isOverPlug(mouse.x, mouse.y));
        Gdx.graphics.setSystemCursor(
            (hs != null || openWindow != null || overPuddle || overPlugged || overUnlocked || overLeft || overRight
                || hoverItem != null || drawers.isDragging() || overButton || overPlug)
                ? Cursor.SystemCursor.Hand
                : Cursor.SystemCursor.Arrow);

        // clicks and dragging
        if (windowOpen && !busy) {
            boolean pressed = Gdx.input.justTouched() && overButton;
            if (pressed || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) onWindowPress();
        } else if (leakOpen || unplugOpen || doorOpen) {
            // nothing to click here: the minigame reads the mouse and keys by itself
        } else if (drawerOpen && !busy) {
            if (Gdx.input.justTouched()) {
                if (hoverItem != null) {
                    Item grabbed = drawers.grab(mouse.x, mouse.y);
                    if (grabbed != null) onGrab(grabbed, mouse.x, mouse.y);
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
                    game.audio.play("sfx_click");
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
            } else if (hoverDevice != null) {
                if (hoverDevice.isDone()) dialogue.say("The " + hoverDevice.getName() + " is already unplugged.");
                else unplugGame.open(hoverDevice);
            } else if (hoverDoor != null) {
                if (hoverDoor.isDone()) dialogue.say("The " + hoverDoor.getName() + " is already locked.");
                else doorGame.open(hoverDoor);
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

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // small marks on the devices and outlets: yellow sparks = still plugged in, green = unplugged
        if (currentTask >= 3) drawDeviceMarks();
        if (currentTask >= 4) drawDoorMarks();

        // edge shadows (and faint light) on the sides that lead somewhere
        if (!drawerOpen && !windowOpen && !leakOpen && !unplugOpen && !doorOpen) {
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

        // the fewer chances you have, the redder and darker the screen edges get
        drawVignette();

        // lightning flash over the room
        lightning.draw(shapes, FLASH_BRIGHTNESS);

        // the open drawer and the minigames
        drawers.render(game.batch, game.font);
        minigame.render(game.batch, game.font, windowFrames, mouse.x, mouse.y);
        leakGame.render(game.batch, game.font);
        unplugGame.render(game.batch, game.font);
        doorGame.render(game.batch, game.font);

        // the surge meter (always on top, even inside a minigame)
        drawSurgeMeter();

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
        else if (currentTask == 3) info = "Devices unplugged: " + (devices.size - devicesLeft()) + " of " + devices.size;
        else if (currentTask == 4) info = "Doors locked: " + (doors.size - doorsLeft()) + " of " + doors.size;
        else info = OBJECTIVE_INFO[currentTask];
        game.font.getData().setScale(1.2f);
        game.font.setColor(0.85f, 0.85f, 0.85f, 1f);
        game.font.draw(game.batch, info, 30 + indent, 658);

        game.font.getData().setScale(1f);
        game.font.setColor(0.7f, 0.7f, 0.7f, 1f);
        game.font.draw(game.batch, "Task " + (currentTask + 1) + " of 8   |   "
            + current.getDisplayName() + "   |   ESC = menu", 30, 630);

        // labels of the surge meter
        if (currentTask == 3) {
            float meterCenter = 1210f + 17f;
            game.font.getData().setScale(1.3f);
            game.font.setColor(surge > 0.75f ? Color.RED : Color.WHITE);
            layout.setText(game.font, "SURGE");
            game.font.draw(game.batch, "SURGE", meterCenter - layout.width / 2f, 600f);
            String pct = (int) (surge * 100f) + "%";
            layout.setText(game.font, pct);
            game.font.draw(game.batch, pct, meterCenter - layout.width / 2f, 150f);
        }

        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);
        dialogue.render(game.batch, game.font);
        game.font.setColor(Color.WHITE);
        game.batch.end();
    }

    /**
     * The storm sound follows the story: rain and wind get loud while the windows are blown open,
     * the horror drone grows as the chances run out, and a heartbeat starts when you are nearly caught.
     */
    private void updateAudioMood() {
        boolean windowsOpen = windowsActive && closedCount() < windows.size;
        float rain = windowsOpen ? 0.95f : 0.55f;
        float wind = windowsOpen ? 0.85f : 0.3f;
        float danger = 1f - (float) chances / MAX_CHANCES;   // 0 = safe, 1 = about to be caught
        float drone = 0.3f + 0.5f * danger + (currentTask >= 3 ? 0.1f : 0f);
        game.audio.setMood(rain, wind, drone);
        game.audio.setHeartbeat(chances <= LOW_CHANCES ? 1f - (float) chances / (LOW_CHANCES + 1) : 0f);
    }

    /** The lost-game screen. The jumpscare plays first if you ran out of chances. */
    private void renderGameOver(float delta) {
        viewport.apply();
        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);

        // the jumpscare
        if (jumpscare.isActive()) {
            jumpscare.update(delta);
            ScreenUtils.clear(0, 0, 0, 1);
            game.batch.setProjectionMatrix(viewport.getCamera().combined);
            jumpscare.render(game.batch);
            return;
        }

        gameOverTimer += delta;

        ScreenUtils.clear(0, 0, 0, 1);
        game.batch.setProjectionMatrix(viewport.getCamera().combined);
        game.batch.begin();
        game.batch.setColor(0.12f, 0.12f, 0.12f, 1f);
        game.batch.draw(current.getTexture(), 0, 0, Main.WORLD_WIDTH, Main.WORLD_HEIGHT);
        game.batch.setColor(Color.WHITE);

        game.font.getData().setScale(4f);
        game.font.setColor(0.85f, 0.05f, 0.05f, 1f);
        layout.setText(game.font, gameOverTitle);
        game.font.draw(game.batch, gameOverTitle, (Main.WORLD_WIDTH - layout.width) / 2f, 430f);

        game.font.getData().setScale(1.8f);
        game.font.setColor(Color.WHITE);
        layout.setText(game.font, gameOverText);
        game.font.draw(game.batch, gameOverText, (Main.WORLD_WIDTH - layout.width) / 2f, 350f);

        if (gameOverTimer > 1f) {
            game.font.getData().setScale(1.5f);
            game.font.setColor(0.7f, 0.7f, 0.7f, 1f);
            layout.setText(game.font, "Click to try again   |   ESC = menu");
            game.font.draw(game.batch, "Click to try again   |   ESC = menu",
                (Main.WORLD_WIDTH - layout.width) / 2f, 290f);
        }

        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);
        game.batch.end();

        if (gameOverTimer > 1f && Gdx.input.justTouched()) {
            game.setScreen(new OpeningScene(game));
        }
    }

    /** Dark red edges that grow stronger as the chances run out. */
    private void drawVignette() {
        float strength = (1f - (float) chances / MAX_CHANCES) * 0.55f;
        if (chances <= LOW_CHANCES) strength *= 0.8f + 0.2f * MathUtils.sin(time * 6f);
        if (strength < 0.02f) return;

        float w = 230f;
        float W = Main.WORLD_WIDTH, H = Main.WORLD_HEIGHT;
        Color dark = new Color(0.25f, 0f, 0f, strength);
        Color clear = new Color(0.25f, 0f, 0f, 0f);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.rect(0, 0, w, H, dark, clear, clear, dark);          // left
        shapes.rect(W - w, 0, w, H, clear, dark, dark, clear);      // right
        shapes.rect(0, H - w, W, w, clear, clear, dark, dark);      // top
        shapes.rect(0, 0, W, w, dark, dark, clear, clear);          // bottom
        shapes.end();
    }

    /** The surge meter on the right side of the screen. */
    private void drawSurgeMeter() {
        if (currentTask != 3) return;
        float x = 1210f, y = 170f, w = 34f, h = 380f;
        float pulse = surge > 0.75f ? 0.5f + 0.5f * MathUtils.sin(time * 10f) : 0f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.35f + 0.65f * pulse, 0.3f * (1f - pulse), 0.3f * (1f - pulse), 1f);
        shapes.rect(x - 4, y - 4, w + 8, h + 8);
        shapes.setColor(0.05f, 0.05f, 0.07f, 1f);
        shapes.rect(x, y, w, h);
        shapes.setColor(1f, MathUtils.lerp(0.85f, 0.1f, surge), 0.1f, 1f);
        shapes.rect(x, y, w, h * surge);
        shapes.end();
    }

    /** Small marks above the devices (and their outlets) of this room. */
    private void drawDeviceMarks() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Device d : devices) {
            if (!d.getRoomId().equals(current.getId())) continue;

            Rectangle r = d.getRect();
            float cx = r.x + r.width / 2f;
            float cy = r.y + r.height + 14f;
            setMarkColor(d.isDone(), cx);
            shapes.circle(cx, cy, 7f);

            Rectangle o = d.getOutlet();
            if (o != null) {
                float ox = o.x + o.width / 2f;
                setMarkColor(d.isDone(), ox);
                shapes.circle(ox, o.y + o.height + 10f, 5f);
            }
        }
        shapes.end();
    }

    /** Small marks above the main doors of this room: yellow = still unlocked, green = locked. */
    private void drawDoorMarks() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (MainDoor d : doors) {
            if (!d.getRoomId().equals(current.getId())) continue;
            Rectangle r = d.getRect();
            float cx = r.x + r.width / 2f;
            setMarkColor(d.isDone(), cx);
            shapes.circle(cx, r.y + r.height + 14f, 7f);
        }
        shapes.end();
    }

    private void setMarkColor(boolean done, float seed) {
        if (done) {
            shapes.setColor(0.2f, 0.85f, 0.35f, 0.9f);
        } else {
            float a = 0.45f + 0.55f * Math.max(0f, MathUtils.sin(time * 9f + seed));
            shapes.setColor(1f, 0.85f, 0.2f, a);
        }
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
        unplugGame.dispose();
        doorGame.dispose();
        jumpscare.dispose();
        shapes.dispose();
    }
}
