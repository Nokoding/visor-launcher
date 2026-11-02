# Button combos

New in 2.9. A combo runs an action when you hold one button and press another. For example L1 + R1 can open Visor's PS menu. You make them yourself in Setup, REMAP, COMBOS. They sit next to the single button remaps from [button-remap.md](button-remap.md) and use the same actions.

## Making one

1. Setup, REMAP, COMBOS, NEW COMBO.
2. Hold one button, then press another while you still hold the first. The screen shows what it saw.
3. Pick the action. The list is the same as for a single button, without DEFAULT, DO NOTHING and the inside Visor key swaps.
4. Open the combo again to change the action, or CLEAR THIS COMBO.

Up to 12 combos. The same pair twice is one combo.

## How it behaves

- Android gives an app no way to hold a button press back. So the **first** button of the pair always reaches the app. Only the **second** one is taken, together with its repeats and its release. Order does not matter: whichever button comes second completes the pair.
- Pressing the second button again while the first is still held runs it again, with a short pause so one press cannot fire twice.
- A third button held at the same time does not break the pair.
- In Visor, combos work as soon as they are made. In other apps they need the overlay service (the same one adb step as the PS menu, see [ps-menu.md](ps-menu.md)). In games a combo only works with REMAP IN GAMES on, like single remaps.
- REMAPS ON in the control center (CONTROLLER) is a master switch: off turns every remap and every combo off without deleting any.
- Combos are made in Visor's own window. The screen that makes them reads the controller there.

## Which buttons

Allowed: X, Y, L1, R1, L2, R2, the two stick clicks, Create, Options, and the spare remote buttons that LEARN A BUTTON can name (the channel buttons, TV, input, the account switcher and others).

Never allowed: the D-pad, OK, Back, Home, Power, the PS button, A and B, typing keys, and the advanced remap buttons. Those keep Visor usable, and a wrong combo can never take them away. The make a combo screen says why when you try one.

Inside Visor, X, Y, L1, R1, Start and Select have jobs of their own (the app menu, the layout switch, jumping five items, Setup and the stats panel). They still do that when pressed alone. Only the second button of a made combo is taken.

## Getting rid of them

CLEAR THIS COMBO removes one. RESET ALL REMAPS in Setup, REMAP, and the adb command `adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd reset_remap`, remove every combo too. Combos cannot take the D-pad, OK or Back, so the Back held for 6 seconds escape is not needed for them.

## What could not be checked

- Whether the onn box passes every controller button to the overlay service, so a combo may work in Visor and not over some other app. Setup, REMAP, LEARN A BUTTON shows what reaches Visor and what reaches the service for a single button.
- How it feels in a real game: the first button reaching the game is how Android works, and some games react to it.
