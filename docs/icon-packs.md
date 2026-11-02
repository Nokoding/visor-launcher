# Visor icon packs

Visor shows each app's own icon or TV banner. An icon pack swaps those for pictures you pick, all at once. It works the same way as a sound pack, so if you've made one of those you already know the steps. The sound side is in [sound-packs.md](sound-packs.md).

New in 2.2. Before that you could only drop single icon files in, one app at a time. Single files still work, and they still win (see Precedence).

## What an icon pack is

A zip (or a folder) full of PNG or WebP images. Each image is named after the package name of the app it replaces:

```
Neon.zip
  com.netflix.ninja.png
  com.google.android.youtube.tv.png
  com.amazon.amazonvideo.livingroom.webp
```

That's it. No pack.json, no manifest. Apps the pack doesn't cover keep their own icon.

## Where it goes

```
/sdcard/Android/data/com.nokoding.deck/files/icons/
```

The same folder that holds single custom icons. Visor creates it the first time it runs, with a short README.txt inside. Open Visor once before you push anything.

- A `.zip` dropped there gets unpacked into its own folder, `icons/Neon/` for `Neon.zip`.
- A folder pushed there is a pack right away. No zip needed.
- A loose `com.example.app.png` right in `icons/` is a single custom icon, not part of any pack.

You can keep as many packs as you like. One is active at a time.

## File names

The name has to be the package name exactly, plus `.png` or `.webp`. `com.netflix.ninja.png` works, `Netflix.png` does nothing.

If both `.png` and `.webp` exist for the same app, the `.png` is used.

## Finding a package name

Three ways, easiest first:

1. **In Visor.** Hold OK (or press X) on the app's tile, pick CHANGE ICON. The top row says PACKAGE and shows it, with the exact file name to use.
2. **adb from your phone.** List packages and filter by a word from the app's name:

   ```
   adb shell pm list packages | grep -i netflix
   ```

   That prints `package:com.netflix.ninja`. Drop the `package:` part.
3. **Android's app info screen.** Hold OK on the tile, pick APP INFO. Some Google TV builds show the package name near the bottom. The onn box doesn't always, so use 1 or 2 if it isn't there.

A few that are common on Google TV:

| App | Package |
|---|---|
| YouTube | com.google.android.youtube.tv |
| Netflix | com.netflix.ninja |
| Prime Video | com.amazon.amazonvideo.livingroom |
| Disney+ | com.disney.disneyplus |
| Spotify | com.spotify.tv.android |

Check yours with method 1 or 2 anyway. Some apps ship different packages per region.

## Image size and format

- **Square art** sits on the icon plate. 256 x 256 px is plenty, Visor draws icons at about 140 px and keeps them at 2x. Bigger works but just costs memory.
- **Wide art** (at least 1.4 times wider than tall, like 16:9) fills the whole tile like a TV banner. 640 x 360 is a good size. Visor caps banners at 480 design px wide.
- PNG with transparency works. WebP (lossy or lossless) is smaller on disk.
- Keep each file under 4 MB. Bigger files are skipped. In practice a good icon is 20 to 150 KB.
- Bright art on a transparent background reads best on the dark plates. Very thin lines get soft from across the room.

## Making the zip

Put the images at the root of the zip, or all inside one folder. Nothing deeper.

On a computer:

```
cd Neon
zip -r ../Neon.zip .
```

In Termux on your phone:

```
pkg install -y zip
cd ~/storage/downloads/Neon
zip ../Neon.zip *.png *.webp
```

On Android without Termux, most file managers have a Compress option. Select the images (not the folder around them, or select only that one folder) and compress.

The pack gets the zip's name when the files are at the root, or the folder's name when they sit in one folder. Letters, digits, space, dot, dash and underscore are kept, anything else becomes `_`.

## Install from your phone (Termux, one command)

`deck/tools/install-iconpack.sh` connects, pushes and reloads:

```
pkg install -y android-tools
bash install-iconpack.sh TVIP:PORT ~/storage/downloads/Neon.zip
```

A folder works too: `bash install-iconpack.sh TVIP:PORT Neon/`. The script checks the address and the name, pushes to Visor's icons folder, then runs:

```
adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd reload_icons
```

Visor comes to the front, unpacks the zip and reloads icons. When exactly one new pack came in this way, Visor also makes it the active pack. TVIP:PORT is on the TV under Developer options, Wireless debugging. Pair once first, see guide-termux.md.

## Install by hand with adb

```
adb connect TVIP:PORT
adb shell mkdir -p /sdcard/Android/data/com.nokoding.deck/files/icons
adb push Neon.zip /sdcard/Android/data/com.nokoding.deck/files/icons/
adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd reload_icons
```

Or push a folder instead of the zip: `adb push Neon /sdcard/Android/data/com.nokoding.deck/files/icons/`.

The last line is optional. Without it, open Setup and pick RELOAD ICONS.

## Choose it and reload in Setup

Setup, APPS tab:

- **ICON PACK**: left or right picks a pack, or NONE (APP ICONS). Icons reload right away.
- **RELOAD ICONS**: unpacks any new zips in the icons folder, reloads every icon, and reopens the tab so new packs show up in the ICON PACK row.
- **ICON PACK STATUS**: the active pack and how many images it has, and the result of the last zip install (or why it failed).
- **ICON MODE** (SHARPER or ORIGINAL) only changes how the app's own icons are drawn. Pack and custom images are used in both modes.

## Precedence

For each app Visor takes the first of these that exists:

1. A single file in `icons/`, like `icons/com.netflix.ninja.png`.
2. The same name inside the active pack, like `icons/Neon/com.netflix.ninja.png`.
3. The app's own banner or icon.

So you can use a pack and still override one or two apps with single files. CHANGE ICON on a tile tells you which file it found and lets you turn custom art off for just that app (USE CUSTOM ICON: OFF). That switch turns off both the single file and the pack image for that app.

## Removing a pack

Pick NONE (APP ICONS) or another pack in Setup first. Then delete the folder and the zip:

```
adb shell rm -r "/sdcard/Android/data/com.nokoding.deck/files/icons/Neon"
adb shell rm "/sdcard/Android/data/com.nokoding.deck/files/icons/Neon.zip"
```

Delete both. If the zip stays, Visor unpacks it again the next time it scans. If the active pack's folder is gone, Visor falls back to app icons and the status row says the pack is missing.

Uninstalling Visor deletes the whole icons folder with the rest of its data.

## Limits

Same safety rules as sound pack zips (the code is shared):

- Only `.png` and `.webp` are copied. Everything else is skipped, including `__MACOSX`, `.DS_Store` and other dot files.
- Up to 400 images per pack, 4 MB per image, 64 MB unpacked in total, 64 MB for the zip.
- Sizes are counted while copying, so a zip that lies about its sizes still stops at the cap.
- Any entry that tries to leave the folder (`../`, a leading `/`, a drive letter) makes Visor refuse the whole zip. Nothing from it gets written.
- Files at the root or in one folder only. Two top folders, or folders inside folders, are refused.
- Visor unpacks into a hidden temp folder and renames it into place at the end, so a failed install never leaves half a pack behind.
- Visor writes a small `.deck-source` file into the pack folder so it doesn't unpack the same zip twice. Push a changed zip with the same name and it gets installed over the old folder.

## Troubleshooting

- **Nothing changed.** Check ICON PACK in Setup, APPS. A new pack is only picked by itself when it arrives through install-iconpack.sh or deck_cmd reload_icons. Otherwise pick it by hand.
- **One app still shows its own icon.** The file name doesn't match the package. Open CHANGE ICON on that tile and compare. Also check USE CUSTOM ICON isn't OFF for that app.
- **The pack isn't in the list.** Pick RELOAD ICONS. If it still isn't there, look at ICON PACK STATUS for the zip error.
- **"NO ICON FILES".** The zip had no .png or .webp files Visor could use. JPG and SVG aren't supported.
- **"PUT FILES AT THE ROOT OR IN ONE FOLDER".** Re-zip so the images aren't nested two folders deep.
- **"UNSAFE PATH IN ZIP".** Some tool wrote odd paths into the zip. Re-zip it with the commands above.
- **"FILE OVER 4 MB".** Shrink that image. 256 px square or 640 x 360 wide is enough.
- **A single icon wins over the pack and you don't want it to.** Delete the loose file from `icons/`.
- **adb push says "No such file or directory".** Open Visor once so it creates its folder, or run the mkdir line above.
