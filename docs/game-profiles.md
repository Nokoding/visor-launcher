# Game profiles

New in 2.8. A game profile lowers the picture resolution for one heavy app and puts it back when you are done. Setup, SYSTEM, GAME PROFILES.

![A game profile, design mock](img/preview-game-profile.png)

![The keep or revert prompt, design mock](img/preview-keep-revert.png)

Design mocks, not screenshots. Nothing in 2.8 has run on a TV.

## Read this first

- This box has 1.4 GB of RAM. A lower resolution helps the GPU and the fill rate, which is what a heavy game is usually short of. It does **not** help memory. Visor does not claim any game will run well at a lower setting.
- Roblox is the usual example. It is not an official Android TV app, so how it behaves on this box is its own business and not known here.
- Android 14 has no per app display density. What exists is below, and Visor offers each one for what it is.

## The four methods

| Method | Who runs it | What it changes |
|---|---|---|
| GAME MODE | You, from adb or the phone helper. Visor cannot run it | One app only. Android's game mode downscale |
| VISOR DISPLAY | Visor itself, after one adb grant. Experimental | The whole display, only while the app is in front |
| PHONE HELPER | You, with `deck/tools/game-profile.sh` | The whole display, until you run off |
| COPY COMMANDS | You, by hand | Nothing. Visor only shows the commands |

**GAME MODE.** Read from the Android 14 source: `cmd game set --mode 4 --downscale 0.75 PACKAGE` sets a custom game mode with a downscale, and `cmd game reset PACKAGE` removes it. It needs the permission MANAGE_GAME_MODE, which only the adb shell has, so an app cannot do it. Android ignores it for an app that does not declare itself a game (appCategory game), and the scale is applied when the game starts. It never touches the display, so it is the safest. Whether Roblox declares itself a game is not known.

**VISOR DISPLAY.** Visor sets the display size and density the way `wm size` and `wm density` do. In the Android 14 source the four window manager calls (set and clear, for size and for density) each check only that the caller holds WRITE_SECURE_SETTINGS. An app gets that permission only from this adb command, once (Visor 2.15 or newer, which lists the permission in its manifest, installing does not give it):

```
adb shell pm grant com.nokoding.deck android.permission.WRITE_SECURE_SETTINGS
```

What was **not** checked is whether Android 14's hidden API rules let an ordinary app reach the window manager by reflection. Visor tries inside a try block and, if it fails, the profile row says why and the commands are shown instead. Setup, GAME PROFILES, DISPLAY ACCESS says whether the grant is there.

**PHONE HELPER and COPY COMMANDS.** From Termux:

```
bash deck/tools/game-profile.sh TVIP:PORT PACKAGE on 75 game
bash deck/tools/game-profile.sh TVIP:PORT PACKAGE off game
bash deck/tools/game-profile.sh TVIP:PORT PACKAGE on 67 display
bash deck/tools/game-profile.sh TVIP:PORT PACKAGE off display
bash deck/tools/game-profile.sh TVIP:PORT PACKAGE status
```

PERCENT is 85, 75, 67 or 50. The display method reads the panel's own size and density with `wm`, scales both, and refuses anything under 640x360 or not the panel's shape. Nothing else is touched.

## Setting one up

Setup, SYSTEM, GAME PROFILES, ADD A PROFILE, pick the app. Then:

- SCALE: 100, 85, 75, 67 or 50 percent, each shown with the size and density it gives.
- METHOD: the four above.
- AUTO RESTORE: on by default. The display goes back after the app has been gone 8 seconds, so a quick visit to the PS menu does not flip it.
- LAUNCH WITH PROFILE: starting the app from Visor applies the profile first (for VISOR DISPLAY), asks to keep it, waits, then starts the app.
- ADB COMMAND and FROM A PHONE show the exact lines.

An app with a profile also has a GAME PROFILE action in the control center's APPS page, and DISPLAY, GAME PROFILE turns the profile of the app in front on or off. The remap action TOGGLE GAME PROFILE can be given to a button (Setup, REMAP), which uses the remap safety rules, so it cannot trap you. It is off until you set it.

The VISOR DISPLAY and GAME MODE methods call hidden parts of Android. The onn box refuses those calls, and the profile then says THIS TV BOX HIDES THE CALL. If you see that, use the PHONE HELPER method: `bash deck/tools/game-profile.sh 192.168.1.50:5555 PACKAGE on 75 display` to turn it on and `... off display` to turn it off (use your own address). It runs from Termux, so it does not need the call.

## Safety

A wrong size or density can make the TV hard to use, so:

- Values are checked: density 120 to 640, size at least 640x360, the panel's own shape, never bigger than the panel.
- Visor saves the **original** size and density before the first change. If a change is already in place and nothing was saved, it refuses and asks you to restore first.
- The first time a value is applied, a prompt asks KEEP IT (A or OK) or PUT IT BACK (B or Back). Without an answer in 15 seconds it puts it back by itself. The prompt shows in front of Visor, or over the app when the overlay service is on.
- Restore happens: when the app has been gone past the grace time, when Visor starts, when the overlay service starts, from RESTORE DISPLAY (Setup and the control center), from the command below, and from the prompt.
- Restore does not read the saved file. It clears the override, so a damaged settings file cannot stop it.
- Changing the size restarts the app's screen. That is normal Android behaviour.

If the picture ever looks wrong, from your phone:

```
adb shell wm size reset; adb shell wm density reset
```

or `adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd restore_display`.

## What could not be checked

- Whether this box's Android lets Visor reach the window manager (VISOR DISPLAY), and whether `cmd game` works on it.
- Whether Roblox declares itself a game.
- How the keep prompt looks and whether A and B reach it over a game through the overlay service.
- That the display really comes back after Visor is killed. The marker is written first and read at the next start, but this was only read through, never run.

## Game bundle and method order (2.17)

**Bundle.** A profile now carries two more settings next to the scale:

| Row | What it does |
|---|---|
| PERFORMANCE MODE FOR THIS GAME | FOLLOW GLOBAL (default), ALWAYS ON or ALWAYS OFF. ALWAYS ON closes the other apps when this game comes to the front even when performance mode is off, ALWAYS OFF never does |
| VOLUME FOR THIS GAME | NO CHANGE (default) or 10 to 100 percent of the music volume. It is set when the game comes to the front and the old volume is put back when another app takes over, or when you return to Visor. Visor's own PS menu and the system bar over the game do not count as leaving |

It works with any method, even COPY COMMANDS, because it does not touch the display. It needs the overlay service when the game is not started from Visor. There is no separate "exit chord" setting: EXIT GAME is a remap action you give to a button or a combo ([button-remap.md](button-remap.md)), and it works for every game. What could not be checked: the volume restore when a game crashes or is killed (it is put back the next time Visor sees another app in front).

**Method order.** New profiles use the PHONE HELPER method, because VISOR DISPLAY is refused on the onn box (the status says THIS TV BOX HIDES THE CALL). Visor remembers that refusal and marks VISOR DISPLAY as BLOCKED ON THIS BOX in the METHOD row. VISOR DISPLAY stays available as an advanced, experimental choice. The row FROM A PHONE (TERMUX) now comes before the ADB COMMAND row and shows the exact `game-profile.sh` command.
