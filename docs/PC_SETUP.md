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

## If something does not arrive

- Delivery log: `Documents\FC26 Export\bridge_send_log.txt`. One line per attempt with the bridge's answer
  (200 = delivered). Anything undelivered is kept in `Documents\FC26 Export\outbox` and retried every 30 seconds,
  also after restarting Live Editor, so nothing is lost while the bridge is closed.
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
