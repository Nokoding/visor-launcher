# Animations

New in 2.3. Menus in Visor don't just pop up anymore. Hold OK (or press X) on an app and its icon glides to the side of the screen, a panel grows out of it, and the options type in like commands in a terminal. Every other menu gets the same text animation and sounds, without the icon move.

![Options flyout design mock](img/preview-flyout.png)

The image is a design mock rendered from deck/tools/mock/flyout-mock.html with the same layout numbers as the app. It's not a screenshot.

## Where it shows

- **Options flyout** (icon glide plus panel): the app menu (hold OK or X), the dock menu, the dock recents menu, and Change icon. Going from an app menu to Change icon and back keeps the icon docked, only the text retypes.
- **Flyout without the icon** (2.4): the PS quick menu, inside Visor and over other apps (docs/ps-menu.md). The panel grows out of the right edge and the rows type in, with the flyout sounds. There's no app icon to carry, so ICON GLIDE doesn't apply.
- **Text animation only**: every Setup tab and sub-screen (switching tabs retypes the rows), Install pack, the controller report, Home adb commands, Add app (the list types in when the scan finishes), confirmation prompts (PRESS OK AGAIN... types in on the row), and toasts. Toasts are Visor's own HUD toasts now, near the bottom of the screen. When Visor isn't in front it still uses Android's toast.

Reopening the same screen after an action (Reload packs, using a pack) doesn't replay the typing. That would get old fast.

## Never in the way

Any key, stick move or click while something animates finishes it on the spot, then does what the key does. You never wait for a menu. The close is faster than the open (about 250 ms against about 450).

## The options

Setup, ANIMATIONS, OPTIONS FLYOUT. PREVIEW at the top plays the real flyout on the focused app, with sample rows. OK or Back takes you straight back to the options, so you can tune and replay.

| Option | Values | Default | What it does |
|---|---|---|---|
| TEXT ANIMATION | TYPE, FADE, NONE | TYPE | TYPE: characters one by one with a block cursor at the end of the line being typed. FADE: each line fades in and slides a few pixels. NONE: instant. |
| TYPING DIRECTION | LEFT TO RIGHT, RIGHT TO LEFT | LEFT TO RIGHT | Right to left types from the end of each line toward its start, and lines the text up on the right edge (each row mirrors: label right, value left). FADE slides the same way. |
| TYPING SPEED | 40 to 200 characters a second | 90 | |
| ROW STAGGER | 0 to 200 ms | 70 | Row n starts this long after row n - 1. Rows type at the same time, so they overlap. |
| CURSOR | ON, OFF | ON | The block cursor in TYPE. |
| SOUND | ON, OFF | ON | The typing ticks and the flyout sweeps. Off means the flyout uses the normal menu sounds and nothing ticks. Mute and Volume in SOUND still apply on top. |
| ICON GLIDE | ON, OFF | ON | Off: the icon fades in at the side instead of traveling, the panel still grows. |
| FLYOUT SIDE | LEFT, RIGHT | LEFT | Which side the icon docks on. |
| DURATION SCALE | 0.5x to 2x | 1x | Multiplies every time above, slower or faster. |
| PANEL STYLE | SOLID, GLASS | SOLID | GLASS is see-through with a lighter scrim and an inner bevel line. It applies to every panel, the PS menu and toasts. No blur, so it costs the GPU nothing extra. |

Below those, SCREENS has one switch per screen: Setup screens, app menus, PS quick menu, Install pack, Controller report, Home adb commands, confirmations and toasts. Turn one off and it shows without animation. RESET TO DEFAULTS puts everything back.

Two speed rules keep long screens fast whatever you pick:

- The row stagger shrinks on long lists so the last row starts within 280 ms (times the duration scale). A 12 row Setup tab doesn't take a second to start.
- One row never types for longer than 320 ms (times the duration scale). Long rows like adb commands type faster than the set speed.

## Text is always complete once it stops (2.4.1)

A finished, interrupted or restarted animation never leaves text cut short. 2.3 and 2.4 kept the line lengths from the moment the typing started. A focused row adds arrows to its value (`<  WHEEL  >`), a value change or a confirmation changes a row's text, and the old length cut it: the focused row showed `< WH`. The views now ask TextReveal for the characters of a line at its real length right now (`visible(i, actual)`). While typing it is a share of that length. Done, interrupted or idle, it is always the whole text. Only a close takes text away.

## A refresh quiets one reveal only (2.5)

When Setup rebuilds the screen you are on after an action, the text does not retype. 2.4.1 did that by switching the panel to NONE for good, so every later tab switch on that panel also showed at once, whatever TEXT ANIMATION said. Now the panel keeps your real settings, the refresh shows its text at once, and the next tab switch types again. Any animation also wakes the frame loop by itself when it starts, so it can no longer stop half typed until another key press.

## Row help in the footer (2.5)

The help line under the rows uses the same options. It fades in when focus moves (TEXT ANIMATION NONE shows it at once), without ticks or a cursor. See the README for what it shows. A sentence too long for the footer scrolls, which is not part of the options. With Reduce effects on it stays still and is cut with three dots.

## Reduce effects

Setup, MOTION, REDUCE EFFECTS wins over all of this: no icon glide, panels appear at once, text fades in within 120 ms, no stagger, no cursor, no ticks. NONE stays NONE.

## Sounds

Three new sound events, all through sound packs like the rest. See [sound-packs.md](sound-packs.md).

- `type_tick`: a very short high tick (built in: 8 ms around 3.2 kHz). One per 2 characters per typing row, at most 40 a second across everything on screen, with a small random pitch change (plus or minus 10 percent) so a burst doesn't sound like a machine gun. Ticks over the cap are dropped, never queued, so nothing ticks after the typing stops.
- `flyout_open`: a short rising sweep when the flyout opens.
- `flyout_close`: a short falling sweep when it closes.

Rumble: flyout_open and flyout_close rumble like menu_open and menu_close. Ticks don't rumble.

## How the options work (for themes later)

Each animation is an `AnimSpec`: an id, a name and a list of `AnimOption`s, built from a tiny text schema, one option per line:

```
key | LABEL | TYPE | args | default
```

TYPE is CHOICE, INT, FLOAT, BOOL or COLOR. The OPTIONS FLYOUT schema, from AnimConfig.java:

```
text    | TEXT ANIMATION   | CHOICE | TYPE, FADE, NONE             | TYPE
dir     | TYPING DIRECTION | CHOICE | LEFT TO RIGHT, RIGHT TO LEFT | LEFT TO RIGHT
speed   | TYPING SPEED     | INT    | 40..200/10 CPS               | 90
stagger | ROW STAGGER      | INT    | 0..200/10 MS                 | 70
cursor  | CURSOR           | BOOL   |                              | on
sound   | SOUND            | BOOL   |                              | on
glide   | ICON GLIDE       | BOOL   |                              | on
side    | FLYOUT SIDE      | CHOICE | LEFT, RIGHT                  | LEFT
scale   | DURATION SCALE   | FLOAT  | 0.5..2/0.25 X                | 1
style   | PANEL STYLE      | CHOICE | SOLID, GLASS                 | SOLID
```

plus one BOOL per screen, `screen.setup`, `screen.appmenu`, `screen.quick`, `screen.install`, `screen.report`, `screen.homecmds`, `screen.confirm` and `screen.toast`.

- INT and FLOAT args are `min..max/step unit`. CHOICE args are the names, comma separated. Bad schema lines are skipped.
- Setup builds its rows from the schema: INT becomes a slider, everything else a row you step through with left and right. A new animation only needs a schema, no new Setup code.
- Values are stored as text in Visor's SharedPreferences under `anim.<id>.<key>`, for example `anim.flyout.speed = 120`. CHOICE by name, BOOL as on or off, COLOR as #RRGGBB.
- Out of range numbers are clamped and snapped to the step. Text that doesn't fit an option is refused and the old value stays.
- A theme file can set the same values by key, one `key = value` per line, # for comments. Unknown keys are ignored. AnimSpec.apply reads that format and AnimSpec.dump writes it, so a theme can carry its animation settings once themes exist:

```
# my theme
text = fade
dir = right to left
speed = 140
style = glass
screen.toast = off
```

## Under the hood

- One shared helper, TextReveal, runs the text for every screen. It only knows line lengths, a clock and a callback, no Android views, so it runs in the JVM tests and the planned PS menu overlay service can reuse it. FlyoutAnim is the icon and panel timeline. AnimConfig turns the options, Reduce effects and the screen switches into their parameters.
- Everything steps on Visor's one Choreographer frame clock (Engine), so it stays in step with the wheel and the reticle.
- No allocation per frame: row text is ellipsized and measured once into TypeDraw pieces when it changes, Paints and Paths are reused, the typing steps a character count instead of building substrings.
- The reticle waits until the flyout panel is fully out, then locks onto the first row.

Not checked on the TV yet. See [KNOWN-ISSUES.md](../KNOWN-ISSUES.md).
