# Performance mode and EXIT GAME

New in 2.12. When a game starts, Visor closes what runs in the background, so the game has more RAM and CPU. The control center has an EXIT GAME tile to leave the game and close it.

## What it does

When a game starts, Visor ends the background and cached processes of your other recent apps with Android's `killBackgroundProcesses`, the same call as KILL BACKGROUND in the task manager ([task-manager.md](task-manager.md)). A short result such as CLOSED 5 APPS shows for a moment.

Never closed: Visor, the home app, the game itself, and the apps on the protect list (Setup, SYSTEM, PS MENU, and an app's PROTECT FROM CLOSE ALL).

It runs when:

- you start the app from Visor, or
- the overlay service sees it come to the front (a game started another way), if the overlay service is on ([ps-menu.md](ps-menu.md)).

The same app starting again within 30 seconds does not close everything a second time.

## Settings

Setup, SYSTEM, PERFORMANCE MODE.

| Row | What it does |
|---|---|
| MODE | OFF (default). GAMES acts for apps that Android marks as games. CHOSEN APPS acts only for apps you pick |
| CHOSEN APPS | Turn it on for single apps, for games that Android does not mark as games |
| CLOSE BACKGROUND NOW | Does the same close right away, after you press OK again |
| ABOUT PERFORMANCE MODE | The short explanation |

The control center's DISPLAY section has a PERFORMANCE MODE toggle that switches between OFF and GAMES.

## EXIT GAME

The control center's QUICK section has an EXIT GAME tile. Press the PS button over the game, pick EXIT GAME: Visor's home comes to the front, which sends the game to the background, and about a second and a half later its processes are closed. Inside Visor the tile closes the game that performance mode remembers. The tile is greyed when no game is known.

From your phone, when a game is on screen: `adb shell am start -n com.nokoding.deck/.HomeActivity --es deck_cmd exit_game`.

## What you see afterwards (2.16)

Visor reads the free RAM before it closes the apps and again about two seconds later, and shows a line such as FREE RAM 410 -> 560 MB (+150 MB). The same line stays in Setup, SYSTEM, PERFORMANCE MODE, LAST RESULT. If nothing big was running, it says NO REAL CHANGE. If Android starts apps again by itself, it says so. This is the honest answer to "did it help": on a box with 1.4 GB, a few apps may free only a little, and Android keeps some apps alive on purpose. 2.16 also tries every launchable app, not only the recent ones.

## Remap actions

EXIT GAME and TOGGLE PERFORMANCE MODE can be given to a button or a combo (Setup, REMAP). EXIT GAME needs the overlay service when a game is in front, so Visor knows which app it is.

## The apps it closes

The list comes from Android's usage history, so it needs the usage access grant ([adb-commands.md](adb-commands.md), Usage access). Without it, Visor uses its own list of recent launches, which is shorter.

## What it cannot do

- It cannot close the app on screen or anything that belongs to the system. It only ends the background and cached processes of other apps.
- Android may start some of them again by itself, so the gain varies. Apps that were closed start again when you open them, which takes a moment. That is the price of the free memory.
- It does not help a game that is slow because of the GPU. For that see [game-profiles.md](game-profiles.md).
- FORCE STOP is not used here. EXIT GAME closes a game the same way KILL BACKGROUND does, after sending it to the background.

## What could not be checked

Nothing in 2.12 has run on a TV. In particular: whether the overlay service sees a game come to the front fast enough, how much memory the close frees on a 1.4 GB box, whether Android restarts the closed apps at once, and whether the game really is gone after EXIT GAME.
