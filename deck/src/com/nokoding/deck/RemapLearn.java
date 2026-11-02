package com.nokoding.deck;

/**
 * 2.6 LEARN A BUTTON: merges what Deck's own window and the overlay service each saw of one
 * press into one picture, and turns it into plain lines for the screen. Pure Java.
 *
 * Two witnesses see the same press: Deck's window (the key arrives as an app would get it) and
 * the accessibility service (the key arrives at the key filter, before the system hands it on).
 * A button the system keeps for itself can reach the service and never Deck, so the two flags
 * are the answer to "can a remap catch this button, and where".
 */
final class RemapLearn {
    /** A press older than this is over: the next press starts a new capture. */
    static final long RESET_MS = 2500;

    long key = RemapKey.INVALID;
    int code, scan, vendor, product, source;
    String device = "";
    boolean deck, svc;
    long t0;

    boolean any() { return RemapKey.valid(key); }

    void clear() {
        key = RemapKey.INVALID;
        code = scan = vendor = product = source = 0;
        device = "";
        deck = svc = false;
        t0 = 0;
    }

    /**
     * One first key down from one witness. @return true if the picture changed.
     * @param service true if it came from the overlay service, false if from Deck's window
     */
    boolean feed(boolean service, int code, int scan, int vendor, int product, String device, int source, long now) {
        long id = RemapKey.pack(code, scan, vendor, product);
        if (!RemapKey.valid(id)) return false;
        boolean fresh = id != key || now - t0 > RESET_MS;
        if (fresh) {
            clear();
            key = id;
            t0 = now;
            this.code = code;
            this.scan = scan;
            this.vendor = vendor;
            this.product = product;
        }
        if (service) svc = true; else deck = true;
        if (device != null && !device.isEmpty() && (this.device.isEmpty() || !service)) this.device = device;
        if (source != 0 && (this.source == 0 || !service)) this.source = source;
        return true;
    }

    /** "Seen by Visor" and "seen by the service" as words. */
    static String yesNo(boolean b) { return b ? "YES" : "NO"; }

    /** The verdict lines under the key facts. */
    String[] verdict(boolean serviceRunning) {
        if (!any()) return new String[]{"PRESS A BUTTON ON YOUR REMOTE.", "IF NOTHING SHOWS UP, THE BOX KEEPS THAT BUTTON FOR ITSELF AND NO APP CAN SEE IT."};
        if (RemapKey.protectedKey(key)) return new String[]{"THIS BUTTON CANNOT BE REMAPPED.", RemapKey.protectedReason(key)};
        int c = RemapKey.cls(key);
        String where;
        if (deck && svc) where = "BOTH SAW IT. A REMAP CAN CATCH THIS BUTTON IN EVERY APP.";
        else if (svc) where = "ONLY THE SERVICE SAW IT. THE SYSTEM TOOK IT BEFORE VISOR. A REMAP SHOULD STILL CATCH IT, BUT THE SYSTEM MAY ACT ON IT TOO. TRY IT.";
        else if (serviceRunning) where = "ONLY VISOR SAW IT. THE KEY FILTER DID NOT, SO A REMAP WORKS INSIDE VISOR ONLY.";
        else where = "ONLY VISOR SAW IT, AND THE SERVICE IS OFF. A REMAP WORKS INSIDE VISOR ONLY. SEE PAD, SHOW ENABLE COMMANDS FOR THE SERVICE.";
        if (c == RemapKey.ADVANCED) return new String[]{where, "THIS IS A SYSTEM BUTTON. IT LIVES IN ADVANCED REMAP."};
        if (c == RemapKey.NAV) return new String[]{where, "THIS IS A NAVIGATION BUTTON. IT LIVES IN ADVANCED REMAP."};
        return new String[]{where};
    }

    /** True if ASSIGN AN ACTION can open this button's page (a standard button, seen at least once). */
    boolean canAssign(boolean advancedUnlocked) {
        if (!any() || RemapKey.protectedKey(key)) return false;
        return RemapKey.cls(key) == RemapKey.STANDARD || advancedUnlocked;
    }

    /** The name for the list: the table's name, or the name this capture would get. */
    String label() { return RemapKey.name(key); }

    // android.view.InputDevice source values (no Android imports here)
    private static final int[] SRC = {0x00000101, 0x00000201, 0x00000401, 0x00001002, 0x00002002, 0x00004002, 0x00010004, 0x00100008, 0x01000010, 0x02000001};
    private static final String[] SRC_NAME = {"KEYBOARD", "DPAD", "GAMEPAD", "TOUCHSCREEN", "MOUSE", "STYLUS", "TRACKBALL", "TOUCHPAD", "JOYSTICK", "HDMI"};

    /** "0x501 KEYBOARD DPAD". The key filter only takes events whose source includes KEYBOARD (0x101). */
    static String sourceText(int source) {
        StringBuilder b = new StringBuilder("0x").append(Integer.toHexString(source).toUpperCase(java.util.Locale.ROOT));
        for (int i = 0; i < SRC.length; i++) if ((source & SRC[i]) == SRC[i]) b.append(' ').append(SRC_NAME[i]);
        return b.toString();
    }

    /** True if the accessibility key filter takes key events from this source (it needs SOURCE_KEYBOARD). */
    static boolean filterSource(int source) { return (source & 0x101) == 0x101; }
}
