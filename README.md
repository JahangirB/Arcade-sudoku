# Arcade Sudoku Battle

A two-player Sudoku race for Android. One player hosts a small Google Apps Script
"server" in their own Google account, then shares the **script link** and a
**secret code**. Once both players are in the lobby and press *Ready*, both phones
get the **same puzzle** and the match **starts at the same moment**. While you solve,
you can watch your opponent's progress as a live percentage. The first player to
solve the puzzle wins.

```
 ┌──────────────┐   script link + secret code   ┌──────────────┐
 │ Host's phone │ ────────────────────────────▶ │ Guest's phone│
 └──────┬───────┘                               └──────┬───────┘
        │      HTTPS (poll every 1.5 s)                │
        └──────────────▶  Google Apps Script  ◀────────┘
                          (backend/Code.gs, runs in the
                           host's Google account)
```

## How a battle works

1. **Host** opens the app, enters a name and the script link, picks a difficulty,
   and taps **Host battle**. The app suggests a random secret code; you can type your own.
2. In the lobby the host taps **Share link + code** and sends the invite
   (WhatsApp, SMS, etc.).
3. **Guest** copies the invite, taps **Paste invite** (it fills in the link and
   code), enters a name and taps **Join battle**.
4. Both tap **I'm ready**. The server creates one puzzle with a single solution
   and sets a start time 5 seconds ahead. Both phones count down to that same
   instant: the app corrects for clock differences between the phones and the
   server, and the grid stays hidden until the countdown ends.
5. During the match:
   - your bar shows how much of the board you have **filled**;
   - the opponent's bar shows the percentage of cells they have **solved
     correctly**. The server checks this, and never tells you your own
     correctness, so you can't use it to test guesses;
   - conflicting numbers in a row, column or box turn red.
6. The first correct board wins. The result screen shows times and the loser
   can reveal the solution. **Rematch** starts a new puzzle once both accept.

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
5. In the app, paste the link and tap **Test link**. You should see
   "Connected to the battle server."

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

- **Download a build:** every push runs the GitHub Actions workflow
  *Build*. Open the latest run and download the `arcade-sudoku-debug-apk`
  artifact, unzip it and install `app-debug.apk` on the phone (allow installs
  from unknown sources).
- **Build it yourself:** open the project in Android Studio (Ladybug or newer)
  and press *Run*, or run `./gradlew assembleDebug` with JDK 17 and the Android
  SDK installed. The APK is written to `app/build/outputs/apk/debug/`.

Requires Android 8.0 (API 26) or newer.

## Project layout

| Path | What it is |
| --- | --- |
| `backend/Code.gs` | Apps Script web app: rooms, puzzle generator, start sync, progress, winner |
| `backend/test/` | Node tests that run `Code.gs` against mocked Apps Script services |
| `app/src/main/java/com/arcade/sudoku/data/` | HTTP client for the script, response models, invite parsing |
| `app/src/main/java/com/arcade/sudoku/game/` | Board helpers (conflicts, progress) |
| `app/src/main/java/com/arcade/sudoku/ui/` | `BattleViewModel` (session, polling, clock sync) and Compose screens |

## Server API

Every call is `GET <script link>?action=…` and returns JSON with `ok` and
`serverTime`. If something goes wrong you get `ok: false`, an `error` message,
and `fatal: true` when the room is gone.

| Action | Parameters | Effect |
| --- | --- | --- |
| `ping` | none | Health check |
| `create` | `code`, `name`, `difficulty` (`easy`/`medium`/`hard`) | Host opens a room; returns `playerId` |
| `join` | `code`, `name`, optional `playerId` to reconnect | Guest joins; returns `playerId` |
| `ready` | `code`, `playerId`, `ready` | When both are ready, a new round starts in 5 s (also used for rematches) |
| `sync` | `code`, `playerId`, optional `board` (81 digits, `0` = empty) | Uploads the board, returns room state and opponent progress |
| `leave` | `code`, `playerId` | Leaves; forfeits a running match, a host leaving closes the room |

## Development

```bash
node --test backend/test/*.test.js   # backend tests
./gradlew testDebugUnitTest          # Kotlin unit tests
./gradlew assembleDebug              # build the APK
```
