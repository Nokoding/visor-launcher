# Install guide: I use a laptop

Same steps as [the phone guide](guide-termux.md). Use this one if you have a Windows, macOS or Linux computer. About 15 minutes the first time.

Every command is in a code block, with one line on what it does. All the commands in one list: [adb-commands.md](adb-commands.md).

Visor Launcher is a beta. Read [KNOWN-ISSUES.md](../KNOWN-ISSUES.md) first. Nothing here can brick the TV, and every change has an undo, but you are the one running the commands.

## 1. Prepare the TV

You need an Android TV or Google TV box with Android 9 or newer, on the same Wi-Fi as the computer.

1. On the TV open Settings, System, About. Select the build entry ("Android TV OS build" or "Build") and press OK seven times. It says you are a developer.
2. Settings, System, Developer options. Turn on USB debugging. Turn on Wireless debugging (on Android 11 and newer; on Android 9 and 10 there is a "ADB over network" switch instead).
3. Write down the TV's IP address (Settings, Network). It looks like 192.168.1.50.

## 2. Install adb on the computer

adb is the tool that talks to the TV. It comes in Google's "platform-tools" download. No account is needed.

**Windows**
1. Download https://dl.google.com/android/repository/platform-tools-latest-windows.zip
2. Right click, Extract All, to `C:\platform-tools`.
3. Open the Start menu, type "Edit the system environment variables", Environment Variables, select Path under your user, Edit, New, `C:\platform-tools`, OK. Open a new Command Prompt.
4. Check it:
```
adb version
```
It prints a version number. If it says "not recognized", the Path step did not take: close the window and open a new one, or run `C:\platform-tools\adb` in place of `adb` below.

**macOS**
```
curl -LO https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
unzip platform-tools-latest-darwin.zip -d ~
echo 'export PATH="$PATH:$HOME/platform-tools"' >> ~/.zshrc
```
Open a new Terminal window, then run `adb version`.

**Linux**
```
curl -LO https://dl.google.com/android/repository/platform-tools-latest-linux.zip
unzip platform-tools-latest-linux.zip -d ~
echo 'export PATH="$PATH:$HOME/platform-tools"' >> ~/.bashrc
```
Open a new terminal, then run `adb version`. (Most distributions also have an `adb` or `android-tools-adb` package.)

## 3. Pair and connect

Wireless debugging needs a one-time pairing step. The pairing port is not the connect port, and the connect port changes after every TV reboot.

1. On the TV open Wireless debugging, then "Pair device with pairing code". Leave the pop-up open. It shows an address and port (the pairing address, for example 192.168.1.50:41234) and a six-digit code.
2. On the computer:
```
adb pair 192.168.1.50:41234
```
Type the six-digit code when it asks. It says "Successfully paired".
3. Close the pop-up. On the Wireless debugging screen, read the IP address and port shown at the top (for example 192.168.1.50:37425). Connect to that one:
```
adb connect 192.168.1.50:37425
adb devices
```
`adb devices` lists the TV as `device`. Below, this guide writes `TVIP:PORT` for that address.

If you have an Android 9 or 10 box, plug in USB or use `adb connect TVIP:5555` after turning on "ADB over network".

## 4. Install the APK

1. Download the newest `visor-launcher-*.apk` from https://github.com/Nokoding/visor-launcher/releases . The beta is marked "Pre-release". The latest-release link on GitHub skips pre-releases, so use this list.
2. Check the download is the one that was published. The release notes show a SHA-256 line.
   - Windows: `certutil -hashfile visor-launcher-v2.18-beta.apk SHA256`
   - macOS: `shasum -a 256 visor-launcher-v2.18-beta.apk`
   - Linux: `sha256sum visor-launcher-v2.18-beta.apk`
   The result must match the line in the notes. If it does not, do not install it.
3. Install and start it:
```
adb install -r visor-launcher-v2.18-beta.apk
adb shell am start -n com.nokoding.deck/.HomeActivity
```
`install -r` installs or updates and keeps your settings. The app is named Visor Launcher, but its package is still `com.nokoding.deck`, which is why that name appears in the commands.

## 5. The one-time grants

Android does not let an app switch these on by itself. Each is optional. Skip any you do not want.

**Usage access** (lets the task manager list and close apps):
```
adb shell appops set com.nokoding.deck GET_USAGE_STATS allow
```
Undo: `adb shell appops set com.nokoding.deck GET_USAGE_STATS default`

**The overlay service** (the PS style menu over games, remap in other apps). First look at what is already on:
```
adb shell settings get secure enabled_accessibility_services
```
If it prints `null`, nothing else is on, so:
```
adb shell settings put secure enabled_accessibility_services com.nokoding.deck/com.nokoding.deck.PsService
adb shell settings put secure accessibility_enabled 1
```
If it prints something, keep that text: put it first, then a colon, then `com.nokoding.deck/com.nokoding.deck.PsService`, in the first command. Off again: put the old text back (or `adb shell settings delete secure enabled_accessibility_services` if Visor was the only one).

**Display access** (only for the experimental display method, which many boxes refuse):
```
adb shell pm grant com.nokoding.deck android.permission.WRITE_SECURE_SETTINGS
```
Undo: `adb shell pm revoke com.nokoding.deck android.permission.WRITE_SECURE_SETTINGS`

If the picture ever looks wrong after a game profile:
```
adb shell wm size reset
adb shell wm density reset
```

**The helper scripts.** The folder `deck/tools` of this repository has bash scripts that run the steps above for you (for example `bash deck/tools/setup-all.sh TVIP:PORT`). Download the repository as a zip from the green Code button if you want them. On Windows they need Git Bash or WSL. They are optional: every script only runs the adb commands listed here and in [adb-commands.md](adb-commands.md).

## 6. Make Visor the home app

```
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.nokoding.deck
adb shell cmd package set-home-activity com.nokoding.deck/.HomeActivity
```
On Google TV this is usually not enough: the stock launcher still answers the Home button. Press Home and see. If the stock home appears, that is normal; Visor still works as an app and you can stop here.

## 7. Optional, last step: turn off the stock launcher

Only do this after Visor has run for a day without trouble and a restart test passed. If nothing else can answer Home, a mistake can leave you with no home screen. You can always undo it with adb from the computer.
```
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```
Undo, to get the stock launcher back:
```
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.apps.tv.launcherx
```
If Home then opens a setup screen: `adb shell pm enable com.google.android.tungsten.setupwraith`. Check who has Home: `adb shell cmd role get-role-holders android.app.role.HOME`.

## Updating, uninstalling, recovering

- **Update:** download the new APK, check its SHA-256 as above, then `adb install -r visor-launcher-NEW.apk`. Your settings stay. It only works over a build signed with the same key.
- **Uninstall:** `adb uninstall com.nokoding.deck` (this clears its settings).
- **A remap locked you out:** hold the Back button for 6 seconds. Or from the computer: `adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd reset_remap`. Or Setup, REMAP, RESET ALL REMAPS.
- **The picture looks wrong:** the two `wm ... reset` commands above, or `adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd restore_display`.
- **Visor keeps stopping:** it shows a safe screen with Try again, Restart and Reset. Pick Reset if it keeps happening.

## Troubleshooting

- **`adb: no devices/emulators found`:** not connected. Run `adb connect TVIP:PORT` again.
- **`device offline`:** run `adb disconnect`, then `adb connect TVIP:PORT`. If it still fails, turn Wireless debugging off and on on the TV.
- **`unauthorized`:** the TV is waiting for you to allow the computer. Look at the TV screen for "Allow USB debugging?" and press Allow (tick "Always").
- **Pairing says the code is wrong or expired:** the code lasts about a minute. Close the pop-up, open "Pair device with pairing code" again and use the new code and the new pairing port.
- **`connection refused` or it worked yesterday and not today:** the wireless debugging port changes after a reboot or when you turn the setting off. Read the new port on the Wireless debugging screen.
- **`adb: more than one device/emulator`:** several devices are connected. Add `-s TVIP:PORT` after `adb`, for example `adb -s 192.168.1.50:37425 shell ...`, or `adb disconnect` the ones you do not need.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE` or "signatures do not match":** the installed build was signed with another key. Uninstall it first (`adb uninstall com.nokoding.deck`), then install.
- **`INSTALL_FAILED_VERSION_DOWNGRADE`:** the installed version is newer. Uninstall first, or install a newer APK.
- **A command prints `Security exception` or "has not requested permission":** the installed build is too old for that step. Update first.
