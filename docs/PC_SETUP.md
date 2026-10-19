# PC setup: sending matches and transfers to your phone

The app can fill in your career by itself while you play. Two small things run on your PC:

1. **The bridge** (`pc-bridge/`). A tiny web server that holds the events until your phone fetches them.
2. **A capture script** (`live-editor-scripts/`) that runs inside FC Live Editor and sends every finished match
   and every transfer to the bridge.

Phone and PC must be on the same Wi-Fi network.

## 1. Start the bridge

Double-click `pc-bridge/Start_Bridge.bat` (PowerShell engine) or `pc-bridge/Start_Bridge_Python.bat`
(needs Python). Keep the window open while you play. It prints your PC's address, for example
`http://192.168.1.20:5050`. Type that address into the app under **Settings → Live Telemetry**, then press
**Test connection**.

If Windows answers with HTTP 503, run `Start_Bridge.bat` as administrator once, or use the Python version.

## 2. Start the capture script

Open FC Live Editor, load your Career Mode save, and run `live-editor-scripts/FC26_Auto_Capture.lua`.
It watches your club every 2 seconds and sends:

- every finished match, with real line-ups, ratings, scorers, man of the match and league position
- every transfer (permanent and loan, in and out) as it happens, as far as the game stores it (see
  "What the game stores about transfers" below)

Keep these two files together in the same Live Editor scripts folder:

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

`FC26_MatchStatsExport.lua` and `FC26_Snapshot.lua` are the complete original exporters that are built into
`FC26_AutoCapture_v1.1.1.lua`. Only use them on their own if you want the raw exports; never run them at the
same time as `FC26_AutoCapture_v1.1.1.lua`.

Other scripts:

| File | Purpose |
| --- | --- |
| `telemetry_auto_loop.lua` | Starts `FC26_Auto_Capture.lua` for you. |
| `telemetry_sync.lua` | The older all-in-one script. It stands down by itself while `FC26_Auto_Capture.lua` runs. |
| `FC26_Probe.lua` | Read-only report of what the game stores about transfers, loans and finances (see step 5). |
| `send_latest_match.lua` | Sends your latest match in one click. |
| `telemetry_test_connection.lua` | Checks that the bridge is reachable and prints what is wrong if it is not. |
| `telemetry_sender_v2.lua` | Small sender used by older setups. |

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

## 3. Playing matches

When a match finishes, click **Advance** on the post-match summary to return to the Career Central Hub. EA FC
only writes results and player stats to the career database once you reach the Hub. Back on the Hub the match
is sent to your phone automatically.

What the match log contains, and where it comes from:

- **Lineup and substitutions.** Everyone who played is listed, including a player who is only on loan with you
  (he may be missing from the exporter's own file; he is then added from the game's record of the match, with
  his minutes but without invented stats, so his rating shows as "-" if the game does not give one). Each
  substitution is listed as "player A off, player B on" with the minute. The pairing is worked out from the
  minutes played and the positions; if the game's record is not of this match, no substitutions are made up.
- **Man of the match** is taken from either team. If the best player was on the other side, his name is shown
  with "(Opponent)" and none of your players gets the star.
- **Goalkeeper saves** (and penalty saves) come from the exporter's player file. If the exporter reports none
  although you faced more shots on target than goals, the saves are estimated for your keeper as shots on target
  minus goals conceded.
- **League position** is worked out from the real results of the league (points, goal difference, goals scored,
  up to and including the match day). The "current table position" field in the game's own data is not used:
  it is not kept up to date during a season. If other clubs' results of the same day are not stored yet, the
  script waits up to 20 seconds for them.
- **Possession is an estimate.** The exporter does not store a possession figure. It is estimated from each
  team's share of the passes and shown as "~NN%" in the app. The ball-win and ball-loss counts in the
  export are event counts, not a percentage.

If the Live Editor script is not running, the bridge reports the match itself from the same files. It waits 45
seconds after an export appears before doing so, so that the script's own (richer) report normally arrives first;
the second report is then recognised as a duplicate and dropped. **Restart the bridge after updating it.**

## 4. Sending things by hand

Type these in the Live Editor Lua console while `FC26_Auto_Capture.lua` runs:

```
FC26_AutoCapture_Status()                  -- what is waiting, and the bridge's last answer
FC26_AutoCapture_SyncLatest()              -- send your latest match again
FC26_AutoCapture_SyncTransfers()           -- send this season's transfers again
FC26_BeamTransfer(name, type, fee, club)   -- send any transfer by hand
```

`pc-bridge/Send_Test_Match.bat` and `Send_Test_Transfer.bat` send a fake match or transfer so you can check the
whole chain without opening the game.

### No flashing command windows

Earlier versions started a small program for every folder check and every message, and Windows showed a black
window for a second each time. The script now:

- saves every message as a small `.json` file in `Documents\FC26 Export\outbox`. The bridge reads that folder
  itself and tells the script that it does so by refreshing `bridge_watching.txt` every few seconds. While the
  heartbeat is fresh, no program is started at all. Only if the bridge is not running does the script fall back to
  a sender, which is started hidden and sends everything waiting in one go.
- checks folders from Lua instead of starting `cmd`, and starts the remaining helper programs without a window.

On start the script logs a line such as `Quiet mode - ...` in `auto_capture_monitor.log` and the console. It says
which of these methods this Live Editor build supports. If it says that hidden start is not available, a window
can still flash when the exporter creates a new folder; the bridge being open is then what keeps everything else
quiet.

## 5. Checking what the game really stores (the probe)

`live-editor-scripts/FC26_Probe.lua` only reads. Run it with a career loaded, wait for "Probe finished" in the
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
  (step 1) while the script runs. The app does not need to be open at that moment: the bridge keeps the latest
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
- Run `telemetry_test_connection.lua`.
- The bridge window prints a line for every request, every duplicate it ignored and every payload it refused.

## What the bridge handles for you

- A match reported twice (database + Atlas export) is stored once.
- A damaged or half-written message is refused (HTTP 400) so one bad message cannot stall the app.
- Accented names and the euro sign arrive intact.
- Match detail exports are found in `Documents\FC26 Export` even when Windows keeps Documents in OneDrive.

## Security

- Your phone has read-only access: it only fetches `/events`.
- Anything that writes (the Live Editor scripts, the dashboard test buttons, Clear) must come from this same PC.
  Writes from other devices on your network are refused (HTTP 403).
- The Windows firewall rule created by `Start_Bridge.bat` only allows devices on your local subnet.
- Web pages in a browser cannot send data to the bridge (no CORS, Host and Origin checks).
- Anyone on your local network can still **read** `/events` if they know the address. Do not run the bridge on
  shared or public Wi-Fi.
