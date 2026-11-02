package com.nokoding.deck;

/**
 * 2.1 Player LEDs. The onn box exposes the DualSense's five player LEDs as on/off lights
 * (type INPUT, BRIGHTNESS only, max brightness 1, names like input8:white:player-3), but
 * no RGB light bar. Deck can use those five LEDs instead. This class is the pure mapping;
 * Pad drives the lights. Bit 0 is player-1 (left) up to bit 4, player-5 (right).
 */
final class PlayerLeds {
    private PlayerLeds() {}

    static final int OFF = 0, WHEEL = 1, BATTERY = 2, LOCK_FLASH = 3;
    static final String[] NAMES = {"OFF", "WHEEL POSITION", "BATTERY LEVEL", "LOCK-ON FLASH"};
    static final int LEDS = 5, ALL = (1 << LEDS) - 1;
    /** Android MonoLight brightness is the color alpha, scaled to the node's max (1 here). */
    static final int ON = 0xFF000000, OFF_COLOR = 0;

    /** Player number from a light name ("input8:white:player-3", "player3"), or -1. */
    static int index(String name) {
        if (name == null) return -1;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        int p = n.lastIndexOf("player");
        if (p < 0) return -1;
        int i = p + 6;
        if (i < n.length() && (n.charAt(i) == '-' || n.charAt(i) == '_')) i++;
        int v = 0, digits = 0;
        while (i < n.length() && Character.isDigit(n.charAt(i)) && digits < 2) { v = v * 10 + (n.charAt(i) - '0'); i++; digits++; }
        if (digits == 0 || i != n.length() || v < 1) return -1;
        return v;
    }

    /** Which LED (0 based) matches the focused item idx of n items, spread over leds buckets. */
    static int wheelBucket(int idx, int n, int leds) {
        if (n <= 0 || leds <= 0) return 0;
        int i = ((idx % n) + n) % n;
        return Math.min(leds - 1, (int) ((long) i * leds / n));
    }

    static int wheelMask(int idx, int n, int leds) { return n <= 0 ? 0 : 1 << wheelBucket(idx, n, leds); }

    /** LEDs lit for a battery level 0..1: any charge lights at least one, full lights all. Unknown lights none. */
    static int batteryCount(float capacity, int leds) {
        if (Float.isNaN(capacity) || capacity <= 0f || leds <= 0) return 0;
        if (capacity >= 1f) return leds;
        return Math.max(1, Math.min(leds, (int) Math.ceil(capacity * leds - 1e-4)));
    }

    static int batteryMask(float capacity, int leds) { return (1 << batteryCount(capacity, leds)) - 1; }

    static int color(int mask, int led) { return (mask & (1 << led)) != 0 ? ON : OFF_COLOR; }
}
