# Visor adb commands

Every adb command Visor can use, grouped by what it is for, with what it does and how to take it back. There are two ways to run them, and they are separate:

- **One at a time.** Each command is listed below, so you can run just the ones you want and know what each one does.
- **All in one go.** `deck/tools/setup-all.sh` runs the setup steps for you from Termux. It prints every command before it runs it, and it only does what is listed here. See [The one script](#the-one-script).

You do not need any of these to use Visor. They switch on the extras that Android does not let an app switch on by itself.

## Before you start

Run these in Termux on your phone, with the phone and the TV on the same Wi-Fi. TVIP:PAIRPORT is shown in the TV's "Pair device with pairing code" popup, and TVIP:PORT is on the main Wireless debugging screen. The port changes after a reboot. The full walkthrough is in [guide-termux.md](guide-termux.md).

```
pkg install -y android-tools
adb pair TVIP:PAIRPORT
adb connect TVIP:PORT
```

Everything below can be run as `adb shell ...` from Termux. The helper scripts take TVIP:PORT as their first argument and connect by themselves.

## The one script

```
bash deck/tools/setup-all.sh TVIP:PORT
```

It shows the plan, asks once, then runs the steps for these groups. Each group is a section below.

| Group | What it does | Section |
|---|---|---|
| access | Usage access, for the task manager and the system recents source | [Usage access](#usage-access) |
| overlay | The overlay service (PS menu over apps, remap and combos in apps, volume gestures in apps) | [The overlay service](#the-overlay-service) |
| display | Lets Visor change the display itself (experimental) | [Display access](#display-access) |
| saver | Makes Visor the TV's screensaver | [The screensaver](#the-screensaver) |
| home | Gives Visor the home role | [Home screen](#home-screen) |

The default is all five groups. It never turns off Google TV's launcher unless you add `--disable-google-launcher`, because that is the one step that can cause trouble.

```
bash deck/tools/setup-all.sh TVIP:PORT run --only access,overlay      # just those groups
bash deck/tools/setup-all.sh TVIP:PORT run --skip home,saver          # everything except those
bash deck/tools/setup-all.sh TVIP:PORT run --apk visor-launcher-v2.18-beta.apk       # install Visor first, then the steps
bash deck/tools/setup-all.sh TVIP:PORT run --saver-minutes 10         # screensaver after 10 idle minutes
bash deck/tools/setup-all.sh TVIP:PORT run --yes                      # do not ask
bash deck/tools/setup-all.sh TVIP:PORT status                         # only read, change nothing
bash deck/tools/setup-all.sh TVIP:PORT undo --only access,display     # take steps back
```

`run` is the default mode, so the first line above can be shortened as shown. `undo` takes back the same groups (the undo for each is listed below). The script stops with a message on a bad address or a bad option, and a group that fails is reported at the end and the others still run.

It was tested against a fake adb with good and bad input. The overlay and screensaver groups call the two scripts below, which were tested the same way. None of it has run against the TV.

## Install and update

```
adb install -r visor-launcher-v2.18-beta.apk
adb shell am start -n com.nokoding.deck/.HomeActivity
```

`install -r` keeps your settings, as long as the APK is signed with the same key.

Remove Visor: `adb uninstall com.nokoding.deck` (this clears its settings).

## Usage access

Lets Visor read Android's usage history. The control center's APPS section (the task manager) needs it, and so does the System usage recents source in Setup, APPS.

```
adb shell appops set com.nokoding.deck GET_USAGE_STATS allow
```

Check: `adb shell appops get com.nokoding.deck GET_USAGE_STATS`. Undo: `adb shell appops set com.nokoding.deck GET_USAGE_STATS default`. Details: [task-manager.md](task-manager.md).

## The overlay service

Switches on Visor's accessibility service. It gives you the PS menu over other apps ([ps-menu.md](ps-menu.md)), the task manager's FORCE STOP, the control center's Home, Back, Recents and screenshot tiles, button remap and combos outside Visor ([button-remap.md](button-remap.md), [combos.md](combos.md)), and touchpad and stick volume over other apps ([touchpad-volume.md](touchpad-volume.md), [stick-volume.md](stick-volume.md)). Android does not let you switch it on from the TV's own settings for a sideloaded app.

The script reads the TV's list of accessibility services, adds only Visor's entry, writes it back and reads it again, so anything else you run (TalkBack, a remote app) stays on:

```
bash deck/tools/enable-ps-overlay.sh TVIP:PORT on
bash deck/tools/enable-ps-overlay.sh TVIP:PORT status
bash deck/tools/enable-ps-overlay.sh TVIP:PORT off
```

By hand, only if nothing else is on (check first with `adb shell settings get secure enabled_accessibility_services`, which prints `null` when the list is empty):

```
adb shell settings put secure enabled_accessibility_services com.nokoding.deck/com.nokoding.deck.PsService
adb shell settings put secure accessibility_enabled 1
```

Off, by hand, only if Visor is the only service on:

```
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
```

That delete switches off every accessibility service on the TV. If the list holds anything else, use the script, or write the old value back without Visor's entry. Setup, PAD, SHOW ENABLE COMMANDS shows the right version for your TV.

## Display access

Lets Visor change the display size and density itself, for the VISOR DISPLAY game profile method. Experimental: whether Android 14 lets an app use it is not known until you try ([game-profiles.md](game-profiles.md)).

```
adb shell pm grant com.nokoding.deck android.permission.WRITE_SECURE_SETTINGS
```

Undo: `adb shell pm revoke com.nokoding.deck android.permission.WRITE_SECURE_SETTINGS`. This needs Visor 2.15 or newer: older builds did not list the permission in their manifest, and Android answers the grant with an error that the package has not requested it. Update first.

If the picture ever looks wrong after a game profile, from your phone (these also work with no grant):

```
adb shell wm size reset
adb shell wm density reset
```

Or ask Visor to put it back: `adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd restore_display`.

## Performance mode

Nothing to switch on with adb, but the full app list needs usage access (above). From your phone, to leave a game that is on screen and close it:

```
adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd exit_game
```

Details: [performance-mode.md](performance-mode.md).

## Game profiles from your phone

PERCENT is 85, 75, 67 or 50. METHOD is `game` (one app, game mode downscale) or `display` (the whole display).

```
bash deck/tools/game-profile.sh TVIP:PORT com.example.app on 75 game
bash deck/tools/game-profile.sh TVIP:PORT com.example.app off game
bash deck/tools/game-profile.sh TVIP:PORT com.example.app on 67 display
bash deck/tools/game-profile.sh TVIP:PORT com.example.app off display
bash deck/tools/game-profile.sh TVIP:PORT com.example.app status
```

By hand, the game mode method:

```
adb shell cmd game set --mode 4 --downscale 0.75 com.example.app
adb shell cmd game reset com.example.app
```

By hand, the display method (the whole display, until you reset it). The size is the panel size times PERCENT, never under 640x360 and the same shape as the panel. Read the panel's own values with `adb shell wm size` and `adb shell wm density`:

```
adb shell wm size 1440x810
adb shell wm density 240
adb shell wm size reset
adb shell wm density reset
```

## Force stop

A normal app cannot force stop another app, so this one is for your phone. It runs only `am force-stop PACKAGE`, after checking the package name, and refuses Visor's own package.

```
bash deck/tools/force-stop.sh TVIP:PORT com.example.app
adb shell am force-stop com.example.app
```

Details: [task-manager.md](task-manager.md).

## The screensaver

Makes Visor's own screensaver the TV's screensaver. MINUTES is optional, 1 to 120, and sets how long the TV must be idle first.

```
bash deck/tools/enable-screensaver.sh TVIP:PORT on 10
bash deck/tools/enable-screensaver.sh TVIP:PORT status
bash deck/tools/enable-screensaver.sh TVIP:PORT off
```

The commands it runs, one by one:

```
adb shell settings put secure screensaver_components com.nokoding.deck/com.nokoding.deck.DeckDream
adb shell settings put secure screensaver_enabled 1
adb shell settings put secure screensaver_activate_on_sleep 1
adb shell settings put system screen_off_timeout 600000
```

The last one is only for the idle time (600000 is 10 minutes in milliseconds). To read what is set: `adb shell settings get secure screensaver_components` and the same for the other three (`secure` for the first three, `system` for the timeout). Undo: `adb shell settings delete secure screensaver_components`, which leaves the other values as they are. Try it without waiting: `adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd preview_screensaver`. Details and what to do if it never starts: [screensaver.md](screensaver.md).

## Home screen

Google TV's own launcher wins the Home button while it is enabled. These steps make Visor the home app (the same steps as Setup, SYSTEM, HOME ADB COMMANDS):

```
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.nokoding.deck
adb shell cmd package set-home-activity com.nokoding.deck/.HomeActivity
```

On Google TV those two are usually not enough. To make Home open Visor you must also turn off Google TV's launcher. Do this only after Visor has run for a day without trouble and a reboot test passed. Visor's safe screen button that opens Google TV's home needs that launcher enabled, and some Google TV devices can need a factory reset if the launcher is turned off and nothing replaces it. `setup-all.sh` only does this with the explicit `--disable-google-launcher` flag.

```
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

Undo, to turn Google TV's launcher back on and give it the home role back:

```
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.apps.tv.launcherx
```

Or: `bash deck/tools/setup-all.sh TVIP:PORT undo --only home --restore-google-launcher`.

Check who has the home role: `adb shell cmd role get-role-holders android.app.role.HOME`. If Home opens a setup screen after this, re-enable it with `adb shell pm enable com.google.android.tungsten.setupwraith`.

## Packs and icons

Install a pack from Termux (connects, pushes, tells Visor to reload):

```
bash deck/tools/install-soundpack.sh TVIP:PORT mypack.zip
bash deck/tools/install-iconpack.sh TVIP:PORT myicons.zip
bash deck/tools/install-wallpaper.sh TVIP:PORT mywallpaper.zip
bash deck/tools/wallpaper-web.sh TVIP:PORT
bash deck/tools/install-theme.sh TVIP:PORT "NEON TEA.json"
bash deck/tools/export-theme.sh TVIP:PORT "NEON TEA"
bash deck/tools/install-model.sh TVIP:PORT dualsense.obj
```

Or push by hand, then reload (a folder is a pack as it is, a zip is unpacked by Visor):

```
adb push mypack /sdcard/Android/data/com.nokoding.deck/files/soundpacks/
adb push myicons /sdcard/Android/data/com.nokoding.deck/files/icons/
adb push mywallpaper /sdcard/Android/data/com.nokoding.deck/files/wallpapers/
adb push "NEON TEA.json" /sdcard/Android/data/com.nokoding.deck/files/themes/
adb push dualsense.obj /sdcard/Android/data/com.nokoding.deck/files/models/
adb push com.example.app.png /sdcard/Android/data/com.nokoding.deck/files/icons/
```

The last line is a single custom icon, named after the app's package. Open Visor once before you push, so its folders exist. Details: [sound-packs.md](sound-packs.md), [icon-packs.md](icon-packs.md), [wallpapers.md](wallpapers.md).

## deck_cmd

Visor accepts a short list of commands as a string extra. Anything else is ignored.

```
adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd NAME
```

| NAME | What it does |
|---|---|
| reload_packs | Unpacks new sound pack zips and reloads the sound pack |
| reload_icons | Reloads custom icons and icon packs |
| open_setup | Opens Visor's Setup |
| reset_remap | Clears every button remap and combo and turns the advanced switches off, even with the D-pad broken or in the safe screen |
| open_inputs | Opens the list of TV inputs |
| reload_wallpapers | Unpacks new wallpaper pack zips and reloads the wallpaper |
| preview_screensaver | Starts the screensaver preview inside Visor |
| restore_display | Puts the display size and density back to the panel's own values |
| open_tutorial | Opens the tutorial |
| exit_game | Brings Visor to the front and closes the game performance mode remembers, like EXIT GAME in the control center. Run it with the game on screen |
| reload_themes | Rescans the themes folder after a theme file was pushed, and says how many it found |

## Reading and checking

```
adb pull /sdcard/Android/data/com.nokoding.deck/files/controller-report.txt
adb shell pm list packages | grep nokoding
adb shell cmd role get-role-holders android.app.role.HOME
```

The first reads the report that Setup, PAD, Controller report writes. The others check that Visor is installed and who has the home role.
