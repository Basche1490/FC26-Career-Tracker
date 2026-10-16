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
- every transfer (permanent and loan, in and out) as it happens

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

## 3. Playing matches

When a match finishes, click **Advance** on the post-match summary to return to the Career Central Hub. EA FC
only writes results and player stats to the career database once you reach the Hub. Back on the Hub the match
is sent to your phone automatically.

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

## 5. Checking what the game really stores (the probe)

`live-editor-scripts/FC26_Probe.lua` only reads. Run it with a career loaded, wait for "Probe finished" in the
console, then open `Documents\FC26 Export\FC26_probe_<date>_<time>.txt`. It lists what the game records about
transfers, loans, joining dates, manager history and club finances. Live Editor prints many "no field" errors
while it runs; that is expected, because the probe tries several possible column names.

Use it when transfers, sales or fees do not show up as they should: the report shows the real column names, so
the capture script can be fixed against the real data instead of guesses.

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
