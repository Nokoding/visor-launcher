# The 3D controller models in the gyro test

New in 2.17. The gyro test draws a low-poly controller that turns with the real one. It is built in code, so there is nothing to install. You can also replace it with your own model.

## What the built-in models are

One for the DualSense and one for the Xbox One, chosen from the detected controller (other pads get the DualSense shape). Each is a few hundred triangles at most:

- The body is the flat outline extruded 2.4 cm thick.
- Two grips hang below it, narrowing downward (the pad is about 6.6 cm thick at the grips on a DualSense and 6.1 cm on an Xbox One).
- Two bumpers on the front edge, two triggers in front of them, two sticks, a D-pad, four face buttons, and on the DualSense a touchpad. Home, start and select are flat marks on top.
- Faces are shaded flat from the camera direction, using the theme colors. Nothing is allocated per frame.

Real sizes that the models are built around (dimensions.com, read in 2026):

| Controller | Width | Front to back | Thickness |
|---|---|---|---|
| DualSense | 160 mm | 106 mm | 66 mm |
| Xbox One | 152.9 mm | 101.9 mm | 61 mm |

**These shapes are approximate.** The 3D body is a hand-made half outline in `ControllerModel`, and the thickness, the grips, the bumpers and the triggers are estimated from the sizes above. Exact side and bottom drawings were not available under a licence that allows redistribution, so none is used. If you have a better model, you can drop it in (below). The estimates make the models a little deeper than the real pads.

## Using your own model

Put one OBJ file per controller in:

```
/sdcard/Android/data/com.nokoding.deck/files/models/dualsense.obj
/sdcard/Android/data/com.nokoding.deck/files/models/xbox-one.obj
```

Visor makes the folder when it starts. From Termux: `bash deck/tools/install-model.sh TVIP:PORT dualsense.obj` (it checks the file first). Visor reads the file each time the gyro test opens, so there is no reload step.

The format is a small part of Wavefront OBJ:

- `v x y z` lines for the points and `f a b c ...` lines for the faces. A face has 3 to 8 corners. Counting starts at 1. `a/b/c` is allowed and only `a` is used. Everything else is ignored.
- Units are centimeters. If the biggest size is over 40, the file is taken to be in millimeters and scaled by 0.1. A model over 400 cm in any direction is refused.
- Y is up out of the face of the controller, Z points toward the player, X to the right. The origin should be the middle of the body.
- Faces must point outward (counter-clockwise seen from outside) because Visor hides faces that look away.
- Limits: 400 KB, 6000 vertices, 4000 triangles (a quad counts as two).
- Your model is drawn as one piece in the theme colors. Buttons do not light up on it.

A file with a bad number, a missing or negative index, a face with too few or too many corners, no faces, or one over a limit is ignored and the built-in model is drawn. The gyro test never fails because of a model file.

## What could not be checked

Nothing in 2.17 has run on a TV. The model construction and the file reader are tested on a computer (every hardware face points out of its part, the sizes are near the real pads, hostile OBJ text is refused). How it looks, and whether it draws smoothly on the box, is not known. The painter's-algorithm drawing sorts faces by distance, so a model with many overlapping parts may show a wrong order at some angles.
