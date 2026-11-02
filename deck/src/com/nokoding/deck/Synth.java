package com.nokoding.deck;

/** Generates the built in UI sounds as tiny 16 bit mono WAV files at runtime. Pure Java. */
final class Synth {
    private Synth() {}

    static final int RATE = 22050;
    private static final int SINE = 0, TRI = 1, SQ = 2;

    static short[] pcm(int ev) {
        float[] b = new float[RATE * 4 / 10];
        int end;
        switch (ev) {
            case SoundMap.MOVE: end = tone(b, 0, 26, 1300, 1500, TRI, 0.40f); break;
            case SoundMap.WHEEL_TICK:
                end = tone(b, 0, 10, 2600, 2300, SINE, 0.50f);
                end = Math.max(end, noise(b, 0, 4, 0.22f));
                break;
            case SoundMap.SELECT:
                tone(b, 0, 45, 880, 880, SINE, 0.45f);
                end = tone(b, 40, 70, 1320, 1320, SINE, 0.45f);
                break;
            case SoundMap.BACK: end = tone(b, 0, 80, 1100, 650, TRI, 0.45f); break;
            case SoundMap.MENU_OPEN:
                tone(b, 0, 120, 420, 1250, SINE, 0.40f);
                end = tone(b, 0, 120, 840, 2500, TRI, 0.10f);
                break;
            case SoundMap.MENU_CLOSE:
                tone(b, 0, 110, 1250, 420, SINE, 0.40f);
                end = tone(b, 0, 110, 2500, 840, TRI, 0.10f);
                break;
            case SoundMap.LOCK_ON:
                tone(b, 0, 16, 2000, 2000, SQ, 0.20f);
                end = tone(b, 36, 22, 2700, 2700, SQ, 0.20f);
                break;
            case SoundMap.LAUNCH:
                tone(b, 0, 220, 360, 1700, SINE, 0.45f);
                end = tone(b, 0, 220, 366, 1730, TRI, 0.14f);
                break;
            case SoundMap.ERROR:
                tone(b, 0, 70, 190, 170, SQ, 0.33f);
                end = tone(b, 95, 90, 160, 140, SQ, 0.33f);
                break;
            case SoundMap.MODE_GAMEPAD:
                tone(b, 0, 45, 660, 660, SINE, 0.40f);
                tone(b, 45, 45, 880, 880, SINE, 0.40f);
                end = tone(b, 90, 80, 1320, 1320, SINE, 0.40f);
                break;
            case SoundMap.MODE_REMOTE:
                tone(b, 0, 45, 1320, 1320, SINE, 0.40f);
                tone(b, 45, 45, 880, 880, SINE, 0.40f);
                end = tone(b, 90, 80, 660, 660, SINE, 0.40f);
                break;
            case SoundMap.TOGGLE: end = tone(b, 0, 22, 1000, 1150, SQ, 0.20f); break;
            case SoundMap.PACK_LOADED:
                tone(b, 0, 220, 660, 660, SINE, 0.25f);
                tone(b, 0, 220, 830, 830, SINE, 0.22f);
                end = tone(b, 30, 200, 990, 990, SINE, 0.22f);
                break;
            case SoundMap.TEST_COMPLETE:
                // gamepad test passed: a quick rising C major arpeggio with a bright top note
                tone(b, 0, 70, 1047, 1047, TRI, 0.34f);
                tone(b, 55, 70, 1319, 1319, TRI, 0.34f);
                tone(b, 110, 70, 1568, 1568, TRI, 0.34f);
                tone(b, 165, 170, 2093, 2093, SINE, 0.40f);
                end = tone(b, 165, 170, 3136, 3136, SINE, 0.10f);
                break;
            case SoundMap.TYPE_TICK:
                // 2.3 typing: a very short high tick, 8 ms around 3.2 kHz. Sounds plays it with a
                // small pitch jitter (TickScheduler.rate), so a burst does not sound machine gunned.
                tone(b, 0, 8, 3300, 3100, SINE, 0.55f);
                end = Math.max(tone(b, 0, 8, 3300, 3100, SQ, 0.08f), noise(b, 0, 2, 0.10f));
                break;
            case SoundMap.FLYOUT_OPEN:
                // the options flyout slides out: a short rising sweep with a thin top
                tone(b, 0, 90, 520, 2600, SINE, 0.36f);
                end = tone(b, 10, 80, 1040, 4200, TRI, 0.07f);
                break;
            case SoundMap.FLYOUT_CLOSE:
                // and back in: falling, a bit shorter
                tone(b, 0, 70, 2400, 600, SINE, 0.34f);
                end = tone(b, 0, 60, 4000, 1200, TRI, 0.06f);
                break;
            default: end = tone(b, 0, 30, 1000, 1000, SINE, 0.3f); break;
        }
        short[] out = new short[Math.max(1, Math.min(end, b.length))];
        for (int i = 0; i < out.length; i++) {
            float v = b[i];
            v = v > 1f ? 1f : (v < -1f ? -1f : v);
            out[i] = (short) Math.round(v * 32000f);
        }
        return out;
    }

    /** Adds a swept tone with a click free envelope. @return end sample index. */
    private static int tone(float[] b, int startMs, int durMs, float f0, float f1, int wave, float amp) {
        int s = startMs * RATE / 1000, n = durMs * RATE / 1000, att = Math.max(1, RATE * 2 / 1000);
        double ph = 0;
        for (int i = 0; i < n && s + i < b.length; i++) {
            float x = i / (float) n;
            double f = f0 + (f1 - f0) * x;
            ph += 2 * Math.PI * f / RATE;
            double sn = Math.sin(ph), w;
            if (wave == TRI) w = 2 / Math.PI * Math.asin(sn);
            else if (wave == SQ) w = (sn >= 0 ? 0.6 : -0.6);
            else w = sn;
            float env = (float) Math.pow(1f - x, 1.6) * Math.min(1f, i / (float) att);
            b[s + i] += (float) (w * env * amp);
        }
        return Math.min(b.length, s + n);
    }

    private static int noise(float[] b, int startMs, int durMs, float amp) {
        int s = startMs * RATE / 1000, n = durMs * RATE / 1000, seed = 12345;
        for (int i = 0; i < n && s + i < b.length; i++) {
            seed = seed * 1103515245 + 12345;
            float r = ((seed >>> 8) & 0xFFFF) / 32767.5f - 1f;
            b[s + i] += r * amp * (1f - i / (float) n);
        }
        return Math.min(b.length, s + n);
    }

    static byte[] wav(short[] pcm) {
        int data = pcm.length * 2;
        byte[] o = new byte[44 + data];
        put4(o, 0, 0x46464952); // RIFF
        le32(o, 4, 36 + data);
        put4(o, 8, 0x45564157); // WAVE
        put4(o, 12, 0x20746d66); // fmt
        le32(o, 16, 16);
        le16(o, 20, 1);
        le16(o, 22, 1);
        le32(o, 24, RATE);
        le32(o, 28, RATE * 2);
        le16(o, 32, 2);
        le16(o, 34, 16);
        put4(o, 36, 0x61746164); // data
        le32(o, 40, data);
        for (int i = 0; i < pcm.length; i++) le16(o, 44 + i * 2, pcm[i]);
        return o;
    }

    private static void put4(byte[] o, int at, int ascii) { le32(o, at, ascii); }

    private static void le32(byte[] o, int at, int v) {
        o[at] = (byte) v; o[at + 1] = (byte) (v >> 8); o[at + 2] = (byte) (v >> 16); o[at + 3] = (byte) (v >> 24);
    }

    private static void le16(byte[] o, int at, int v) {
        o[at] = (byte) v; o[at + 1] = (byte) (v >> 8);
    }
}
