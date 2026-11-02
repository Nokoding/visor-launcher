# Gamepad test and gyro test

Two screens in Setup, PAD (new in 2.2), plus GYRO CALIBRATION (new in 2.3). They check that every button and the motion sensors on your controller reach Visor. Both take over the whole screen and keep every button to themselves, so nothing behind them moves, opens or launches while you test.

## Gamepad test

Setup, PAD, GAMEPAD TEST, then pick a mode. You can switch modes on the screen too (the FULL TEST and QUICK TEST chips, or left and right on the remote).

**FULL TEST.** Each button lights up when you press it and stays lit. Press every required button once. When the last one goes in, you get a confetti burst, a short rising sound (the `test_complete` sound event) and ALL BUTTONS WORK. RESET (the chip, or OK on the remote) starts over. With Reduce effects on there's no confetti, the sound and the message still come.

**QUICK TEST.** A button lights only while you hold it and goes dark when you let go. No confetti. Use it to check a button that feels flaky.

### Required buttons (16)

The count on the right reads like 11/16.

- Four face buttons: Cross, Circle, Square, Triangle (PlayStation) or A, B, X, Y (Xbox and others)
- L1 and R1 (LB and RB)
- L2 and R2 (LT and RT). They count as pressed from the trigger button or past half travel on the analog axis.
- L3 and R3, the stick clicks
- D-pad up, down, left and right. From D-pad keys or from the hat axes, whichever the controller sends.
- Options and Create (Menu and View on Xbox, Start and Select elsewhere). DualShock 4 shows SHARE instead of CREATE.

### Optional buttons (shown, not counted)

- The PS button (Guide on Xbox, Home elsewhere)
- The touchpad click (PlayStation only). On Android the DualSense touchpad is a separate pointer device, so a click on it arrives as a cursor click. Visor counts a click from a Sony touchpad that doesn't land on a chip.

Lots of TV boxes grab the PS or Guide button for themselves, and some never pass the touchpad click on. The test finishes without them. If they light up, great. If not, that's the box.

### What else is on screen

- DETECTED: the controller type, the model when the product id is known, the name Android reports, and the vendor and product id in hex. PlayStation is Sony (054C), Xbox is Microsoft (045E), Nintendo is 057E. Anything else is generic, and a known name (for example "Wireless Controller") helps as a fallback.
- NINTENDO SWITCH (LAYOUT NOT FULLY SUPPORTED): a Switch controller is found and labeled, but drawn with the generic layout. Its A and B (and X and Y) may light the swapped spots, because Nintendo's labels sit the other way round.
- Triggers: L2 and R2 bars show analog travel from 0.00 to 1.00.
- Sticks: a dot moves inside each stick and the right side shows X and Y from -1.00 to +1.00. Moving them isn't required.
- OTHER KEYS SEEN: any key the layout doesn't have, with Android's name and number, for example `BUTTON_C (98)`. If a button lights nothing on the drawing, look here. The last six show.

## Gyro test

New in 2.17: the 3D controller has grips, bumpers and triggers, and you can use your own model file, see [gyro-models.md](gyro-models.md).

Setup, PAD, GYRO TEST. A low poly controller turns as your real one turns. It's drawn with plain Canvas: a PlayStation or Xbox shape (generic gets the PlayStation shape without the touchpad), neon outline, faces shaded by how much they face you.

- **RECENTER** (A on the controller, OK on the remote, or the chip): the way you hold it now becomes straight. The screen also recenters once on its own when the first sensor reading comes in. (In 2.2 X recentered too. In 2.3 X opens calibration.)
- **CALIBRATE** (X or Square on the controller, or the chip): opens GYRO CALIBRATION, and comes back here when it's done.
- **SWAY PREVIEW** (Y, left or right on the remote, or the chip): a tile that sways exactly like the focused tile on the home screen, with your real Gyro direction, Gyro strength and Reduce effects. Use it to check the direction you picked. If Reduce effects is on or Gyro sway is off in Setup, the tile stays still and says why.
- **Corner markers** TL, TR, BL and BR light up when you tilt clearly into that corner (at least 10 degrees, and diagonal).
- **Tilt level**: a dot that moves the way you tilt, with a trail of the last second. The rim is 45 degrees.
- **Numbers**: pitch (up is +), roll (right is +) and yaw (turning left is +) in degrees, gyro rates in rad/s and the accelerometer in m/s2, both after calibration.
- **Calibration line**: CALIBRATED with the date and the bias size, or NOT CALIBRATED.

How it works: the gyroscope is integrated for all three angles. The accelerometer knows where down is, so it keeps pulling pitch and roll back toward the truth (a complementary filter, about 0.6 s). That stops drift on those two. Yaw has nothing to correct it. In 2.2 it crept slowly and you had to press RECENTER.

2.3 changes, after the model got badly displaced on the TV:

- Every sample goes through the controller's calibration first (see below): the resting bias is taken off and the raw axes are mapped to the model's axes.
- The time step comes from the sensor's own timestamps. The first three samples are dropped, a step is clamped to 50 ms, and a pause over 250 ms isn't integrated at all.
- The accelerometer only corrects pitch and roll when it reads 0.9 to 1.1 g and the controller turns slower than about 29 degrees a second. During fast moves it's ignored, because then it isn't just measuring gravity. (2.2 accepted 0.6 to 1.4 g at any speed.)
- When the controller lies still for a second, the bias is fine tuned in the background and yaw drifts back toward straight over about 8 seconds. Never while it moves.

The sensors are only switched on while this screen is open, and switched off when Visor goes to the background.

### Status line

| Status | Meaning |
|---|---|
| LIVE | Sensor data is coming in. |
| GYROSCOPE AND ACCELEROMETER FOUND, WAITING FOR DATA | Found both, nothing arrived yet. Move the controller. |
| NO GYROSCOPE (ACCELEROMETER ONLY: TILT WORKS, NO YAW) | Tilt works from gravity alone, turning doesn't. |
| NO ACCELEROMETER (GYRO ONLY: NO DRIFT CORRECTION) | Everything moves but slowly drifts. Recenter often. |
| NO GYROSCOPE AND NO ACCELEROMETER ON THIS CONTROLLER | Android sees no motion sensors on it. The model stays still. |
| NO SENSORMANAGER FOR THIS CONTROLLER | Android gives no sensor access for this controller. |
| NEEDS ANDROID 12 FOR CONTROLLER SENSORS | Controller sensors need Android 12 or newer. |
| NO CONTROLLER CONNECTED. PRESS A BUTTON ON A CONTROLLER. | Nothing to test yet. Press a button and it picks that controller. |

## Gyro calibration

Setup, PAD, GYRO CALIBRATION, or X (Square) in the gyro test, or its CALIBRATE chip. It fixes the drift and the "model goes the wrong way" problems for one controller and remembers the result.

Three steps. The ring in the middle shows how far along each one is.

1. **BIAS.** "Put the controller flat on a table and do not touch it." Every gyro reads a small turn even when it lies still. Visor waits until the controller really is still (the gyro barely moves and the accelerometer reads 1 g within 3 percent, steadily), then averages 3 seconds. If it moves, it says MOVED. STARTING OVER. When it's done you see the bias for each axis. Pick it up and press A or OK.
2. **AXES.** Three turns, one after the other: TILT THE FRONT EDGE DOWN, ROLL IT TO THE RIGHT, TURN IT TO THE LEFT. Hold it still in your hands first, then turn about 45 degrees. Visor looks at which sensor axis turned the most, and which way, and builds the mapping from that. If a turn is too mixed it asks again. If the three answers don't make a proper 3D frame (for example one turn went the wrong way), it asks you to redo the three turns. The step also checks the gravity it saw in step 1. If the accelerometer's axes don't match the gyro, tilt correction is turned off for this controller, and the result line says so.
3. **CHECK.** The model now runs on the new calibration. Hold still, turn about 90 degrees any way you like, bring it back and hold still. Visor shows the leftover angle. Then SAVE (A, OK or the chip) or REDO (X, left or right on the remote, or the chip), which starts over from step 1.

Exit any time with B, Back, holding Start and Select for 2 seconds, or the EXIT chip. Nothing is saved until SAVE.

What it changes: the saved bias and axes are used by the gyro test and by gyro sway on the home screen. The 2.2 behavior was zero bias and an assumed axis frame (X right, Y up, Z toward the player) that was never measured. A controller that has never been calibrated still runs that way.

Saved per controller in Visor's settings, keyed by vendor id, product id and Android's device descriptor, so two controllers of the same model keep their own. Setup, PAD shows CALIBRATION (CALIBRATED with the date and bias, or NOT CALIBRATED) and RESET CALIBRATION, which clears it for the connected controller. Uninstalling Visor clears it too.

While a calibrated controller lies still for more than a second, its bias keeps being fine tuned with a slow average, and that's saved when the screen or the home screen sensor stops. It never updates while the controller moves, and a reading far from the current bias is ignored, so a slow steady turn can't be mistaken for bias.

## Reaching the top buttons with a stick (new in 2.4.1)

The chips at the top (RECENTER, CALIBRATE, SWAY PREVIEW, EXIT, and FULL TEST, QUICK TEST, RESET, EXIT in the gamepad test, REDO, SAVE, EXIT in calibration) can be reached without the cursor.

- Push the navigator stick up. That is the left stick, or the right one if you swapped WHEEL STICK in Setup. A ring appears on the first chip, or on the one you left last time.
- Move the stick left or right to pick a chip, down to leave the chips. Holding it repeats.
- A (Cross on PlayStation) or OK on the remote presses the ringed chip. In the gamepad test A also counts as a pressed button, so the 16 required buttons stay accurate. A press on RESET resets first, then A lights up as pressed.
- The remote's D-pad up starts the ring, left and right move it, down leaves, OK presses. With no ring, the old shortcuts work as before (left and right switch the mode or the sway preview, OK recenters or resets).
- In the gyro tests the controller's D-pad can start the ring too. In the gamepad test it cannot: D-pad presses are test inputs there. Once the ring exists, D-pad left, right and down move it, and still count as pressed.
- The ring lets go by itself after 6 seconds of no chip input, so a drifting stick or a forgotten ring can't keep taking A. A small line at the bottom says how to start it.

The shortcuts stay: A recenters, X calibrates, Y switches the sway preview, Back and B (gyro tests) exit, holding Start and Select for 2 seconds exits. Chips still click with the cursor.

## Getting out (both tests)

You can always leave:

- **Back on the remote.** Back from the controller itself doesn't count in the gamepad test, because B and any button mapped to Back are being tested. In the gyro test, B also exits.
- **Hold B (Circle on a DualSense) for 1.5 seconds** in the gamepad test (new in 2.17, like Steam's input test). A ring fills around the B button. Let go to cancel. A quick tap only counts as B pressed.
- **Hold Start and Select together for 2 seconds** (Options and Create, Menu and View). A ring fills in the middle. Let go of either to cancel.
- **Click EXIT** with the cursor (touchpad or mouse). A right click exits too.
- **Turn the controller off or unplug it.** The test closes by itself and says so.
- **Home.** If Visor is your home app, Home closes the test. If Google TV's launcher still answers Home, you land there, and the test is still open when you come back to Visor.

Volume keys on the remote still change the volume while a test is open.

## The controller drawing

The flat button and stick test draws an outline of the controller it detected: a DualSense-style pad (with its touchpad and light bar slits) or an Xbox One style pad (with its view and menu buttons and the offset sticks). Other pads use the DualSense-style drawing. The outlines are curves written by hand in the code (`PadOutline`), so the app carries no image files and no drawing data from anyone else. They approximate the general shape and proportions of the real pads and are not exact. The buttons sit where the real ones are, roughly. Real photos or exact technical drawings were deliberately not used, because their licences do not allow redistributing them in an open source project.

The 3D controller in the gyro test is separate, see [gyro-models.md](gyro-models.md).

## Steam style extras (2.17)

Taken from how Steam's Test Device Inputs works, as far as it could be read about (Steam was not run for this):

- The checklist already counts the required buttons (for example 14/16). New: **SKIP NEXT** marks the first button not seen yet as skipped, for a button the box never reports (the PS button on this box, for one). It counts as done, and the report says how many were skipped. RESET clears the skips. QUICK TEST has nothing to skip.
- When every button is seen or skipped you get DONE (or ALL BUTTONS WORK when none were skipped), with confetti unless Reduce effects is on.
- **Rumble check.** After DONE, press X (Square) to make the controller rumble, then A (Cross) if you felt it or Y (Triangle) if you did not. The answer shows in the REPORT in the left column with the button count. The rumble is Visor's own completion rumble, so it needs RUMBLE turned on in Setup, and it is only as good as what the box lets Visor send.
- The left column also says which drawing is used (Xbox One or DualSense). Anything else uses the DualSense drawing and says DEFAULT.
- Not done: Steam's separate Setup Device Inputs step, where you press each control to map it. It could come later.

What could not be checked: nothing here has run on the TV. In particular, whether X, A and Y reach the test after DONE on the DualSense over Bluetooth on this box is not known.
