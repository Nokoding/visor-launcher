package com.nokoding.deck;

/** Allocation free number formatting into char buffers for the stats panel. Pure Java. */
final class Fmt {
    private Fmt() {}
    private static final char[] UNITS = {'K', 'M', 'G', 'T'};

    static int str(char[] b, int o, String s) {
        int n = Math.min(s.length(), b.length - o);
        for (int i = 0; i < n; i++) b[o + i] = s.charAt(i);
        return o + n;
    }

    static int num(char[] b, int o, long v) {
        if (v < 0) { if (o < b.length) b[o++] = '-'; v = -v; }
        int start = o;
        do {
            if (o >= b.length) break;
            b[o++] = (char) ('0' + (v % 10));
            v /= 10;
        } while (v > 0);
        for (int i = start, j = o - 1; i < j; i++, j--) { char t = b[i]; b[i] = b[j]; b[j] = t; }
        return o;
    }

    /** Byte count as 999B, 9.5K, 12M, 1.4G (base 1024). */
    static int size(char[] b, int o, long v) {
        if (v < 0) v = 0;
        if (v < 1000) { o = num(b, o, v); return o < b.length ? put(b, o, 'B') : o; }
        double d = v;
        int u = -1;
        while (d >= 1000 && u < UNITS.length - 1) { d /= 1024; u++; }
        if (d < 9.95) {
            int t = (int) Math.round(d * 10);
            o = num(b, o, t / 10);
            o = put(b, o, '.');
            o = put(b, o, (char) ('0' + t % 10));
        } else {
            o = num(b, o, Math.round(d));
        }
        return put(b, o, UNITS[u]);
    }

    /** Signed fixed point like +0.42 or -12.5 with 0 to 3 decimals. NaN prints as "--". */
    static int fixed(char[] b, int o, float v, int decimals) {
        if (Float.isNaN(v) || Float.isInfinite(v)) { o = put(b, o, '-'); return put(b, o, '-'); }
        int d = Math.max(0, Math.min(3, decimals));
        long scale = d == 0 ? 1 : (d == 1 ? 10 : (d == 2 ? 100 : 1000));
        long t = Math.round(Math.abs((double) v) * scale);
        o = put(b, o, t == 0 ? ' ' : (v < 0 ? '-' : '+'));
        o = num(b, o, t / scale);
        if (d > 0) {
            o = put(b, o, '.');
            long f = t % scale;
            for (long k = scale / 10; k >= 1; k /= 10) { o = put(b, o, (char) ('0' + (f / k) % 10)); }
        }
        return o;
    }

    static int put(char[] b, int o, char c) {
        if (o < b.length) b[o++] = c;
        return o;
    }
}
