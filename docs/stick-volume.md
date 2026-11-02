# Stick volume

New in 2.10. Hold a controller key and spin the right thumbstick round its rim, like the wheel dial, and the volume goes up or down. It does the same job as [touchpad volume](touchpad-volume.md) for controllers or boxes where the touchpad does not reach Visor. Setup, PAD.

## Settings

| Row | What it does |
|---|---|
| STICK VOLUME | OFF (default), or the key to hold: L1, R1, L2, R2, CREATE or OPTIONS. The PS button, Home and Back are not allowed |
| STICK VOLUME SPEED | How far you spin for one step: SLOW 90 degrees, NORMAL 60, FAST 45, FASTER 30 |
| STICK VOLUME INVERT | Flips the direction |

## How it behaves

- Press and hold. The key works like a modifier: stick volume is on while the key is down and off the moment you let go. It is not a toggle. (2.10 could behave like a toggle over other apps, see the note below.) Hold the key and spin the right stick round its rim. Clockwise is louder, counter clockwise is quieter. Let go of the key and the stick goes back to what it did.
- It uses the same angle logic as the wheel stick, so spinning feels the same. Pushing the stick one way and letting go (a flick) does nothing, so bumping the stick while holding the key cannot move the volume. A single call never moves more than 4 steps.
- The volume changes by whole steps through AudioManager, with the system volume bar showing the level. On a box with fixed volume (HDMI-CEC) Visor sends the volume key action instead, and the box decides.
- The hold key still goes to the app: Visor never takes it. While it is held the stick does not go to the app either.
- It is the right stick. If the box reports the right stick on other axes, Visor uses the same choice the wheel stick uses (Z and RZ when the pad has them, otherwise RX and RY).
- It can use the same hold key as touchpad volume. Then both work at once.
- Inside Visor it works with nothing else turned on. Over other apps it needs the overlay service (the same one adb step as the PS menu, see [ps-menu.md](ps-menu.md)). On Android 14 the service asks Android for joystick events only while the hold key is down, so a game keeps its sticks the rest of the time.

## Why it acted like a toggle, and the fix (2.11)

Over other apps the service has to ask Android for joystick events while the key is held. In the Android 14 source (AccessibilityInputFilter), a change in which motion sources a service wants rebuilds Android's input filter and resets its stream state, and the key release that follows can then go straight to the app and never reach the service. The service never saw the release, so the key stayed "held" until the next press, which looked like a toggle.

Three things fix it. The service keeps a placeholder motion source (a trackball, which no game pad produces) asked for all the time while stick or touchpad volume is on, so the set of features does not change when the key goes down and only the joystick part is added and removed. The key is tracked by a small class (HoldKey) that counts as let go when a release was lost: 20 seconds with no key event and no stick movement. And the key is now tracked before any screen can use the key, and let go when Visor pauses. None of this has run on the TV, so whether it fixes the box is the thing to check: hold the key, spin, let go, and the stick should go straight back to the wheel or the game.

## What could not be checked

- Whether the overlay service receives the right stick over a game on this box, and whether asking for joystick events takes the stick away from the game while the key is held (it should only then).
- How the speed choices feel. The angles are a guess.
