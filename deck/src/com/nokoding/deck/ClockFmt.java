package com.nokoding.deck;

import java.util.TimeZone;

/**
 * 2.7: the clock's pure logic. When the next tick is due, how the time is written (12 or 24
 * hour, with or without seconds, always the same number of characters so the top bar never
 * moves) and the short list of time zones for the optional second clock line.
 *
 * Seconds off: tick on the minute boundary, so nothing runs per second at rest. Seconds on: tick
 * on the second boundary. Both use one delayed Handler post, never a per frame callback.
 * format() writes into a caller's char array, so the screensaver clock allocates nothing per tick.
 * Pure Java.
 */
final class ClockFmt {
    private ClockFmt() {}

    static final long SECOND_MS = 1000L, MINUTE_MS = 60000L, DAY_MS = 86400000L;
    /** A few ms past the boundary, so the post never fires just before it and shows the old second. */
    static final long SLACK_MS = 40L;
    /** The longest text format() writes: " 12:45:09 PM". */
    static final int MAX_CHARS = 12;

    /** Milliseconds until the next tick: the next second boundary or the next minute boundary, plus the slack. */
    static long nextDelay(long utcMs, long zoneOffsetMs, boolean seconds) {
        long period = seconds ? SECOND_MS : MINUTE_MS;
        long local = utcMs + zoneOffsetMs;
        long into = Math.floorMod(local, period);
        return period - into + SLACK_MS;
    }

    /** Hour 0..23 of the local time. */
    static int hour(long utcMs, long zoneOffsetMs) { return (int) (secOfDay(utcMs, zoneOffsetMs) / 3600); }

    static int minute(long utcMs, long zoneOffsetMs) { return (int) (secOfDay(utcMs, zoneOffsetMs) / 60 % 60); }

    static int second(long utcMs, long zoneOffsetMs) { return (int) (secOfDay(utcMs, zoneOffsetMs) % 60); }

    private static long secOfDay(long utcMs, long zoneOffsetMs) {
        return Math.floorMod(Math.floorDiv(utcMs + zoneOffsetMs, 1000L), 86400L);
    }

    /**
     * Writes the time into out and returns the length. 12 hour: " 7:05 PM" or " 7:05:09 PM" (the hour
     * has a leading space so the text is the same length all day, and the right aligned clock never
     * shifts when 9:59 becomes 10:00). 24 hour: "19:05" or "19:05:09". Bad numbers are clamped.
     */
    static int format(char[] out, int hour24, int minute, int second, boolean is24, boolean seconds) {
        int h = Math.max(0, Math.min(23, hour24)), m = Math.max(0, Math.min(59, minute)), s = Math.max(0, Math.min(59, second));
        int n = 0;
        if (is24) {
            out[n++] = (char) ('0' + h / 10);
            out[n++] = (char) ('0' + h % 10);
        } else {
            int h12 = h % 12 == 0 ? 12 : h % 12;
            out[n++] = h12 >= 10 ? '1' : ' ';
            out[n++] = (char) ('0' + h12 % 10);
        }
        out[n++] = ':';
        out[n++] = (char) ('0' + m / 10);
        out[n++] = (char) ('0' + m % 10);
        if (seconds) {
            out[n++] = ':';
            out[n++] = (char) ('0' + s / 10);
            out[n++] = (char) ('0' + s % 10);
        }
        if (!is24) {
            out[n++] = ' ';
            out[n++] = h < 12 ? 'A' : 'P';
            out[n++] = 'M';
        }
        return n;
    }

    /** The same as format(), from an instant and a zone offset, as a String. */
    static String format(long utcMs, long zoneOffsetMs, boolean is24, boolean seconds) {
        char[] b = new char[MAX_CHARS];
        int n = format(b, hour(utcMs, zoneOffsetMs), minute(utcMs, zoneOffsetMs), second(utcMs, zoneOffsetMs), is24, seconds);
        return new String(b, 0, n);
    }

    // ---- the second clock line

    /** Time zone ids for SECOND CLOCK ZONE, after OFF. Names are what the row and the line show. */
    static final String[] ZONE_IDS = {
        "UTC", "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Sao_Paulo",
        "Europe/London", "Europe/Paris", "Europe/Berlin", "Africa/Lagos", "Africa/Johannesburg", "Asia/Dubai",
        "Asia/Kolkata", "Asia/Shanghai", "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland"};
    static final String[] ZONE_NAMES = {
        "UTC", "NEW YORK", "CHICAGO", "DENVER", "LOS ANGELES", "SAO PAULO",
        "LONDON", "PARIS", "BERLIN", "LAGOS", "JOHANNESBURG", "DUBAI",
        "MUMBAI", "SHANGHAI", "TOKYO", "SYDNEY", "AUCKLAND"};

    /** Index in ZONE_IDS for a saved id, or -1 for OFF, an empty id or one that is not on the list. */
    static int zoneIndex(String id) {
        if (id == null) return -1;
        for (int i = 0; i < ZONE_IDS.length; i++) if (ZONE_IDS[i].equals(id)) return i;
        return -1;
    }

    /** The zone for a saved id, or null (OFF). Only ids on the list count, so a stray pref never builds a zone. */
    static TimeZone zone(String id) {
        int i = zoneIndex(id);
        return i < 0 ? null : TimeZone.getTimeZone(ZONE_IDS[i]);
    }

    /**
     * The second clock line: "LONDON  9:45 PM" in the same style as the main clock. Writes into out
     * (at least ZONE_LINE_CHARS long) and returns the length, or 0 when the zone is off.
     */
    static final int ZONE_LINE_CHARS = 16 + 2 + MAX_CHARS;

    static int zoneLine(char[] out, String id, long utcMs, boolean is24, boolean seconds) {
        int i = zoneIndex(id);
        if (i < 0 || out.length < ZONE_LINE_CHARS) return 0;
        TimeZone tz = TimeZone.getTimeZone(ZONE_IDS[i]);
        long off = tz.getOffset(utcMs);
        String name = ZONE_NAMES[i];
        int n = 0;
        for (int k = 0; k < name.length(); k++) out[n++] = name.charAt(k);
        out[n++] = ' ';
        out[n++] = ' ';
        char[] t = new char[MAX_CHARS];
        int tn = format(t, hour(utcMs, off), minute(utcMs, off), second(utcMs, off), is24, seconds);
        int from = 0;
        while (from < tn && t[from] == ' ') from++;   // no leading space inside the line
        for (int k = from; k < tn; k++) out[n++] = t[k];
        return n;
    }

    static String zoneLine(String id, long utcMs, boolean is24, boolean seconds) {
        char[] b = new char[ZONE_LINE_CHARS];
        int n = zoneLine(b, id, utcMs, is24, seconds);
        return n == 0 ? "" : new String(b, 0, n);
    }
}
