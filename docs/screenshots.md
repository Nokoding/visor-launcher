# Screenshots wanted

The pictures in the README, the docs and the website are **rendered previews**: they are drawn from the project's own layout numbers with the HTML sources in `deck/tools/preview-src` (rendered with `deck/tools/render.js`). They are not photos or screenshots of a TV, and they use made-up apps. Real screenshots are welcome and will replace them.

## How to take them

With adb connected to the TV (see the [laptop guide](guide-laptop.md) or the [phone guide](guide-termux.md)):

```
bash deck/tools/screenshots.sh TVIP:PORT
```

It lists the screens below one at a time, tells you what to open on the TV, and saves `NAME.png` into `visor-screenshots`. By hand, for one screen: `adb exec-out screencap -p > NAME.png`. `bash deck/tools/screenshots.sh --list` prints the list without connecting.

Some apps and some TVs block screenshots. If the file is tiny, that is the reason.

## The screens

| File name | What to show |
|---|---|
| home-wheel | The home screen in WHEEL layout |
| home-grid | The home screen in GRID layout |
| setup-help | Setup with a row focused, the help line in the footer |
| control-center-quick | The control center, QUICK section |
| control-center-apps | The control center, APPS section (the task manager) |
| menu-over-game | The control center opened over a game |
| game-profile | Setup, SYSTEM, GAME PROFILES, one app |
| remap | Setup, REMAP |
| remap-learn | LEARN A BUTTON |
| theme-editor | Setup, LOOK, THEME EDITOR |
| wallpaper | The home screen with a wallpaper |
| screensaver | The screensaver preview |
| gamepad-test | The gamepad test with a controller and some buttons pressed |
| gyro-test | The gyro test |
| tutorial | A tutorial lesson |

## Before you share one

Look at the picture. Your own app names, account names and Wi-Fi names can show up. Crop or retake it if you do not want them public. Do not send pictures of other people's logos as part of a pull request unless they are clearly yours to share. Open a pull request that adds the PNG (or WebP) under `docs/images/` with a short alt text, or attach them to an issue.
