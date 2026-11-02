# Visor themes: the theme editor and sharing

New in 2.17. Visor has six built-in themes and a CUSTOM one. The theme editor lets you change every main color of CUSTOM, check that it is readable, and save it as a small file you can share with someone else.

## The editor

Setup, LOOK, THEME EDITOR. The page has:

| Row | What it does |
|---|---|
| COLOR | Which of the five colors you change: ACCENT, SECOND ACCENT, BACKGROUND, PANEL or TEXT |
| HUE, SATURATION, BRIGHTNESS | Three sliders for that color. Move them with the D-pad or the stick. Everything behind the panel changes live, so you see the result on the home screen at once |
| HEX | The color you are editing as a hex code such as #FF2E97 |
| READABILITY | READABLE, or a warning in capitals when the text is hard to read on the background or the panels, the accent melts into the background, or the quiet text is too faint. A warning does not stop you |
| SAVED THEMES, LOAD THEME | The theme files found in the themes folder, and loading the one you picked into CUSTOM |
| THEME NAME, SAVE THEME FILE | Writes your colors to NAME.json in the themes folder |
| RELOAD THEMES, THEMES FOLDER | Scans the folder again, and shows where it is |
| RESET THEME EDITOR | Forgets the editor's colors and goes back to the two color mixer (Setup, LOOK, CUSTOM COLORS) |

Two more colors are worked out for you: the second background (a little of the accent mixed into the background) and the quiet text (the text color faded toward the background). A file can name them too.

The warning color (lock failure, danger) is not part of a theme on purpose. It stays red so no theme can hide it.

The editor and the older CUSTOM COLORS screen both feed the same CUSTOM theme. The last one you used wins. Your old CUSTOM colors are carried over exactly when you first open the editor. Nothing changes until you move a slider.

## The theme file

One small JSON file. At most 8 KB. The name of the file is the name of the theme: letters, digits, space, dot, dash and underscore, at most 24, not starting with a dot.

```json
{
  "format": 1,
  "name": "Neon Tea",
  "colors": {
    "accent": "#FF2E97",
    "accent2": "#A855F7",
    "background": "#07040D",
    "panel": "#120A21",
    "text": "#F4EEFF"
  }
}
```

Required: format 1 and the five colors, each exactly six hex digits (the # is optional). Optional: `background2` and `dim`. Unknown keys are ignored. A file with a wrong format number, a missing or bad color, a number where a color should be, or more than 8 KB is skipped, and Visor never crashes on it. There is no zip for themes because a theme is one small file. The folder holds at most 40 files.

## Where files go

```
/sdcard/Android/data/com.nokoding.deck/files/themes/NAME.json
```

Visor creates the folder the first time you press SAVE THEME FILE. Open Visor once before you push a file.

## Sharing without the internet

Visor has no INTERNET permission and keeps it that way. Sharing is a file that moves through your phone:

1. On the TV, press SAVE THEME FILE.
2. On your phone, Termux: `bash deck/tools/export-theme.sh TVIP:PORT "NEON TEA"` copies the file to the phone. `--list` instead of a name lists what is on the TV.
3. Send the file to a friend with any messenger or cloud drive.
4. Your friend runs `bash deck/tools/install-theme.sh TVIP:PORT "NEON TEA.json"`. The script checks the name, the size and that it looks like a theme, pushes it, and tells Visor to rescan (`deck_cmd reload_themes`).
5. On their TV: Setup, LOOK, THEME EDITOR, pick it under SAVED THEMES, LOAD THEME.

By hand: `adb push "NEON TEA.json" /sdcard/Android/data/com.nokoding.deck/files/themes/` then `adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd reload_themes`.

## What could not be checked

Nothing in 2.17 has run on a TV. The file reading, the contrast math, the folder handling and the migration are tested on a computer. How the sliders feel with the D-pad and the stick, whether the live preview is smooth on the box, and the two Termux scripts against a real TV are not.
