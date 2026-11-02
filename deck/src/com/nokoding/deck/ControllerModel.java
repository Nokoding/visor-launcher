package com.nokoding.deck;

import java.util.ArrayList;

/**
 * Low poly controller for the gyro test, built once: a body extruded from a top view outline,
 * two stick cylinders, a D-pad, four face buttons and (PlayStation) a touchpad. Units are
 * centimeters, origin in the middle of the body. X right, Y up out of the face, Z toward the
 * player. Faces are vertex index lists; group 0 is the body, group 1 sits on top of it.
 * The 2D gamepad test reuses the outline. Pure Java.
 */
final class ControllerModel {

    /** Half outlines (right side, x >= 0) from the far edge to the gap between the grips, as x, z pairs. */
    static final float[] PS_HALF = {
            0f, -5.0f, 3.5f, -5.0f, 5.8f, -4.6f, 7.2f, -3.6f, 7.9f, -1.8f, 8.1f, 0.5f, 8.0f, 3.0f,
            7.5f, 5.2f, 6.6f, 6.6f, 5.4f, 7.0f, 4.4f, 6.4f, 3.7f, 4.6f, 3.0f, 3.0f, 1.5f, 2.6f, 0f, 2.6f};
    static final float[] XBOX_HALF = {
            0f, -4.6f, 3.6f, -4.8f, 6.0f, -4.3f, 7.4f, -3.0f, 8.0f, -0.8f, 7.9f, 1.8f, 7.3f, 4.4f,
            6.3f, 6.0f, 5.1f, 6.3f, 4.2f, 5.5f, 3.4f, 3.8f, 2.4f, 3.0f, 0f, 2.9f};

    static final float BODY_TOP = 1.2f, BODY_BOTTOM = -1.2f;

    final int type;
    final float[] verts;   // x, y, z per vertex
    final int[][] faces;
    final int[] group;
    /** Which logical PadTest button a face shows, or -1. */
    final int[] button;
    /** 2.17: the part a face belongs to (0 is the body and its decorations, 1 and up are grips, bumpers and triggers). */
    final int[] part;
    /** 2.17: true when the model was read from a file in the models folder. */
    boolean fromFile;
    /** Index of the body top cap. Group 1 is drawn after the body only while this face looks at the camera. */
    final int topFace;
    final int vertexCount;

    private float[] vb;
    private int vn;
    private int[][] fb;
    private int[] gb, bb, pb;
    private int fn, curPart;

    /** Full closed outline as x, z pairs, counter clockwise seen from above. */
    static float[] outline(int layout) {
        float[] h = layout == PadTest.XBOX ? XBOX_HALF : PS_HALF;
        int n = h.length / 2;            // points on the right half, including both centerline points
        int total = n * 2 - 2;
        float[] o = new float[total * 2];
        int k = 0;
        for (int i = 0; i < n; i++) { o[k++] = h[i * 2]; o[k++] = h[i * 2 + 1]; }
        for (int i = n - 2; i >= 1; i--) { o[k++] = -h[i * 2]; o[k++] = h[i * 2 + 1]; }
        return o;
    }

    ControllerModel(int padType) {
        type = PadTest.layout(padType);
        vb = new float[2400];
        fb = new int[260][];
        gb = new int[260];
        bb = new int[260];
        pb = new int[260];
        float[] o = outline(type);
        int n = o.length / 2;
        int base = vn;
        for (int i = 0; i < n; i++) vert(o[i * 2], BODY_TOP, o[i * 2 + 1]);
        for (int i = 0; i < n; i++) vert(o[i * 2], BODY_BOTTOM, o[i * 2 + 1]);
        // the outline runs clockwise when seen from above (+Y), so reverse it for an outward top normal
        int[] top = new int[n], bottom = new int[n];
        for (int i = 0; i < n; i++) { top[i] = base + n - 1 - i; bottom[i] = base + n + i; }
        int tf = face(top, 0, -1);
        face(bottom, 0, -1);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            face(new int[]{base + i, base + j, base + n + j, base + n + i}, 0, -1);
        }
        topFace = tf;
        boolean xb = type == PadTest.XBOX;
        // sticks
        float lsx = xb ? -4.6f : -2.6f, lsz = xb ? -1.6f : 1.4f, rsx = xb ? 2.4f : 2.6f, rsz = 1.4f;
        cylinder(lsx, lsz, 0.95f, BODY_TOP, BODY_TOP + 1.0f, PadTest.L3);
        cylinder(rsx, rsz, 0.95f, BODY_TOP, BODY_TOP + 1.0f, PadTest.R3);
        // D-pad cross
        float dx = xb ? -2.4f : -5.3f, dz = xb ? 1.4f : -1.6f, a = 0.32f, b = 1.0f, y = BODY_TOP + 0.08f;
        float[] cross = {-a, -b, a, -b, a, -a, b, -a, b, a, a, a, a, b, -a, b, -a, a, -b, a, -b, -a, -a, -a};
        int[] cf = new int[12];
        // clockwise in x, z seen from above means counter clockwise for the +Y normal when reversed
        for (int i = 0; i < 12; i++) cf[11 - i] = vert(dx + cross[i * 2], y, dz + cross[i * 2 + 1]);
        face(cf, 1, -1);
        // face buttons: north, east, south, west diamonds
        float fx = xb ? 4.6f : 5.3f, fz = -1.6f, off = 1.05f, r = 0.42f;
        float[][] pos = {{0, -off}, {off, 0}, {0, off}, {-off, 0}};
        int[] ids = {PadTest.NORTH, PadTest.EAST, PadTest.SOUTH, PadTest.WEST};
        for (int k = 0; k < 4; k++) {
            float cx = fx + pos[k][0], cz = fz + pos[k][1];
            int v0 = vert(cx, y, cz - r), v1 = vert(cx + r, y, cz), v2 = vert(cx, y, cz + r), v3 = vert(cx - r, y, cz);
            face(new int[]{v3, v2, v1, v0}, 1, ids[k]);
        }
        if (type == PadTest.PLAYSTATION || type == PadTest.GENERIC) {
            float ty = BODY_TOP + 0.12f;
            int t0 = vert(-2.7f, ty, -4.7f), t1 = vert(2.7f, ty, -4.7f), t2 = vert(2.7f, ty, -1.5f), t3 = vert(-2.7f, ty, -1.5f);
            face(new int[]{t3, t2, t1, t0}, 1, type == PadTest.PLAYSTATION ? PadTest.TOUCHPAD : -1);
        }
        addHardware(xb);
        verts = new float[vn * 3];
        System.arraycopy(vb, 0, verts, 0, vn * 3);
        vertexCount = vn;
        faces = new int[fn][];
        group = new int[fn];
        button = new int[fn];
        part = new int[fn];
        for (int i = 0; i < fn; i++) { faces[i] = fb[i]; group[i] = gb[i]; button[i] = bb[i]; part[i] = pb[i]; }
        vb = null;
        fb = null;
        gb = null;
        bb = null;
        pb = null;
    }

    /** 2.17: a model read from a file (see ModelObj). Every face is body (group 0), no buttons light up. */
    ControllerModel(int padType, float[] v, int[][] f) { this(padType, v, f, null); }

    ControllerModel(int padType, float[] v, int[][] f, int[] btn) {
        type = PadTest.layout(padType);
        fromFile = true;
        verts = v;
        faces = f;
        vertexCount = v.length / 3;
        group = new int[f.length];
        button = new int[f.length];
        part = new int[f.length];
        java.util.Arrays.fill(button, -1);
        if (btn != null && btn.length == f.length) System.arraycopy(btn, 0, button, 0, btn.length);
        int best = 0;
        float bestY = -1e9f;
        float[] n = new float[3];
        for (int i = 0; i < f.length; i++) {
            normal(v, f[i], n);
            float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            float ny = len > 0 ? n[1] / len : -1f;
            float cy = 0;
            for (int k : f[i]) cy += v[k * 3 + 1];
            cy /= f[i].length;
            float score = ny * 10f + cy * 0.001f;
            if (score > bestY) { bestY = score; best = i; }
        }
        topFace = best;
    }

    /**
     * Grips, bumpers, triggers and the home, start and select marks (2.17). Real sizes: DualSense 160 mm wide, 106 mm
     * front to back, 66 mm thick; Xbox One 152.9 by 101.9 by 61 (dimensions.com). Hardware parts are group 2: drawn with the body.
     */
    private void addHardware(boolean xb) {
        float[] half = xb ? XBOX_HALF : PS_HALF;
        int n = half.length / 2;
        // the grip: the part of the outline behind the sticks, from the widest point to the gap, hanging below the body
        ArrayList<float[]> top = new ArrayList<>();
        for (int i = 0; i < n; i++) if (half[i * 2 + 1] >= 0.4f && half[i * 2] >= 2.9f) top.add(new float[]{half[i * 2], half[i * 2 + 1]});
        for (int side = 1; side >= -1; side -= 2) {
            curPart++;
            float cxz = 0, czz = 0;
            for (float[] q : top) { cxz += q[0]; czz += q[1]; }
            cxz /= top.size(); czz /= top.size();
            int m = top.size();
            int[] a = new int[m], b = new int[m];
            float depthY = xb ? -2.7f : -3.4f;
            for (int i = 0; i < m; i++) a[i] = vert(side * top.get(i)[0], BODY_BOTTOM, top.get(i)[1]);
            for (int i = 0; i < m; i++) b[i] = vert(side * (cxz + (top.get(i)[0] - cxz) * 0.72f), depthY, czz + (top.get(i)[1] - czz) * 0.72f);
            float gcx = side * cxz, gcy = (BODY_BOTTOM + depthY) / 2f, gcz = czz;
            outward(new int[]{a[0], a[1], b[1], b[0]}, 2, -1, gcx, gcy, gcz);
            for (int i = 0; i < m; i++) {
                int j = (i + 1) % m;
                outward(new int[]{a[i], a[j], b[j], b[i]}, 2, -1, gcx, gcy, gcz);
            }
            int[] cap = new int[m];
            for (int i = 0; i < m; i++) cap[i] = b[i];
            outward(cap, 2, -1, gcx, gcy, gcz);
        }
        // bumpers (L1 R1) along the front edge, triggers (L2 R2) in front of them
        box(3.3f, 7.0f, BODY_TOP - 0.7f, BODY_TOP + 0.35f, -5.5f, -4.5f, PadTest.R1);
        box(-7.0f, -3.3f, BODY_TOP - 0.7f, BODY_TOP + 0.35f, -5.5f, -4.5f, PadTest.L1);
        box(3.3f, 6.2f, -0.5f, 0.8f, -6.3f, -5.2f, PadTest.R2);
        box(-6.2f, -3.3f, -0.5f, 0.8f, -6.3f, -5.2f, PadTest.L2);
        // the home mark, and start and select as flat marks on the body (group 1, they light with their button)
        float y = BODY_TOP + 0.1f;
        curPart = 0;
        if (xb) {
            mark(0f, -3.2f, 0.45f, 0.45f, y, PadTest.HOME);
            mark(-1.6f, -2.0f, 0.4f, 0.3f, y, PadTest.SELECT);
            mark(1.6f, -2.0f, 0.4f, 0.3f, y, PadTest.START);
        } else {
            mark(0f, 0.9f, 0.5f, 0.5f, y, PadTest.HOME);
            mark(-3.5f, -3.3f, 0.3f, 0.5f, y, PadTest.SELECT);
            mark(3.5f, -3.3f, 0.3f, 0.5f, y, PadTest.START);
        }
    }

    private void mark(float cx, float cz, float hx, float hz, float y, int btn) {
        int v0 = vert(cx - hx, y, cz - hz), v1 = vert(cx + hx, y, cz - hz), v2 = vert(cx + hx, y, cz + hz), v3 = vert(cx - hx, y, cz + hz);
        face(new int[]{v3, v2, v1, v0}, 1, btn);
    }

    private void box(float x0, float x1, float y0, float y1, float z0, float z1, int btn) {
        curPart++;
        int[] v = new int[8];
        for (int i = 0; i < 8; i++) v[i] = vert((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1);
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        int[][] f = {{v[0], v[1], v[3], v[2]}, {v[4], v[5], v[7], v[6]}, {v[0], v[1], v[5], v[4]}, {v[2], v[3], v[7], v[6]}, {v[0], v[2], v[6], v[4]}, {v[1], v[3], v[7], v[5]}};
        for (int[] q : f) outward(q, 2, btn, cx, cy, cz);
    }

    /** Adds a face, turned round when needed so its normal points away from (cx, cy, cz), the centre of its convex part. */
    private void outward(int[] idx, int g, int btn, float cx, float cy, float cz) {
        float[] n = new float[3];
        normal(vb, idx, n);
        float fx = 0, fy = 0, fz = 0;
        for (int k : idx) { fx += vb[k * 3]; fy += vb[k * 3 + 1]; fz += vb[k * 3 + 2]; }
        fx /= idx.length; fy /= idx.length; fz /= idx.length;
        if (n[0] * (fx - cx) + n[1] * (fy - cy) + n[2] * (fz - cz) < 0) {
            int[] r = new int[idx.length];
            for (int i = 0; i < idx.length; i++) r[i] = idx[idx.length - 1 - i];
            idx = r;
        }
        face(idx, g, btn);
    }

    private int vert(float x, float y, float z) {
        vb[vn * 3] = x;
        vb[vn * 3 + 1] = y;
        vb[vn * 3 + 2] = z;
        return vn++;
    }

    private int face(int[] idx, int g, int btn) {
        fb[fn] = idx;
        gb[fn] = g;
        bb[fn] = btn;
        pb[fn] = curPart;
        return fn++;
    }

    private void cylinder(float cx, float cz, float r, float y0, float y1, int btn) {
        int seg = 8, b0 = vn;
        for (int i = 0; i < seg; i++) {
            double t = Math.PI * 2 * i / seg;
            vert(cx + (float) Math.cos(t) * r, y0, cz + (float) Math.sin(t) * r);
        }
        int t0 = vn;
        for (int i = 0; i < seg; i++) {
            double t = Math.PI * 2 * i / seg;
            vert(cx + (float) Math.cos(t) * r * 0.85f, y1, cz + (float) Math.sin(t) * r * 0.85f);
        }
        int[] cap = new int[seg];
        for (int i = 0; i < seg; i++) cap[i] = t0 + seg - 1 - i;
        face(cap, 1, btn);
        for (int i = 0; i < seg; i++) {
            int j = (i + 1) % seg;
            face(new int[]{b0 + j, b0 + i, t0 + i, t0 + j}, 1, -1);
        }
    }

    /** Face normal (Newell's method) of face f using positions in p (x, y, z per vertex). Not normalized. */
    static void normal(float[] p, int[] f, float[] out) {
        float nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < f.length; i++) {
            int a = f[i] * 3, b = f[(i + 1) % f.length] * 3;
            nx += (p[a + 1] - p[b + 1]) * (p[a + 2] + p[b + 2]);
            ny += (p[a + 2] - p[b + 2]) * (p[a] + p[b]);
            nz += (p[a] - p[b]) * (p[a + 1] + p[b + 1]);
        }
        out[0] = nx;
        out[1] = ny;
        out[2] = nz;
    }
}
