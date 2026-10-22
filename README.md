# Game Suite

<p align="center">
  <a href="https://github.com/JahangirB/Game-Suite/releases/latest/download/GameSuite.apk">
    <img alt="Download the Android app" src="https://img.shields.io/badge/%E2%AC%87%20Download-Android%20APK-0072E3?style=for-the-badge&logo=android&logoColor=white">
  </a>
</p>

<p align="center">Tap the button on your phone, open the downloaded file and allow installs from your browser or file manager when Android asks. Needs Android 8.0 or newer.</p>

Game Suite (formerly Arcade Sudoku) is an Android app for playing together with someone far away. Sudoku has three ways to play:

- **Solo:** classic Sudoku on your phone, six levels, no internet needed.
- **Online battle:** a two-player race over the internet. One player hosts the battle
  server in their own free account, either a **Google Apps Script** (easiest) or a
  **Cloudflare Worker** (fastest), and shares the **server link** and a **secret code**.
- **Nearby battle:** two phones in the same room connect directly over Bluetooth and
  Wi‑Fi. No internet and no server.

In every battle both players choose a level in the lobby; when both pick the **same
level** both phones get the **same puzzle** and the match **starts at the same moment**.
While you solve you see your opponent's progress as a live percentage, and you can send
quick **reactions** (👍 👏 🔥 😂 😮 😅 🤔 😎 ❤️ ⏰). The first player to solve the puzzle wins.

## Games

- **Sudoku:** solo, online battle against your partner, or nearby battle (details below).
- **Ship Battle:** each player hides five ships (5, 4, 3, 3 and 2 squares) on a 10×10 sea and
  you take turns firing at each other's sea. A hit lets you fire again; the first to sink the
  whole fleet wins. Play against the computer, online with your partner (Cloudflare server)
  or nearby. Tap **Shuffle** until you like your fleet, then **Ready**. The server keeps both
  fleets, so neither phone can peek; enemy ships are revealed as they sink.

## Playing with your partner

After your first online battle the app **remembers your partner**: the server link,
password and secret code. The home screen then shows **Play with *name***:

- **Join** (or **Play**) reopens the same room with the same code. Whoever taps first opens
  it and the other joins; nothing to type or share again.
- **Invite** opens the room and sends your partner a **notification**: *"Sam invites you to
  play"* with **Play** (opens the app straight into the game) and **Decline** (you get a
  notification that they can't play right now). Invitations need the Cloudflare server and the
  one-time Firebase setup below.

*Profile → Saved partner* shows who is saved and has a **Forget** button.

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

- your **name** and **avatar** (🦊 🐱 🐶 🐼 🦉 🐸 🐯 🦄 🤖 👽 🦁 🐧), which your opponent sees
- the **server link** for online battles (Google Script or Cloudflare), the **server
  password** if the host set one, plus *Paste invite* and *Test link*
- **appearance** (system, light or dark)
- your **statistics** (wins and best time per level, battles won and lost)

## How a battle works

1. **Host** opens *Battle a friend → Host*. The app suggests a random secret code (or
   type your own) and starts the lobby. In the lobby tap **Share link + code** and send
   the invite (WhatsApp, SMS, etc.).
2. **Guest** copies the invite, opens *Battle a friend → Join* and taps *Paste the invite
   message instead* (it fills in the link and code), then **Join battle**. The server link
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

## Nearby battles (same room, no internet)

1. Both players open *Battle a friend → Nearby (no internet)*.
2. One taps **Host nearby battle**; the other taps **Find nearby battles** and then **Join**
   next to the host's name.
3. Android asks once for the Bluetooth / nearby devices permissions (on Android 12 and
   older also location, which Android requires for Bluetooth scanning).

The host's phone runs the battle, so the rules, level matching, countdown and reactions
work exactly like online. Keep Bluetooth on and the phones within a few metres. If the
link drops, the guest's phone finds the host again by itself; 45 seconds away during a
match counts as leaving. Nearby battles use Google Play services (on almost every Android
phone).

## Choosing a battle server

| | Google Apps Script | Cloudflare Worker |
|---|---|---|
| Accounts | Google | Cloudflare (free, email only); GitHub for the one-click button |
| Setup | Paste one file, deploy (≈3 min) | Deploy button (≈5 min) or command line (≈10 min) |
| Speed | Moves show within 2–4 s (the app asks every few seconds) | Moves show instantly (live WebSocket connection) |
| Rooms | Kept in the script cache (can rarely be lost) | Kept in a Durable Object per room |
| Password | Not needed (the secret code protects each battle) | Optional `SERVER_PASSWORD` keeps strangers from opening rooms |
| Match log | `Matches` sheet in your Google Sheet | Not kept |

The app recognises the server from the link, so players just paste whatever link the
host shares. Both servers follow the same rules.

## 1a. Host setup: Cloudflare Worker (fast, free)

**Accounts:** a free [Cloudflare](https://dash.cloudflare.com/sign-up) account (email only,
no card on the free plan; going over the free limits makes requests fail until the next
day, it doesn't bill you). For the one-click option also a free GitHub account.

**Option A: deploy button.**

[![Deploy to Cloudflare](https://deploy.workers.cloudflare.com/button)](https://deploy.workers.cloudflare.com/?url=https://github.com/JahangirB/Game-Suite/tree/ccr-d293cbfe-amy86v/backend/cloudflare)

1. Sign up at Cloudflare and confirm your email. The first time, Cloudflare asks you to
   pick a `workers.dev` subdomain (for example `alex`).
2. Tap the button above, sign in, connect GitHub when asked and keep the project name
   `arcade-sudoku`. Cloudflare copies the server into your GitHub and deploys it.
3. You get a link like `https://game-suite.alex.workers.dev`. That is the
   **server link**.

**Option B: command line.** Install [Node.js](https://nodejs.org), download this
repository, then:

```bash
cd backend/cloudflare
npm install
npx wrangler login     # opens the browser to approve
npx wrangler deploy    # prints your server link
```

**If the Cloudflare build fails** with *"Could not detect a directory containing static
files"*, the build ran from an old copy of the code without the root `wrangler.jsonc`.
Connect the Worker to this repository (see *Updating the server* below) and press **Retry build** in
*Workers & Pages → arcade-sudoku → Deployments*. Both the repository root and
`backend/cloudflare` can be deployed. If the log says the Worker name doesn't match, name
the Worker `arcade-sudoku` or change `name` in `wrangler.jsonc` to your Worker's name.

**Optional password (recommended).** Anyone with the link could open rooms on your
server; a password keeps it to people you invite. In the Cloudflare dashboard open
*Workers & Pages → arcade-sudoku → Settings → Variables and Secrets*, add a **Secret**
named `SERVER_PASSWORD`, or run `npx wrangler secret put SERVER_PASSWORD`.

**In the app:** *Profile → Battle server*: paste the link (and the password), tap
**Test link**; it should say "Connected to the Cloudflare battle server". Then host a
battle as usual. **Share link + code** puts the link, password and code in one message,
and the guest's **Paste invite** fills in all three. Anyone who sees that message can
also open rooms on your server, so send the password separately if that matters to you.

**Updating the server:** the deploy button makes a separate *copy* of the code in your
GitHub, which never receives updates. To get updates automatically, connect the Worker to
this repository instead: *Workers & Pages → your Worker → Settings → Builds*, disconnect the
copy, connect `JahangirB/Game-Suite` with the default branch, root directory `/` and deploy
command `npx wrangler deploy --name YOUR-WORKER-NAME`. Every push then redeploys the server.
(The `name` in `wrangler.jsonc` is `game-suite`; `--name` deploys under your Worker's name
instead. If Cloudflare still reports a name mismatch, name the Worker `game-suite`.) With the
command line, download the new code and run `npx wrangler deploy` again.

**Free limits** (check Cloudflare's pricing page, they change): around 100,000 requests a
day; a battle is a few hundred small messages, so thousands of matches a day fit.

## 1b. Host setup: Google Apps Script (easiest, free)

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

## 1c. Invitations: one-time Firebase setup (free)

Android only delivers instant notifications to a closed app through Google's push service,
Firebase Cloud Messaging. It is free. The host does this once:

1. Open the [Firebase console](https://console.firebase.google.com), **Create a project**
   (any name, e.g. `game-suite`; Google Analytics can be off).
2. In the project, **Add app → Android**. Package name: your app id, **`com.gamesuite.app`**
   unless you set your own (see *Make your own copy*). App nickname: Game Suite. Register,
   then **download `google-services.json`** and skip the remaining steps (no Gradle changes
   are needed). Don't commit the file: add its content as the **`GOOGLE_SERVICES_JSON`**
   repository secret; the next build switches invitations on.
3. In Firebase: **Project settings → Service accounts → Generate new private key**. This
   downloads a JSON key, which *is* secret: don't commit or share it.
4. In Cloudflare: **Workers & Pages → your Worker → Settings → Variables and Secrets → Add**,
   type **Secret**, name **`FIREBASE_SERVICE_ACCOUNT`**, value: the whole content of that key
   file. Save and deploy.
5. Update both phones to the new app, open it once (so it registers for notifications and
   asks for the notification permission), and play one online battle together. From then on
   **Invite** works.

Check it worked: open `https://YOUR-WORKER.workers.dev/ping` (add `?password=…` if you set
one). It shows `"invitations": true` once the key is set. If it says `false`, the secret is
missing, misspelled, saved as *Text* instead of *Secret* (Cloudflare removes plain-text
variables on the next deploy from GitHub), or was added under *Build* variables instead of
the Worker's *Variables and Secrets*.

If an invitation fails with a permission error, open the Google Cloud console for the same
project and make sure **Firebase Cloud Messaging API** is enabled.

## 2. Install the Android app

> **Coming from Arcade Sudoku (version 1.0.9 or older)?** With the rename the app got a new
> internal id (`com.gamesuite.app`), so Android treats it as a new app: uninstall the old one,
> install from the Download button, and set your name, avatar and server link again in the
> profile. Play one online battle with your partner to save them again. Old apps that update
> themselves would install the new one next to the old; just remove the old one afterwards.

- **Download:** use the **Download** button at the top of this page. It always points to the
  newest build, which CI publishes as the *latest* release whenever the default branch changes.
  Builds are signed with the repository owner's private key, so a new download installs over
  the old one and keeps your saved game and settings.
- **Build it yourself:** open the project in Android Studio (Ladybug or newer)
  and press *Run*, or run `./gradlew assembleDebug` with JDK 17 and the Android
  SDK installed. The APK is written to `app/build/outputs/apk/debug/`.

**Automatic updates:** when the app starts it checks the latest release (and you can check
by hand under *Profile → App*). If a newer build exists it offers to update, downloads it and
opens Android's install screen. The first time, Android asks you to allow Game Suite to
install apps; after that each update is two taps. Android only accepts the update if it is
signed with the same key as the installed app, and it never installs without your
confirmation. The offer is never shown during a battle. Version 1.0.N is the CI run number.
Builds older than the updater (anything installed before it existed) need one manual download.

## Make your own copy

Everything personal lives outside the code: in **GitHub repository secrets** (signing key,
Firebase config) and in **Cloudflare** (server password, Firebase key). To run your own Game
Suite for you and your friends:

1. **Fork** this repository on GitHub (or use it as a template).
2. **Signing key** (so your releases can update your installed app): create one with
   `keytool -genkeypair -keystore gamesuite.keystore -storetype PKCS12 -alias gamesuite
   -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Game Suite"`, then add these
   secrets under *Settings → Secrets and variables → Actions*:
   - `SIGNING_KEYSTORE_BASE64`: the keystore file in base64 (`base64 -w0 gamesuite.keystore`)
   - `SIGNING_STORE_PASSWORD` and `SIGNING_KEY_PASSWORD`: its password
   - `SIGNING_KEY_ALIAS`: `gamesuite`

   Keep the keystore file private and safe: losing it means your next version can't update
   the installed app. Without these secrets CI still builds and tests, but publishes nothing.
3. **Own app id (optional):** to install your copy next to someone else's Game Suite, add a
   repository **variable** (*Variables* tab) `APP_ID`, e.g. `com.yourname.gamesuite`, and use
   that id in Firebase.
4. **Invitations (optional):** create your own Firebase project (*1c* above) and add its
   `google-services.json` content as the `GOOGLE_SERVICES_JSON` secret.
5. **Server:** deploy your own Cloudflare Worker (*1a*) or Google Script (*1b*).
6. Push to your default branch: CI builds the app and publishes it to *your* releases. The
   download button in your copy of this README and the app's updater use your repository.

Requires Android 8.0 (API 26) or newer.

## Project layout

| Path | What it is |
| --- | --- |
| `backend/Code.gs` | Apps Script web app: rooms, puzzle generator, start sync, progress, winner |
| `backend/test/` | Node tests that run `Code.gs` against mocked Apps Script services |
| `backend/cloudflare/` | Cloudflare Worker: one Durable Object per room (`src/room.js`: Sudoku and Ship Battle rules) and a Directory object for invitations (`src/directory.js`, `src/push.js`), with unit and end-to-end tests |
| `app/src/main/java/com/arcade/sudoku/data/` | Server clients (`BattleApi` for Apps Script, `CloudflareBattle`), response models, invites, profile, updater |
| `app/src/main/java/com/arcade/sudoku/game/ships/` | Ship Battle rules, random fleets and the computer opponent |
| `app/src/main/java/com/arcade/sudoku/push/` | Invitations: Firebase setup, notifications with Play/Decline, the invite endpoints |
| `app/src/main/java/com/arcade/sudoku/data/nearby/` | Nearby battles: `LocalRoom` (the rules, run on the host phone), `NearbyHost`, `NearbyGuest` |
| `app/src/main/java/com/arcade/sudoku/game/` | Board, notes/undo, puzzle generator and the solo game rules |
| `app/src/main/java/com/arcade/sudoku/ui/` | `BattleViewModel` (session, polling, clock sync), `SoloViewModel` and the Compose screens |

## Server API (Apps Script)

The Cloudflare server speaks the same room format over a WebSocket; its messages are
described at the top of `backend/cloudflare/src/index.js`.

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
| `emoji` | `code`, `playerId`, `emoji` (`thumbsup`, `clap`, `fire`, `laugh`, `wow`, `sweat`, `think`, `cool`, `heart`, `hurry`) | Shows a reaction to the opponent (at most one per 1.5 s) |
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
node --test backend/test/*.test.js           # Apps Script server tests
cd backend/cloudflare && npm install
npm test                                     # Cloudflare rules
RUN_WORKER_TESTS=1 npm test                  # + a full battle against `wrangler dev`
npx wrangler dev                             # run the Cloudflare server locally
./gradlew testDebugUnitTest                  # Kotlin unit tests
./gradlew assembleDebug                      # build the APK
```
