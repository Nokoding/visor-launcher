package com.nokoding.deck;

/**
 * A pooled confetti burst for the gamepad test. All arrays are made once; a burst only
 * rewrites them, and update() never allocates. Pure Java, drawn by PadTestView.
 */
final class Confetti {
    static final int N = 120;
    static final float GRAVITY = 1500f, DRAG = 0.9f, LIFE = 1.8f;

    final float[] x = new float[N], y = new float[N], vx = new float[N], vy = new float[N];
    final float[] rot = new float[N], spin = new float[N], len = new float[N], age = new float[N];
    final int[] color = new int[N];
    private int seed = 1;
    private int alive;

    private float rnd() {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 8) & 0xFFFF) / 65535f;
    }

    /** Launches every particle from (cx, cy) upward in a wide fan. Units are pixels and seconds. */
    void burst(float cx, float cy, float scale, int colors, int s) {
        seed = s == 0 ? 1 : s;
        int nc = Math.max(1, colors);
        for (int i = 0; i < N; i++) {
            double a = Math.toRadians(-90 + (rnd() - 0.5f) * 150);
            float sp = (650f + rnd() * 900f) * scale;
            x[i] = cx + (rnd() - 0.5f) * 60f * scale;
            y[i] = cy;
            vx[i] = (float) Math.cos(a) * sp;
            vy[i] = (float) Math.sin(a) * sp;
            rot[i] = rnd() * 6.2832f;
            spin[i] = (rnd() - 0.5f) * 18f;
            len[i] = (10f + rnd() * 14f) * scale;
            age[i] = -rnd() * 0.12f;   // a slight stagger
            color[i] = i % nc;
        }
        alive = N;
    }

    void update(float dt) {
        if (alive == 0 || !(dt > 0f)) return;
        if (dt > 0.05f) dt = 0.05f;
        float k = (float) Math.pow(DRAG, dt * 10f);
        int n = 0;
        for (int i = 0; i < N; i++) {
            if (age[i] >= LIFE) continue;
            age[i] += dt;
            if (age[i] < 0f) { n++; continue; }
            vx[i] *= k;
            vy[i] = vy[i] * k + GRAVITY * dt;
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            rot[i] += spin[i] * dt;
            if (age[i] < LIFE) n++;
        }
        alive = n;
    }

    boolean active() { return alive > 0; }

    int alive() { return alive; }

    /** 1 while young, fading to 0 over the last third of the life. */
    float alpha(int i) {
        float a = age[i];
        if (a < 0f || a >= LIFE) return 0f;
        float fade = LIFE * 0.66f;
        return a < fade ? 1f : 1f - (a - fade) / (LIFE - fade);
    }

    void clear() {
        for (int i = 0; i < N; i++) age[i] = LIFE;
        alive = 0;
    }
}
