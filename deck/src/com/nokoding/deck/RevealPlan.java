package com.nokoding.deck;

/**
 * 2.5: which text animation parameters the next reveal of one panel starts with.
 *
 * Bug it fixes (A1): HomeActivity forced the NONE text mode when it rebuilt the same screen
 * after a value change, so the typing would not replay. It did that on the Params object it
 * then handed to the panel, and the panel kept it, so every later tab switch on that panel
 * stayed NONE even with TEXT ANIMATION on TYPE or FADE. Now the panel always holds the user's
 * real parameters, and a refresh only arms quietNext(), which turns the very next start
 * into NONE and then clears itself.
 *
 * The help line under the rows uses helpParams(): a crossfade (FADE) unless the user chose
 * NONE. Reduce effects already arrives as a short FADE in the user parameters.
 * Pure Java.
 */
final class RevealPlan {
    private final TextReveal.Params user;
    private boolean quiet;

    /** @param user the panel's live parameters. Read at every start, never changed here. */
    RevealPlan(TextReveal.Params user) { this.user = user; }

    /** The next start shows its text at once (a refresh of the same screen). One start only. */
    void quietNext() { quiet = true; }

    boolean quietPending() { return quiet; }

    /** Fills out with the parameters for the reveal that starts now. Consumes a pending quiet. */
    void next(TextReveal.Params out) {
        out.set(user);
        if (quiet) {
            out.mode = TextReveal.NONE;
            out.cursor = false;
            out.ticks = false;
            quiet = false;
        }
    }

    /** The help line: FADE (a crossfade without a slide), NONE stays NONE. */
    static void helpParams(TextReveal.Params user, TextReveal.Params out) {
        out.set(user);
        out.mode = user.mode == TextReveal.NONE ? TextReveal.NONE : TextReveal.FADE;
        out.slide = 0f;
        out.cursor = false;
        out.ticks = false;
        out.staggerMs = 0;
        if (out.fadeMs > 220) out.fadeMs = 220;
    }
}
