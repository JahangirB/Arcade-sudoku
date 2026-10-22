# Arcade Sudoku

<p align="center">
  <a href="https://github.com/JahangirB/Arcade-sudoku/releases/latest/download/ArcadeSudoku.apk">
    <img alt="Download the Android app" src="https://img.shields.io/badge/%E2%AC%87%20Download-Android%20APK-0072E3?style=for-the-badge&logo=android&logoColor=white">
  </a>
</p>

<p align="center">Tap the button on your phone, open the downloaded file and allow installs from your browser or file manager when Android asks. Needs Android 8.0 or newer.</p>

A Sudoku app for Android with two ways to play:

- **Solo:** classic Sudoku on your phone, six levels, no internet needed.
- **Battle:** a two-player race. One player hosts a small Google Apps Script
  "server" in their own Google account and shares the **script link** and a
  **secret code**. In the lobby both players choose a level, and when both pick
  the **same level** both phones get the **same puzzle** and the match **starts
  at the same moment**. While you solve you see your opponent's progress as a
  live percentage. The first player to solve the puzzle wins.

## Solo play

Tap **New Game**, pick a level and play. You get the tools of popular Sudoku apps:
**Undo**, **Erase**, **Notes** (pencil marks, cleared automatically when you place a
digit) and **3 Hints**. Highlighting shows the selected cell's row, column and box and
every cell with the same digit; wrong digits turn red and count as a mistake
(3 mistakes end the game). Digits you have placed nine times disappear from the number
pad. An unfinished game is saved, so **Continue** brings it back, even after closing
the app.

Levels: **Beginner, Easy, Medium, Hard, Expert, Master**. The harder the level, the fewer
clues you get. Every puzzle has exactly one solution.

## Profile

The **profile icon** (top right of the home screen) holds all setup:

- your **name**
- the **Google Script link** for battles (with *Paste invite* and *Test link*)
- **appearance** (system, light or dark)
- your **statistics** (wins and best time per level, battles won and lost)

## How a battle works

1. **Host** opens *Battle a friend → Host*. The app suggests a random secret code (or
   type your own) and starts the lobby. In the lobby tap **Share link + code** and send
   the invite (WhatsApp, SMS, etc.).
2. **Guest** copies the invite, opens *Battle a friend → Join* and taps *Paste the invite
   message instead* (it fills in the link and code), then **Join battle**. The script link
   is saved in the profile, so you only do this once per host.
3. **Both players choose a level in the lobby.** Tapping a level means "I'm ready for
   this level" (tap it again to cancel). You can see your opponent's pick. The game only
   starts when **both players have picked the same level**. If the levels differ, both
   players see a message ("Levels don't match: you chose Hard, Sam chose Easy…") and the
   game waits until someone changes their pick.
4. When the levels match, the server creates one puzzle and sets a start time 5 seconds
   ahead. Both phones count down to that same instant: the app corrects for clock
   differences between the phones and the server, and the grid stays hidden until the
   countdown ends.
5. During the match:
   - your bar shows how much of the board you have **filled**;
   - the opponent's bar shows the percentage of cells they have **solved
     correctly**. The server checks this, and never tells you your own
     correctness, so you can't use it to test guesses;
   - repeated digits in a row, column or box turn red.
6. The first correct board wins. The result screen shows times and the loser
   can reveal the solution. For a **rematch**, both players choose a level again.

Leaving a running match counts as a loss. If a player stops syncing for 45
seconds (app closed, no network), the opponent wins by timeout.

## 1. Host setup: deploy the Google Script (about 3 minutes, free)

1. Create a new Google Sheet (it will store a log of finished matches). Open
   **Extensions → Apps Script**.
   *Or* create a standalone project at <https://script.google.com>. It works
   the same, just without the match log.
2. Delete the sample code and paste the contents of [`backend/Code.gs`](backend/Code.gs).
   Save.
3. Click **Deploy → New deployment**, choose type **Web app** and set:
   - **Execute as:** Me
   - **Who has access:** Anyone
4. Click **Deploy**, approve the permissions, and copy the **Web app URL**
   (`https://script.google.com/macros/s/…/exec`). That is the *script link*.
5. In the app, open the **profile** (icon at the top right), paste the link and tap
   **Test link**. You should see "Connected to the battle server."

If you change `Code.gs` later, use **Deploy → Manage deployments → Edit → New
version** so the same link keeps working.

**Where the data lives:** rooms are kept in the script's cache for up to 6
hours, so there's no setup and no storage quota to manage. When the script is
bound to a Sheet, each finished match is added to a `Matches` sheet with the
players, winner, reason, time and percentages.

**Privacy:** anyone with the link can create rooms on your script, but they can
only join a battle if they also have its secret code. The script only touches
its own cache and (if bound) its own spreadsheet.

## 2. Install the Android app

- **Download:** use the **Download** button at the top of this page. It always points to the
  newest build, which CI publishes as the *latest* release whenever the default branch changes.
  Builds are signed with the same key, so a new download installs over the old one and keeps
  your saved game and settings.
- **Build it yourself:** open the project in Android Studio (Ladybug or newer)
  and press *Run*, or run `./gradlew assembleDebug` with JDK 17 and the Android
  SDK installed. The APK is written to `app/build/outputs/apk/debug/`.

**Automatic updates:** when the app starts it checks the latest release (and you can check
by hand under *Profile → App*). If a newer build exists it offers to update, downloads it and
opens Android's install screen. The first time, Android asks you to allow Arcade Sudoku to
install apps; after that each update is two taps. Android only accepts the update if it is
signed with the same key as the installed app, and it never installs without your
confirmation. The offer is never shown during a battle. Version 1.0.N is the CI run number.
Builds older than the updater (anything installed before it existed) need one manual download.

The APK is signed with a debug key that is committed to this repository
(`app/debug.keystore`). That is fine for sideloading; use your own key before publishing
to the Play Store.

Requires Android 8.0 (API 26) or newer.

## Project layout

| Path | What it is |
| --- | --- |
| `backend/Code.gs` | Apps Script web app: rooms, puzzle generator, start sync, progress, winner |
| `backend/test/` | Node tests that run `Code.gs` against mocked Apps Script services |
| `app/src/main/java/com/arcade/sudoku/data/` | HTTP client for the script, response models, invite parsing |
| `app/src/main/java/com/arcade/sudoku/game/` | Board, notes/undo, puzzle generator and the solo game rules |
| `app/src/main/java/com/arcade/sudoku/ui/` | `BattleViewModel` (session, polling, clock sync), `SoloViewModel` and the Compose screens |

## Server API

Every call is `GET <script link>?action=…` and returns JSON with `ok` and
`serverTime`. If something goes wrong you get `ok: false`, an `error` message,
and `fatal: true` when the room is gone.

| Action | Parameters | Effect |
| --- | --- | --- |
| `ping` | none | Health check |
| `create` | `code`, `name` | Host opens a room; returns `playerId` |
| `join` | `code`, `name`, optional `playerId` to reconnect | Guest joins; returns `playerId` |
| `ready` | `code`, `playerId`, `difficulty`, `ready` | Marks the player ready for a level (`beginner`, `easy`, `medium`, `hard`, `expert`, `master`); `ready=false` cancels. A round starts in 5 s only when both players are ready with the same level (also used for rematches) |
| `sync` | `code`, `playerId`, optional `board` (81 digits, `0` = empty), optional `v` (last room version seen) | Uploads the board, returns room state and opponent progress, or just `unchanged: true` when the room is still at version `v` |
| `leave` | `code`, `playerId` | Leaves; forfeits a running match, a host leaving closes the room |

## How light the backend is

- **Reads don't take the lock.** Most `sync` calls only read the room from the cache. The
  script lock (which serialises writes) is taken only to store a new board, record a timeout,
  or refresh the player's "last seen" time, which happens at most every 10 seconds.
- **Tiny replies.** The app sends the room version it already has; if nothing changed the
  reply is just `{"unchanged": true}` instead of the whole room.
- **Adaptive polling.** During a match the app checks every 2 s while the opponent is making
  progress and every 4 s otherwise; the lobby and result screens check every 4 s. Your own
  moves are sent right away (quick moves within 0.7 s go out together).
- **No polling in the background** outside a match. During a match polling continues, since
  stopping for 45 seconds counts as leaving.

## Development

```bash
node --test backend/test/*.test.js   # backend tests
./gradlew testDebugUnitTest          # Kotlin unit tests
./gradlew assembleDebug              # build the APK
```
