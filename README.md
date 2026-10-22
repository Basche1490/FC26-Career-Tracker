# FC26 Career Tracker

An Android app for keeping track of an EA SPORTS FC 26 Career Mode save: squad, academy, transfers, matches,
seasons, trophies and finances. With the optional PC companion it fills itself in while you play.

This is the cleaned-up successor to the original FC Tracker app. It has the same features, plus safer storage,
background sync and automated tests (see [What is new](#what-is-new)).

## Install

1. Open the **Releases** page of this repository and download `FC26-Career-Tracker.apk` from the
   **Latest build** release. (Every build is also kept under *Actions → newest run → Artifacts*.)
2. Open the file on your phone and allow installing from this source when Android asks.

The app installs next to the old FC Tracker (it has its own package name, `com.fctracker.career`) and keeps its
own data. To move a career over, use **Settings → Export** in the old app and **Settings → Import** in this one.
Re-enter your AI API key afterwards; keys are never part of a backup.

Updates install over the previous build: every build is signed with the same key. That key is committed to the
repository (`signing/`) so builds stay updatable. It is meant for sideloading only, not for the Play Store.

## Features

- **Dashboard** with club, season, form, objectives and quick links
- **Squad** with player cards, headshots, edit dialog, formation board and tactics
- **Academy** for youth players
- **Transfers** log with fees, loans, income and spending
- **Matches** with scorers, assists, ratings, man of the match, line-ups, substitutions and league table
- **Season summary** and **Career summary** with trophies, awards and history
- **AI assistant** (Google Gemini, Claude, Grok, ChatGPT or any compatible endpoint) with your own API key
- **Multiple save slots**, notes, encrypted export/import
- **Live telemetry** from your PC: finished matches, transfers and injuries arrive on the phone by themselves, with
  notifications you can switch on and off per kind (Settings → *Notifications*). Keeps working while the app is in the background
  (Settings → *Keep syncing in the background*); swiping the app away from recent apps stops it and removes the notification.
- **Connection health**: a small pill at the top of the main screens (*Live*, *Check*, *Down*, *Not set up*) and a **PC Connection** screen with a
  self check of the phone, bridge and capture script, version check, *Find my PC* and a **Re-send last match** button.
- **First-run guide** that walks through the PC setup and ticks each step off as it works.
- **Match history** with filters (competition, season, result, *Estimated*), a form strip and swipe to edit or delete; matches whose
  statistics are estimated are marked as such.
- **Transfer windows** (Summer / Winter / In season) with spent, received and net figures; tap a headshot to open the player.
- **Contract and loan end dates** in the Squad, read from the game, with a *Needs attention* list.

## PC companion

`pc-bridge/` and `live-editor-scripts/` send data from FC Live Editor to the app. **One file sets it up:** unzip
`FC26-PC-Tools.zip` from the **Latest build** release and double-click `FC26_Launcher.bat`. It finds the bridge and Live Editor wherever
they are, installs the two capture scripts, lets the phone in through the firewall and starts the bridge in the background with no window.
The phone pairs with the PC through a **pairing code** the launcher shows: only a phone with that code can read anything
from the bridge, and the app only believes answers signed with it ([docs/SECURITY.md](docs/SECURITY.md)).
Setup steps and troubleshooting are in [docs/PC_SETUP.md](docs/PC_SETUP.md). The detailed player statistics come from the Project Atlas
exporter (`FC26_AutoCapture_v1.1.1.lua`, which has the snapshot and match-statistics exporters built in) by its original author
on r/seriousfifacareers.

## What is new

- The capture script (2.10) no longer flashes a command window while you play: messages wait in the outbox for the bridge,
  and the Project Atlas exporter (1.1.3, a small change to the original) makes its folders silently and names its newest
  export in a file. The game's career report now also brings the trophies you win, player attributes and form, the
  squad's league statistics and the club's prestige.
- Career data is written to one file per save slot, atomically, with a backup copy. A crash or power loss in the
  middle of a save can no longer damage a career.
- The AI API key is stored encrypted with the Android Keystore and is left out of save files and backups.
- Phone and PC pair with a code: every request is signed (HMAC-SHA256) and every answer checked, other devices on the network
  get nothing, the firewall rule is limited to home networks, and the launcher starts Windows programs by full path.
- Live sync runs in a foreground service, so matches arrive while the app is in the background. Swiping the app away from recent apps stops it and removes its notification. It can be turned off in Settings.
- The match and transfer handling is one tested class (`TelemetryProcessor`) instead of code spread through the
  screen logic; about 60 unit tests run on every build.
- Player stats that the game database could not provide are flagged as *estimated* instead of looking exact.
- Fixed: signed players got a made-up age of 25; editing a match erased its goal and substitution details;
  matchday numbers could be counted twice; man-of-the-match counts and injury handling were off.
- Removed: unused libraries and image files, and the design-mockup gallery screen.

## Build

CI does everything: push to `main` (or any `claude/**` branch) and GitHub Actions runs the unit tests, builds the
signed release APK, uploads it as an artifact and, on `main`, refreshes the **Latest build** release.

To build locally you need JDK 17 and Gradle 9.3 (or Android Studio):

```
gradle :app:testDebugUnitTest
gradle :app:assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`.

## Project layout

```
app/src/main/java/com/fctracker/career/
  Models.kt            data classes that make up a career (AppState and friends)
  MainViewModel.kt     screen logic
  data/                CareerStore (state + autosave), SaveStore (files), SecretStore (Keystore), PlayerDatabase
  telemetry/           TelemetryProcessor (events -> career), TelemetrySync (polling), LiveSyncService, notifications
  ai/                  AiClient
  ui/                  Compose screens and theme
app/src/test/          unit tests
FC26_Launcher.bat      the one launcher for the PC side (finds, installs, starts)
pc-bridge/             the PC bridge (PowerShell and Python versions); legacy/ holds the older starters
live-editor-scripts/   the two FC Live Editor scripts; legacy/ and tools/ hold older scripts and the probe
tests/                 Lua, bridge (Python + PowerShell) and launcher tests
docs/                  PC setup guide and security notes
```
