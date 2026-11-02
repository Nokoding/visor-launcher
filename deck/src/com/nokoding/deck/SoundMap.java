package com.nokoding.deck;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;

/**
 * Maps UI events to files in a sound pack folder. A file named after the event
 * (move.wav, select.ogg, ...) just works. An optional pack.json can rename files per
 * event and set per event volume. Events the pack lacks fall back to the built in tone.
 * Uses only org.json, so it runs on the JVM tests too.
 */
final class SoundMap {
    static final int MOVE = 0, WHEEL_TICK = 1, SELECT = 2, BACK = 3, MENU_OPEN = 4, MENU_CLOSE = 5,
            LOCK_ON = 6, LAUNCH = 7, ERROR = 8, MODE_GAMEPAD = 9, MODE_REMOTE = 10, TOGGLE = 11, PACK_LOADED = 12,
            TEST_COMPLETE = 13, TYPE_TICK = 14, FLYOUT_OPEN = 15, FLYOUT_CLOSE = 16;
    static final String[] EVENTS = {"move", "wheel_tick", "select", "back", "menu_open", "menu_close",
            "lock_on", "launch", "error", "mode_gamepad", "mode_remote", "toggle", "pack_loaded", "test_complete",
            "type_tick", "flyout_open", "flyout_close"};
    /** Volume of the built in tones. */
    static final float[] BUILTIN_VOL = {0.5f, 0.45f, 0.7f, 0.6f, 0.6f, 0.55f, 0.35f, 0.7f, 0.7f, 0.7f, 0.7f, 0.5f, 0.6f, 0.6f, 0.22f, 0.45f, 0.4f};
    static final String[] EXTS = {".wav", ".ogg", ".mp3"};

    String name;
    final String[] file = new String[EVENTS.length];
    final float[] volume = new float[EVENTS.length];

    static int index(String event) {
        for (int i = 0; i < EVENTS.length; i++) if (EVENTS[i].equals(event)) return i;
        return -1;
    }

    int found() {
        int n = 0;
        for (String f : file) if (f != null) n++;
        return n;
    }

    static boolean plainName(String s) {
        return s != null && !s.isEmpty() && s.indexOf('/') < 0 && s.indexOf('\\') < 0 && !s.contains("..");
    }

    static SoundMap resolve(String packName, String[] files, String json) {
        SoundMap m = new SoundMap();
        m.name = packName;
        for (int i = 0; i < m.volume.length; i++) m.volume[i] = 1f;
        HashMap<String, String> lower = new HashMap<>();
        if (files != null) for (String f : files) if (plainName(f)) lower.put(f.toLowerCase(Locale.ROOT), f);

        JSONObject map = null, vol = null;
        if (json != null && !json.trim().isEmpty()) {
            try {
                JSONObject o = new JSONObject(json);
                String n = o.optString("name", "");
                if (n != null && !n.trim().isEmpty()) m.name = n.trim();
                map = o.optJSONObject("map");
                vol = o.optJSONObject("volume");
            } catch (Exception badJson) {
                map = null;
                vol = null;
            }
        }
        for (int i = 0; i < EVENTS.length; i++) {
            String ev = EVENTS[i], f = null;
            if (map != null) {
                String want = map.optString(ev, "");
                if (plainName(want)) f = lower.get(want.toLowerCase(Locale.ROOT));
            }
            if (f == null) {
                for (String ext : EXTS) {
                    f = lower.get(ev + ext);
                    if (f != null) break;
                }
            }
            m.file[i] = f;
            if (vol != null && vol.has(ev)) {
                double v = vol.optDouble(ev, 1.0);
                if (Double.isNaN(v)) v = 1.0;
                m.volume[i] = (float) Math.max(0.0, Math.min(1.0, v));
            }
        }
        return m;
    }
}
