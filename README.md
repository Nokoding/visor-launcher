# Visor Launcher

**The Android launcher for gamers**

> Public beta. The full README with pictures comes in the next push. Start here: [I use a laptop](docs/guide-laptop.md) or [I use only my phone](docs/guide-termux.md). Questions: [FAQ](docs/faq.md). What is not verified: [KNOWN-ISSUES.md](KNOWN-ISSUES.md).

Visor Launcher is a launcher for Android TV and Google TV boxes with a game controller in mind. It has no INTERNET permission.

## Guides

- [docs/guide-laptop.md](docs/guide-laptop.md): install with a Windows, macOS or Linux computer.
- [docs/guide-termux.md](docs/guide-termux.md): install with only an Android phone.
- [docs/faq.md](docs/faq.md): the FAQ.

## Documents

- [docs/adb-commands.md](docs/adb-commands.md): every adb command Visor can use.
- [docs/tutorial.md](docs/tutorial.md): the tutorial.
- [docs/control-center.md](docs/control-center.md): the PS style menu as a control center.
- [docs/ps-menu.md](docs/ps-menu.md): the menu over other apps.
- [docs/task-manager.md](docs/task-manager.md): the task manager.
- [docs/performance-mode.md](docs/performance-mode.md): performance mode and EXIT GAME.
- [docs/game-profiles.md](docs/game-profiles.md): game profiles.
- [docs/button-remap.md](docs/button-remap.md): button remap.
- [docs/combos.md](docs/combos.md): combos.
- [docs/touchpad-volume.md](docs/touchpad-volume.md): touchpad volume.
- [docs/stick-volume.md](docs/stick-volume.md): stick volume.
- [docs/gamepad-test.md](docs/gamepad-test.md): the gamepad and gyro tests.
- [docs/gyro-models.md](docs/gyro-models.md): the 3D controller models.
- [docs/animations.md](docs/animations.md): animations.
- [docs/screensaver.md](docs/screensaver.md): the screensaver.
- [docs/themes.md](docs/themes.md): themes.
- [docs/wallpapers.md](docs/wallpapers.md): wallpapers.
- [docs/sound-packs.md](docs/sound-packs.md): sound packs.
- [docs/icon-packs.md](docs/icon-packs.md): icon packs.

## Commands

All adb commands are in [docs/adb-commands.md](docs/adb-commands.md). One script runs the setup steps in one go: `deck/tools/setup-all.sh`.

Visor commands (sent with `adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd NAME`):

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

## Contributing and licence

See [CONTRIBUTING.md](CONTRIBUTING.md). Code: MIT ([LICENSE](LICENSE)). Documentation and images: CC BY 4.0 ([LICENSE-DOCS.md](LICENSE-DOCS.md)). Fonts: SIL Open Font License ([NOTICE](NOTICE)).

Visor Launcher is not affiliated with or endorsed by Google, Sony, Microsoft or Valve.
