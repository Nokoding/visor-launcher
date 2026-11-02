# FAQ

Short answers. Visor Launcher is a public beta, so a lot of these end with "not tested". That is on purpose.

## What is Visor Launcher?

A launcher (home screen) for Android TV and Google TV boxes, made with a game controller in mind. It has a wheel and a grid for your apps, a PS style menu you can open over a game, performance mode, game profiles, stick volume, a gamepad test, themes, wallpapers, a screensaver and a tutorial. The tagline is "The Android launcher for gamers".

## Is it safe?

It has no INTERNET permission, so it cannot send anything off your TV. You can check: `aapt2 dump badging visor-launcher-*.apk` lists the permissions, and the README lists them too. The code is open and builds with plain tools.

The optional **accessibility service** (the "overlay service", off until you switch it on) is how the PS style menu can appear over other apps. While it is on, Android lets it see key presses and controller movement, which it needs to spot the PS button and to run your remaps, combos and volume gestures. It also notes which app is in front, only to skip apps you told it to leave alone. It does not read what is on the screen. The one exception is the task manager's experimental Force stop, which you start yourself and which presses the buttons on an app's App Info page. It stores nothing about the apps you use. You can turn it off any time.

## Which devices work?

It was tried on one box: an onn. Full HD Streaming Device (Google TV, Android 14, 1.4 GB RAM) with a DualSense controller. Everything else is unknown. Android 9 or newer is the target (minSdk 28), but only newer versions were used.

## Do I need a controller?

No. The remote works for browsing. The controller-only parts are the PS style menu, stick volume, touchpad volume, the gamepad and gyro tests and the combos. A DualSense and an Xbox style controller are the ones it was built around.

## Does it work with Google TV and Android TV?

It is built for both. On Google TV the stock launcher still answers the Home button unless you turn it off (see below), so on Google TV Visor is more of an app you open than a replacement home screen until you do the optional last step.

## Can I go back to the stock launcher?

Yes. Uninstall Visor (`adb uninstall com.nokoding.deck`). If you turned the stock launcher off, turn it back on first: `adb shell pm enable com.google.android.apps.tv.launcherx` and give it the home role again. The guides ([laptop](guide-laptop.md), [phone](guide-termux.md)) have the exact commands.

## How do I update?

Download the new APK from the Releases page, check its SHA-256, and run `adb install -r` on it. Your settings stay. It only works over a build signed with the same key.

## Why is adb needed?

Android does not let an ordinary app switch on its own accessibility service, read the usage history of other apps, or change the display. Those things need a one-time command that only adb (or a computer or phone with developer access) can give. You do them once. Visor works without them, with fewer features.

## Why does Home still open the Google TV home screen?

Google TV's own launcher answers the Home button as long as it is enabled. To change that you can turn it off with one adb command. That step is optional and the last one in the guides, because if nothing else answers Home you can end up without a home screen until you undo it. The undo is in the guides.

## What happens if a remap locks me out?

Hold the Back button for 6 seconds, anywhere. It clears the D-pad, OK and Back remaps. Or, from adb: `adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd reset_remap`. Or Setup, REMAP, RESET ALL REMAPS. Some buttons (Power, the PS button, game buttons) cannot be remapped at all, and Home needs extra warnings and has its own five-press escape.

## Does it work on Fire TV or Nvidia Shield?

Unknown. Nobody has tried. It might, it might not. Please tell us if you do.

## Why no internet permission, and how do wallpapers, themes and packs get on the TV?

Because a launcher that cannot go online cannot leak anything, and does not need an update server. Wallpapers, themes, sound packs and icon packs are files that you push to the TV with adb from a computer or a phone. There are small scripts for it, and there is a page that runs on your phone (not on the TV) for sending wallpapers from its browser. See [wallpapers.md](wallpapers.md), [themes.md](themes.md), [sound-packs.md](sound-packs.md) and [icon-packs.md](icon-packs.md).

## How much battery, RAM or CPU does it use?

Not measured properly. The target box has 1.4 GB of RAM, so the code keeps one decoded wallpaper at a time and frees things when you leave. Video wallpapers are experimental and may not suit a box that small. If you measure it, please share the numbers.

## How do I report a bug?

Open an issue with the bug report form on GitHub. Say which TV or box, which controller, which Android version, and what you pressed.

## What is the licence?

The code is MIT. The docs and images are CC BY 4.0. The fonts keep the SIL Open Font License. See [NOTICE](../NOTICE).

## Is it related to Steam Deck?

No. Visor is not related to the Steam Deck, the Valve handheld, or to any hardware or software that is also called Visor (for example a low vision magnifier app and an AR headset company use the same word). The word "Launcher" in the name is what sets this project apart. It is also not affiliated with or endorsed by Google, Sony, Microsoft or Valve.

## Why is the package called com.nokoding.deck?

The project was called Deck before it was called Visor. The package name stays so that older installs update in place.

## Can I make my own theme, sound pack or icon pack?

Yes. [themes.md](themes.md) explains the theme editor and the theme file. [sound-packs.md](sound-packs.md), [icon-packs.md](icon-packs.md) and [wallpapers.md](wallpapers.md) explain the packs.

## Why is it a beta?

Almost everything was written and tested on a computer, and tried on one box. Several features were never tried on a real TV, and a few things were found not to work on that box. [KNOWN-ISSUES.md](../KNOWN-ISSUES.md) lists what, honestly.
