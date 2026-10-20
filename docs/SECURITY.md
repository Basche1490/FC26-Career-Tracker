# Security: how the phone and the PC talk, and who else can

This page explains what protects the connection between the app and the PC bridge, what an attacker on your network
could and could not do, and what is still not protected. The short version is at the top. The details are for anyone
who wants to check the work.

## The short version

- **The phone has to prove it knows the pairing code before the bridge tells it anything.** The code is shown on the PC
  only (in the launcher window, on the bridge page at `http://localhost:5050`, and with `FC26_Launcher.bat status`).
  You type it into the app once. Without the code a device on your network gets nothing but "a bridge is here, version 3.0.0".
- **The phone checks that every answer comes from your bridge.** An answer that is not signed with your code is thrown
  away, so a fake "bridge" on the network cannot slip invented matches or transfers into your career.
- **The phone can only read.** It can fetch the news, the health report, and ask for the last match to be sent again.
  Everything that changes something (the capture script's reports, the test buttons, Clear, a new pairing code) works
  only from the PC itself.
- **Nothing is reachable from the internet.** The Windows firewall rule lets in devices on your own network only, and only
  on networks Windows treats as Private (home) or Domain (work). On a Public network (a café, a hotel) the bridge stays closed.
- **The bridge cannot be used to get into your PC or your network.** It runs without administrator rights, never runs
  anything it receives, never opens files named in a request, and only writes its own files.
- **What is not protected:** the connection is not encrypted. Someone who can already see the traffic on your Wi-Fi can
  read your match and transfer data as it travels (they still cannot change it or ask for more). See
  [What is still open](#what-is-still-open).

## What to do

1. Run `FC26_Launcher.bat` and type the pairing code it shows into the app (menu → PC Connection).
2. Keep the code to yourself. Do not post a screenshot of the launcher window or the bridge page.
3. If you think someone else has the code, make a new one: `FC26_Launcher.bat newcode`, or **New pairing code** on the
   bridge page. The old code stops working at once, on every phone. Type the new one into the app.
4. Never forward port 5050 on your router, and do not put the bridge behind a tunnel (ngrok and the like). Both would put
   it on the internet; the bridge also refuses anything that arrives through a tunnel's forwarding headers.
5. Use WPA2 or WPA3 on your Wi-Fi. If you have a guest network, keep the PC and the phone off it.
6. On the PC, the bridge page shows a **Network access** line. If it says a device was refused and that was not your phone,
   make a new pairing code.

## The pieces

| Piece | What it may do |
| --- | --- |
| **Phone (the app)** | `GET /events`, `GET /status`, `POST /resend`, each signed with the pairing code. Nothing else. |
| **This PC** (a program on it, or a browser on it opening `http://localhost:5050`) | Everything: the dashboard, the test buttons, Clear, a new pairing code, and the capture script's reports. |
| **Anybody else on the network** | `GET /ping`: `{"app":"fc26-bridge","protocol":3,"version":"3.0.0","name":"<PC name>","auth":"hmac-sha256"}`. That is what **Find my PC** uses. Anything else is refused. |
| **A web page in a browser** | Nothing. No CORS headers, and Host / Origin checks refuse other sites and DNS rebinding. |

"This PC" means all three: the connection comes from the loopback address, it is addressed to `localhost`, `127.0.0.1`
or `[::1]`, and it carries none of the headers a proxy or tunnel adds (`X-Forwarded-For`, `Forwarded`, `Via`, `X-Real-IP`
and the like). Opening the bridge page by the PC's network address (`http://192.168.x.x:5050`) therefore does not count as
this PC; open `http://localhost:5050` instead.

## The pairing code

- 16 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no I, O, 0 or 1), shown in groups of four:
  `ABCD-EFGH-JKLM-NPQR`. That is 80 bits of randomness, made with the operating system's secure random generator.
- Stored on the PC in `%APPDATA%\FC26Tracker\bridge.key`, a folder only your Windows account (and the PC's administrators) can open. The launcher and
  both bridges use this one file; a new code written there is used from the bridge's next request on. The code is never
  written to the bridge's log.
- Stored on the phone encrypted with a key that lives in the Android Keystore, the same way as the AI API key. It is not
  part of backups, exports or save files.
- Key: `SHA-256("fc26-bridge-v1:" + code without dashes, in capitals)`.

## Signed requests and answers

The phone adds three headers to every request:

| Header | Value |
| --- | --- |
| `X-FC-Time` | Unix time in seconds |
| `X-FC-Nonce` | 16 random bytes as 32 lowercase hex characters, new for every request |
| `X-FC-Sig` | `HMAC-SHA256(key, "FC26-REQ-V1\n" + METHOD + "\n" + path + "\n" + time + "\n" + nonce + "\n" + hex(SHA-256(body)))`, lowercase hex |

The bridge checks, in this order:

1. The address is not locked out (see below).
2. The three headers are there and well formed (otherwise `401 unsigned`).
3. The signature is right, compared in constant time (otherwise `401 bad_signature`).
4. The time is within 5 minutes of the PC's clock (otherwise `401 clock_skew`, with the bridge's time so the app can say
   how far apart the clocks are).
5. The nonce was not used before (otherwise `401 replay`). Nonces are remembered for 10 minutes, so a recorded request
   cannot be played again, neither now nor later.

The bridge signs its answer with the header
`X-FC-Sig: HMAC-SHA256(key, "FC26-RESP-V1\n" + nonce of the request + "\n" + HTTP status + "\n" + hex(SHA-256(body)))`.
The app throws away any answer without that header or with a wrong one. Because the request's nonce is part of it, an old
answer cannot be replayed either.

**Lockout:** 10 wrong signatures (or unsigned requests, or replays) from one address within 60 seconds lock that address
out for 60 seconds (`429`, with `Retry-After`). Other devices are not affected, and `/ping` stays open. With an 80-bit code,
guessing is hopeless anyway; the lockout keeps the log quiet and the bridge responsive. When the app's code is refused, the
app asks only once a minute, so a phone with an old code never locks itself out.

The same rules are written three times: `pc-bridge/telemetry_bridge.py`, `pc-bridge/bridge.ps1` and the app's
`BridgeAuth.kt`. All three are tested against the same numbers in `tests/vectors/auth_vectors.json` (and those numbers were
checked once more with `openssl`), and the app's real client code was run against both real bridges.

## The launcher

- Windows programs are started by their full path (`%SystemRoot%\System32\...`): a `powershell.exe` or `netsh.exe` left in
  Downloads next to the launcher cannot run instead, with or without administrator rights.
- The only things it asks Windows to do as administrator: the firewall rule, and (only when the PowerShell bridge is used)
  the permission to listen on port 5050. The commands are fixed text with no outside input in them.
- **Firewall rule** "EA FC Telemetry Bridge": TCP port 5050, devices on the local subnet only, Private and Domain networks
  only. A rule made by an earlier launcher for every network is replaced. If Windows treats the network you are on as
  Public, the launcher asks what you want: open Windows Settings to make it a Private network (it never changes this
  itself), allow the bridge on Public networks too (you choose it, and the pairing code still protects it), or leave it closed.
- **Port permission** (PowerShell bridge only): for your Windows user only. Earlier launchers gave it to Everyone, which let
  any program of any user take the port; that is replaced.
- Files it finds by searching the disk (rather than next to itself, or used before) are only used after you say yes, because
  the launcher runs them.
- Python runs the bridge in isolated mode (`-I`): it ignores `PYTHON*` settings and loads no modules from the bridge folder or
  the current folder.
- Older starters in `pc-bridge/legacy` (`Start_Bridge.bat`, `Start_Bridge_Python.bat`) now just run the launcher. The old
  `Start_Bridge.bat` used to run the bridge as administrator, give the port to Everyone and switch every Public network to Private.

## What is still open

- **Not encrypted.** The data travels as plain HTTP on your Wi-Fi. Someone who is on your Wi-Fi and can capture its traffic
  can read matches, transfers and the health report (club name, folder paths on the PC, script version) as they travel. They
  cannot change them, cannot ask the bridge for anything, and cannot work out the pairing code from what they see. Your Wi-Fi
  password (WPA2/WPA3) is what keeps strangers from capturing the traffic in the first place. Encrypting the connection itself
  (HTTPS with a certificate the app pins at pairing) is possible as a later step.
- **This PC is trusted.** Any program running on the PC under your account can use the bridge page, read the pairing code
  file and read the export folder anyway. The bridge protects you from other devices, not from malware on the PC itself.
- **A tunnel at the network level** (port forwarding set up with administrator rights, SSH port forwarding) looks to the
  bridge like this PC. Only you can set one up; do not.
- **Not yet tested on a real Windows PC.** Everything above runs in the automated tests (Python, PowerShell 7, the app's
  code on a plain JVM, and the whole launcher once against the real bridge), on Linux. The Windows-only parts (firewall rule,
  port permission, network type, Windows PowerShell 5.1) are written for Windows and checked as far as Linux allows.

## Checking the downloads

The Latest build release lists the SHA-256 of `FC26-Career-Tracker.apk` and `FC26-PC-Tools.zip` in its notes and in
`SHA256SUMS.txt`. To check the zip on the PC, open PowerShell in the download folder and run:

```
Get-FileHash .\FC26-PC-Tools.zip -Algorithm SHA256
```

The number must match the one in the release.
