package com.nokoding.deck;

/** 2.8 tests: runs the three groups (A tasks, B display and profiles, C control center and touchpad) and the 2.8 guards. */
final class Tests28 {
    private Tests28() {}

    static void run() {
        for (String n : new String[]{"Tests28A", "Tests28B", "Tests28C", "Guards28"}) {
            try {
                Class.forName("com.nokoding.deck." + n).getDeclaredMethod("run").invoke(null);
            } catch (ClassNotFoundException e) {
                // not written yet
            } catch (ReflectiveOperationException e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                LogicTests.check(false, n + " threw " + c);
            }
        }
    }
}
