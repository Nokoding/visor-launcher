package com.nokoding.deck;

/**
 * 2.12: flat outline drawings of the controllers for the 2D gamepad test, in design pixels around the middle of the
 * drawing (x right, y down), the same space the test puts its buttons in. They are drawn here as curves, original
 * shapes of a DualSense and of an Xbox One pad seen from above, not copied from anyone's artwork, so there is no
 * image file in the app and nothing to license. The 3D controller in the gyro test is separate (ControllerModel).
 *
 * A body is the right half as cubic Bezier segments, from the top centre clockwise to the bottom centre: a start
 * point (x, y), then per segment c1x, c1y, c2x, c2y, x, y. The left half is the mirror. The extras are the rings
 * (stick wells, button groups, the logo), rounded rectangles (touchpad, shoulders, view and menu) and light bar lines
 * that sit inside or beside the body. Pure Java, so the JVM tests can check the numbers.
 */
final class PadOutline {
    private PadOutline() {}

    // Hand-written curves. They only approximate the general shape and proportions of a DualSense and an Xbox One pad
    // seen from above, as drawn by the project. No drawing, tracing data or image of anyone else is in the app.
    static final float[] PS_START = {0, -250};
    static final float[] PS_SEGS = {
            60, -253, 130, -254, 188, -252,
            250, -250, 292, -236, 314, -213,
            336, -190, 352, -130, 364, -73,
            378, -20, 390, 30, 392, 83,
            394, 120, 392, 150, 388, 178,
            384, 215, 365, 245, 330, 255,
            315, 265, 292, 268, 276, 258,
            266, 246, 266, 232, 262, 219,
            255, 190, 240, 150, 222, 124,
            210, 100, 195, 90, 181, 83,
            150, 78, 60, 80, 0, 81};
    static final float[] XB_START = {0, -233};
    static final float[] XB_SEGS = {
            60, -234, 120, -244, 153, -250,
            190, -252, 240, -225, 272, -192,
            295, -168, 315, -140, 333, -118,
            352, -60, 380, 60, 384, 154,
            388, 200, 360, 250, 322, 255,
            300, 262, 280, 260, 265, 255,
            240, 230, 205, 205, 184, 182,
            160, 160, 130, 135, 102, 120,
            70, 112, 30, 114, 0, 117};

    /** x, y, radius. */
    private static final float[] PS_RINGS = {-125, 1, 56, 125, 1, 56, -125, 1, 82, 125, 1, 82};
    private static final float[] XB_RINGS = {-197, -90, 52, 99, 27, 52, -197, -90, 62, 99, 27, 62, -99, 37, 62, 0, -175, 34};
    /** x, y, width, height, corner radius. Shoulders first (L2, R2, L1, R1), then the layout's own. */
    private static final float[] SHOULDERS = {-300, -342, 150, 56, 16, 300, -342, 150, 56, 16, -300, -282, 170, 40, 14, 300, -282, 170, 40, 14};
    private static final float[] PS_RECTS = {0, -155, 313, 170, 22, -181, -190, 22, 36, 10, 181, -190, 22, 36, 10};
    private static final float[] XB_RECTS = {};
    /** x1, y1, x2, y2: the DualSense's light bar runs along the touchpad's sides. */
    private static final float[] PS_LINES = {-166, -236, -158, -76, 166, -236, 158, -76};

    /**
     * Where every button of the flat gamepad test sits on the drawing, in design pixels around the middle: x, y per
     * PadTest button number. Plain numbers chosen for the project's own curves.
     */
    static float[] positions(int layout) {
        float[] p = new float[PadTest.COUNT * 2];
        boolean xb = xbox(layout);
        float dpx = xb ? -99 : -243, dpy = xb ? 37 : -106, dpo = xb ? 46 : 48;
        float fpx = xb ? 209 : 244, fpy = xb ? -92 : -106, fpo = xb ? 55 : 57;
        set(p, PadTest.UP, dpx, dpy - dpo); set(p, PadTest.DOWN, dpx, dpy + dpo);
        set(p, PadTest.LEFT, dpx - dpo, dpy); set(p, PadTest.RIGHT, dpx + dpo, dpy);
        set(p, PadTest.NORTH, fpx, fpy - fpo); set(p, PadTest.SOUTH, fpx, fpy + fpo);
        set(p, PadTest.WEST, fpx - fpo, fpy); set(p, PadTest.EAST, fpx + fpo, fpy);
        set(p, PadTest.L1, -300, -282); set(p, PadTest.R1, 300, -282);
        set(p, PadTest.L2, -300, -342); set(p, PadTest.R2, 300, -342);
        set(p, PadTest.L3, xb ? -197 : -125, xb ? -90 : 1); set(p, PadTest.R3, xb ? 99 : 125, xb ? 27 : 1);
        if (xb) {
            set(p, PadTest.SELECT, -57, -92); set(p, PadTest.START, 55, -92);
            set(p, PadTest.HOME, 0, -175); set(p, PadTest.TOUCHPAD, 0, 0);
        } else {
            set(p, PadTest.SELECT, -181, -190); set(p, PadTest.START, 181, -190);
            set(p, PadTest.HOME, 0, -12); set(p, PadTest.TOUCHPAD, 0, -155);
        }
        return p;
    }

    private static void set(float[] p, int button, float x, float y) { p[button * 2] = x; p[button * 2 + 1] = y; }

    static boolean xbox(int layout) { return layout == PadTest.XBOX; }

    static float[] start(int layout) { return xbox(layout) ? XB_START : PS_START; }

    static float[] segments(int layout) { return xbox(layout) ? XB_SEGS : PS_SEGS; }

    static float[] rings(int layout) { return xbox(layout) ? XB_RINGS : PS_RINGS; }

    static float[] shoulders() { return SHOULDERS; }

    static float[] rects(int layout) { return xbox(layout) ? XB_RECTS : PS_RECTS; }

    static float[] lines(int layout) { return xbox(layout) ? new float[0] : PS_LINES; }

    /** The whole body as one list of points sampled along the curves, for tests and hit checks. Right half then left half. */
    static float[] sample(int layout, int perSegment) {
        float[] s = start(layout), g = segments(layout);
        int n = g.length / 6, k = Math.max(2, perSegment);
        float[] right = new float[(n * k + 1) * 2];
        int o = 0;
        right[o++] = s[0];
        right[o++] = s[1];
        float px = s[0], py = s[1];
        for (int i = 0; i < n; i++) {
            float c1x = g[i * 6], c1y = g[i * 6 + 1], c2x = g[i * 6 + 2], c2y = g[i * 6 + 3], x = g[i * 6 + 4], y = g[i * 6 + 5];
            for (int j = 1; j <= k; j++) {
                float t = j / (float) k, u = 1 - t;
                right[o++] = u * u * u * px + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * x;
                right[o++] = u * u * u * py + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * y;
            }
            px = x;
            py = y;
        }
        float[] all = new float[right.length * 2];
        System.arraycopy(right, 0, all, 0, right.length);
        for (int i = 0; i < right.length / 2; i++) {
            all[right.length + i * 2] = -right[right.length - 2 - i * 2];
            all[right.length + i * 2 + 1] = right[right.length - 1 - i * 2];
        }
        return all;
    }

    /** True if the point is inside the body outline (even odd rule on the sampled polygon). */
    static boolean inside(int layout, float x, float y) {
        float[] p = sample(layout, 16);
        int n = p.length / 2;
        boolean in = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = p[i * 2], yi = p[i * 2 + 1], xj = p[j * 2], yj = p[j * 2 + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }
}
