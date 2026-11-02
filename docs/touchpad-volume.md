# Touchpad volume

New in 2.8. Hold a controller key and drag a finger on the DualSense touchpad to change the volume. Setup, PAD, TOUCHPAD VOLUME.

## Settings

| Row | What it does |
|---|---|
| TOUCHPAD VOLUME | OFF (default), or the key to hold: L1, R1, L2, R2, CREATE or OPTIONS. The PS button, Home and Back are not allowed |
| TOUCHPAD DIRECTION | UP AND DOWN or LEFT AND RIGHT |
| TOUCHPAD SENSITIVITY | The number of volume steps for a drag across the whole pad: 8, 12, 16 or 24 |
| TOUCHPAD INVERT | Flips the direction |
| TOUCHPAD SEEN | Shows what the touchpad last reported. OK reads it again |

The hold key is chosen from a list and not by pressing it, so a wrong press cannot pick the PS button or Back. Sensitivity is in steps per full swipe and not in dp, because the pad's size in units differs between devices and Visor divides by the pad's own range.

## How it behaves

- Hold the key, touch the pad, drag. Up (or right) is louder. Release stops it. A small dead zone ignores the first bit of movement, a slow drag still adds up to whole steps, and one call never moves more than 6 steps, so a glitch cannot blast the volume.
- The volume changes by whole steps through AudioManager, with the system volume bar showing the level. On a box with fixed volume (HDMI-CEC) Visor sends the volume key action instead, and the box decides.
- The hold key still goes to the app: Visor never takes it. A touchpad click is never taken.
- Inside Visor the touchpad arrives as ordinary motion events. Over other apps the accessibility service has to receive them. On Android 14 the service asks Android for touchpad and pointer motion only while this feature is on (the overlay service must be on).

## The fallback

Whether the DualSense's touchpad reaches the service on the onn box is not known. Visor already treats the pad as a pointer like device, so Android may deliver it as mouse movement and not as a touchpad. Visor handles both shapes (an absolute touchpad over its range, and relative mouse movement over one screen). If nothing arrives, hold the key and press the D-pad (up and down, or left and right, as DIRECTION says) to step the volume. That press is taken only while the key is held, and only while no touchpad source has been seen.

To send real data: Setup, PAD, TOUCHPAD SEEN shows the source kind, the device name and the last position. The Gamepad test and controller report in the same tab list the device's sources too.

## What could not be checked

- Whether the touchpad reaches Visor, or the service, at all, and in which shape.
- How the sensitivity feels. The numbers are a guess.

## No touchpad?

[Stick volume](stick-volume.md) (2.10) does the same with the right thumbstick: hold a key and spin the stick round its rim.

