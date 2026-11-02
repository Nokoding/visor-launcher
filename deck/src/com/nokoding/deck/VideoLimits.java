package com.nokoding.deck;

/**
 * 2.17 video wallpapers: the hard limits, checked from the file's header before anything is played. Pure Java.
 * The box has 1.4 GB of RAM and a weak GPU, so a video wallpaper must be small. The numbers are the same ones
 * written in docs/wallpapers.md.
 */
final class VideoLimits {
    private VideoLimits() {}

    static final int MAX_W = 1280, MAX_H = 720, MAX_FPS = 30;
    static final long MAX_BITRATE = 6_000_000L, MAX_BYTES = 40L * 1024 * 1024;
    static final long MIN_MS = 500, MAX_MS = 300_000;
    /** A header rate a little over the limit is allowed, since encoders write 30.0003 and 6.02 Mbit/s. */
    static final double FPS_SLACK = 0.5, BITRATE_SLACK = 1.05;

    /**
     * @param w          the width the header gives (before rotation)
     * @param h          the height
     * @param rotation   0, 90, 180 or 270; 90 and 270 swap the sides
     * @param durationMs length in milliseconds
     * @param bitrate    bits per second, 0 if unknown
     * @param fps        frames per second, 0 if unknown
     * @param bytes      file size
     * @return null when the video is allowed, otherwise the reason in plain capitals
     */
    static String check(int w, int h, int rotation, long durationMs, long bitrate, double fps, long bytes) {
        if (w <= 0 || h <= 0) return "CANNOT READ THE VIDEO SIZE";
        if (rotation == 90 || rotation == 270) { int t = w; w = h; h = t; }
        if (w > MAX_W || h > MAX_H) return "VIDEO IS " + w + "x" + h + ", THE LIMIT IS " + MAX_W + "x" + MAX_H;
        if (bytes <= 0) return "EMPTY FILE";
        if (bytes > MAX_BYTES) return "VIDEO FILE IS OVER 40 MB";
        if (durationMs < MIN_MS) return "VIDEO IS TOO SHORT OR HAS NO LENGTH";
        if (durationMs > MAX_MS) return "VIDEO IS OVER 5 MINUTES";
        if (fps > MAX_FPS + FPS_SLACK) return "VIDEO IS OVER 30 FPS";
        if (bitrate > MAX_BITRATE * BITRATE_SLACK) return "VIDEO IS OVER 6 MBIT/S";
        return null;
    }

    /** A number from header text, or fallback for null, empty or not a number. */
    static long parseLong(String s, long fallback) {
        if (s == null) return fallback;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    static double parseDouble(String s, double fallback) {
        if (s == null) return fallback;
        try {
            double d = Double.parseDouble(s.trim());
            return Double.isNaN(d) || Double.isInfinite(d) ? fallback : d;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
