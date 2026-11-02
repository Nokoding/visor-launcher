package com.nokoding.deck;

import android.os.SystemClock;
import android.view.Choreographer;

import java.util.ArrayList;

/**
 * One Choreographer loop that steps every active spring, then lets the host apply
 * view properties. It only runs while something moves.
 * 2.3: it is also the clock of every text and flyout animation (TextReveal.Clock): inside a
 * frame it returns that frame's time, so everything on screen steps in step.
 */
final class Engine implements Choreographer.FrameCallback, Spring.Sched, TextReveal.Clock {
    interface Host {
        void onFrame(long nowMs, float dt);
        boolean wantsFrames(long nowMs);
        /** @return false to ignore this sample and keep measuring. */
        boolean onSlowFrames();
    }

    /** v2: only trips below about 25 fps for 150 frames, and only lowers detail. */
    final FrameStats stats = new FrameStats(0.040f, 150);
    private final ArrayList<Spring> active = new ArrayList<>();
    private final Host host;
    private boolean posted, running = true;
    private long lastNanos, frameNow;
    private boolean inFrame;

    @Override public long now() { return inFrame ? frameNow : SystemClock.uptimeMillis(); }

    Engine(Host host) { this.host = host; }

    @Override
    public void add(Spring s) {
        if (!s.active) {
            s.active = true;
            active.add(s);
        }
        kick();
    }

    /** 2.5: TextReveal.Clock.wake(). A reveal that starts calls this, so the loop is never asleep under it. */
    @Override public void wake() { kick(); }

    void kick() {
        if (!posted && running) {
            posted = true;
            try {
                Choreographer.getInstance().postFrameCallback(this);
            } catch (Throwable t) {
                posted = false;
            }
        }
    }

    void stop() {
        running = false;
        if (posted) {
            try { Choreographer.getInstance().removeFrameCallback(this); } catch (Throwable ignored) {}
            posted = false;
        }
        lastNanos = 0;
    }

    void start() {
        running = true;
        kick();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        posted = false;
        if (!running) return;
        float dt = 1f / 60f;
        if (lastNanos != 0) {
            float raw = (frameTimeNanos - lastNanos) / 1e9f;
            if (raw > 0f) dt = raw;
            if (stats.sample(raw)) {
                boolean taken = true;
                try { taken = host.onSlowFrames(); } catch (Throwable ignored) {}
                if (!taken) stats.resetSlow();
            }
        }
        lastNanos = frameTimeNanos;
        dt = Ui.clamp(dt, 0.001f, 0.05f);
        for (int i = active.size() - 1; i >= 0; i--) {
            Spring s = active.get(i);
            if (s.step(dt)) {
                s.active = false;
                active.remove(i);
            }
        }
        long now = SystemClock.uptimeMillis();
        frameNow = now;
        inFrame = true;
        try {
            host.onFrame(now, dt);
        } catch (Throwable ignored) {
            // Never let a drawing glitch kill the home screen.
        }
        inFrame = false;
        if (!active.isEmpty() || host.wantsFrames(now)) {
            kick();
        } else {
            lastNanos = 0;
        }
    }
}
