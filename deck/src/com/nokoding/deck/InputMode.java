package com.nokoding.deck;

/** Decides whether an input event came from a gamepad or a remote. Pure Java. */
final class InputMode {
    private InputMode() {}

    static final int REMOTE = 0, GAMEPAD = 1, POINTER = 2;
    /** Same value as android.view.InputDevice.SOURCE_CLASS_POINTER. */
    static final int CLASS_POINTER = 0x00000002;
    static final int STYLE_GENERIC = 0, STYLE_SONY = 1, STYLE_XBOX = 2;
    // Same values as android.view.InputDevice.SOURCE_GAMEPAD and SOURCE_JOYSTICK.
    static final int SRC_GAMEPAD = 0x00000401, SRC_JOYSTICK = 0x01000010;

    static boolean has(int sources, int src) { return (sources & src) == src; }

    static int classify(int eventSource, int deviceSources, boolean gamepadButton, boolean virtualDevice) {
        if (gamepadButton) return GAMEPAD;
        if (has(eventSource, SRC_JOYSTICK) || has(eventSource, SRC_GAMEPAD)) return GAMEPAD;
        if (!virtualDevice && (has(deviceSources, SRC_GAMEPAD) || has(deviceSources, SRC_JOYSTICK))) return GAMEPAD;
        return REMOTE;
    }

    static int style(int vendorId) {
        if (vendorId == 0x054c) return STYLE_SONY;
        if (vendorId == 0x045e) return STYLE_XBOX;
        return STYLE_GENERIC;
    }
}
