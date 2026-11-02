package com.nokoding.deck;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

/**
 * 2.17 user 3D models for the gyro test: a small subset of Wavefront OBJ, one file per controller in the models
 * folder (dualsense.obj, xbox-one.obj). Pure Java and java.io.
 *
 * Read: lines "v x y z" and "f a b c ..." (a face of 3 to 8 corners, indices counting from 1, "a/b/c" takes the
 * first number). Everything else (comments, vt, vn, g, o, s, usemtl, mtllib) is ignored. Units are centimetres,
 * Y up out of the face, the player is toward +Z. If the biggest size is over 40 the file is taken to be in
 * millimetres and scaled by 0.1. Caps: 1.5 MB, 24000 vertices, 24000 triangles (a face of n corners counts n - 2).
 * A line "g bN" (N a PadTest button number) tags the faces after it with that button; any other group name means no button.
 * Faces are cut into triangles when the file is read.
 * Anything wrong gives null and the built-in model is used: a bad number, a missing or negative or too big index,
 * a face with fewer than 3 or more than 8 corners, no faces, a model over 400 cm.
 */
final class ModelObj {
    private ModelObj() {}

    static final int MAX_BYTES = 1536 * 1024, MAX_VERTS = 24000, MAX_TRIS = 24000, MAX_CORNERS = 8;
    static final String[] FILES = {"dualsense.obj", "xbox-one.obj"};

    /** The file name for a layout (PadTest.PLAYSTATION or PadTest.XBOX), or null for other layouts. */
    static String fileFor(int layout) {
        if (layout == PadTest.PLAYSTATION) return FILES[0];
        if (layout == PadTest.XBOX) return FILES[1];
        return null;
    }

    /** Reads OBJ text. Never throws. @return the model, or null when the text is not usable. */
    static ControllerModel parse(int padType, String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_BYTES) return null;
        try {
            float[] v = new float[300];
            int nv = 0;
            ArrayList<int[]> faces = new ArrayList<>();
            ArrayList<Integer> btns = new ArrayList<>();
            int curBtn = -1;
            int tris = 0;
            int pos = 0, len = text.length();
            while (pos < len) {
                int end = text.indexOf('\n', pos);
                if (end < 0) end = len;
                String line = text.substring(pos, end).trim();
                pos = end + 1;
                if (line.length() < 3) continue;
                if (line.startsWith("v ")) {
                    String[] p = line.split("\\s+");
                    if (p.length < 4) return null;
                    if (nv >= MAX_VERTS) return null;
                    if (nv * 3 + 3 > v.length) v = java.util.Arrays.copyOf(v, v.length * 2);
                    for (int i = 0; i < 3; i++) {
                        float f = Float.parseFloat(p[i + 1]);
                        if (Float.isNaN(f) || Float.isInfinite(f) || Math.abs(f) > 4000f) return null;
                        v[nv * 3 + i] = f;
                    }
                    nv++;
                } else if (line.startsWith("g ") || line.equals("g")) {
                    String name = line.length() > 2 ? line.substring(2).trim() : "";
                    curBtn = -1;
                    if (name.length() >= 2 && name.length() <= 4 && name.charAt(0) == 'b') {
                        try { int k = Integer.parseInt(name.substring(1)); if (k >= 0 && k < PadTest.COUNT) curBtn = k; } catch (NumberFormatException ignored) {}
                    }
                } else if (line.startsWith("f ")) {
                    String[] p = line.split("\\s+");
                    int corners = p.length - 1;
                    if (corners < 3 || corners > MAX_CORNERS) return null;
                    int[] idx = new int[corners];
                    for (int i = 0; i < corners; i++) {
                        String t = p[i + 1];
                        int slash = t.indexOf('/');
                        if (slash >= 0) t = t.substring(0, slash);
                        int k = Integer.parseInt(t);
                        if (k < 1 || k > nv) return null;   // only positive indices, only vertices already read
                        idx[i] = k - 1;
                    }
                    tris += corners - 2;
                    if (tris > MAX_TRIS) return null;
                    for (int k = 1; k + 1 < corners; k++) { faces.add(new int[]{idx[0], idx[k], idx[k + 1]}); btns.add(curBtn); }
                }
            }
            if (nv < 4 || faces.isEmpty()) return null;
            float[] out = java.util.Arrays.copyOf(v, nv * 3);
            float max = 0;
            for (float f : out) max = Math.max(max, Math.abs(f));
            if (max > 400f) return null;
            if (max > 40f) for (int i = 0; i < out.length; i++) out[i] *= 0.1f;   // millimetres
            int[] bt = new int[btns.size()];
            for (int i = 0; i < bt.length; i++) bt[i] = btns.get(i);
            return new ControllerModel(padType, out, faces.toArray(new int[0][]), bt);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Opens a file of the app's own models folder (the APK assets), set by Deck at start. */
    interface Opener { java.io.InputStream open(String name) throws IOException; }

    static volatile Opener bundled;

    /** The model that ships inside the app for this pad (assets/models), or null. */
    static ControllerModel loadBundled(int padType) {
        Opener o = bundled;
        String name = fileFor(PadTest.layout(padType));
        if (o == null || name == null) return null;
        try (java.io.InputStream in = o.open(name)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int r, total = 0;
            while ((r = in.read(buf)) > 0) { total += r; if (total > MAX_BYTES) return null; bo.write(buf, 0, r); }
            return parse(padType, new String(bo.toByteArray(), "UTF-8"));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The user's model for this pad in the folder, or null (no folder, no file, a bad file). */
    static ControllerModel load(File dir, int padType) {
        try {
            String name = fileFor(PadTest.layout(padType));
            if (dir == null || name == null) return null;
            File f = new File(dir, name);
            if (!f.isFile() || f.length() > MAX_BYTES) return null;
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int n = 0, r;
                while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
                return parse(padType, new String(b, 0, n, "UTF-8"));
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The model the gyro test draws: the user's file if there is a good one, then the one inside the app, then the hand made one. */
    static ControllerModel forType(File dir, int padType) {
        ControllerModel m = load(dir, padType);
        if (m == null) m = loadBundled(padType);
        return m != null ? m : new ControllerModel(padType);
    }

    static String describe(File dir, int padType) {
        ControllerModel m = load(dir, padType);
        String n = fileFor(PadTest.layout(padType));
        if (n == null) return "BUILT-IN MODEL";
        if (m != null) return "YOUR MODEL " + n.toUpperCase(Locale.ROOT) + " (" + m.faces.length + " TRIANGLES)";
        ControllerModel b = loadBundled(padType);
        return b != null ? "BUNDLED MODEL (" + b.faces.length + " TRIANGLES)" : "BUILT-IN MODEL";
    }
}
