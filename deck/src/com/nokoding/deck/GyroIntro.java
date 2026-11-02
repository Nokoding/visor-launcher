package com.nokoding.deck;

/**
 * 2.18: the one-time introduction to the gyro. The first time the home screen is showing and a controller with motion
 * sensors is connected, Visor opens the gyro test with a short welcome. It is asked once: showing it is what counts, so
 * a crash or a power cut cannot bring it back at every start. Pure Java, tested.
 */
final class GyroIntro {
    private GyroIntro() {}

    static final String PREF = "gyroIntro";

    /**
     * @param done            the intro was shown before
     * @param tutorialPending the first run tutorial is about to open or is open (the intro waits for it to close)
     * @param homeShowing     nothing else is open on top of the home screen, and Visor is in front
     * @param padHasGyro      a connected controller reports a gyroscope
     */
    static boolean shouldOffer(boolean done, boolean tutorialPending, boolean homeShowing, boolean padHasGyro) {
        return !done && !tutorialPending && homeShowing && padHasGyro;
    }

    /** The two lines the gyro test shows in place of its usual first line while the intro is open. */
    static final String[] LINES = {
            "WELCOME. YOUR CONTROLLER HAS MOTION SENSORS. TILT AND TURN IT AND WATCH THE MODEL FOLLOW.",
            "ON THE HOME SCREEN THEY CAN SWAY THE SCENE. SETUP, MOTION, GYRO SWAY. A RECENTERS. EXIT WHEN DONE."};
}
