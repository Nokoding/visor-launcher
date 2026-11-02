package com.nokoding.deck;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * The low poly controller of the gyro test (moved out of GyroTestView in 2.3 so the gyro
 * calibration screen draws the same model). Precomputed vertices, rotation matrix,
 * perspective, back face culling, painter's sort, neon outline. Scratch arrays are made once,
 * nothing is allocated per frame. Coordinates are design px (the canvas is scaled).
 */
final class ControllerDraw {
    static final float S = 34f, DIST = 1500f, FOCAL = 1500f, VIEW_TILT = 0.95f;

    private ControllerModel model = new ControllerModel(PadTest.GENERIC);
    private final float[] rot = new float[9], view = new float[9], mvp = new float[9], t1 = new float[9], t2 = new float[9];
    private float[] tv = new float[600], sv = new float[400], depth = new float[100], facing = new float[100];
    private int[] order0 = new int[100], order1 = new int[100];
    private final float[] nrm = new float[3];

    ControllerDraw() { Mat3D.rotX(VIEW_TILT, view); }

    /** 2.17: the folder with the user's models (dualsense.obj, xbox-one.obj), set by Deck at start. */
    static volatile java.io.File modelsDir;

    void setType(int type) { model = ModelObj.forType(modelsDir, type); }

    /** Angles in radians: yaw (turn left +), pitch (far edge up +), roll (right side up +). */
    void draw(Canvas c, Ui.Theme t, float yaw, float pitch, float roll, float cx, float cy, Paint fill, Paint line, Path path) {
        ControllerModel m = model;
        int nv = m.vertexCount, nf = m.faces.length;
        if (tv.length < nv * 3) { tv = new float[nv * 3]; sv = new float[nv * 2]; }
        if (!m.fromFile && depth.length < nf) { depth = new float[nf]; facing = new float[nf]; order0 = new int[nf]; order1 = new int[nf]; }
        Mat3D.euler(yaw, pitch, roll, rot, t1, t2);
        Mat3D.mul(view, rot, mvp);
        float[] v = m.verts;
        for (int i = 0; i < nv; i++) {
            Mat3D.apply(mvp, v[i * 3] * S, v[i * 3 + 1] * S, v[i * 3 + 2] * S, tv, i * 3);
            Mat3D.project(tv[i * 3], tv[i * 3 + 1], tv[i * 3 + 2], DIST, FOCAL, cx, cy, sv, i * 2);
        }
        if (m.fromFile) { drawMesh(c, t, m, fill); return; }
        int n0 = 0, n1 = 0;
        for (int f = 0; f < nf; f++) {
            int[] fc = m.faces[f];
            float zx = 0, zy = 0, zz = 0;
            for (int k : fc) { zx += tv[k * 3]; zy += tv[k * 3 + 1]; zz += tv[k * 3 + 2]; }
            float inv = 1f / fc.length;
            zx *= inv; zy *= inv; zz *= inv;
            ControllerModel.normal(tv, fc, nrm);
            float vx = -zx, vy = -zy, vz = DIST - zz;
            float nl = (float) Math.sqrt(nrm[0] * nrm[0] + nrm[1] * nrm[1] + nrm[2] * nrm[2]), vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            facing[f] = nl > 0 && vl > 0 ? (nrm[0] * vx + nrm[1] * vy + nrm[2] * vz) / (nl * vl) : 0f;
            depth[f] = vl;
            if (facing[f] <= 0.001f) continue;
            if (m.group[f] != 1) order0[n0++] = f; else order1[n1++] = f;   // group 2 (grips, bumpers, triggers) is drawn with the body
        }
        Mat3D.sortFarFirst(depth, order0, n0);
        Mat3D.sortFarFirst(depth, order1, n1);
        boolean topVisible = facing[m.topFace] > 0.001f;
        if (topVisible) { faces(c, t, order0, n0, fill, line, path); faces(c, t, order1, n1, fill, line, path); }
        else { faces(c, t, order1, n1, fill, line, path); faces(c, t, order0, n0, fill, line, path); }
    }

    // ---- 2.18: real models (up to 100,000 triangles). The triangle normals and centres are worked out once when the
    // model loads, so a frame costs a few multiplications per triangle: no square roots, no allocation. The visible
    // triangles are sorted far to near with a counting sort and drawn in a few drawVertices calls.
    private float[] vpos = new float[0];
    private int[] vcol = new int[0];
    private int[] bucketCount = new int[SORT_BUCKETS + 1], sortTmp = new int[0], sortIdx = new int[0];
    private static final int SORT_BUCKETS = 4096, SHADES = 64, BATCH_TRIS = 10000;
    private final int[] shadeLut = new int[SHADES];

    private void drawMesh(Canvas c, Ui.Theme t, ControllerModel m, Paint fill) {
        final int nf = m.faces.length;
        final float[] fn = m.faceN, fc = m.faceC;
        // the camera and a light, in model space (the matrix is a rotation, so its transpose is its inverse)
        final float k = DIST / S, invK = S / DIST;
        final float cx = mvp[6] * k, cy = mvp[7] * k, cz = mvp[8] * k;
        // light from the upper left front, fixed on the screen
        final float lx = mvp[0] * -0.35f + mvp[3] * 0.55f + mvp[6] * 0.75f;
        final float ly = mvp[1] * -0.35f + mvp[4] * 0.55f + mvp[7] * 0.75f;
        final float lz = mvp[2] * -0.35f + mvp[5] * 0.55f + mvp[8] * 0.75f;
        final float zx = mvp[6] * S, zy = mvp[7] * S, zz = mvp[8] * S;   // view space depth of a model point
        for (int i = 0; i < SHADES; i++) {
            float f = i / (float) (SHADES - 1);
            shadeLut[i] = Ui.mix(Ui.mix(t.panel, t.b, 0.10f + 0.66f * f), t.a, (1f - f) * 0.12f);
        }
        if (sortIdx.length < nf) { sortIdx = new int[nf]; sortTmp = new int[nf]; depth = new float[nf]; facing = new float[nf]; }
        int n = 0;
        float dmin = Float.MAX_VALUE, dmax = -Float.MAX_VALUE;
        for (int f = 0; f < nf; f++) {
            int o = f * 3;
            float nx = fn[o], ny = fn[o + 1], nz = fn[o + 2];
            float vx = cx - fc[o], vy = cy - fc[o + 1], vz = cz - fc[o + 2];
            float dot = nx * vx + ny * vy + nz * vz;
            if (dot <= 0f) continue;
            float lit = nx * lx + ny * ly + nz * lz;
            float shade = 0.55f * Math.min(1f, dot * invK) + 0.45f * (lit > 0f ? lit : 0f);
            facing[f] = shade;
            float d = -(fc[o] * zx + fc[o + 1] * zy + fc[o + 2] * zz);   // larger = farther
            depth[f] = d;
            sortIdx[n++] = f;
            if (d < dmin) dmin = d;
            if (d > dmax) dmax = d;
        }
        if (n == 0) return;
        java.util.Arrays.fill(bucketCount, 0);
        final float span = Math.max(1e-3f, dmax - dmin), kk = (SORT_BUCKETS - 1) / span;
        for (int i = 0; i < n; i++) bucketCount[SORT_BUCKETS - 1 - (int) ((depth[sortIdx[i]] - dmin) * kk)]++;
        int run = 0;
        for (int i = 0; i < SORT_BUCKETS; i++) { int cnt = bucketCount[i]; bucketCount[i] = run; run += cnt; }
        for (int i = 0; i < n; i++) { int f = sortIdx[i]; sortTmp[bucketCount[SORT_BUCKETS - 1 - (int) ((depth[f] - dmin) * kk)]++] = f; }
        if (vpos.length < BATCH_TRIS * 6) { vpos = new float[BATCH_TRIS * 6]; vcol = new int[BATCH_TRIS * 3]; }
        fill.setStyle(Paint.Style.FILL);
        int inBatch = 0;
        for (int i = 0; i < n; i++) {
            int f = sortTmp[i];
            int[] fv = m.faces[f];
            int o = inBatch * 6;
            int a = fv[0] * 2, b2 = fv[1] * 2, d2 = fv[2] * 2;
            vpos[o] = sv[a]; vpos[o + 1] = sv[a + 1];
            vpos[o + 2] = sv[b2]; vpos[o + 3] = sv[b2 + 1];
            vpos[o + 4] = sv[d2]; vpos[o + 5] = sv[d2 + 1];
            float sh = facing[f];
            int col = shadeLut[sh <= 0f ? 0 : (sh >= 1f ? SHADES - 1 : (int) (sh * (SHADES - 1)))];
            int q = inBatch * 3;
            vcol[q] = col; vcol[q + 1] = col; vcol[q + 2] = col;
            if (++inBatch == BATCH_TRIS) {
                c.drawVertices(Canvas.VertexMode.TRIANGLES, inBatch * 6, vpos, 0, null, 0, vcol, 0, null, 0, 0, fill);
                inBatch = 0;
            }
        }
        if (inBatch > 0) c.drawVertices(Canvas.VertexMode.TRIANGLES, inBatch * 6, vpos, 0, null, 0, vcol, 0, null, 0, 0, fill);
    }

    private void faces(Canvas c, Ui.Theme t, int[] order, int n, Paint fill, Paint line, Path path) {
        ControllerModel m = model;
        for (int i = 0; i < n; i++) {
            int f = order[i];
            int[] fc = m.faces[f];
            path.reset();
            for (int k = 0; k < fc.length; k++) {
                float x = sv[fc[k] * 2], y = sv[fc[k] * 2 + 1];
                if (k == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            float shade = 0.12f + 0.62f * facing[f];
            boolean deco = m.group[f] == 1;
            fill.setColor(Ui.mix(t.panel, deco ? t.a : t.b, deco ? shade * 0.8f : shade * 0.55f));
            c.drawPath(path, fill);
            line.setColor(Ui.alpha(deco ? t.a : t.b, 0.55f + 0.45f * facing[f]));
            line.setStrokeWidth(deco ? 2 : 1.6f);
            c.drawPath(path, line);
        }
        line.setStrokeWidth(2);
    }
}
