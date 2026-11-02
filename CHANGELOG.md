# Changelog

Visor Launcher was developed privately as "Deck" until this public beta. Versions below are the internal numbers.

## Not released yet

- The 12,000 triangle controller model is the default again, in the app and on the website. FULL stays in Setup (3D MODEL DETAIL). The download link on the website and in the guides points at 2.19-beta again.

## 2.20-beta (extra build)

This build has the full detail models as the default. It stays published as an extra. A later release must have a version code above 24 to install over it.

- The controller models now keep every triangle of the originals: 111,676 for the DualSense and 43,119 for the Xbox pad (2.19-beta had cut them to 12,000). They are stored in a compact binary format (VMD), and the drawing code works out each face's direction once, not every frame. The app is about 0.9 MB bigger.
- New Setup row 3D MODEL DETAIL: FULL (every triangle, the default) or LIGHT (12,000 triangles) in case the box stutters. Not tested on a real TV yet.
- The website shows the same full detail models, with smooth shading and a soft glow on the pressed button.

## 2.19-beta

- Detailed 3D models of a DualSense and an Xbox One style pad in the gyro test (12,000 triangles each, CC BY 4.0, credited in the README and NOTICE). Your own OBJ files still win, and the old hand-made shapes stay as a last resort.
- The first time the home screen shows with a gyro controller connected, the gyro test opens once with a short welcome.
- The website uses the same models.

## 2.18-beta (public beta)

First public release. The same code as 2.17 plus:
- Renamed to Visor Launcher on screen and in the docs. The package name and the file names on the TV are unchanged, so an older build updates in place.
- The controller outlines in the gamepad test are the project's own hand-written curves again (drawing data from a third party is not shipped).
- New guides, FAQ, logo and website.

## 2.17

- Tutorial can no longer trap you, with a SKIP TRY chip.
- A game profile can carry its own performance mode and volume. New profiles use the phone helper method.
- Home can be remapped in ADVANCED REMAP behind a last warning, only if the service sees it. Five quick Home presses give it back.
- Gamepad test: hold B to leave, SKIP NEXT, a rumble check and a small report.
- Theme editor with a readability warning, theme files and install scripts.
- Better 3D controller models and your own OBJ models.
- Experimental video wallpapers and a phone side upload page.

## 2.16

- Tutorial combo page fix, performance mode shows free memory before and after, EXIT GAME and PERFORMANCE MODE remap actions.

## 2.9 to 2.15

- Tutorial and combos (2.9), stick volume (2.10, 2.11), wheel stick speed up to 500 percent, setup script and adb command doc, performance mode and EXIT GAME (2.12), flat gamepad test with controller outlines (2.12 to 2.14), a fix for the display grant (2.15).

## 2.0 to 2.8

- Wheel and grid home screen, themes, sound packs, icon packs, wallpaper packs, animations, the PS style overlay and control center, task manager, button remap, screensaver, game profiles and touchpad volume.
