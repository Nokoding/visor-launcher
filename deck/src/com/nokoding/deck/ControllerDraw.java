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
        if (depth.length < nf) { depth = new float[nf]; facing = new float[nf]; order0 = new int[nf]; order1 = new int[nf]; }
        Mat3D.euler(yaw, pitch, roll, rot, t1, t2);
        Mat3D.mul(view, rot, mvp);
        float[] v = m.verts;
        for (int i = 0; i < nv; i++) {
            Mat3D.apply(mvp, v[i * 3] * S, v[i * 3 + 1] * S, v[i * 3 + 2] * S, tv, i * 3);
            Mat3D.project(tv[i * 3], tv[i * 3 + 1], tv[i * 3 + 2], DIST, FOCAL, cx, cy, sv, i * 2);
        }
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
