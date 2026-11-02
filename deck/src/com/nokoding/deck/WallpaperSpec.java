package com.nokoding.deck;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 2.7: what a wallpaper pack's wallpaper.json says, read and checked. Pure Java (org.json only), so
 * the JVM tests can feed it good, bad and hostile files.
 *
 * type: "image" (one file), "layers" (a list of layers with parallax, scale and opacity) or
 * "animated" (a GIF or animated WebP with a frame rate cap and a speed). Unknown keys are ignored.
 * A bad number falls back to its default. A pack that cannot work at all (no type, no file, not
 * JSON) gets an error text, and Deck shows it in the status line and uses the built-in skyline.
 *
 * "options" is a list of per wallpaper options in the 2.3 AnimOption style (key, label, type, min,
 * max, step, default, choices, unit). Each is turned into an AnimSpec schema line and checked by
 * AnimSpec, so the same rules apply. Layer numbers may be a value or "@key" to follow an option,
 * a layer can follow a COLOR option for its tint, and "when": "key=VALUE" shows a layer only when a
 * CHOICE or BOOL option has that value. Deck adds its own common options (see WallpaperOptions).
 */
final class WallpaperSpec {
    static final int IMAGE = 0, LAYERS = 1, ANIMATED = 2, VIDEO = 3;
    static final String[] TYPE_NAMES = {"image", "layers", "animated", "video"};
    static final int FORMAT = 1;
    static final int MAX_LAYERS = 8, MAX_OPTIONS = 12, MAX_JSON = 64 * 1024;
    static final double PARALLAX_MAX = 2.0, SCALE_MAX = 3.0;
    static final int FPS_MIN = 5, FPS_MAX = 30, FPS_DEFAULT = 20;
    static final double SPEED_MIN = 0.25, SPEED_MAX = 1.0;
    /** Option keys Deck adds itself. A pack cannot reuse them. */
    static final String[] RESERVED = {"parallax", "pan", "dim", "tint", "zoom", "speed", "fps"};

    /** A number that is either fixed or follows an option ("@key"). */
    static final class Num {
        final double value;
        final String ref;
        Num(double value, String ref) { this.value = value; this.ref = ref; }
    }

    static final class Layer {
        String file = "";
        Num parallax = new Num(0.5, null), scale = new Num(1.0, null), opacity = new Num(1.0, null);
        /** A fixed tint color (ARGB) or an option key holding a color, and how much of it to mix in. */
        int tintColor;
        String tintRef;
        double tintMix = 0.5;
        /** "key=VALUE" or null. */
        String whenKey, whenValue;
    }

    String name = "", author = "", version = "";
    int type = IMAGE;
    String file = "";
    final ArrayList<Layer> layers = new ArrayList<>();
    int fps = FPS_DEFAULT;
    double speed = 1.0;
    /** The pack's own options. Deck's common ones are added by WallpaperOptions. */
    AnimSpec options;
    /** Non-null when the pack cannot be used. Plain capitals, shown in the status line. */
    String error;
    /** Things that were fixed or skipped, for the status line. */
    final ArrayList<String> notes = new ArrayList<>();

    static WallpaperSpec bad(String why) {
        WallpaperSpec s = new WallpaperSpec();
        s.error = why;
        return s;
    }

    /** Every image file the pack's spec uses, in draw order. */
    ArrayList<String> files() {
        ArrayList<String> out = new ArrayList<>();
        if (type == LAYERS) for (Layer l : layers) out.add(l.file);
        else if (!file.isEmpty()) out.add(file);
        return out;
    }

    /**
     * Reads the text of a wallpaper.json. files = the names (any case) of the image files that are
     * really in the pack folder. fallbackName is the folder name, used when "name" is missing or bad.
     */
    static WallpaperSpec parse(String text, Set<String> files, String fallbackName) {
        if (text == null) return bad("NO wallpaper.json");
        if (text.length() > MAX_JSON) return bad("wallpaper.json IS OVER 64 KB");
        JSONObject root;
        try {
            Object o = new org.json.JSONTokener(text).nextValue();
            if (!(o instanceof JSONObject)) return bad("wallpaper.json MUST BE AN OBJECT");
            root = (JSONObject) o;
        } catch (StackOverflowError deep) {
            return bad("wallpaper.json IS NESTED TOO DEEP");
        } catch (Exception e) {
            return bad("wallpaper.json IS NOT VALID JSON");
        }
        try {
            return read(root, lower(files), fallbackName);
        } catch (StackOverflowError deep) {
            return bad("wallpaper.json IS NESTED TOO DEEP");
        } catch (RuntimeException e) {
            return bad("wallpaper.json COULD NOT BE READ");
        }
    }

    private static Set<String> lower(Set<String> in) {
        HashSet<String> out = new HashSet<>();
        if (in != null) for (String s : in) if (s != null) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    /** A pack with no wallpaper.json: one image file works as a plain image, one GIF as an animation. */
    static WallpaperSpec fallback(Set<String> files, String folderName) {
        String only = null;
        int n = 0;
        if (files != null) for (String f : files) {
            if (f != null && PackZip.isMedia(f, PackZip.WALLPAPERS) && !f.toLowerCase(Locale.ROOT).equals("wallpaper.json")) { only = f; n++; }
        }
        if (n == 0) return bad("NO IMAGE FILES AND NO wallpaper.json");
        if (n > 1) return bad("NO wallpaper.json, AND MORE THAN ONE IMAGE");
        WallpaperSpec s = new WallpaperSpec();
        s.name = cleanText(folderName, 40);
        s.file = only;
        s.type = only.toLowerCase(Locale.ROOT).endsWith(".gif") ? ANIMATED : (only.toLowerCase(Locale.ROOT).endsWith(".mp4") ? VIDEO : IMAGE);
        s.options = new AnimSpec("wp", s.name);
        s.notes.add("NO wallpaper.json, USING THE ONE IMAGE");
        return s;
    }

    private static WallpaperSpec read(JSONObject o, Set<String> files, String fallbackName) {
        WallpaperSpec s = new WallpaperSpec();
        Object fmt = o.opt("format");
        if (fmt instanceof Number && ((Number) fmt).doubleValue() > FORMAT) return bad("NEEDS A NEWER VISOR (FORMAT " + ((Number) fmt).intValue() + ")");
        s.name = cleanText(str(o, "name"), 40);
        if (s.name.isEmpty()) s.name = cleanText(fallbackName, 40);
        s.author = cleanText(str(o, "author"), 40);
        Object v = o.opt("version");
        s.version = v instanceof Number || v instanceof String ? cleanText(String.valueOf(v), 16) : "";

        String t = str(o, "type").toLowerCase(Locale.ROOT).trim();
        int ty = -1;
        for (int i = 0; i < TYPE_NAMES.length; i++) if (TYPE_NAMES[i].equals(t)) ty = i;
        if (ty < 0) return bad("TYPE MUST BE image, layers, animated OR video");
        s.type = ty;

        s.options = new AnimSpec("wp", s.name);
        readOptions(o.opt("options"), s);

        if (ty == LAYERS) {
            Object la = o.opt("layers");
            if (!(la instanceof JSONArray)) return bad("layers MUST BE A LIST");
            JSONArray arr = (JSONArray) la;
            int seen = 0;
            for (int i = 0; i < arr.length(); i++) {
                Object e = arr.opt(i);
                if (!(e instanceof JSONObject)) { s.notes.add("LAYER " + (i + 1) + " IS NOT AN OBJECT"); continue; }
                if (++seen > MAX_LAYERS) { s.notes.add("ONLY THE FIRST " + MAX_LAYERS + " LAYERS ARE USED"); break; }
                Layer l = readLayer((JSONObject) e, files, s, i + 1);
                if (l != null) s.layers.add(l);
            }
            if (s.layers.isEmpty()) return bad(arr.length() == 0 ? "layers IS EMPTY" : "NO LAYER HAS A FILE THAT IS IN THE PACK");
        } else {
            String f = str(o, "file").trim();
            String why = fileProblem(f, files);
            if (why != null) return bad(why);
            s.file = f;
            boolean mp4 = f.toLowerCase(Locale.ROOT).endsWith(".mp4");
            if (ty == VIDEO && !mp4) return bad("A video wallpaper needs an .mp4 file");
            if (ty != VIDEO && mp4) return bad(".mp4 NEEDS \"type\": \"video\"");
            if (ty == ANIMATED) {
                Object fp = o.opt("fps");
                if (fp instanceof Number) {
                    double d = ((Number) fp).doubleValue();
                    if (Double.isNaN(d) || d < FPS_MIN || d > FPS_MAX) s.notes.add("fps NOT " + FPS_MIN + " TO " + FPS_MAX + ", USING A CLAMPED VALUE");
                    s.fps = Double.isNaN(d) ? FPS_DEFAULT : (int) Math.max(FPS_MIN, Math.min(FPS_MAX, Math.round(d)));
                } else if (fp != null && fp != JSONObject.NULL) s.notes.add("fps IS NOT A NUMBER");
                Object sp = o.opt("speed");
                if (sp instanceof Number) {
                    double d = ((Number) sp).doubleValue();
                    if (Double.isNaN(d) || d < SPEED_MIN || d > SPEED_MAX) s.notes.add("speed CLAMPED TO " + SPEED_MIN + " TO " + SPEED_MAX + " (ANDROID CANNOT PLAY FASTER)");
                    s.speed = Double.isNaN(d) ? 1.0 : Math.max(SPEED_MIN, Math.min(SPEED_MAX, d));
                } else if (sp != null && sp != JSONObject.NULL) s.notes.add("speed IS NOT A NUMBER");
            }
        }
        return s;
    }

    private static Layer readLayer(JSONObject o, Set<String> files, WallpaperSpec s, int n) {
        String f = str(o, "file").trim();
        String why = fileProblem(f, files);
        if (why != null) { s.notes.add("LAYER " + n + ": " + why); return null; }
        Layer l = new Layer();
        l.file = f;
        l.parallax = num(o.opt("parallax"), 0.5, 0.0, PARALLAX_MAX, s);
        l.scale = num(o.opt("scale"), 1.0, 1.0, SCALE_MAX, s);
        l.opacity = num(o.opt("opacity"), 1.0, 0.0, 1.0, s);
        Object tint = o.opt("tint");
        if (tint instanceof String) {
            String ts = ((String) tint).trim();
            if (ts.startsWith("@") && optionIsColor(s, ts.substring(1))) l.tintRef = ts.substring(1);
            else if (ts.startsWith("#")) {
                AnimOption c = new AnimOption("c", "C", AnimOption.COLOR, 0, 0, 1, null, "", 0);
                if (c.parse(ts)) l.tintColor = c.color();
            }
        }
        Object mix = o.opt("tintMix");
        if (mix instanceof Number && !Double.isNaN(((Number) mix).doubleValue())) l.tintMix = Math.max(0, Math.min(1, ((Number) mix).doubleValue()));
        Object when = o.opt("when");
        if (when instanceof String) {
            String w = ((String) when).trim();
            int eq = w.indexOf('=');
            if (eq > 0 && eq < w.length() - 1) {
                String k = w.substring(0, eq).trim(), val = w.substring(eq + 1).trim();
                AnimOption opt = s.options.get(k);
                if (opt != null && (opt.type == AnimOption.CHOICE || opt.type == AnimOption.BOOL)) { l.whenKey = k; l.whenValue = val; }
                else s.notes.add("LAYER " + n + ": when NEEDS A CHOICE OR BOOL OPTION");
            }
        }
        return l;
    }

    private static boolean optionIsColor(WallpaperSpec s, String key) {
        AnimOption o = s.options.get(key);
        return o != null && o.type == AnimOption.COLOR;
    }

    /** A fixed number in range, or "@key" when that option exists and is a number or a switch. */
    private static Num num(Object v, double def, double lo, double hi, WallpaperSpec s) {
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return new Num(def, null);
            return new Num(Math.max(lo, Math.min(hi, d)), null);
        }
        if (v instanceof String) {
            String t = ((String) v).trim();
            if (t.startsWith("@")) {
                AnimOption o = s.options.get(t.substring(1));
                if (o != null && o.type != AnimOption.COLOR && o.type != AnimOption.CHOICE) return new Num(def, t.substring(1));
            }
        }
        return new Num(def, null);
    }

    /** The value of a Num now, given the option values. Always inside lo..hi. */
    static double value(Num n, AnimSpec opts, double lo, double hi) {
        double v = n.value;
        if (n.ref != null && opts != null) {
            AnimOption o = opts.get(n.ref);
            if (o != null) v = o.value;
        }
        if (Double.isNaN(v)) v = lo;
        return Math.max(lo, Math.min(hi, v));
    }

    /** True when a layer shows now. A layer with no "when" always shows. */
    static boolean layerShows(Layer l, AnimSpec opts) {
        if (l.whenKey == null) return true;
        AnimOption o = opts == null ? null : opts.get(l.whenKey);
        if (o == null) return true;
        String want = l.whenValue.toUpperCase(Locale.ROOT).replace('_', ' ');
        if (o.type == AnimOption.BOOL) {
            boolean on = want.equals("ON") || want.equals("TRUE") || want.equals("YES") || want.equals("1");
            boolean off = want.equals("OFF") || want.equals("FALSE") || want.equals("NO") || want.equals("0");
            return on ? o.on() : (off ? !o.on() : true);
        }
        return o.choice().toUpperCase(Locale.ROOT).replace('_', ' ').equals(want);
    }

    /** The tint color of a layer now (ARGB), or 0 for none. */
    static int layerTint(Layer l, AnimSpec opts) {
        if (l.tintRef != null && opts != null) {
            AnimOption o = opts.get(l.tintRef);
            if (o != null && o.type == AnimOption.COLOR) return o.color();
        }
        return l.tintColor;
    }

    // ------------------------------------------------------------------ options

    private static void readOptions(Object oo, WallpaperSpec s) {
        if (!(oo instanceof JSONArray)) return;
        JSONArray arr = (JSONArray) oo;
        for (int i = 0; i < arr.length(); i++) {
            Object e = arr.opt(i);
            if (s.options.options.size() >= MAX_OPTIONS) { s.notes.add("ONLY THE FIRST " + MAX_OPTIONS + " OPTIONS ARE USED"); break; }
            if (!(e instanceof JSONObject)) { s.notes.add("OPTION " + (i + 1) + " IS NOT AN OBJECT"); continue; }
            String line = optionLine((JSONObject) e);
            AnimOption op = line == null ? null : AnimSpec.option(line);
            if (op == null) { s.notes.add("OPTION " + (i + 1) + " SKIPPED"); continue; }
            boolean reserved = false;
            for (String r : RESERVED) if (r.equals(op.key)) reserved = true;
            if (reserved || s.options.get(op.key) != null) { s.notes.add("OPTION " + op.key + " IS TAKEN, SKIPPED"); continue; }
            s.options.options.add(op);
        }
    }

    /** One AnimSpec schema line from an option object, or null if the object cannot make one. */
    static String optionLine(JSONObject o) {
        String key = str(o, "key").trim();
        if (!key.matches("[a-z][a-z0-9_]{0,23}")) return null;
        String label = labelText(str(o, "label"));
        if (label.isEmpty()) label = key.toUpperCase(Locale.ROOT).replace('_', ' ');
        String type = str(o, "type").trim().toUpperCase(Locale.ROOT);
        Object def = o.opt("default");
        switch (type) {
            case "CHOICE": {
                Object ch = o.opt("choices");
                if (!(ch instanceof JSONArray)) return null;
                JSONArray a = (JSONArray) ch;
                StringBuilder b = new StringBuilder();
                ArrayList<String> names = new ArrayList<>();
                for (int i = 0; i < a.length() && names.size() < 12; i++) {
                    Object c = a.opt(i);
                    if (!(c instanceof String)) continue;
                    String t = labelText((String) c);
                    if (t.isEmpty() || names.contains(t)) continue;
                    names.add(t);
                    if (b.length() > 0) b.append(", ");
                    b.append(t);
                }
                if (names.size() < 2) return null;
                String d = def instanceof String ? labelText((String) def) : "";
                if (!names.contains(d)) d = names.get(0);
                return key + " | " + label + " | CHOICE | " + b + " | " + d;
            }
            case "INT":
            case "INT_RANGE":
            case "FLOAT":
            case "FLOAT_RANGE": {
                boolean fl = type.startsWith("FLOAT");
                double min = number(o.opt("min"), 0), max = number(o.opt("max"), fl ? 1 : 100);
                if (!(max >= min) || Math.abs(min) > 1e6 || Math.abs(max) > 1e6) return null;
                double step = number(o.opt("step"), fl ? 0.01 : 1);
                if (!(step > 0) || step > (max - min) && max > min) step = fl ? 0.01 : 1;
                double d = def instanceof Number ? ((Number) def).doubleValue() : min;
                if (Double.isNaN(d) || Double.isInfinite(d)) d = min;
                d = Math.max(min, Math.min(max, d));
                String unit = unitText(str(o, "unit"));
                return key + " | " + label + " | " + (fl ? "FLOAT" : "INT") + " | " + plain(min) + ".." + plain(max) + "/" + plain(step)
                        + (unit.isEmpty() ? "" : " " + unit) + " | " + plain(d);
            }
            case "BOOL": {
                boolean on = def instanceof Boolean ? (Boolean) def : (def instanceof Number && ((Number) def).doubleValue() >= 0.5);
                return key + " | " + label + " | BOOL |  | " + (on ? "on" : "off");
            }
            case "COLOR": {
                String d = def instanceof String ? ((String) def).trim() : "";
                AnimOption c = new AnimOption("c", "C", AnimOption.COLOR, 0, 0, 1, null, "", 0);
                if (!c.parse(d)) d = "#FF2E97";
                return key + " | " + label + " | COLOR |  | " + d;
            }
            default:
                return null;
        }
    }

    private static double number(Object v, double def) {
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (!Double.isNaN(d) && !Double.isInfinite(d)) return d;
        }
        return def;
    }

    private static String plain(double d) {
        String s = String.format(Locale.ROOT, "%.4f", d);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) s = s.substring(0, s.length() - 1);
        return s.equals("-0") ? "0" : s;
    }

    // ------------------------------------------------------------------ text and files

    private static String str(JSONObject o, String k) {
        Object v = o.opt(k);
        return v instanceof String ? (String) v : "";
    }

    /** Letters, digits, space, dot, dash, underscore and percent; capitals; no pipes or newlines; max 24. */
    private static String labelText(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < 24; i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == ' ' || c == '.' || c == '-' || c == '%') b.append(c);
            else if (c == '_') b.append(' ');
        }
        return b.toString().trim().toUpperCase(Locale.ROOT);
    }

    private static String unitText(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < 6; i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '%') b.append(c);
        }
        return b.toString().toUpperCase(Locale.ROOT);
    }

    /** Name text for display: plain characters only, no control characters, trimmed. */
    static String cleanText(String s, int max) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == ' ' || c == '-' || c == '_' || c == '.' || c == '\'';
            b.append(ok ? c : ' ');
        }
        return b.toString().trim().replaceAll(" +", " ");
    }

    /** Null if f names an image the pack has, else the reason. */
    private static String fileProblem(String f, Set<String> files) {
        if (f.isEmpty()) return "file IS MISSING";
        if (f.length() > 100) return "file NAME IS TOO LONG";
        if (f.indexOf('/') >= 0 || f.indexOf('\\') >= 0 || f.indexOf('\0') >= 0 || f.startsWith(".") || f.contains("..")) return "file MUST BE A PLAIN NAME IN THE PACK";
        if (!PackZip.isMedia(f, PackZip.WALLPAPERS) || f.toLowerCase(Locale.ROOT).equals("wallpaper.json")) return "file MUST BE .png .jpg .jpeg .webp .gif OR .mp4";
        if (!files.contains(f.toLowerCase(Locale.ROOT))) return "FILE NOT IN THE PACK: " + f;
        return null;
    }
}
