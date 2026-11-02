# Install guide: I use only my phone

Same steps as [the laptop guide](guide-laptop.md). Use this one if you have an Android phone and no computer. The phone runs a terminal app called Termux, and it talks to the TV over Wi-Fi. About 20 minutes the first time.

Every command is in a code block, with one line on what it does. All the commands in one list: [adb-commands.md](adb-commands.md).

Visor Launcher is a beta. Read [KNOWN-ISSUES.md](../KNOWN-ISSUES.md) first. Nothing here can brick the TV, and every change has an undo, but you are the one running the commands.

## 1. Prepare the TV

You need an Android TV or Google TV box with Android 11 or newer for the phone route (Wireless debugging with pairing is an Android 11 feature), on the same Wi-Fi as the phone.

1. On the TV open Settings, System, About. Select the build entry ("Android TV OS build" or "Build") and press OK seven times. It says you are a developer.
2. Settings, System, Developer options. Turn on USB debugging and Wireless debugging.
3. Open Wireless debugging. Keep this screen handy: it shows the TV's IP address and port.

## 2. Install Termux and adb on the phone

Install Termux from F-Droid (https://f-droid.org/packages/com.termux/) or from its GitHub releases (https://github.com/termux/termux-app/releases). Do not use the Play Store version: it is old and does not work for this.

Open Termux and run:
```
pkg update -y && pkg upgrade -y
pkg install -y android-tools curl unzip
adb version
```
The first line updates Termux. The second installs adb, curl and unzip. The third prints a version number.

Get the Visor scripts (optional but handy):
```
curl -L -o visor-src.zip https://github.com/Nokoding/visor-launcher/archive/refs/heads/main.zip
unzip -q visor-src.zip
cd visor-launcher-main
```
The scripts are bash scripts in `deck/tools`. They only run the adb commands that are also written out below.

## 3. Pair and connect

Wireless debugging needs a one-time pairing step. The pairing port is not the connect port, and the connect port changes after every TV reboot. To read the TV's screen and type into Termux at the same time, use the split-screen mode of your phone or switch back and forth quickly: the pairing code expires.

1. On the TV open Wireless debugging, then "Pair device with pairing code". Leave the pop-up open. It shows the pairing address and port (for example 192.168.1.50:41234) and a six-digit code.
2. In Termux:
```
adb pair 192.168.1.50:41234
```
Type the six-digit code when it asks. It says "Successfully paired".
3. Close the pop-up. On the Wireless debugging screen read the IP address and port at the top (for example 192.168.1.50:37425). Connect:
```
adb connect 192.168.1.50:37425
adb devices
```
`adb devices` lists the TV as `device`. Below, this guide writes `TVIP:PORT` for that address.

## 4. Install the APK

1. Find the newest `visor-launcher-*.apk` at https://github.com/Nokoding/visor-launcher/releases . The beta is marked "Pre-release". GitHub's "latest release" link skips pre-releases, so use this list. Copy the link of the `.apk` file.
2. Download it in Termux and check it (the release notes show a SHA-256 line):
```
curl -L -o visor.apk https://github.com/Nokoding/visor-launcher/releases/download/v2.18-beta/visor-launcher-v2.18-beta.apk
sha256sum visor.apk
```
The `sha256sum` result must match the SHA-256 line in the release notes. If it does not, do not install it.
3. Install and start it:
```
adb -s TVIP:PORT install -r visor.apk
adb -s TVIP:PORT shell am start -n com.nokoding.deck/.HomeActivity
```
`install -r` installs or updates and keeps your settings. The app is named Visor Launcher, but its package is still `com.nokoding.deck`, which is why that name appears in the commands. The `-s TVIP:PORT` part says which device, which matters if your phone has more than one.

## 5. The one-time grants

Android does not let an app switch these on by itself. Each is optional. The easy way is one script that shows every command, asks once and runs them (from the `visor-launcher-main` folder):
```
bash deck/tools/setup-all.sh TVIP:PORT
```
Add `status` to only read what is set, `undo` to take steps back, `--only access,overlay` to pick groups. The same steps by hand, one at a time:

**Usage access** (lets the task manager list and close apps):
```
adb -s TVIP:PORT shell appops set com.nokoding.deck GET_USAGE_STATS allow
```
Undo: `adb -s TVIP:PORT shell appops set com.nokoding.deck GET_USAGE_STATS default`

**The overlay service** (the PS style menu over games, remap in other apps):
```
bash deck/tools/enable-ps-overlay.sh TVIP:PORT on
```
It reads the TV's list of accessibility services, adds only Visor, and writes it back, so anything else you have on stays on. Off again: `bash deck/tools/enable-ps-overlay.sh TVIP:PORT off`. The raw commands are in [adb-commands.md](adb-commands.md).

**Display access** (only for the experimental display method, which many boxes refuse):
```
adb -s TVIP:PORT shell pm grant com.nokoding.deck android.permission.WRITE_SECURE_SETTINGS
```
Undo: `adb -s TVIP:PORT shell pm revoke com.nokoding.deck android.permission.WRITE_SECURE_SETTINGS`

If the picture ever looks wrong after a game profile:
```
adb -s TVIP:PORT shell wm size reset
adb -s TVIP:PORT shell wm density reset
```

**A game profile from the phone** (the method that works on boxes that refuse the in-app one). PACKAGE is the game's package name, 75 is the percent of the full picture:
```
bash deck/tools/game-profile.sh TVIP:PORT PACKAGE on 75 display
bash deck/tools/game-profile.sh TVIP:PORT PACKAGE off display
```

## 6. Make Visor the home app

```
adb -s TVIP:PORT shell cmd role add-role-holder --user 0 android.app.role.HOME com.nokoding.deck
adb -s TVIP:PORT shell cmd package set-home-activity com.nokoding.deck/.HomeActivity
```
On Google TV this is usually not enough: the stock launcher still answers the Home button. Press Home and see. If the stock home appears, that is normal; Visor still works as an app and you can stop here.

## 7. Optional, last step: turn off the stock launcher

Only do this after Visor has run for a day without trouble and a restart test passed. If nothing else can answer Home, a mistake can leave you with no home screen. You can always undo it from the phone with the commands below, as long as Wireless debugging is still on.
```
adb -s TVIP:PORT shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```
Undo, to get the stock launcher back:
```
adb -s TVIP:PORT shell pm enable com.google.android.apps.tv.launcherx
adb -s TVIP:PORT shell cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.apps.tv.launcherx
```
If Home then opens a setup screen: `adb -s TVIP:PORT shell pm enable com.google.android.tungsten.setupwraith`.

## Updating, uninstalling, recovering

- **Update:** download the new APK as in step 4, check its SHA-256, then `adb -s TVIP:PORT install -r visor.apk`. Your settings stay. It only works over a build signed with the same key.
- **Uninstall:** `adb -s TVIP:PORT uninstall com.nokoding.deck` (this clears its settings).
- **A remap locked you out:** hold the Back button for 6 seconds. Or from the phone: `adb -s TVIP:PORT shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd reset_remap`. Or Setup, REMAP, RESET ALL REMAPS.
- **The picture looks wrong:** the two `wm ... reset` commands above, or `adb -s TVIP:PORT shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd restore_display`.
- **Visor keeps stopping:** it shows a safe screen with Try again, Restart and Reset. Pick Reset if it keeps happening.

## Troubleshooting

- **`adb: no devices/emulators found`:** not connected. Run `adb connect TVIP:PORT` again.
- **`device offline`:** run `adb disconnect`, then `adb connect TVIP:PORT`. If it still fails, turn Wireless debugging off and on on the TV.
- **`unauthorized`:** the TV is waiting for you to allow the phone. Look at the TV screen for "Allow USB debugging?" and press Allow.
- **Pairing says the code is wrong or expired:** the code lasts about a minute. Close the pop-up, open "Pair device with pairing code" again and use the new code and the new pairing port. Type quickly.
- **`connection refused` or it worked yesterday and not today:** the wireless debugging port changes after a reboot or when you turn the setting off. Read the new port on the Wireless debugging screen.
- **`adb: more than one device/emulator`:** several devices are connected. Use `-s TVIP:PORT` as in the commands above, or `adb disconnect` the ones you do not need.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE` or "signatures do not match":** the installed build was signed with another key. Uninstall first, then install.
- **`INSTALL_FAILED_VERSION_DOWNGRADE`:** the installed version is newer. Uninstall first, or install a newer APK.
- **Termux says `Permission denied` for a script:** run it with `bash deck/tools/NAME.sh ...`, not by name.
- **A command prints `Security exception` or "has not requested permission":** the installed build is too old for that step. Update first.
