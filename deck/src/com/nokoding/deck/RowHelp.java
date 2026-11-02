package com.nokoding.deck;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 2.5: one short plain sentence for every row in Setup and its sub screens. Panel shows the
 * focused row's sentence in the footer, next to the controller hints.
 *
 * A row is looked up by its id (its label in capitals, or an explicit id such as "anim.speed" for
 * rows built from the animation schema) and by the kind of the panel it sits in. The order:
 * "kind:ID" exactly, then "ID" exactly, then prefix entries ("kind:PREFIX*" and "PREFIX*", for rows
 * whose label ends in a changing part such as "ZIP  name"), then "kind:*", which covers screens
 * whose rows are data (app names, sound events, report lines). Pure Java, no Android types.
 *
 * Write each sentence as: what it does, then what the options mean. Plain words, no jargon.
 * The footer shows it in capitals like the rest of the HUD. A guard test (LogicTests) fails when a
 * row label in the source has no entry, so a new row cannot ship without its sentence.
 */
final class RowHelp {
    private RowHelp() {}

    private static final LinkedHashMap<String, String> MAP = new LinkedHashMap<>();
    private static final ArrayList<String> PREFIXES = new ArrayList<>();

    /** Panel kinds that show help. The hold OK app menus (tile, dock) and the plain input list do not. */
    static final String[] KINDS = {"settings", "anim", "preview", "padtest", "homecmds", "install", "report", "icon",
            "custom", "light", "test", "addapp", "appact", "psapps", "pscmds", "remapkey", "remapact", "remapapp", "remapadv",
            "saver", "savercmds", "wpopts", "psmenu", "profiles", "profile", "combos", "combo", "comboact", "perf", "perfapps", "themeed"};

    private static void h(String id, String text) {
        MAP.put(id, text);
        if (id.endsWith("*")) PREFIXES.add(id);
    }

    static boolean covers(String kind) {
        if (kind == null) return false;
        for (String k : KINDS) if (k.equals(kind)) return true;
        return false;
    }

    /** The sentence for a row, or null when there is none. id is the row's label or explicit id. */
    static String of(String kind, String id) {
        if (id == null) return null;
        String up = id.toUpperCase(Locale.ROOT);
        String k = kind == null ? "" : kind;
        String s = MAP.get(k + ":" + up);
        if (s == null) s = MAP.get(up);
        if (s == null) s = byPrefix(k, up);
        if (s == null) s = byPrefix("", up);
        if (s == null) s = MAP.get(k + ":*");
        return s;
    }

    /** The kind part of a key ("install" in "install:ZIP  *"), or "" for a key that is not tied to a panel kind. */
    private static String kindOf(String key) {
        int c = key.indexOf(':');
        if (c <= 0) return "";
        String k = key.substring(0, c);
        return covers(k) ? k : "";
    }

    private static String byPrefix(String kind, String up) {
        String best = null;
        int bestLen = -1;
        for (String key : PREFIXES) {
            if (!kindOf(key).equals(kind)) continue;
            String pre = kind.isEmpty() ? key.substring(0, key.length() - 1) : key.substring(kind.length() + 1, key.length() - 1);
            if (pre.isEmpty()) continue;   // "kind:*" is the last resort, handled in of()
            if (up.startsWith(pre) && pre.length() > bestLen) { best = key; bestLen = pre.length(); }
        }
        return best == null ? null : MAP.get(best);
    }

    /** True when some entry knows this exact label (in any panel kind) or a prefix rule matches it. */
    static boolean known(String label) {
        if (label == null) return false;
        String up = label.toUpperCase(Locale.ROOT);
        for (Map.Entry<String, String> e : MAP.entrySet()) {
            String key = e.getKey();
            String k = kindOf(key);
            String id = k.isEmpty() ? key : key.substring(k.length() + 1);
            if (id.equals("*") || key.startsWith("tab:")) continue;
            if (id.endsWith("*") ? up.startsWith(id.substring(0, id.length() - 1)) : id.equals(up)) return true;
        }
        return false;
    }

    /** The sentence for a tab on the tab strip (focus on the strip), or null. */
    static String tab(String name) {
        return name == null ? null : MAP.get("tab:" + name.toUpperCase(Locale.ROOT));
    }

    /** Every entry key, for the guard tests. */
    static java.util.Set<String> keys() { return MAP.keySet(); }

    static String raw(String key) { return MAP.get(key); }

    static {
        // ---- tabs
        h("tab:LOOK", "Layout, colors, clock, stats panel, wallpaper and screensaver.");
        h("tab:MOTION", "How the screen moves: effects, the wheel stick, gyro sway and the pointer.");
        h("tab:ANIMATIONS", "How menus and text animate when they open.");
        h("tab:SOUND", "Sound packs, volume and the sound tests.");
        h("tab:PAD", "Rumble, light bar, player lights, controller tests and the PS button menu.");
        h("tab:APPS", "Which apps show up, recents, icons and resetting the layout.");
        h("tab:SYSTEM", "Android shortcuts, the default home setup and version info.");
        h("tab:SOUNDS", "Plays each Visor sound once so you can hear it.");
        h("tab:HAPTICS", "Plays each rumble pattern once on the controller.");
        h("tab:REMAP", "Changes what the remote's spare buttons do, such as channel, TV, input and the account switcher.");

        // ---- Setup, LOOK
        h("LAYOUT", "Chooses how your apps are laid out: WHEEL is an arc you spin, GRID is a plain grid of tiles.");
        h("THEME", "Changes the colors of the whole screen, and the two squares next to the name show the theme's colors.");
        h("THEME EDITOR", "Opens the full theme editor: five colors, a readability check, and theme files you can save and share.");
        h("COLOR", "Picks which of the five theme colors the three sliders below change.");
        h("HEX", "Shows the color you are editing as a hex code such as #FF2E97.");
        h("READABILITY", "Says whether the text, accent and panels have enough contrast, or what is hard to read.");
        h("SAVED THEMES", "Lists the theme files found in the themes folder.");
        h("LOAD THEME", "Loads the theme file picked above into the CUSTOM theme.");
        h("THEME NAME", "The name the theme file is saved under.");
        h("SAVE THEME FILE", "Writes your colors to a theme file in the themes folder, ready to share.");
        h("RELOAD THEMES", "Looks in the themes folder again after you pushed a new file with adb.");
        h("THEMES FOLDER", "Shows the folder on the TV where theme files go.");
        h("RESET THEME EDITOR", "Forgets the editor's colors and goes back to the two color mixer for the CUSTOM theme.");
        h("CUSTOM COLORS", "Opens sliders to mix your own two colors, which then become the CUSTOM theme.");
        h("CLOCK", "Shows the time with AM and PM when set to 12 HOUR, or on a 24 hour clock when set to 24 HOUR.");
        h("STATS PANEL", "ON shows a small readout of network speed, memory, storage and Visor's own frame rate.");

        // ---- Setup, MOTION
        h("REDUCE EFFECTS", "ON turns off the heavy motion for slower boxes, so panels appear at once and text only fades in.");
        h("PAN DIRECTION", "Sets which way the background drifts as you move: FOLLOW goes with you, OPPOSITE goes against you.");
        h("WHEEL STICK", "Picks the thumbstick that spins the wheel, RIGHT or LEFT, and the other stick moves around like the D-pad.");
        h("WHEEL STICK SPEED", "Sets how fast the stick spins the wheel, from 25% (slow) to 500% (very fast), and it never runs away.");
        h("GYRO SWAY", "ON tilts the focused tile as you tilt the controller, if the controller has motion sensors.");
        h("GYRO SENSOR", "Shows whether a motion sensor was found on the controller, and OK checks again.");
        h("GYRO DIRECTION", "Sets which way the tile tilts: FOLLOW tilts with the controller, OPPOSITE tilts against it.");
        h("GYRO STRENGTH", "Sets how far the tile tilts for each turn of the controller, from 25 (slight) to 200 (big).");
        h("POINTER EFFECTS", "ON adds a glow that follows the pointer, and in grid mode it pushes nearby tiles away.");
        h("EFFECT STRENGTH", "Sets how strong the pointer glow and push are, from 0 (none) to 100 (full).");
        h("GLOW SIZE", "Sets how big the pointer glow is, from 50 (small) to 150 (large).");
        h("THEMED CURSOR", "ON swaps the plain arrow for a cursor in the colors of your theme.");
        h("HIDE SYSTEM CURSOR", "ON hides Android's own cursor so only Visor's cursor shows.");
        h("POINTER SEEN", "Shows whether Visor has received any mouse or touchpad movement yet, and OK checks again.");

        // ---- Setup, ANIMATIONS
        h("OPTIONS FLYOUT", "Opens the settings for the menu animation: typing style, speed, glide and more.");
        h("HOW IT WORKS", "Explains the animation settings in short: one set of options drives every menu, and any key finishes an animation.");
        h("REDUCE EFFECTS IS ON", "Tells you that menu animations are cut down because REDUCE EFFECTS is on in MOTION.");

        // ---- Setup, SOUND
        h("SOUND PACK", "Chooses which set of sounds Visor plays: BUILT-IN is made in code, the others come from your files.");
        h("VOLUME", "Sets how loud Visor's own sounds are, from 0 (silent) to 100 (full).");
        h("MUTE", "ON silences all of Visor's sounds without changing the volume.");
        h("INSTALL PACK", "Opens a list of zip files you can unpack into a sound pack, and of packs you can switch to.");
        h("RELOAD PACKS", "Looks for new sound pack folders and zip files, and installs any new zips.");
        h("TEST SOUNDS", "Opens a list where each Visor sound plays once when you press OK.");
        h("PACK STATUS", "Shows how the chosen sound pack loaded and which sounds it replaced.");
        h("ZIP STATUS", "Shows the result of the last sound pack zip that Visor looked at.");
        h("FOLDER", "Shows the folder on the TV where sound pack folders or zip files go.");

        // ---- Setup, PAD
        h("HAPTICS", "ON makes the controller rumble as you move and select, and OFF keeps it still.");
        h("RUMBLE STRENGTH", "Sets how hard the controller rumbles, from OFF through 25%, 50% and 75% up to 100%.");
        h("FEEL", "Picks the rumble style: CRISP is short and sharp, SOFT is longer and gentler, HEAVY is stronger.");
        h("WHEEL HUM", "ON adds a rumble that builds the longer you keep spinning the wheel.");
        h("RUMBLE STATUS", "Shows whether Visor found a controller that can rumble.");
        h("TEST HAPTICS", "Opens a list where each rumble pattern plays once when you press OK.");
        h("LIGHT BAR", "Sets the controller's light bar: OFF, MATCH THEME uses your theme color, CUSTOM uses the color you pick.");
        h("LIGHT COLOR", "Opens sliders to pick the light bar color, and turns the light bar to CUSTOM.");
        h("LIGHT BRIGHTNESS", "Sets how bright the light bar is, from 0 (dark) to 100 (full).");
        h("PULSE ON LOCK-ON", "ON flashes the light bar for a moment each time the lock-on frame settles on a tile.");
        h("LIGHT STATUS", "Shows whether this box lets Visor control the controller's light bar.");
        h("PLAYER LEDS", "Sets what the controller's five small lights show: OFF, WHEEL POSITION, BATTERY LEVEL or a LOCK-ON FLASH.");
        h("PLAYER LEDS STATUS", "Shows whether the controller's player lights can be controlled.");
        h("GAMEPAD TEST", "Opens a full screen check that lights each button as you press it, in a FULL or QUICK mode.");
        h("GYRO TEST", "Opens a 3D controller that turns with your real one, so you can see the motion sensor work.");
        h("GYRO CALIBRATION", "Walks you through three steps that measure the sensor's resting error and which way its axes point.");
        h("CALIBRATION", "Shows the saved gyro calibration for the connected controller.");
        h("RESET CALIBRATION", "Deletes the saved gyro calibration for the connected controller, and asks you to press OK again first.");
        h("CONTROLLER REPORT", "Writes a report of what this box exposes about your controller, which helps when something does not work.");
        h("PS BUTTON SEEN", "Shows whether Visor has ever received the PS button, which tells you if this box passes it on.");
        h("PS OVERLAY: *", "Shows whether the PS menu over other apps is on, and what is still missing.");
        h("PS SEEN BY THE OVERLAY SERVICE", "Shows whether the overlay service itself received the PS button, which is the key test for this box.");
        h("DOES IT WORK ON THIS BOX", "Gives a plain verdict on whether the PS menu over apps can work here, based on what Visor has seen.");
        h("OPENING APPS FROM THE OVERLAY", "Shows whether Android let the overlay open an app: WORKS, BLOCKED BY ANDROID or NOT TRIED YET.");
        h("SHOW ENABLE COMMANDS", "Opens the adb commands that turn the PS menu over apps on or off.");
        h("TEST PS OVERLAY", "Opens the PS menu over whatever is on screen, to check that the overlay window works.");
        h("OVERLAY CAPTURE", "ON lets Visor take the PS button over other apps, and OFF gives the button back to every app.");
        h("GAMES KEEP THE PS BUTTON", "ON leaves the PS button to games, so Visor's menu opens only over other apps.");
        h("PS BUTTON APPS", "Opens a list of every app where you can choose whether Visor or the app gets the PS button.");

        // ---- Setup, APPS
        h("ADD APP", "Lists every app on the box, including ones Android TV hides, so you can add it to the wheel.");
        h("SHOW ALL APPS", "ON also shows apps that Android TV normally leaves off the launcher.");
        h("SHOW HIDDEN APPS", "ON brings back the apps you hid from the wheel, and OFF keeps them out of sight.");
        h("RECENT APPS IN DOCK", "ON puts your recently opened apps in the dock after a divider.");
        h("RECENT COUNT", "Sets how many recent apps the dock shows, from 0 to 8.");
        h("RECENTS SOURCE", "Chooses where recents come from: VISOR counts only apps opened from Visor, SYSTEM USAGE asks Android.");
        h("USAGE ACCESS", "Shows whether Android's usage history is allowed, which only SYSTEM USAGE needs, and OK checks again.");
        h("ICON MODE", "Chooses how icons look: SHARPER uses the biggest art on a dark plate, ORIGINAL shows each app's own icon.");
        h("ICON PACK", "Picks a set of replacement icons from your icons folder, or NONE to use each app's own.");
        h("ICON PACK STATUS", "Shows how the chosen icon pack loaded and how many icons it replaced.");
        h("RELOAD ICONS", "Reads the icons folder again so new pictures and packs show up.");
        h("CUSTOM ICON FOLDER", "Shows the folder on the TV where your own icon pictures or icon pack zips go.");
        h("RESET LAYOUT", "Puts the wheel order back to the start and clears hidden apps, dock pins and apps you added, after you press OK again.");

        // ---- Setup, SYSTEM
        h("OPEN GOOGLE TV HOME", "Opens the stock Google TV home screen for a moment.");
        h("DEFAULT HOME APP", "Opens Android's own page for choosing which app is your home screen.");
        h("ANDROID SETTINGS", "Opens the box's normal Android settings.");
        h("DEFAULT HOME", "Shows whether Visor is the home screen that the Home button opens.");
        h("HOME ADB COMMANDS", "Opens the adb commands that make Visor the default home screen, and how to undo them.");
        h("VERSION", "Shows which version of Visor is installed.");
        h("FONTS", "Shows whether Visor's own fonts loaded or the plain system font is used instead.");
        h("INPUT", "Shows which kind of input Visor is reacting to now: REMOTE, GAMEPAD or POINTER.");

        // ---- Gamepad test chooser
        h("FULL TEST", "Starts the gamepad test where buttons stay lit, and it ends in confetti once all 16 are pressed.");
        h("QUICK TEST", "Starts the gamepad test where a button lights only while you hold it, which is good for finding a flaky one.");
        h("HOW TO EXIT", "Tells you the ways out of the test, since the test takes over all the buttons.");
        h("OPTIONAL BUTTONS", "Explains that the PS button and touchpad click are shown but not counted, since many boxes swallow them.");

        // ---- Options flyout (rows made from the animation schema)
        h("PREVIEW", "Plays the real menu animation on a sample app so you can see your settings at work.");
        h("ANIM.TEXT", "Picks how menu text appears: TYPE writes it letter by letter, FADE fades it in, NONE shows it at once.");
        h("ANIM.DIR", "Sets the direction the text types in: LEFT TO RIGHT or RIGHT TO LEFT, and the rows mirror to match.");
        h("ANIM.SPEED", "Sets how fast TYPE writes letters, from 40 to 200 characters per second.");
        h("ANIM.STAGGER", "Sets the delay between one row starting and the next, from 0 (together) to 200 milliseconds.");
        h("ANIM.CURSOR", "ON shows a block cursor on the line that is being typed.");
        h("ANIM.SOUND", "ON plays the quiet tick while text types and the sounds for opening and closing.");
        h("ANIM.GLIDE", "ON lets the app's icon glide to the side when a menu opens, and OFF makes it jump.");
        h("ANIM.SIDE", "Sets which side of the screen the app menu opens on, LEFT or RIGHT.");
        h("ANIM.SCALE", "Makes every animation faster or slower at once, from half the normal time (quick) to double the time (slow).");
        h("ANIM.STYLE", "Picks the panel look: SOLID is a dark panel, GLASS lets the screen show through.");
        h("ANIM.SCREEN.*", "Turns the animation on or off for this one screen, and REDUCE EFFECTS still wins.");
        h("SCREENS", "Explains the switches below: turn one off to show that screen without any animation.");
        h("RESET TO DEFAULTS", "Puts every animation option back to its starting value, and asks you to press OK again first.");
        h("SAVED AS", "Shows the name these options are saved under, which a future theme file can set too.");
        h("preview:*", "Pretends to be a real menu row so you can see the animation, and OK or BACK returns to the options.");
        h("THIS IS A PREVIEW", "Tells you that nothing on this screen changes your apps.");

        // ---- Home adb commands
        h("homecmds:NOW", "Shows whether Visor is the default home screen right now.");
        h("1. MAKE VISOR THE HOME ROLE", "The first adb command: it gives Visor the home role, which is the main step.");
        h("2. SET THE HOME ACTIVITY", "The second adb command: it also sets Visor's home screen as the preferred one.");
        h("3. ONLY AFTER A DAY WITHOUT TROUBLE AND A REBOOT TEST", "An optional third command that disables Google TV's launcher, so only try it after a day of trouble free use.");
        h("UNDO STEP 3", "The adb command that turns Google TV's launcher back on.");
        h("WHY HOME STILL OPENS GOOGLE TV", "Explains that Google TV's launcher wins the Home button while it is enabled, which is not a Visor bug.");

        // ---- Install pack
        h("STORAGE NOT AVAILABLE", "Tells you the TV's storage could not be read, so try again later.");
        h("install:ZIP  *", "A zip file in the sound packs folder: OK unpacks it into a sound pack and switches to it.");
        h("install:BUILT-IN", "Switches back to the sounds Visor makes itself.");
        h("install:PACK  *", "An installed sound pack: OK switches to it.");
        h("NO ZIPS YET", "Tells you there is no zip file to install, and where to put one.");
        h("FROM A PHONE (TERMUX)", "Shows the one command that sends a sound pack zip from your phone to the TV.");
        h("install:LAST RESULT", "Shows what happened the last time Visor looked for sound pack zips.");

        // ---- Controller report
        h("NEEDS ANDROID 12", "The controller report needs Android 12, and this box is older.");
        h("SAVED TO", "Shows the file where the report was written, so you can copy it off the TV.");
        h("report:LAST TEST", "Shows the result of the last light bar color test.");
        h("TEST RED", "Turns the controller's light bar red, to check that Visor can control it.");
        h("TEST GREEN", "Turns the controller's light bar green, to check that Visor can control it.");
        h("TEST BLUE", "Turns the controller's light bar blue, to check that Visor can control it.");
        h("TEST OFF", "Turns the controller's light bar off, to check that Visor can control it.");
        h("WRITE REPORT AGAIN", "Collects the controller details again and writes a fresh report.");
        h("report:*", "One finding in the controller report, shown in full as a line of text.");

        // ---- Change icon
        h("PACKAGE", "Shows the app's package name, which is also the name to give your own icon file.");
        h("MATCHING FILE", "Shows which picture of yours Visor found for this app, or that none was found.");
        h("HOW TO ADD ONE", "Shows the adb command that puts your own icon picture in the right place.");
        h("USE CUSTOM ICON", "ON shows your own picture for this app, and OFF goes back to the normal icon.");
        h("NOW SHOWING", "Shows where the icon on screen right now came from.");

        // ---- Custom colors and light bar color
        h("PRIMARY HUE", "Slides the main color around the color wheel, from 0 to 350 degrees.");
        h("PRIMARY SATURATION", "Sets how strong the main color is, from 0 (gray) to 100 (full color).");
        h("PRIMARY BRIGHTNESS", "Sets how bright the main color is, from 30 (dark) to 100 (bright).");
        h("SECONDARY HUE", "Slides the second color around the color wheel, from 0 to 350 degrees.");
        h("SECONDARY SATURATION", "Sets how strong the second color is, from 0 (gray) to 100 (full color).");
        h("SECONDARY BRIGHTNESS", "Sets how bright the second color is, from 30 (dark) to 100 (bright).");
        h("custom:PRESET", "Fills the sliders with a ready made pair of colors to start from.");
        h("light:PRESET", "Fills the sliders with a ready made light bar color to start from.");
        h("DONE", "Goes back to Setup with your custom colors in place.");
        h("HUE", "Slides the light bar color around the color wheel, from 0 to 350 degrees.");
        h("SATURATION", "Sets how strong the light bar color is, from 0 (white) to 100 (full color).");
        h("BRIGHTNESS", "Sets how bright the light bar color is, from 0 (dark) to 100 (full).");
        h("USE THEME CUSTOM PRIMARY", "Copies the main color of your custom theme into the light bar color.");

        // ---- Test sounds and haptics
        h("test:*", "Plays this one once when you press OK, even if the sounds or rumble are switched off elsewhere.");
        h("test:STATUS", "Shows whether a controller that can rumble is connected, and what strength is set.");
        h("test:WHEEL_HUM (1.5 S DEMO)", "Plays a short demo of the hum that builds while you spin the wheel.");

        // ---- Add app and the app actions
        h("SCANNING INSTALLED APPS", "Tells you Visor is still reading the list of apps on the box.");
        h("NO LAUNCHABLE APPS FOUND", "Tells you that Visor found no apps it could start.");
        h("addapp:*", "Opens this app's page, where you can add it to the wheel, pin it to the dock or open it.");
        h("ADD TO WHEEL", "Puts this app on the wheel so it shows with your others.");
        h("ON THE WHEEL", "Shows whether this app is on the wheel and visible or hidden.");
        h("UNHIDE", "Brings this app back onto the wheel.");
        h("PIN TO DOCK", "Pins this app to the dock so it is always one step away.");
        h("REMOVE FROM DOCK", "Takes this app off the dock.");
        h("REMOVE FROM WHEEL", "Takes this app off the wheel, which you added by hand.");
        h("OPEN NOW", "Starts this app right away.");

        // ---- PS button apps
        h("SHOW", "Filters the list: ALL apps, only GAMES, or only the apps you CHANGED.");
        h("NOTHING CHANGED YET", "Tells you that no app has its own PS button choice yet.");
        h("NO APP IS MARKED AS A GAME", "Tells you that Android has not marked any installed app as a game.");
        h("psapps:DEFAULT", "Explains who gets the PS button for apps you have not changed.");
        h("psapps:*", "Chooses who gets the PS button in this app: VISOR opens Visor's menu, THE APP keeps the button for itself.");

        // ---- PS overlay adb commands
        h("pscmds:NOW", "Shows whether the PS menu over other apps is on right now.");
        h("OTHER ACCESSIBILITY SERVICES ON", "Shows how many other accessibility services are on, since the commands must keep them.");
        h("EASIEST: FROM TERMUX ON YOUR PHONE", "Shows the one command that turns the PS menu on from your phone, and it keeps other services safe.");
        h("OR 1. ADD VISOR'S SERVICE", "The first adb command by hand: it lists Visor's service as an accessibility service.");
        h("2. TURN ACCESSIBILITY ON", "The second adb command by hand: it switches accessibility on.");
        h("TURN IT OFF", "The command that turns the PS menu back off.");
        h("OR BY HAND (VISOR WAS THE ONLY ONE)", "The adb command that clears the accessibility list, safe only when Visor was the single service.");
        h("BY HAND: OTHER SERVICES ARE ON, KEEP THEM", "The adb command that reads the current accessibility list so you can keep the other services.");
        h("THEN PUT THAT LIST BACK WITH THIS ADDED AT THE END", "The text to add at the end of that list, after a colon.");
        h("AND", "The adb command that switches accessibility on after you set the list.");
        h("TURN IT OFF (KEEPS THE OTHERS)", "The command that turns the PS menu off and leaves your other services alone.");
        h("WHY ADB", "Explains that Android 13 and newer block this setting in the menus, and adb is allowed.");
        h("STUCK IN AN APP", "Tells you how to get out: hold PS for 4 seconds, or set OVERLAY CAPTURE to OFF.");
        h("IF THE PS BUTTON NEVER ARRIVES", "Explains that some boxes eat the PS button before any app, and then the overlay cannot work.");
        h("AFTER A GOOGLE TV UPDATE", "Tells you to run the enable command again if the menu stops opening.");

        // ---- 2.6 button remap: the REMAP tab
        h("LEARN A BUTTON", "Opens a screen where you press any remote button to see its name and codes, and whether Visor and the service can catch it.");
        h("REMAP OUTSIDE VISOR", "Shows whether remaps also work in other apps, which they do only while the overlay service is on.");
        h("REMAP IN GAMES", "ON lets remaps work in games too, and OFF leaves every button alone in the apps marked as games.");
        h("RESET ALL REMAPS", "Removes every button remap and turns the advanced switches off, after you press OK again.");
        h("ADVANCED REMAP", "Opens a warning first, then the system buttons such as the D-pad, OK, Back, media and volume.");
        h("remapkey:SHORT PRESS", "Picks what happens when you tap the button, from a list of actions.");
        h("remapkey:LONG PRESS", "Picks what happens when you hold the button for half a second, from the same list.");
        h("CLEAR THIS BUTTON", "Puts this button back to default, after you press OK again.");
        h("WHERE IT WORKS", "Tells you where this remap can act, since Android may keep some buttons for itself.");
        h("remapapp:*", "Picks this app for LAUNCH APP, and the button opens it from then on.");
        h("IF NAVIGATION BREAKS", "Lists the three ways out if a remap leaves you unable to move around.");
        h("SYSTEM WIDE NAVIGATION REMAP", "ON lets the D-pad, OK and Back run actions in every app, after a last warning, and OFF keeps that to Visor.");
        h("HOME KEY CHECK", "Says whether the overlay service has ever seen the Home button on this box, which decides if Home can be remapped.");
        h("REMAP HOME", "Lets the Home button run an action, behind a last warning, and only if the service has seen Home.");
        h("SWAP OK AND BACK IN VISOR", "Makes OK act as Back and Back act as OK inside Visor only, and other apps keep the normal buttons.");
        h("SWAP LEFT AND RIGHT IN VISOR", "Makes left act as right and right as left inside Visor only, which suits a left handed layout.");
        h("TURN OFF ADVANCED REMAP", "Clears every system button remap and locks advanced remap again, after you press OK again.");
        for (RemapKey.Def d : RemapKey.all()) h("REMAP.KEY." + d.code, d.help);
        h("REMAP.KEY.OTHER", "Sets what this learned button does, on a short press and on a long press.");
        for (int a = 0; a < RemapAction.COUNT; a++) h(RemapAction.helpId(a).toUpperCase(Locale.ROOT), RemapAction.help(a));
        h("REMAP.ACT.SAME", RemapAction.SAME_HELP);

        // ---- 2.8 control center, task manager, game profiles, touchpad volume
        h("TOUCHPAD VOLUME", "OFF, or the controller key to hold while you drag a finger on the touchpad to change the volume.");
        h("TOUCHPAD DIRECTION", "Chooses whether dragging up and down or left and right changes the volume.");
        h("TOUCHPAD SENSITIVITY", "How many volume steps a drag across the whole touchpad makes: fewer is calmer, more is quicker.");
        h("TOUCHPAD INVERT", "ON flips the direction, so the drag that raised the volume lowers it.");
        h("TOUCHPAD SEEN", "Shows what the touchpad last reported when you press OK, and the D-pad with the key held is the fallback if it stays empty.");
        h("PS MENU", "Opens the list of control center sections, where you hide tiles, move them and clear the protect list.");
        h("GAME PROFILES", "Opens the apps that have a display profile, to lower the picture size for a heavy game and put it back after.");
        h("RESTORE DISPLAY", "Puts the display size and density back to the panel's own values and drops any applied game profile.");
        h("psmenu:*", "Shows or hides this tile in the control center, or moves it up or down inside its section.");
        h("PROTECTED APPS", "Shows how many apps are kept out of CLOSE ALL BACKGROUND by you, besides Visor and the app in front.");
        h("CLEAR PROTECT LIST", "Empties the list of apps you protected from CLOSE ALL BACKGROUND.");
        h("RESET PS MENU", "Shows every tile again in the original order.");
        h("profiles:*", "Opens this app's game profile, or starts one for it.");
        h("ADD A PROFILE", "Picks an installed app and gives it a display profile.");
        h("DISPLAY ACCESS", "Says whether Visor may change the display itself, and shows the one adb command that allows it.");
        h("IF THE PICTURE LOOKS WRONG", "Shows the adb line that puts the display back from your phone, even if Visor cannot.");
        h("NO APPS FOUND", "Visor's list of apps is empty, so there is nothing to pick.");
        h("SCALE", "How much of the full picture size the game gets, with the size and density it would use.");
        h("METHOD", "How the profile is applied: game mode, Visor itself, a phone helper, or only showing the commands.");
        h("AUTO RESTORE", "ON puts the display back when the app has been gone for a few seconds, OFF leaves it until you restore it.");
        h("PERFORMANCE MODE FOR THIS GAME", "Makes performance mode always run or never run for this game, or follows the global setting.");
        h("VOLUME FOR THIS GAME", "Sets the music volume when this game comes to the front and puts the old volume back when you leave it.");
        h("LAUNCH WITH PROFILE", "ON applies the profile first when you start the app from Visor, and asks you to keep it the first time.");
        h("WHAT IT DOES", "Explains in plain words what the chosen method does and what it cannot do.");
        h("ADB COMMAND", "Shows the exact commands that turn this profile on and off from adb.");
        h("REMOVE PROFILE", "Deletes this app's profile without changing the display.");

        // ---- 2.12 performance mode
        h("PERFORMANCE MODE", "Opens the settings that close background apps when a game starts, to free memory and CPU for the game.");
        h("MODE", "OFF does nothing, GAMES acts when an app that Android calls a game starts, CHOSEN APPS acts only for the apps you pick.");
        h("CHOSEN APPS", "Opens the list where you turn performance mode on for single apps, for games that Android does not mark as games.");
        h("CLOSE BACKGROUND NOW", "Ends the background processes of your recent apps right away, after you press OK again.");
        h("ABOUT PERFORMANCE MODE", "Explains what performance mode closes, what it leaves alone and that closed apps start again when opened.");
        h("LAST RESULT", "Shows what the last close did, with the free memory before and after.");
        h("EXIT GAME", "Says where the exit game tile is: the control center goes back to Visor and closes the game.");
        h("perfapps:*", "Turns performance mode on or off for this app when MODE is CHOSEN APPS.");

        // ---- 2.10 stick volume
        h("STICK VOLUME", "OFF, or the controller key to hold while you spin the right thumbstick round its rim to change the volume.");
        h("STICK VOLUME SPEED", "How far you spin the stick for one volume step: slow needs a wide spin, faster needs a small one.");
        h("STICK VOLUME INVERT", "ON flips the direction, so a clockwise spin lowers the volume.");

        // ---- 2.9 tutorial and combos
        h("TUTORIAL", "Opens the lessons that teach the controls and the features, starting where you stopped last time.");
        h("COMBOS", "Opens the list of button combos, where two buttons held together run an action.");
        h("NEW COMBO", "Waits for you to hold one button and press another, then asks what the pair should do.");
        h("HOW COMBOS WORK", "Explains that the first button still reaches the app, that only the second is taken, and where combos work.");
        h("COMBOS OUTSIDE VISOR", "Says whether the overlay service is on, which combos need to work over other apps.");
        h("combos:*", "Opens this combo, to change its action or clear it.");
        h("ACTION", "Chooses what this combo does, from the same list as a single button.");
        h("CLEAR THIS COMBO", "Removes this combo, and the two buttons go back to doing only their own jobs, after you press OK again.");
        h("comboact:*", "Makes this the action of the combo.");

        // ---- 2.7 clock, wallpaper and screensaver (LOOK tab and its screens)
        h("CLOCK SECONDS", "ON shows seconds on the clock and ticks once a second, OFF shows hours and minutes and ticks once a minute.");
        h("SECOND CLOCK ZONE", "Adds a small second clock for another city under the main clock and on the screensaver, or OFF for none.");
        h("WALLPAPER", "Chooses the picture behind everything: the built-in skyline, a plain dark background, or a pack from the wallpapers folder.");
        h("WALLPAPER OPTIONS", "Opens the options of the chosen wallpaper pack, such as parallax, dim, tint, zoom and the frame rate cap.");
        h("RELOAD WALLPAPERS", "Unpacks new wallpaper zips from the wallpapers folder and loads the chosen wallpaper again.");
        h("WALLPAPER FOLDER", "Shows where wallpaper packs go on the TV, either as folders or as zip files.");
        h("WALLPAPER STATUS", "Says what is loaded, or why a pack could not be used and Visor fell back to the skyline.");
        h("SCREENSAVER", "Opens the settings of Visor's own screensaver, a clock screen the TV shows when it has been idle.");
        h("wpopts:PACK", "Shows the name, author and type of the loaded pack, and anything Visor fixed in its settings file.");
        h("RESET OPTIONS", "Puts every option of this wallpaper back to its default, after you press OK again.");
        h("WP.PARALLAX", "Sets how far each layer moves with the camera, where 0 keeps the picture still and 200 doubles the pack's own setting.");
        h("WP.PAN", "Sets how fast the picture follows the camera and drifts on the screensaver, where 100 is normal.");
        h("WP.DIM", "Darkens the wallpaper so the menus stay readable, from 0 to 80 percent.");
        h("WP.TINT", "Mixes the theme's accent color into the picture, from 0 to 100 percent.");
        h("WP.ZOOM", "Enlarges the picture, which also gives it more room to move without showing an edge.");
        h("WP.SPEED", "Slows an animated wallpaper by pausing it in short bursts, and it can never play faster than the file.");
        h("WP.FPS", "Caps how many times a second an animated wallpaper redraws, which spares the box's weak graphics chip.");
        h("WP.CUSTOM", "An option that comes from the wallpaper pack itself, and its author decides what it changes.");
        h("SCREENSAVER STYLE", "Chooses what the screensaver shows: CLOCK only, CLOCK AND WALLPAPER, or WALLPAPER ONLY.");
        h("SCREENSAVER DIM", "Sets how dark the screensaver is, from 30 to 95 percent, and a higher number is darker.");
        h("SCREENSAVER MOTION", "ON pans the background slowly and glides the clock to a new place each minute, OFF keeps the picture still and jumps the clock instead.");
        h("SCREENSAVER STATS", "ON adds a small line with the network link and memory use under the clock, OFF leaves it out.");
        h("PREVIEW SCREENSAVER", "Starts the screensaver now inside Visor, and any key, stick push or pointer move brings you back.");
        h("SCREENSAVER STATUS", "Reads Android's settings and says whether the TV will use Visor's screensaver and when it starts.");
        h("OPEN ANDROID SCREENSAVER SETTINGS", "Tries to open the TV's own screensaver page, which some Google TV builds do not have.");
        h("SET AS SYSTEM SCREENSAVER", "Shows the adb commands that make Visor's screensaver the one Android uses.");
        h("savercmds:NOW", "Shows what Android has set for the screensaver right now.");
        h("1. CHOOSE VISOR AS THE SCREENSAVER", "The adb command that names Visor's screensaver as the one to use.");
        h("2. TURN SCREENSAVERS ON", "The adb command that switches screensavers on, which some boxes ship with off.");
        h("3. START IT WHEN THE TV IS IDLE", "The adb command that lets Android start the screensaver when the idle time runs out.");
        h("OPTIONAL: START AFTER 15 MINUTES", "The adb command that sets how long the TV waits before the screensaver starts, here 15 minutes.");
        h("savercmds:FROM A PHONE (TERMUX)", "Shows the one command that does all of this from your phone with the helper script.");
        h("savercmds:UNDO", "The adb command that drops Visor's choice so Android goes back to its own screensaver.");
        h("TRY IT NOW", "The adb command that starts the screensaver preview from your phone without waiting for the idle time.");
    }
}
