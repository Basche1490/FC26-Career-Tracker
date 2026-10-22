# PC setup: sending matches and transfers to your phone

The app can fill in your career by itself while you play. Two small things run on your PC:

1. **The bridge** (`pc-bridge/`). A tiny web server that holds the events until your phone fetches them.
2. **A capture script** (`live-editor-scripts/`) that runs inside FC Live Editor and sends every finished match
   and every transfer to the bridge.

Phone and PC must be on the same Wi-Fi network. One file sets up the PC side: `FC26_Launcher.bat`.

## Quick start (about three minutes)

1. **On the PC:** download `FC26-PC-Tools.zip` from the **Latest build** release of this repository
   (*Releases → Latest build*), unzip it anywhere, and double-click **`FC26_Launcher.bat`**.
   Windows may ask once for permission (the shield window): say **Yes**. That is the firewall rule that lets your phone in.
2. **On the phone:** open the app, menu → **PC Connection** → **Find my PC**, then type the **pairing code** the launcher
   shows and tap **Save & connect**. (The app's **Set up live sync** guide walks through these same steps and ticks each one
   off as it works.) Only a phone with that code can read anything from your PC: see [SECURITY.md](SECURITY.md).
3. **In FC Live Editor:** load your career, open the **Lua Scripts** tab, pick **`FC26_Auto_Capture.lua`** and press **Execute**.

Then play. After each match press **Advance** to reach the Career Hub: the match appears in the app by itself.

The small coloured pill at the top of the app shows whether this is working: green *Live*, amber *Check* (something to fix,
tap it to see what), red *Down*, grey *Not set up*.

## 1. The launcher

`FC26_Launcher.bat` is the only file you need to start. Run it again any time: it fixes what is missing and updates what is old.
It does these things, and tells you plainly what it did:

1. **Finds the files.** The bridge and the Live Editor scripts can be in different folders than the launcher, and Live Editor can be
   anywhere (Desktop, Documents, another drive). The launcher looks next to itself first, then in Desktop, Downloads, Documents
   (also when they are in OneDrive) and, if still needed, on your fixed drives. If it finds several copies it takes the newest
   bridge; if it finds several Live Editors it takes the one that already has the capture script, then the one with "26" in its name,
   and only otherwise asks you. If it finds nothing it opens a folder picker: choose the Live Editor folder (the one that contains
   the `lua` folder). It remembers the answer in `%APPDATA%\FC26Tracker\launcher.json`.
2. **Installs the capture scripts** (`FC26_Auto_Capture.lua` and `FC26_AutoCapture_v1.1.1.lua`) into Live Editor's `lua\scripts`
   folder. A changed script is replaced and the previous one kept as `.bak`; a newer script that is already there is never overwritten.
   Scripts of earlier versions in that folder (`telemetry_sync.lua`, `telemetry_auto_loop.lua` and so on) are renamed to `.old`,
   never deleted, because they would otherwise run next to the new one.
3. **Lets your phone in.** It adds one Windows Firewall rule, "EA FC Telemetry Bridge", for port 5050, for devices on your local
   network only and on Private (home) or Domain (work) networks only, after asking Windows once. It does **not** change your
   network type itself. If Windows treats your network as Public, the launcher says so and asks: open Windows Settings so you
   can make it a Private network (recommended for your home Wi-Fi), allow the bridge on Public networks too, or leave it closed.
   A rule from an earlier launcher that was open on every network is replaced.
4. **Starts the bridge in the background, with no window.** It uses Python when a working Python 3 is installed, otherwise the built-in
   PowerShell version (which needs one more permission, an HTTP reservation for your Windows user only, asked in the same single prompt). If a bridge is already
   running it is kept; if it is older than the files, it is restarted. The bridge's output goes to
   `%APPDATA%\FC26Tracker\bridge.log` and `bridge-errors.log`.
5. **Prints the address and the pairing code** to type into the phone, and the next steps. The window then waits for **Enter** so you can read it; if
   anything goes wrong the window stays open instead of vanishing.

Options (type them after the file name in a command prompt, or ignore them):

| Command | Does |
| --- | --- |
| `FC26_Launcher.bat status` | Shows what the bridge sees: phone, script, versions, folders, and the pairing code. |
| `FC26_Launcher.bat stop` | Stops the bridge. |
| `FC26_Launcher.bat newcode` | Makes a new pairing code. The old one stops working at once; type the new one into the app. |
| `FC26_Launcher.bat reset` | Forgets the remembered folders and network choices, then runs normally. |
| `FC26_Launcher.bat nopause` | Does not wait for Enter at the end. |

The bridge does not start with Windows: run the launcher when you sit down to play. (Closing the launcher window does not stop
the bridge; use `stop`, or restart the PC.)

If Live Editor was open when the scripts were installed, restart it once so it lists them.

### If the launcher says something is wrong

| It says | What to do |
| --- | --- |
| The bridge files were not found | Run it from the unzipped folder, or point the folder picker at `pc-bridge`. |
| No Live Editor folder was chosen | Run it again and pick the folder that contains `lua`, or copy the two `.lua` files into Live Editor's `lua\scripts` yourself. |
| Something that is not this bridge is using port 5050 | Close that program, then run the launcher again. |
| An old bridge could not be stopped | It was started as administrator (for example by an older `Start_Bridge.bat`). Close its window, run the launcher again. |
| The permission was not given | Run the launcher again and choose **Yes** when Windows asks. |
| Windows treats the network as Public | If it is your home Wi-Fi: Windows Settings → Network → your network → **Private network**, then run the launcher again. |
| Found the bridge files in ... Use this folder? | The launcher found them by searching. Say yes only if that is the folder you unzipped. |
| The bridge did not start | The lines under it are the bridge's own message; `%APPDATA%\FC26Tracker\bridge-errors.log` has more. |

## 2. Checking that it all works (PC Connection)

In the app, menu → **PC Connection** shows the whole path, from phone to bridge to script, each step ticked or marked with what to do:

- **Self check on the PC:** the bridge looks at itself every time you open the screen or press **Check again**: export folder, outbox,
  capture script (running? which version? is it new enough?), match statistics (is the Project Atlas exporter running?), undelivered
  reports, last match received, phone connection, network address. Every problem comes with one sentence saying how to fix it.
  The same list is on the PC at `http://localhost:5050` in a browser.
- **Versions:** app, bridge and script. If the script is older than the bridge needs (`2.8.0`), the screen says to update it (run the launcher again).
- **Re-send last match:** asks the capture script to send the newest finished match again, for example when the phone missed it
  or you cleared it. The screen tells you what happened (sent, nothing to send, script not running).
- **PC address and pairing code:** the saved address, **Find my PC** (looks for the bridge on your Wi-Fi), the pairing code, and
  **Forget this PC** (deletes the code from the phone). A VPN on the phone can hide the PC; the screen says so. When the code is
  missing, wrong, or the clocks of phone and PC differ by more than five minutes, the *Pairing code* step says which and what to do.
- **Testing** (collapsed): *Test match* and *Test transfer* put a made-up match or transfer into the app so you can see how it looks and
  that alerts work, without touching the bridge. Delete them afterwards.

## 3. The capture script

Open FC Live Editor, load your Career Mode save, and run `FC26_Auto_Capture.lua` (the launcher put it in `lua\scripts`).
It watches your club every 2 seconds and sends:

- every finished match, with real line-ups, ratings, scorers, man of the match and league position
- every transfer (permanent and loan, in and out) as it happens, as far as the game stores it (see
  "What the game stores about transfers" below)
- once per start and after every match, the squad's **contract end dates and loan details**, read from the game (see "Contracts and loans in the Squad")
- every few minutes, and soon after a match or a transfer, a **career report** (script 2.9.0 and newer): the league tables of
  your league, the leagues above and below it and the five big leagues (with every club's last five results), the fixtures
  still to play, your league's top scorers, shirt numbers, squad roles, wages, ratings, injuries (with their return day) and
  suspensions, the team sheet you picked, your club's honours, stadium and transfer budget, the European club ranking and,
  in June and July, last season's league and cup winners for Around the World. It is only sent when something changed, and
  the bridge keeps only the newest one waiting for the phone. It never opens the game tables `transfers` or
  `scenarioevents` (they closed the game during the table census).

The folder `live-editor-scripts` holds exactly two scripts. Keep them together in the same Live Editor scripts folder (the launcher does this):

| File | Purpose |
| --- | --- |
| `FC26_Auto_Capture.lua` | Sends everything to the phone. Run this one. |
| `FC26_AutoCapture_v1.1.1.lua` | The Project Atlas exporter (passes, shots, tackles, xG, real ratings). `FC26_Auto_Capture.lua` starts it for you. |

**Both files must sit in the same folder.** "Project Atlas" is only the name of that exporter; you do not need
any Atlas desktop app, just the `.lua` file. If it is missing, the Live Editor console says
`Project Atlas exporter: NOT running`. Matches and transfers are still sent, but no snapshots are written
(`Documents\FC26 Export\Snapshots` stays empty) and player stats are estimated.

Live Editor does not always tell a script which folder it runs from, so `FC26_Auto_Capture.lua` also looks in
Live Editor's working folder and `lua\scripts`, in `FC26-LE` on the Desktop (also when the Desktop is in
OneDrive), and in Downloads and Documents. If it still reports the exporter as missing, run
`FC26_AutoCapture_v1.1.1.lua` yourself first, then `FC26_Auto_Capture.lua`: it notices that the exporter is
already running. `auto_capture_monitor.log` says what was searched.

With the exporter running you get, under `Documents\FC26 Export`:

- `Snapshots\ready\snapshot_<date>_<id>` - a snapshot of the whole career (squad, contracts, loans, fixtures,
  competition progress, `manifest.json` and `export_log.txt`), taken when the script starts and after every match.
  If the Atlas desktop app is installed it may move finished snapshots to `imported` or `failed`.
- `MatchStats\ready\<folder>` - the detailed player statistics of each match.

When a match ends, `FC26_Auto_Capture.lua` waits up to 45 seconds for the Atlas files so the detailed numbers
are included. If none arrive, the match is sent from the game database and the app marks its player stats as
*estimated*.

Older scripts are kept in `live-editor-scripts/legacy/` and are not needed any more:

| File | Replaced by |
| --- | --- |
| `telemetry_auto_loop.lua`, `telemetry_sync.lua`, `telemetry_sender_v2.lua` | `FC26_Auto_Capture.lua` (run it directly). |
| `send_latest_match.lua` | **Re-send last match** on the app's PC Connection screen. |
| `telemetry_test_connection.lua` | The **Self check** on the PC Connection screen. |
| `FC26_MatchStatsExport.lua`, `FC26_Snapshot.lua` | Built into `FC26_AutoCapture_v1.1.1.lua`. Only use them on their own if you want the raw exports, and never at the same time as the exporter. |

`live-editor-scripts/tools/FC26_Probe.lua` is an optional read-only report (see step 6).

## Contracts and loans in the Squad

The Squad screen shows each player's **contract end** and, for players on loan, **who he is on loan from or to and until when**. These
are the game's own values, not estimates:

- The contract runs to **30 June** of the year the game stores for the player (`contractvaliduntil`); the app calculates the time left
  from the game's own date (the career date the script reports, or the date of your newest match), never from today's date on your phone.
- Loans come from the game's loan table. A loan with no known end date is shown without one.
- Colours: contracts end in under 6 months = red, under 12 months = amber; loans end in under 1 month = red, under 3 months = amber.
  **Needs attention** at the top of the Squad lists exactly those players, soonest first.
- If the script has not reported yet, or the player was added by hand, the app shows nothing rather than guessing. Open a player's edit
  dialog: the **From the game** section says what the game reported and when.

## What the game stores about transfers

FC 26 has no transfer-history table. The script therefore works out each deal from what the save does hold, and
says "unknown" where it cannot know instead of guessing:

| Part of a deal | Where it comes from | Reliable? |
| --- | --- | --- |
| Loans in and out, the other club, the day the loan began | loans table + the player's contract | yes |
| Signings: who, and the day they joined | join date in the player table + contract | yes |
| Sales: who, which club bought him, the day | the squad saved at the end of the last run + the buyer's join date | yes, from the second run on |
| Promotion from the academy | the player was in the youth academy list in the last run | yes, from the second run on |
| Fee of the **biggest** signing and the **biggest** sale of the season | manager history (the game keeps only these two, by surname) | yes |
| Every other fee, and the club a player was bought from | not stored by the game | no: shown as **Fee ?** / "Unknown club" in the app |
| Players returning from a loan | noticed and not sent as a transfer | - |

The very first run has no earlier squad to compare with, so it cannot see sales made before it, and it has to
guess promotion versus signing from the sign-on bonus (promoted players have none). The monitor log says
`Transfer rule: <player> -> <type> (<reason>)` for every deal, so a guess is visible; a wrong one can be
corrected in the app or in `manual_transfers.csv`.

### Filling the gaps: manual_transfers.csv

Create `Documents\FC26 Export\manual_transfers.csv`, one deal per line (`#` starts a comment):

```
date,player,type,fee_m,club
2028-07-06,Pedro Lima,Out (Permanent),20.0,Brighton
2028-07-13,Joe Crook,Out (Permanent),280K,Metaloglobus
2028-07-14,Oliver Ebner,In (Permanent),11.7,Athletic Club
2028-07-01,Denilson Fernandes,In (Permanent),free,Free Agents
```

`type` is `In (Permanent)`, `Out (Permanent)`, `In (Loan)`, `Out (Loan)` or `Promoted In` (short forms such as
`in`, `out`, `loan out`, `promoted` also work). `fee_m` is in millions (`26.0`, `€26.0M`, `280K`), `free`, or
empty when unknown. The file is read every time `FC26_Auto_Capture.lua` starts and each line is sent once. A
player listed there replaces what the script works out for that player and direction.

### Player-plus-cash deals

When another club takes one of your players as part of a deal for one of theirs, the save only shows who arrived and
who left. The script records both moves, each as its own entry with the club and the day (for example "Cole Swap, In
(Permanent), from Brighton" and "Pedro Lima, Out (Permanent), to Brighton" on the same date). The save does not record
that the two belong together or how much cash changed hands, so the fees show as "Fee ?" (unless one of them is the
season's biggest signing or sale, whose fee the game does keep). Put the real amounts in `manual_transfers.csv` or edit
the entries in the app.

## 4. Playing matches

When a match finishes, click **Advance** on the post-match summary to return to the Career Central Hub. EA FC
only writes results and player stats to the career database once you reach the Hub. Back on the Hub the match
is sent to your phone automatically.

What the match log contains, and where it comes from:

- **Lineup and substitutions.** Everyone who played is listed, including a player who is only on loan with you
  (he may be missing from the exporter's own file; he is then added from the game's record of the match, with
  his minutes but without invented stats, so his rating shows as "-" if the game does not give one). Each player
  carries the position he played, so the app lists the team from striker to goalkeeper. A substitution is shown with
  symbols (a green arrow and the minute for the player who came on, a red one for the player who went off), the
  substitute listed under the player he replaced. The pairing is worked out from the minutes played and the
  positions; if the game's record is not of this match, no substitutions are made up.
- **Goals.** Our scorers and the other side's scorers are named. The exporter does not always know who the other
  side's players are, so the script also compares every player's goal tally in the career database from before
  and after the match: whoever's tally went up scored. A goal that still cannot be named, such as an own goal,
  is shown as the club's, never as "Player". The exporter has no goal minutes, so the app shows none.
- **Man of the match** is taken from either team. If the best player was on the other side, his name is shown
  with "(Opponent)" and none of your players gets the star.
- **Goalkeeper saves** (and penalty saves) come from the exporter's player file. If the exporter reports none
  although you faced more shots on target than goals, the saves are estimated for your keeper as shots on target
  minus goals conceded.
- **League position** is worked out from the real results of the league (points, goal difference, goals scored,
  up to and including the match day). The "current table position" field in the game's own data is not used:
  it is not kept up to date during a season. If other clubs' results of the same day are not stored yet, the
  script waits up to 20 seconds for them.
- **Possession is left out.** The exporter does not store a possession figure, and a share worked out from the
  pass counts is not the number the game shows. The app therefore shows no possession for a match unless the game
  itself gave one. The ball-win and ball-loss counts in the export are event counts, not a percentage.
- **Cup matches have no league position.** The position is only worked out and shown for league matches (and
  promotion play-offs).

If the Live Editor script is not running, the bridge reports the match itself from the same files. It waits 45
seconds after an export appears before doing so, so that the script's own (richer) report normally arrives first;
the second report is then recognised as a duplicate and dropped. **Restart the bridge after updating it.**

## 5. Sending things by hand

Type these in the Live Editor Lua console while `FC26_Auto_Capture.lua` runs:

```
FC26_AutoCapture_Status()                  -- what is waiting, and the bridge's last answer
FC26_AutoCapture_SyncLatest()              -- send your latest match again
FC26_AutoCapture_SyncTransfers()           -- send this season's transfers again
FC26_BeamTransfer(name, type, fee, club)   -- send any transfer by hand
```

To check the whole chain without opening the game, use **Test match** / **Test transfer** under *Testing* on the PC Connection screen
(they only go into the app), or the older `pc-bridge/legacy/Send_Test_Match.bat` and `Send_Test_Transfer.bat`, which send a fake match
or transfer through the bridge to the phone.

### No flashing command windows

Earlier versions started a small program for every folder check and every message, and Windows showed a black
window for a second each time. The script now:

- saves every message as a small `.json` file in `Documents\FC26 Export\outbox`. The bridge reads that folder
  itself and tells the script that it does so by refreshing `bridge_watching.txt` every few seconds. While the
  heartbeat is fresh, no program is started at all. Only if the bridge is not running does the script fall back to
  a sender, which is started hidden and sends everything waiting in one go.
- checks folders from Lua instead of starting `cmd`, and starts the remaining helper programs without a window.

- creates every folder it needs with one command, together with 60 empty spare folders in
  `Documents\FC26 Export\spare_folders`. The Project Atlas exporter makes a new folder for every export; the
  script answers that by renaming one of the spare folders into place, which needs no program and shows no window.
  The stock is topped up when the script starts and fewer than 20 are left. So only the very first start on a PC
  can flash one window (none if hidden start is available); later starts and the time after a match are silent.
  The `spare_folders` folder is safe to delete: it is made again.

On start the script logs a line such as `Quiet mode - ...` in `auto_capture_monitor.log` and the console. It says
which of these methods this Live Editor build supports, how many spare folders are ready and how many windows were
opened so far. If windows still appear, send that line.

### Phone alerts

The app shows an alert when a new match arrives in the match log, when a transfer is recorded and when one of your players is
injured (a single alert for each, or one summary when many arrive at once). Alerts only appear for events from the last 24 hours, so
installing the app later does not announce old matches. They are raised when the app fetches from the bridge: about every 10 seconds
while the app is open, and in the background while **Keep syncing in the background** (Settings, *Live sync with your PC*) is on. A small
"Live sync" notice then stays in the notification area; swiping the app away from recent apps stops it.

In **Settings → Notifications**:

- **Matches**, **Transfers** and **Injuries** switch each kind of alert on or off for this phone. The data is still saved when an alert is off.
- The card says whether Android is allowing the app's alerts at all. **Send test alert** shows one at once; **Android settings** opens the
  page where notifications can be allowed again if they were blocked.
- Tapping an alert opens the matching screen (Matches, Transfers or Squad).

Android 13 and later ask for permission the first time the app opens. After updating the app the alert channel is replaced by a fresh
one ("Match & transfer alerts"), so an earlier choice to silence or block the old channel does not carry over.

### Estimated statistics

A match whose player statistics could not be read from the Project Atlas exporter is sent from the game database instead, and the app
marks it with an **Estimated** chip on the match card (tap it to see what that means and to edit the match). In the match history,
the **Estimated** filter shows only those matches. Matches with real statistics carry no mark.

## 6. Checking what the game really stores (the probe)

`live-editor-scripts/tools/FC26_Probe.lua` only reads (copy it into Live Editor's scripts folder to run it). Run it with a career loaded, wait for "Probe finished" in the
console, then open `Documents\FC26 Export\FC26_probe_<date>_<time>.txt`. It lists what the game records about
transfers, loans, joining dates, manager history and club finances. Live Editor prints many "no field" errors
while it runs; that is expected, because the probe tries several possible column names.

Use it when transfers, sales or fees do not show up as they should: the report shows the real column names, so
the capture script can be fixed against the real data instead of guesses.

## Does the transfer sync repeat every time?

Each time `FC26_Auto_Capture.lua` starts it checks this season's transfers, but it only sends deals whose key is
not yet in `beamed_transfers.txt`. A burst of transfers right after a start therefore only happens once (after an
update that changes how deals are described, or the first time a new `manual_transfers.csv` line is read). The
deals are sent again only if `beamed_transfers.txt` or `auto_capture_state.lua` is deleted, or if you run
`FC26_AutoCapture_SyncTransfers()` yourself.

## If something does not arrive

- The capture script sends to the **bridge**, and the app fetches from the bridge. The bridge must be running
  (run `FC26_Launcher.bat`) while the script runs. The app does not need to be open at that moment: the bridge keeps the latest
  100 events and the app picks them up the next time it checks. Opening the app alone is not enough.
- Delivery log: `Documents\FC26 Export\bridge_send_log.txt`. One line per attempt with the bridge's answer
  (200 = delivered). Anything undelivered is kept in `Documents\FC26 Export\outbox` and retried every 30 seconds,
  also after restarting Live Editor, so nothing is lost while the bridge is closed.
- To send a season's transfers again from scratch (for example after a script fix), close the capture script,
  delete the files in `Documents\FC26 Export\outbox` plus `beamed_transfers.txt` and `auto_capture_state.lua`
  in `Documents\FC26 Export`, start the bridge, then run `FC26_Auto_Capture.lua` again.
  Deleting `auto_capture_state.lua` also forgets which sales and promotions it had already seen, so do it only
  when you really want a fresh start. If the phone has not fetched the old events yet, stop the bridge and delete
  `pc-bridge\telemetry_events.json` first, otherwise the phone receives the old (wrong) version as well as the
  corrected one. The app merges a repeated deal (same player and day) into one entry and fills in a missing club or
  fee, but it cannot know that an old entry with a different day is the same deal.
- After updating to script version 2.5, the current season's transfers are sent once more with corrected types,
  dates and fees; the keys of the earlier versions are not reused.
- Open **PC Connection** in the app (menu) and press **Check again**: it names the step that is broken. The same self check is at `http://localhost:5050` on the PC.
- The bridge window prints a line for every request, every duplicate it ignored and every payload it refused.

## What the bridge handles for you

- A match reported twice (database + Atlas export) is stored once.
- A damaged or half-written message is refused (HTTP 400) so one bad message cannot stall the app.
- Accented names and the euro sign arrive intact.
- Match detail exports are found in `Documents\FC26 Export` even when Windows keeps Documents in OneDrive.

## Security

The whole story is in [SECURITY.md](SECURITY.md). In short:

- Your phone has to prove it knows the **pairing code** before the bridge tells it anything, and the app only accepts answers
  signed with that code. Other devices on your network get nothing but "a bridge is here".
- The phone can only read (news, health report) and ask for the last match again. The capture script's reports, the test
  buttons, Clear and a new pairing code work from this PC only.
- The firewall rule lets in devices on your own network, on Private or Domain networks only. Nothing is reachable from the
  internet; never forward port 5050 on your router.
- The bridge runs without administrator rights. The launcher starts Windows programs by their full path and asks before using
  files it found by searching.
- Not protected: the traffic is not encrypted, so someone already capturing your Wi-Fi traffic could read match data as it travels
  (not change it). A strong Wi-Fi password keeps them out.
- To remove the firewall rule: `netsh advfirewall firewall delete rule name="EA FC Telemetry Bridge"` in an administrator command prompt.

## Without the launcher

You can do the launcher's work by hand: copy the two `.lua` files from `live-editor-scripts` into Live Editor's `lua\scripts` folder, then
start the bridge with `python -I telemetry_bridge.py` in the `pc-bridge` folder (it shows the pairing code in its window and keeps the
window open while you play), and allow port 5050 for your local network in the Windows firewall yourself. The older starters in
`pc-bridge/legacy` (`Start_Bridge.bat`, `Start_Bridge_Python.bat`) now simply run `FC26_Launcher.bat`.
