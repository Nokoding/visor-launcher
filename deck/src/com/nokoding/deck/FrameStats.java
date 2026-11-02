package com.nokoding.deck;

/** Frame time bookkeeping: slow motion detection and a per second FPS window. Pure Java. */
final class FrameStats {
    final float slowDt;
    final int minFrames;
    private float ema, winTime;
    private int measured, winFrames;
    private boolean reported;

    FrameStats(float slowDt, int minFrames) {
        this.slowDt = slowDt;
        this.minFrames = minFrames;
    }

    /** @return true exactly once, when motion has stayed slow for minFrames frames. */
    boolean sample(float dt) {
        if (!(dt > 0f) || dt >= 0.25f) return false;
        winFrames++;
        winTime += dt;
        ema = ema == 0f ? dt : ema * 0.93f + dt * 0.07f;
        measured++;
        if (!reported && measured > minFrames && ema > slowDt) {
            reported = true;
            return true;
        }
        return false;
    }

    void resetSlow() {
        measured = 0;
        ema = 0f;
        reported = false;
    }

    /** Average FPS of the frames drawn since the last call, or -1 when Deck was idle. */
    float takeFps() {
        float f = winFrames >= 5 && winTime > 0f ? winFrames / winTime : -1f;
        winFrames = 0;
        winTime = 0f;
        return f;
    }
}
