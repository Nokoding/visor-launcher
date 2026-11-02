package com.nokoding.deck;

/**
 * 2.4.1: who may own the lock-on reticle (FocusFrame). The reticle belongs to the home screen
 * and to Setup panels. A full screen test (Gyro test, Gamepad test, Gyro calibration, Controller
 * report), the PS quick menu and a panel that is still growing all hide or replace the wheel, so
 * the reticle, its index tag, its lock-on sound and its lights must stay off while they are open.
 * 2.4 only checked the quick menu and the panel, so the Gyro test showed the wheel's frame and
 * its "05/13" tag in the middle of the screen. Pure Java.
 */
final class FrameGate {
    private FrameGate() {}

    static final int HIDDEN = 0, PANEL = 1, HOME = 2;

    static int owner(boolean testOpen, boolean quickOpen, boolean panelOpen, boolean panelReady) {
        if (testOpen || quickOpen) return HIDDEN;
        if (panelOpen) return panelReady ? PANEL : HIDDEN;
        return HOME;
    }
}
