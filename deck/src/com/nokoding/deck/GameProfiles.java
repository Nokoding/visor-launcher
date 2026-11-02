package com.nokoding.deck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 2.8 game profiles: the per app profiles and how they are saved. Pure Java (org.json only).
 *
 * Saved as one compact JSON line in prefs:
 *   {"v":1,"profiles":[{"pkg":"com.x.y","scale":75,"method":0,"auto":true,"launch":false}]}
 * Reading is defensive: a bad version, wrong types, nonsense or hostile text never throws and never
 * produces a profile that could build a bad command. Bad data gives an empty set, a bad entry is
 * dropped. See DisplayRules for what the methods can and cannot do on Android 14.
 *
 * The list holds copies: change a profile by getting it, editing the copy and calling put.
 */
final class GameProfiles {
    static final int VERSION = 1, MAX = 40;
    /** Longest saved text that is read at all. */
    static final int MAX_JSON = 64 * 1024;

    /** Lower the resolution of the game itself (shell command cmd game set). Never touches the display. */
    static final int M_GAME_MODE = 0;
    /** Deck changes the whole display (needs WRITE_SECURE_SETTINGS; experimental). */
    static final int M_DISPLAY_DECK = 1;
    /** A Termux helper script on the user's phone changes the whole display. */
    static final int M_DISPLAY_HELPER = 2;
    /** Deck only shows the commands to copy. */
    static final int M_COPY = 3;

    static String methodName(int m) {
        switch (m) {
            case M_GAME_MODE: return "GAME MODE";
            case M_DISPLAY_DECK: return "VISOR DISPLAY";
            case M_DISPLAY_HELPER: return "PHONE HELPER";
            case M_COPY: return "COPY COMMANDS";
            default: return "UNKNOWN";
        }
    }

    static boolean validMethod(int m) { return m >= M_GAME_MODE && m <= M_COPY; }

    /** One app's profile. */
    static final class Profile {
        String pkg;
        int scalePct = 75;
        int method = M_GAME_MODE;
        /** Put the display back some seconds after the app was left. */
        boolean autoRestore = true;
        /** Apply when Deck launches the app (not only when it comes to the front). */
        boolean launchWith = false;
        /** 2.17 game bundle: performance mode for this game (GameBundle.PERF_*) and a volume percent (GameBundle.VOL_NONE for none). */
        int perf = GameBundle.PERF_FOLLOW;
        int volume = GameBundle.VOL_NONE;

        Profile() {}

        Profile(String pkg) { this.pkg = pkg; }

        Profile copy() {
            Profile p = new Profile(pkg);
            p.scalePct = scalePct; p.method = method; p.autoRestore = autoRestore; p.launchWith = launchWith;
            p.perf = perf; p.volume = volume;
            return p;
        }

        boolean valid() {
            return ProfileCmd.validPkg(pkg) && DisplayRules.validScale(scalePct) && validMethod(method)
                    && GameBundle.validPerf(perf) && GameBundle.validVolume(volume);
        }
    }

    private final LinkedHashMap<String, Profile> map = new LinkedHashMap<>();

    GameProfiles() {}

    // ---------------------------------------------------------------- access

    int count() { return map.size(); }

    boolean has(String pkg) { return pkg != null && map.containsKey(pkg); }

    /** A copy of the profile for a package, or null. */
    Profile get(String pkg) {
        Profile p = pkg == null ? null : map.get(pkg);
        return p == null ? null : p.copy();
    }

    /** Copies of all profiles, in the order they were added. */
    List<Profile> list() {
        ArrayList<Profile> out = new ArrayList<>(map.size());
        for (Profile p : map.values()) out.add(p.copy());
        return Collections.unmodifiableList(out);
    }

    /**
     * Adds a profile, or replaces the one with the same package (keeping its place in the list).
     * @return false (nothing changed) if it is not valid or the set is full with a new package.
     */
    boolean put(Profile p) {
        if (p == null || !p.valid()) return false;
        if (!map.containsKey(p.pkg) && map.size() >= MAX) return false;
        map.put(p.pkg, p.copy());
        return true;
    }

    boolean remove(String pkg) { return pkg != null && map.remove(pkg) != null; }

    // ---------------------------------------------------------------- text form

    /** Compact JSON: {"v":1,"profiles":[{...}]}. */
    String save() {
        StringBuilder b = new StringBuilder(64 + map.size() * 90);
        b.append("{\"v\":").append(VERSION).append(",\"profiles\":[");
        boolean first = true;
        for (Profile p : map.values()) {
            if (!first) b.append(',');
            first = false;
            b.append("{\"pkg\":").append(JSONObject.quote(p.pkg))
                    .append(",\"scale\":").append(p.scalePct)
                    .append(",\"method\":").append(p.method)
                    .append(",\"auto\":").append(p.autoRestore)
                    .append(",\"launch\":").append(p.launchWith);
            if (p.perf != GameBundle.PERF_FOLLOW) b.append(",\"perf\":").append(p.perf);
            if (p.volume != GameBundle.VOL_NONE) b.append(",\"vol\":").append(p.volume);
            b.append('}');
        }
        return b.append("]}").toString();
    }

    /**
     * Reads saved text. Never throws. Null, empty, not JSON, a wrong version, wrong types or more
     * than 64 KB give an empty set. Per entry: a bad package, a present scale that is not in
     * DisplayRules.SCALES, an unknown method or a value of the wrong type drops the entry; a missing
     * scale reads as 75, a missing method as GAME MODE, missing flags as their defaults; unknown keys
     * are ignored; a repeated package keeps the first; only the first MAX good entries are kept.
     */
    static GameProfiles parse(String json) {
        GameProfiles out = new GameProfiles();
        if (json == null || json.isEmpty() || json.length() > MAX_JSON) return out;
        try {
            JSONObject root = new JSONObject(json);
            if (!isInt(root.opt("v"), VERSION)) return out;
            Object arr = root.opt("profiles");
            if (!(arr instanceof JSONArray)) return out;
            JSONArray a = (JSONArray) arr;
            // look at no more entries than could ever matter, so a giant array stays cheap
            int n = Math.min(a.length(), MAX * 20);
            for (int i = 0; i < n && out.map.size() < MAX; i++) {
                Object o = a.opt(i);
                if (!(o instanceof JSONObject)) continue;
                Profile p = entry((JSONObject) o);
                if (p != null && !out.map.containsKey(p.pkg)) out.map.put(p.pkg, p);
            }
        } catch (org.json.JSONException e) {
            // the Android org.json throws this checked; the JVM test jar throws it unchecked
            out.map.clear();
        } catch (RuntimeException | StackOverflowError e) {
            // bad data gives whatever was read cleanly before, but a half read file is not trusted
            out.map.clear();
        }
        return out;
    }

    private static Profile entry(JSONObject o) {
        Object pk = o.opt("pkg");
        if (!(pk instanceof String) || !ProfileCmd.validPkg((String) pk)) return null;
        Profile p = new Profile((String) pk);
        if (o.has("scale")) {
            int s = asInt(o.opt("scale"));
            if (!DisplayRules.validScale(s)) return null;
            p.scalePct = s;
        }
        if (o.has("method")) {
            int m = asInt(o.opt("method"));
            if (!validMethod(m)) return null;
            p.method = m;
        }
        if (o.has("auto")) {
            Object v = o.opt("auto");
            if (!(v instanceof Boolean)) return null;
            p.autoRestore = (Boolean) v;
        }
        if (o.has("launch")) {
            Object v = o.opt("launch");
            if (!(v instanceof Boolean)) return null;
            p.launchWith = (Boolean) v;
        }
        if (o.has("perf")) {
            int v = asInt(o.opt("perf"));
            if (!GameBundle.validPerf(v)) return null;
            p.perf = v;
        }
        if (o.has("vol")) {
            int v = asInt(o.opt("vol"));
            if (v < 0 || v > 100) return null;
            p.volume = v;
        }
        return p;
    }

    /** A whole number value, or -1 for anything else (strings, fractions, null, huge). */
    private static int asInt(Object v) {
        if (!(v instanceof Number)) return -1;
        double d = ((Number) v).doubleValue();
        if (d < 0 || d > 1000 || d != Math.floor(d)) return -1;
        return (int) d;
    }

    private static boolean isInt(Object v, int want) { return asInt(v) == want; }

    /** Package names of all profiles as a set (handy for marking apps in a list). */
    Set<String> packages() { return new HashSet<>(map.keySet()); }
}
