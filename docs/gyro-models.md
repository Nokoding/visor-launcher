# The 3D controller models in the gyro test

New in 2.17. The gyro test draws a low-poly controller that turns with the real one. It is built in code, so there is nothing to install. You can also replace it with your own model.

## The models that ship in the app (2.18, full detail since 2.19)

Since 2.18 the gyro test draws two detailed models that ship inside the app (`deck/assets/models`). Since 2.19 they keep every triangle of the originals:

| Controller | Model | Author | Licence | Triangles |
|---|---|---|---|---|
| DualSense style | "Playstation 5 Dualsense", https://skfb.ly/opKOx | AHarmlessPotato | CC BY 4.0 | 111,676 |
| Xbox style | "Xbox Inalambric Controller (White)", https://skfb.ly/o6Kox | Chistodrako._. | CC BY 4.0 | 43,119 |

What was done to them: they were scaled to the real width (160 mm for the DualSense, 152.9 mm for the Xbox One pad), turned to the app's axes, joined into one piece, their textures were dropped and the small embossed logos (the PlayStation symbol on the PS button, the Xbox lettering on the back) were flattened, and the faces were turned the right way round for drawing. No triangles were removed from the full models. The faces of the buttons are tagged in the file for later use. The original files are not in this repository.

The models are stored in a small binary format called VMD (`dualsense.vmd`, `xbox-one.vmd`): the points as 16 bit numbers in units of 0.005 mm, the triangles as 16 bit indexes, and one byte per triangle for the button it belongs to. That is 1.1 MB and 0.4 MB. Next to each there is a `-light.vmd` with 12,000 triangles, for a slow box. In Setup, the row 3D MODEL DETAIL switches between FULL (every triangle) and LIGHT (12,000 triangles). Since the next release LIGHT is the default, and FULL is a choice in Setup.

Credit lines are in the NOTICE file and in the README. The author of the DualSense model says there were no accurate blueprints, so it is close but not exact. Other controllers (and the Switch layout) use the DualSense model. The hand made shapes of 2.17 stay in the code as a last resort if a file cannot be read.

How it is drawn: one call per batch of 10,000 visible triangles (Canvas drawVertices), sorted far to near with a counting sort, shaded flat from the camera direction and one light with the theme colours. Normals and centres are worked out once when the model loads, and nothing is allocated per frame. The test box has never run it. Drawing 112,000 triangles every frame on a box with 1.4 GB of RAM may be slow, and nobody knows yet how slow. If it stutters, set 3D MODEL DETAIL to LIGHT. The app uses a painter's algorithm and not a depth buffer, so a model with this many triangles may show a wrong order of faces at some angles.

The real sizes the models are built around (dimensions.com):

| Controller | Width | Front to back | Thickness |
|---|---|---|---|
| DualSense | 160 mm | 106 mm | 66 mm |
| Xbox One | 152.9 mm | 101.9 mm | 61 mm |

## Using your own model

Put one OBJ file per controller in:

```
/sdcard/Android/data/com.nokoding.deck/files/models/dualsense.obj
/sdcard/Android/data/com.nokoding.deck/files/models/xbox-one.obj
```

Visor makes the folder when it starts. From Termux: `bash deck/tools/install-model.sh TVIP:PORT dualsense.obj` (it checks the file first). Visor reads the file each time the gyro test opens, and it wins over the models that ship in the app, so there is no reload step.

The format is a small part of Wavefront OBJ:

- `v x y z` lines for the points and `f a b c ...` lines for the faces. A face has 3 to 8 corners. Counting starts at 1. `a/b/c` is allowed and only `a` is used. Everything else is ignored.
- Units are centimeters. If the biggest size is over 40, the file is taken to be in millimeters and scaled by 0.1. A model over 400 cm in any direction is refused.
- Y is up out of the face of the controller, Z points toward the player, X to the right. The origin should be the middle of the body.
- Faces must point outward (counter-clockwise seen from outside) because Visor hides faces that look away.
- Limits: 1.5 MB, 24000 vertices, 24000 triangles (a quad counts as two). Faces are cut into triangles when the file is read.
- Your model is drawn as one piece in the theme colors. A line `g b4` before some faces tags them with a button number (see PadTest), which the app does not use yet.

A file with a bad number, a missing or negative index, a face with too few or too many corners, no faces, or one over a limit is ignored and the model that ships in the app is drawn. The gyro test never fails because of a model file.

## What could not be checked

Nothing in 2.17 has run on a TV. The model construction and the file reader are tested on a computer (every hardware face points out of its part, the sizes are near the real pads, hostile OBJ text is refused). How it looks, and whether it draws smoothly on the box, is not known. The painter's-algorithm drawing sorts faces by distance, so a model with many overlapping parts may show a wrong order at some angles.
