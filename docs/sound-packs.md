# Visor sound packs

Visor ships with a built-in sound set. It's made by code when Visor starts, so the APK has no audio files. You can replace any of the sounds with your own files.

Icon packs work the same way: a zip or a folder, the same safety rules, a Termux script and a reload command. See [icon-packs.md](icon-packs.md).

## Where packs go

Each pack is its own folder here, or a .zip dropped into the same place:

```
/sdcard/Android/data/com.nokoding.deck/files/soundpacks/<packname>/
/sdcard/Android/data/com.nokoding.deck/files/soundpacks/<packname>.zip
```

Visor creates the `soundpacks` folder the first time it runs and puts a short README.txt in it. Open Visor once before you push a pack.

## Zips

The easy way is a zip. Visor unpacks it by itself when it starts and when you pick Reload packs.

- The files can sit at the root of the zip, or all inside one folder. Root files make a pack named after the zip (`Arcade.zip` becomes `Arcade`). One folder inside makes a pack named after that folder.
- Only `.wav`, `.ogg`, `.mp3` and `pack.json` are copied. Everything else is skipped, including `__MACOSX` and dot files like `.DS_Store`. (Icon packs copy only `.png` and `.webp`.)
- Limits: 64 files, 1 MB per sound, 64 KB for pack.json, 16 MB unpacked in total, 32 MB for the zip itself.
- A zip with a path that tries to leave the folder (`../`, a leading `/`, a drive letter) is refused as a whole. Nothing from it is written.
- Two folders in one zip, or folders inside folders, are refused too. One pack per zip.
- Visor unpacks into a hidden temp folder and renames it into place at the end, so a failed install never leaves half a pack behind.
- Visor keeps the zip and writes a small `.deck-source` file into the pack folder, so it does not unpack the same zip again. Push a changed zip with the same name and Visor installs it over the old folder.

If a zip fails, Setup shows why under SOUND > Zip status and on the Install pack screen.

## Install pack screen

Setup > SOUND > Install pack lists every zip and every pack folder Visor can see. Pick one with OK or A:

- a zip that is new (or changed) gets unpacked and selected
- a pack folder, or BUILT-IN, gets selected right away

## Install from your phone (Termux, one command)

`deck/tools/install-soundpack.sh` does the connect, the push and the reload in one go:

```
pkg install -y android-tools
bash install-soundpack.sh TVIP:PORT ~/storage/downloads/Arcade.zip
```

A folder works too: `bash install-soundpack.sh TVIP:PORT mypack/`. The script checks the address and the name, connects, pushes to Visor's folder, then runs:

```
adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd reload_packs
```

That brings Visor to the front and tells it to unpack and reload. When exactly one new pack comes in this way, Visor also switches to it. Visor only accepts `reload_packs` and `reload_icons` in that extra and ignores anything else. There is no broadcast receiver for it. The icon pack script, `install-iconpack.sh`, is the same with `reload_icons`.

## File names

Name a file after the event it should play for and it just works. Use `.wav`, `.ogg` or `.mp3`. Case doesn't matter.

| File name | When it plays |
|---|---|
| move | focus moves in the dock, top bar, grid or a menu |
| wheel_tick | each wheel step (D-pad, stick dial, shoulder buttons) |
| select | OK or A on a menu item |
| back | Back or B |
| menu_open | a menu or the setup panel opens |
| menu_close | a menu closes |
| lock_on | the reticle locks onto the focused item |
| launch | an app opens |
| error | something failed (app won't open, dock full) |
| mode_gamepad | you pick up the gamepad |
| mode_remote | you go back to the remote |
| toggle | a setting changes |
| pack_loaded | a pack finished loading |
| test_complete | the gamepad test (FULL mode) saw every required button (new in 2.2) |
| type_tick | one tick while menu text types in, per 2 characters per row, at most 40 a second (new in 2.3). Visor plays it with a small random pitch change. Keep it very short, under 15 ms |
| flyout_open | the options flyout opens (hold OK or X on an app) (new in 2.3) |
| flyout_close | the options flyout closes (new in 2.3) |

Any event your pack leaves out keeps Visor's built-in sound, so a pack with only `wheel_tick.wav` is fine.

The three 2.3 sounds follow Setup, ANIMATIONS, OPTIONS FLYOUT, SOUND as well as Mute and Volume. With that SOUND off nothing ticks, and the flyout plays menu_open and menu_close instead. More in [animations.md](animations.md).

Keep each file under 1 MB. Visor skips bigger files, because the box only has about 450 MB free. Short clips (under half a second) feel best.

## Optional pack.json

Put a `pack.json` in the pack folder to use other file names or change the volume per event:

```json
{
  "name": "Arcade",
  "map": { "move": "blip.wav", "error": "buzz.ogg" },
  "volume": { "move": 0.6, "wheel_tick": 0.4 }
}
```

- `name` is shown in Setup.
- `map` points an event at a file in the same folder. Paths with `/` or `..` are ignored.
- `volume` runs from 0.0 to 1.0. Events not listed play at 1.0. The master volume in Setup still applies on top.
- If the JSON is broken, Visor ignores it and still uses the event file names.

## Install by hand with adb

```
adb connect TV_IP:PORT
adb shell mkdir -p /sdcard/Android/data/com.nokoding.deck/files/soundpacks
adb push mypack /sdcard/Android/data/com.nokoding.deck/files/soundpacks/
```

That copies the local folder `mypack` to `.../soundpacks/mypack/`. To check it landed:

```
adb shell ls /sdcard/Android/data/com.nokoding.deck/files/soundpacks/mypack
```

## Choose it and reload in Setup

Open Setup (Menu, hold Back, or Start/Options on a gamepad) and go to the SOUND tab:

- **Sound pack**: left or right picks a pack folder, or BUILT-IN.
- **Volume** and **Mute**
- **Install pack**: the list of zips and packs described above.
- **Reload packs**: run this after you push new files. It also unpacks new zips.
- **Test sounds**: plays every event so you can check the mapping. Each row shows which file it uses, or BUILT-IN.
- **Pack status**: shows the pack name and how many of the 17 events it found.
- **Zip status**: the result of the last zip install.

## Removing a pack

Pick BUILT-IN or another pack in Setup first, then delete the folder and the zip:

```
adb shell rm -r "/sdcard/Android/data/com.nokoding.deck/files/soundpacks/Arcade"
adb shell rm "/sdcard/Android/data/com.nokoding.deck/files/soundpacks/Arcade.zip"
```

Delete both. If the zip stays, Visor unpacks it again on the next scan. If the active pack is gone, Visor plays the built-in sounds and Pack status says MISSING.

Uninstalling Visor deletes this folder along with the app's other data.

## Troubleshooting

- **A sound didn't change.** The file name doesn't match an event in the table, or the file is over 1 MB. Test sounds shows which file each event uses.
- **The pack isn't in the list.** Pick Reload packs, then look at Zip status for the error.
- **"NO SOUND FILES", "PUT FILES AT THE ROOT OR IN ONE FOLDER", "UNSAFE PATH IN ZIP".** Same meaning as for icon packs. Re-zip with the files at the root or in one folder.
- **No sound at all.** Check Mute and Volume in Setup, and the TV volume.

## Why not read the Downloads folder?

It would be nice to drop a zip into Downloads on the TV and have Visor find it. On Android 11 and newer an app can only list other apps' files in Downloads with the all files permission (MANAGE_EXTERNAL_STORAGE), or by asking the user to pick a file through the system picker. The all files permission gives Visor far more access than a launcher should have, so 2.1 does not ask for it. A system picker button could come later. Its catch: many Android TV builds ship a limited document picker or none at all, and nobody has checked the onn box yet. So adb push stays the main route.
