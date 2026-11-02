package com.nokoding.deck;

/** 2.9 tests: runs the two groups (A combos, B tutorial) and the 2.9 guards. */
final class Tests29 {
    private Tests29() {}

    static void run() {
        for (String n : new String[]{"Tests29A", "Tests29B", "Tests210", "Tests217", "Guards29"}) {
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
