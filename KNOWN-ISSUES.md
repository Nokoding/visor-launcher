# Known issues and what is not verified

Visor Launcher is a public beta. Almost every feature was written and tested on a computer (about 5,000 automatic checks on the logic), and then tried on one onn. Full HD Streaming Device (Android 14, 1.4 GB RAM) with one DualSense controller. Nothing has been tried on other boxes. If it works or fails on yours, please open an issue.

## Worked on the one test box

- Opening the control center (the PS style menu) with the PS button over a game.
- Stick volume (hold a key and spin the right stick).
- The tutorial pages and the gamepad test basics.

## Found not to work on the test box

- The "DISPLAY" and "GAME MODE" game profile methods are refused by the box. It hides the Android calls they need. Use the PHONE HELPER method, which runs the display commands from your phone with adb.
- Performance mode closes background apps, but a visible change in free memory was not seen. Android restarts some apps and the box may have little running in the background. The feature now shows free memory before and after so you can judge it.

## Written but not tried on a real TV yet

- Whether the accessibility service ever sees the Home key (HOME KEY CHECK in ADVANCED REMAP tells you). Remapping Home only works if it does.
- Whether the rumble check, SKIP NEXT and hold B in the gamepad test behave well with your controller.
- The theme editor sliders with the D-pad and the stick, and the live preview speed.
- The 3D controller models in the gyro test: how smoothly the FULL setting draws on a 1.4 GB box (111,676 and 43,119 triangles, drawn in batches of 10,000). The default is LIGHT (12,000 triangles). The DualSense model's author says it is not from exact blueprints, so it is close but not exact. A painter's-algorithm drawing may show a wrong face order at some angles.
- The gyro welcome on first launch only appears if the box reports the controller's gyroscope to the app (Android 12 or newer).
- Video wallpapers (experimental): memory use, smoothness and start time on a 1.4 GB box. Try a short, small file first.
- The helper scripts in deck/tools against a real TV (they were run against a fake adb).
- Other boxes and TVs, including Fire TV and Nvidia Shield: unknown. Do not assume they work.
- Wallpaper, icon, sound and theme packs: file reading and size limits are tested on a computer, not every pack you can make.

## Limits by design

- No INTERNET permission, so no online features. Packs, wallpapers and themes get on the TV with adb from a phone or computer.
- Android gives an app no way to press another button for you, so a remap can only run an action.
- Home is handled by the system. Android may still open the stock home screen, see the FAQ.
