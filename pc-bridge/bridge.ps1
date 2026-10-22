# EA Sports FC Live Telemetry Bridge (Native PowerShell - No Python Required)
param(
    [int]$Port = 5050
)

$ErrorActionPreference = "Continue"
$EventsFile = Join-Path $PSScriptRoot "telemetry_events.json"
$BridgeVersion = "3.2.0"
$BridgeProtocol = 3             # 3 = the phone must prove it knows the pairing code (docs/SECURITY.md)
$MinScriptVersion = "2.8.0"     # older capture scripts do not report their health or answer a re-send request
$IsWindowsHost = ($env:OS -eq 'Windows_NT')   # Windows PowerShell 5.1 has no $IsWindows
$ScriptAliveSeconds = 20        # the heartbeat is written on career events (often, but not in matches or menus)
$PhoneAliveSeconds = 30         # the app asks the bridge for news every 10 seconds
$ResendMinSeconds = 10          # at most one "re-send the last match" request per this many seconds
$StartedAt = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$global:PhoneSeen = @{ Time = [long]0; Ip = "" }
$global:ResendLast = [long]0
$global:AuthStats = @{ Rejected = 0; Time = [long]0; Ip = ""; Code = "" }
$global:AuthLogged = @{}

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host " EA SPORTS FC TELEMETRY BRIDGE $BridgeVersion (Native PowerShell)" -ForegroundColor Yellow
Write-Host " Direct PC-to-Mobile Connection" -ForegroundColor Green
Write-Host "===================================================" -ForegroundColor Cyan

# Check network profiles
$profiles = Get-NetConnectionProfile -ErrorAction SilentlyContinue
foreach ($p in $profiles) {
    if ($p.NetworkCategory -eq "Private") {
        Write-Host "🟢 Network profile '$($p.Name)': Private (LAN traffic permitted)" -ForegroundColor Green
    } else {
        Write-Host "⚠️ Network profile '$($p.Name)': $($p.NetworkCategory)" -ForegroundColor Yellow
        Write-Host "   (Note: Windows blocks phone traffic on Public networks. Switch to Private in Windows Settings if phone cannot connect)" -ForegroundColor DarkGray
    }
}

# Find local IP addresses, prioritizing real Wi-Fi & Ethernet
$allIpObjs = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { 
    $_.IPAddress -notlike "127.*" -and 
    $_.IPAddress -notlike "169.254.*" 
}

$wifiIps = @()
$otherIps = @()
foreach ($ipObj in $allIpObjs) {
    $ip = $ipObj.IPAddress
    $alias = $ipObj.InterfaceAlias
    if ($alias -match "WSL|vEthernet|VirtualBox|VMware|Docker|Tailscale") {
        # Virtual adapter
    } elseif ($ip -like "192.168.*" -or $ip -like "10.*" -or ($ip -like "172.*" -and $alias -match "Wi-Fi|Ethernet|WLAN")) {
        $wifiIps += $ip
    } else {
        $otherIps += $ip
    }
}

$primaryIp = if ($wifiIps.Count -gt 0) { $wifiIps[0] } elseif ($otherIps.Count -gt 0) { $otherIps[0] } else { "127.0.0.1" }
$displayIps = ($wifiIps + $otherIps) | Select-Object -Unique

Write-Host ""
Write-Host "⭐ ENTER THIS IN YOUR ANDROID APP:" -ForegroundColor Yellow
Write-Host "   http://${primaryIp}:${Port}" -ForegroundColor Green
if ($displayIps.Count -gt 1) {
    Write-Host "Other detected network interfaces:" -ForegroundColor DarkGray
    foreach ($ip in $displayIps) {
        if ($ip -ne $primaryIp) {
            Write-Host "   http://${ip}:${Port}" -ForegroundColor DarkGray
        }
    }
}

Write-Host ""
Write-Host "🌐 WEB DASHBOARD & MANUAL TESTING:" -ForegroundColor Yellow
Write-Host "   http://localhost:${Port}" -ForegroundColor White
Write-Host "===================================================" -ForegroundColor Cyan

# =========================================================================
# SMALL HELPERS
# =========================================================================
# CSV exports contain decimals ("86.4"), empty cells and sometimes text. A bare [int]"86.4" throws, which
# used to abort the whole import silently, so every conversion goes through these helpers.
function ConvertTo-Dbl {
    param($Value, [double]$Default = 0.0)
    if ($null -eq $Value) { return $Default }
    $s = ([string]$Value).Trim()
    if ($s -eq "") { return $Default }
    $d = 0.0
    if ([double]::TryParse($s, [System.Globalization.NumberStyles]::Float, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$d)) {
        if ([double]::IsNaN($d) -or [double]::IsInfinity($d)) { return $Default }
        return $d
    }
    return $Default
}

function ConvertTo-Int {
    param($Value, [int]$Default = 0)
    $d = ConvertTo-Dbl $Value ([double]$Default)
    if ($d -gt 2147483647 -or $d -lt -2147483648) { return $Default }
    return [int][Math]::Round($d, [MidpointRounding]::AwayFromZero)
}

# 0.55 -> 55 ; 55 -> 55
function ConvertTo-Pct {
    param($Value)
    $x = ConvertTo-Dbl $Value 0.0
    if ($x -gt 0 -and $x -le 1) { return ($x * 100.0) }
    return $x
}

# First argument unless it is empty (CSV cells are strings, so "0" counts as a value)
function Get-FirstValue {
    param($A, $B)
    if ($null -ne $A -and ([string]$A).Trim() -ne "") { return $A }
    return $B
}

function Test-ContainsAny {
    param([string]$Text, [string[]]$Keys)
    $t = ([string]$Text).ToLower()
    foreach ($k in $Keys) {
        if ($k -and $t.Contains($k.ToLower())) { return $true }
    }
    return $false
}

# Removes the bogus "(Pens: 255-0)" text the game engine's "no shootout" value (255) used to produce
function Remove-PenaltyNoise {
    param([string]$Text)
    if ([string]::IsNullOrEmpty($Text)) { return $Text }
    $s = $Text
    $s = [regex]::Replace($s, '\s*\(Pens:\s*255-\d+\)', '', 'IgnoreCase')
    $s = [regex]::Replace($s, '\s*\(Pens:\s*\d+-255\)', '', 'IgnoreCase')
    $s = [regex]::Replace($s, '\s*\(Pens:\s*0-0\)', '', 'IgnoreCase')
    $s = [regex]::Replace($s, '\b255\s*penalties\b', '0 penalties', 'IgnoreCase')
    return $s
}

# Compacts JSON text and escapes raw control characters (a raw newline or tab) inside strings, which strict
# JSON parsers reject. Whitespace between tokens is dropped. String-aware, so string contents are preserved.
function ConvertTo-CompactJson {
    param([string]$Text)
    $sb = New-Object System.Text.StringBuilder ($Text.Length)
    $inStr = $false
    $esc = $false
    foreach ($ch in $Text.ToCharArray()) {
        $c = [int]$ch
        if ($inStr) {
            if ($esc) { [void]$sb.Append($ch); $esc = $false }
            elseif ($c -eq 92) { [void]$sb.Append($ch); $esc = $true }          # backslash
            elseif ($c -eq 34) { [void]$sb.Append($ch); $inStr = $false }       # closing quote
            elseif ($c -lt 32) {
                if ($c -eq 10) { [void]$sb.Append('\n') }
                elseif ($c -eq 13) { [void]$sb.Append('\r') }
                elseif ($c -eq 9) { [void]$sb.Append('\t') }
                else { [void]$sb.Append(('\u{0:x4}' -f $c)) }
            }
            else { [void]$sb.Append($ch) }
        } else {
            if ($c -eq 34) { $inStr = $true; [void]$sb.Append($ch) }
            elseif ($c -le 32) { }                                              # whitespace between tokens
            else { [void]$sb.Append($ch) }
        }
    }
    return $sb.ToString()
}

# Strict JSON-object check. Returns the parsed object, or $null when the text is not a JSON object.
function ConvertFrom-JsonObject {
    param([string]$Text)
    try {
        $o = $Text | ConvertFrom-Json -ErrorAction Stop
        if ($o -is [System.Management.Automation.PSCustomObject]) { return $o }
    } catch {}
    return $null
}

# Initialize Events Cache
$global:EventList = [System.Collections.Generic.List[string]]::new()
$global:EventSeq = 0
$DedupeWindowSeconds = 600

if (Test-Path $EventsFile) {
    try {
        $existing = @(Get-Content $EventsFile -Raw -Encoding UTF8 | ConvertFrom-Json)
        $dropped = 0
        foreach ($e in $existing) {
            if ($null -eq $e) { continue }
            if ($e.message -is [string]) {
                $e.message = Remove-PenaltyNoise $e.message
                # An old event that looks like JSON but is malformed would stall the phone app for every event
                # after it, so it is dropped here.
                if ($e.message.TrimStart().StartsWith("{") -and ($null -eq (ConvertFrom-JsonObject $e.message))) {
                    $dropped++
                    continue
                }
            }
            $global:EventList.Add(($e | ConvertTo-Json -Compress))
        }
        Write-Host "Loaded $($global:EventList.Count) historical events from cache." -ForegroundColor DarkGray
        if ($dropped -gt 0) { Write-Host "  (dropped $dropped malformed stored event(s))" -ForegroundColor Yellow }
    } catch {
        Write-Host "Could not read $EventsFile ($($_.Exception.Message)). Starting with an empty list; the old file was kept as .corrupt" -ForegroundColor Yellow
        try { Copy-Item -LiteralPath $EventsFile -Destination "$EventsFile.corrupt" -Force } catch {}
        $global:EventList.Clear()
    }
}

function Save-TelemetryEvents {
    try {
        $text = "[" + ($global:EventList -join ",") + "]"
        $tmp = "$EventsFile.tmp"
        [System.IO.File]::WriteAllText($tmp, $text, (New-Object System.Text.UTF8Encoding($false)))
        Move-Item -LiteralPath $tmp -Destination $EventsFile -Force
    } catch {
        Write-Host "  (could not save $EventsFile : $($_.Exception.Message))" -ForegroundColor Yellow
    }
}

# Function to add event
function Add-TelemetryEvent {
    param(
        [string]$Title,
        [string]$Message,
        [string]$RawPayload,
        [string]$ReplaceKind = ""
    )
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $global:EventSeq++
    $formattedEvent = [PSCustomObject]@{
        event = "message"
        id = "ev-$timestamp-$($global:EventSeq)"
        time = $timestamp
        title = $Title
        message = if ($RawPayload) { $RawPayload } else { $Message }
    }
    $jsonStr = $formattedEvent | ConvertTo-Json -Compress
    # Career and squad reports carry the whole picture: only the newest of each kind waits for the phone.
    if ($ReplaceKind) {
        for ($i = $global:EventList.Count - 1; $i -ge 0; $i--) {
            $old = ConvertFrom-JsonObject ([string]$global:EventList[$i])
            if ($null -eq $old) { continue }
            $inner = ConvertFrom-JsonObject ([string]$old.message)
            if ($null -ne $inner -and [string]$inner.dataType -eq $ReplaceKind) { $global:EventList.RemoveAt($i) }
        }
    }
    $global:EventList.Add($jsonStr)

    # Keep last 100 events
    while ($global:EventList.Count -gt 100) {
        $global:EventList.RemoveAt(0)
    }

    Save-TelemetryEvents
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] TELEMETRY SAVED: $Title" -ForegroundColor Green
}

# -------------------------------------------------------------------------
# Match de-duplication. The Lua script and the hot-folder importer can both report the same match;
# the first one to arrive wins and the other is dropped.
# -------------------------------------------------------------------------
function ConvertTo-TeamKey {
    param($Name)
    return ([regex]::Replace(([string]$Name).ToLower(), '[^a-z0-9]', ''))
}

function Test-DuplicateMatch {
    param([string]$Opponent, [int]$GoalsFor, [int]$GoalsAgainst, [string]$FixtureKey)
    $opp = ConvertTo-TeamKey $Opponent
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    for ($i = $global:EventList.Count - 1; $i -ge 0; $i--) {          # newest first
        $ev = $null
        try { $ev = $global:EventList[$i] | ConvertFrom-Json } catch { $ev = $null }
        if ($null -eq $ev) { continue }
        if (($now - (ConvertTo-Int $ev.time)) -gt $DedupeWindowSeconds) { break }
        $msg = $ev.message
        if (($msg -isnot [string]) -or (-not $msg.TrimStart().StartsWith("{"))) { continue }
        $other = ConvertFrom-JsonObject $msg
        if ($null -eq $other -or $other.dataType -ne "match") { continue }
        if ($FixtureKey -and ([string]$other.fixtureKey -eq $FixtureKey)) { return $true }
        $oOpp = ConvertTo-TeamKey $other.opponentName
        $sameOpponent = ($oOpp -eq $opp) -or (($opp -ne "") -and ($oOpp -ne "") -and ($opp.Contains($oOpp) -or $oOpp.Contains($opp)))
        if ($sameOpponent -and ((ConvertTo-Int $other.goalsFor) -eq $GoalsFor) -and ((ConvertTo-Int $other.goalsAgainst) -eq $GoalsAgainst)) {
            return $true
        }
    }
    return $false
}

# Adds a match unless the same match was reported moments ago. Returns $true when it was added.
function Add-MatchEvent {
    param([string]$Title, [string]$Message, [string]$RawPayload, [string]$Opponent, [int]$GoalsFor, [int]$GoalsAgainst, [string]$FixtureKey)
    if (Test-DuplicateMatch -Opponent $Opponent -GoalsFor $GoalsFor -GoalsAgainst $GoalsAgainst -FixtureKey $FixtureKey) {
        Write-Host "  -> (duplicate match ignored: $Message)" -ForegroundColor DarkYellow
        return $false
    }
    Add-TelemetryEvent -Title $Title -Message $Message -RawPayload $RawPayload
    return $true
}

# Reads the POST body (bounded) and decodes it. Returns $null when it is larger than $MaxBodyBytes.
# HttpListener's ContentEncoding falls back to the Windows ANSI code page when the sender names no charset,
# which garbles characters such as the euro sign and accented player names, so UTF-8 is tried first.
function Read-RequestBody {
    param($Request)
    $ms = New-Object System.IO.MemoryStream
    try {
        $buf = New-Object byte[] 65536
        while (($n = $Request.InputStream.Read($buf, 0, $buf.Length)) -gt 0) {
            $ms.Write($buf, 0, $n)
            if ($ms.Length -gt $MaxBodyBytes) { return $null }
        }
        $bytes = $ms.ToArray()
    } finally {
        $ms.Dispose()
    }
    try {
        $text = (New-Object System.Text.UTF8Encoding($false, $true)).GetString($bytes)
    } catch {
        $text = [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($bytes)
    }
    return $text.TrimStart([char]0xFEFF)
}

# Stores one payload. Used for HTTP POSTs and for the payload files found in the outbox folder.
# Returns @{ Code = 200 (stored, or a duplicate that was ignored) or 400 (refused); Reply = JSON text; Reason = why refused }.
function Receive-PayloadText {
    param([string]$Text)
    $text = $Text.Trim()
    if ($text -eq "") {
        return @{ Code = 400; Reply = ""; Reason = "Empty body." }
    }

    $reply = '{"status":"ok"}'
    if ($text.StartsWith("{") -or $text.StartsWith("[")) {
        # JSON from the Live Editor scripts. A malformed one is refused with HTTP 400 instead of being stored:
        # a broken event that looks like JSON would stall the phone app for every event after it.
        $jsonObj = ConvertFrom-JsonObject $text
        if ($null -eq $jsonObj) {
            $preview = if ($text.Length -gt 120) { $text.Substring(0, 120) } else { $text }
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] REJECTED malformed JSON payload ($($text.Length) bytes): $preview" -ForegroundColor Red
            return @{ Code = 400; Reply = ""; Reason = "Payload is not a valid JSON object." }
        }

        # Store compact, strictly valid JSON (raw control characters inside strings are escaped)
        if ([regex]::IsMatch($text, '[\x00-\x1F]')) {
            $compact = ConvertTo-CompactJson $text
            if ($null -ne (ConvertFrom-JsonObject $compact)) { $text = $compact }
        }

        $title = "Match Update"
        if ($jsonObj.title) { $title = [string]$jsonObj.title }
        $msg = ""
        if ($jsonObj.message) { $msg = Remove-PenaltyNoise ([string]$jsonObj.message) }

        if ($jsonObj.dataType -eq "match") {
            $added = Add-MatchEvent -Title $title -Message $msg -RawPayload $text -Opponent ([string]$jsonObj.opponentName) -GoalsFor (ConvertTo-Int $jsonObj.goalsFor) -GoalsAgainst (ConvertTo-Int $jsonObj.goalsAgainst) -FixtureKey ([string]$jsonObj.fixtureKey)
            if ($added) {
                Write-Host "  -> Result: $($jsonObj.goalsFor) - $($jsonObj.goalsAgainst) vs $($jsonObj.opponentName)" -ForegroundColor Yellow
            } else {
                $reply = '{"status":"duplicate"}'
            }
        } else {
            $kind = [string]$jsonObj.dataType
            $replace = if ($kind -eq "career" -or $kind -eq "squad") { $kind } else { "" }
            Add-TelemetryEvent -Title $title -Message $msg -RawPayload $text -ReplaceKind $replace
            if ($jsonObj.playerName) {
                Write-Host "  -> Transfer: $($jsonObj.playerName) ($($jsonObj.type))" -ForegroundColor Cyan
            }
        }
    } else {
        Add-TelemetryEvent -Title "Match Update" -Message (Remove-PenaltyNoise $text)
    }
    return @{ Code = 200; Reply = $reply; Reason = "" }
}

function Create-SampleMatchJson {
    $today = (Get-Date -Format 'yyyy-MM-dd')
    return @"
{
    "dataType": "match",
    "title": "Live Match Final",
    "message": "Full Time: vs 3 - 1 Arsenal (Premier League)",
    "opponentName": "Arsenal",
    "isHome": true,
    "goalsFor": 3,
    "goalsAgainst": 1,
    "resultBadge": "W",
    "date": "$today",
    "competition": "Premier League",
    "shotsFor": 12,
    "shotsAgainst": 6,
    "possession": 56,
    "passingAccuracy": 88,
    "expectedGoals": 2.8,
    "expectedGoalsAgainst": 0.9,
    "playerPerformances": [
        {"playerName": "Bukayo Saka", "goals": 2, "assists": 1, "rating": 8.9, "yellowCard": false, "redCard": false},
        {"playerName": "Declan Rice", "goals": 1, "assists": 0, "rating": 7.8, "yellowCard": true, "redCard": false},
        {"playerName": "Martin Odegaard", "goals": 0, "assists": 2, "rating": 8.4, "yellowCard": false, "redCard": false}
    ],
    "goalDetails": [
        {"playerName": "Bukayo Saka", "isOpponent": false},
        {"playerName": "Declan Rice", "isOpponent": false},
        {"playerName": "Bukayo Saka", "isOpponent": false},
        {"playerName": "Kai Havertz (Opponent)", "isOpponent": true}
    ]
}
"@
}

function Create-SampleTransferJson {
    $today = (Get-Date -Format 'yyyy-MM-dd')
    return @"
{
    "dataType": "transfer",
    "title": "Transfer Finalized",
    "message": "Erling Haaland signed from Manchester City for €120.0M",
    "playerName": "Erling Haaland",
    "type": "Bought",
    "feeM": 120.0,
    "fromToClub": "Manchester City",
    "date": "$today"
}
"@
}

# =========================================================================
# ACCESS CONTROL (protocol 3, written up in docs/SECURITY.md)
#  * This PC's own browser (loopback address, host name localhost / 127.0.0.1, no proxy headers) may open the
#    dashboard and press its buttons. That is the only unsigned access to anything.
#  * The phone may do exactly three things: GET /events, GET /status and POST /resend. Each has to carry a signature
#    made with the pairing code (HMAC-SHA256 over method, path, time, a one-time number and the body), and the bridge
#    signs its answer the same way so the phone knows the answer comes from this bridge.
#  * Everybody else gets the one-line GET /ping answer and nothing more.
#  * Host / Origin checks keep web pages open in a browser from reaching the bridge.
# =========================================================================
$MaxBodyBytes = 5MB
$PcName = "$env:COMPUTERNAME".ToLower()
$PairAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"    # no I, O, 0 or 1: nothing to mix up when typing
$PairLength = 16                                       # 16 x 5 bits = 80 bits of randomness
$KeyLabel = "fc26-bridge-v1:"
$AuthWindowSeconds = 300                               # how far the phone's clock may differ from this PC's
$AuthMaxFails = 10                                     # wrong signatures from one address ...
$AuthFailWindowSeconds = 60                            # ... within this time ...
$AuthLockoutSeconds = 60                               # ... and that address is refused for this long
$AuthMaxNonces = 20000
$PhoneRoutes = @("GET /events", "GET /json", "GET /api/telemetry", "GET /status", "GET /api/status", "POST /resend")
$LocalHostNames = @("localhost", "127.0.0.1", "::1")
$ProxyHeaders = @("X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto", "X-Real-IP", "Forwarded", "Via",
                  "CF-Connecting-IP", "True-Client-IP", "X-Client-IP")
$global:PairCache = @{ Stamp = ""; Code = ""; Key = $null }
$global:AuthNonces = @{}      # one-time number -> when it may be forgotten
$global:AuthFails = @{}       # address -> times of recent wrong signatures
$global:AuthLocked = @{}      # address -> refused until
$global:SignWith = $null      # @{ Key; Nonce } once the phone's request has been checked: the answer is signed too

# ---- the pairing code ----
function Get-StateDir {
    # %APPDATA%\FC26Tracker: where the launcher keeps its notes and the bridge keeps the pairing code.
    if ($env:FC26_STATE_DIR) { return $env:FC26_STATE_DIR }
    $base = $env:APPDATA
    if (-not $base) { $base = Join-Path $HOME ".config" }
    return (Join-Path $base "FC26Tracker")
}

function Get-PairingFile { return (Join-Path (Get-StateDir) "bridge.key") }

function ConvertTo-PairCode {
    param([string]$Text)
    return ([regex]::Replace([string]$Text, "[^A-Za-z0-9]", "")).ToUpperInvariant()
}

function Test-PairCode {
    param([string]$Text)
    $c = ConvertTo-PairCode $Text
    if ($c.Length -ne $PairLength) { return $false }
    foreach ($ch in $c.ToCharArray()) { if ($PairAlphabet.IndexOf($ch) -lt 0) { return $false } }
    return $true
}

function Format-PairCode {
    param([string]$Text)
    $c = ConvertTo-PairCode $Text
    $parts = @()
    for ($i = 0; $i -lt $c.Length; $i += 4) { $parts += $c.Substring($i, [Math]::Min(4, $c.Length - $i)) }
    return ($parts -join "-")
}

function New-PairCode {
    $bytes = New-Object byte[] $PairLength
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $sb = New-Object System.Text.StringBuilder
    foreach ($b in $bytes) { [void]$sb.Append($PairAlphabet[[int]$b % 32]) }   # 256 is a multiple of 32: no bias
    return $sb.ToString()
}

function ConvertTo-HexText {
    param([byte[]]$Bytes)
    return ([BitConverter]::ToString($Bytes).Replace("-", "")).ToLowerInvariant()
}

function Get-Sha256Hex {
    param([byte[]]$Data)
    if ($null -eq $Data) { $Data = New-Object byte[] 0 }
    $h = [System.Security.Cryptography.SHA256]::Create()
    try { return (ConvertTo-HexText $h.ComputeHash($Data)) } finally { $h.Dispose() }
}

function Get-KeyFromCode {
    param([string]$Code)
    $h = [System.Security.Cryptography.SHA256]::Create()
    try { return ,$h.ComputeHash([System.Text.Encoding]::ASCII.GetBytes($KeyLabel + (ConvertTo-PairCode $Code))) } finally { $h.Dispose() }
}

function Get-HmacHex {
    param([byte[]]$Key, [string]$Text)
    $h = [System.Security.Cryptography.HMACSHA256]::new($Key)
    try { return (ConvertTo-HexText $h.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Text))) } finally { $h.Dispose() }
}

function Get-RequestSignature {
    param([byte[]]$Key, [string]$Method, [string]$Target, [string]$Stamp, [string]$Nonce, [byte[]]$Body)
    $text = "FC26-REQ-V1`n" + $Method.ToUpperInvariant() + "`n" + $Target + "`n" + $Stamp + "`n" + $Nonce + "`n" + (Get-Sha256Hex $Body)
    return (Get-HmacHex $Key $text)
}

function Get-ResponseSignature {
    param([byte[]]$Key, [string]$Nonce, [int]$Status, [byte[]]$Body)
    $text = "FC26-RESP-V1`n" + $Nonce + "`n" + [string]$Status + "`n" + (Get-Sha256Hex $Body)
    return (Get-HmacHex $Key $text)
}

function Save-PairingCode {
    param([string]$Code)
    $dir = Get-StateDir
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $path = Get-PairingFile
    $tmp = $path + ".tmp"
    [System.IO.File]::WriteAllText($tmp, ((Format-PairCode $Code) + "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $tmp -Destination $path -Force
}

function Get-PairingStamp {
    try {
        $fi = New-Object System.IO.FileInfo (Get-PairingFile)
        if ($fi.Exists) { return ("{0}:{1}" -f $fi.LastWriteTimeUtc.Ticks, $fi.Length) }
    } catch { }
    return ""
}

# The normalised pairing code. The first call creates it. A change to the file (the launcher's "new code") is picked
# up on the next request, no restart needed. Returns "" when there is none and it cannot be created.
function Get-PairingCode {
    $stamp = Get-PairingStamp
    if ($stamp -and $stamp -eq $global:PairCache.Stamp) { return [string]$global:PairCache.Code }
    $code = ""
    if ($stamp) { try { $code = ConvertTo-PairCode ([System.IO.File]::ReadAllText((Get-PairingFile))) } catch { $code = "" } }
    if (-not (Test-PairCode $code)) {
        $code = New-PairCode
        try {
            Save-PairingCode $code
        } catch {
            Write-Host "[SECURITY] Could not save the pairing code to $(Get-PairingFile): $_" -ForegroundColor Red
            $global:PairCache = @{ Stamp = ""; Code = ""; Key = $null }
            return ""
        }
        $stamp = Get-PairingStamp
    }
    $global:PairCache = @{ Stamp = $stamp; Code = $code; Key = (Get-KeyFromCode $code) }
    return $code
}

function Get-PairingKey {
    $code = Get-PairingCode
    if (-not $code) { return $null }
    return ,([byte[]]$global:PairCache.Key)
}

# Makes a new pairing code. Every phone has to be given the new one.
function Reset-PairingCode {
    $code = New-PairCode
    Save-PairingCode $code
    $global:PairCache = @{ Stamp = (Get-PairingStamp); Code = $code; Key = (Get-KeyFromCode $code) }
    return $code
}

# ---- who is asking ----
function Get-RequestHostName {
    # The host part of the Host header, lower case: "192.168.1.5:5050" -> "192.168.1.5", "[::1]:5050" -> "::1".
    param($Request)
    $h = [string]$Request.UserHostName
    if ([string]::IsNullOrWhiteSpace($h)) { return "" }
    $h = $h.Trim().ToLower()
    if ($h.StartsWith("[")) {
        $end = $h.IndexOf("]")
        if ($end -gt 0) { return $h.Substring(1, $end - 1) }
        return $h
    }
    if (($h.Split(":").Count - 1) -eq 1) { return $h.Split(":")[0] }
    return $h
}

# True only for a request that really comes from a program on this PC: the connection is from the loopback address, it
# is addressed to localhost / 127.0.0.1 / [::1], and no proxy or tunnel added forwarding headers. A request that
# arrived through a tunnel (ngrok and the like) always carries such headers, so it is not trusted.
function Test-TrustedLocal {
    param($Request)
    try {
        $remote = $Request.RemoteEndPoint.Address
        if ($null -eq $remote) { return $false }
        if ($remote.IsIPv4MappedToIPv6) { $remote = $remote.MapToIPv4() }
        if (-not [System.Net.IPAddress]::IsLoopback($remote)) { return $false }
        $name = Get-RequestHostName $Request
        if ($name -and ($LocalHostNames -notcontains $name)) { return $false }
        foreach ($h in $ProxyHeaders) { if (-not [string]::IsNullOrEmpty($Request.Headers[$h])) { return $false } }
        return $true
    } catch { return $false }
}

function Test-SameText {
    # Compares two texts without stopping at the first difference (so the time taken tells nothing).
    param([string]$A, [string]$B)
    if ($A.Length -ne $B.Length) { return $false }
    $diff = 0
    for ($i = 0; $i -lt $A.Length; $i++) { $diff = $diff -bor ([int][char]$A[$i] -bxor [int][char]$B[$i]) }
    return ($diff -eq 0)
}

function Get-LockSeconds {
    param([string]$Ip, [long]$Now)
    if (-not $global:AuthLocked.ContainsKey($Ip)) { return 0 }
    $until = [long]$global:AuthLocked[$Ip]
    if ($until -gt $Now) { return [int]($until - $Now) + 1 }
    $global:AuthLocked.Remove($Ip)
    return 0
}

function Add-AuthFail {
    param([string]$Ip, [long]$Now, [string]$Code)
    $global:AuthStats.Rejected = [int]$global:AuthStats.Rejected + 1
    $global:AuthStats.Time = $Now
    $global:AuthStats.Ip = $Ip
    $global:AuthStats.Code = $Code
    $recent = @()
    if ($global:AuthFails.ContainsKey($Ip)) { $recent = @($global:AuthFails[$Ip] | Where-Object { ($Now - $_) -lt $AuthFailWindowSeconds }) }
    $recent += $Now
    $global:AuthFails[$Ip] = $recent
    if ($recent.Count -ge $AuthMaxFails) {
        $global:AuthLocked[$Ip] = $Now + $AuthLockoutSeconds
        $global:AuthFails.Remove($Ip)
    }
    if ($global:AuthFails.Count -gt 2000) { $global:AuthFails.Clear() }
}

function New-AuthResult {
    param([bool]$Ok, [int]$Status = 200, [string]$Code = "", [string]$Message = "", [string]$Nonce = "", $Extra = @{})
    return @{ Ok = $Ok; Status = $Status; Code = $Code; Message = $Message; Nonce = $Nonce; Extra = $Extra }
}

# The result of checking one request from another machine. $Body is what was sent (empty for a GET).
function Test-SignedRequest {
    param([string]$Ip, [string]$Method, [string]$Target, [byte[]]$Body, [string]$Stamp, [string]$Nonce, [string]$Sig, [byte[]]$Key, [long]$Now)
    $wait = Get-LockSeconds $Ip $Now
    if ($wait -gt 0) { return (New-AuthResult $false 429 "locked" "Too many wrong attempts. Try again in $wait seconds." "" @{ retryAfter = $wait }) }
    if ($null -eq $Key -or $Key.Length -eq 0) { return (New-AuthResult $false 503 "not_paired" "The bridge has no pairing code yet.") }
    $Stamp = ([string]$Stamp).Trim()
    $Nonce = ([string]$Nonce).Trim().ToLowerInvariant()
    $Sig = ([string]$Sig).Trim().ToLowerInvariant()
    if (-not (($Stamp -cmatch '^[0-9]{1,12}$') -and ($Nonce -cmatch '^[0-9a-f]{16,64}$') -and ($Sig -cmatch '^[0-9a-f]{64}$'))) {
        Add-AuthFail $Ip $Now "unsigned"
        return (New-AuthResult $false 401 "unsigned" "This request is not signed. Enter the pairing code in the app.")
    }
    $expected = Get-RequestSignature $Key $Method $Target $Stamp $Nonce $Body
    if (-not (Test-SameText $expected $Sig)) {
        Add-AuthFail $Ip $Now "bad_signature"
        return (New-AuthResult $false 401 "bad_signature" "The pairing code was not accepted.")
    }
    if ([Math]::Abs($Now - [long]$Stamp) -gt $AuthWindowSeconds) {
        return (New-AuthResult $false 401 "clock_skew" "The clocks of the phone and the PC differ by more than 5 minutes." "" @{ bridgeTime = $Now })
    }
    foreach ($n in @($global:AuthNonces.Keys)) { if ([long]$global:AuthNonces[$n] -lt $Now) { $global:AuthNonces.Remove($n) } }
    if ($global:AuthNonces.ContainsKey($Nonce)) {
        Add-AuthFail $Ip $Now "replay"
        return (New-AuthResult $false 401 "replay" "This request was already used.")
    }
    if ($global:AuthNonces.Count -ge $AuthMaxNonces) { return (New-AuthResult $false 503 "busy" "Too many requests right now.") }
    $global:AuthNonces[$Nonce] = $Now + 2 * $AuthWindowSeconds
    return (New-AuthResult $true 200 "" "" $Nonce)
}

# What anybody on the network may learn: that a bridge is here, which kind, and that it wants a pairing code.
function Get-PingInfo {
    return [ordered]@{ app = "fc26-bridge"; protocol = $BridgeProtocol; version = $BridgeVersion; name = $PcName; auth = "hmac-sha256" }
}

function Test-AllowedHost {
    # Allow IP addresses, localhost and this PC's name. Other host names are refused
    # so a malicious web page cannot reach the bridge through DNS rebinding.
    param($Request)
    $name = Get-RequestHostName $Request
    if (-not $name) { return $true }   # HTTP/1.0 client without Host header - not a browser
    if ($name -eq "localhost" -or $name -eq $PcName -or $name -eq "$PcName.local") { return $true }
    $parsedIp = $null
    return [System.Net.IPAddress]::TryParse($name, [ref]$parsedIp)
}

function Test-OriginAllowed {
    # Writes may come from command-line tools (no Origin header) or from this bridge's
    # own dashboard page. A page on any other site is refused.
    param($Request)
    $origin = $Request.Headers["Origin"]
    if ([string]::IsNullOrEmpty($origin)) {
        $site = $Request.Headers["Sec-Fetch-Site"]
        if ([string]::IsNullOrEmpty($site)) { return $true }
        return ($site -eq "none" -or $site -eq "same-origin")
    }
    try {
        $u = [System.Uri]$origin
        if ([string]::IsNullOrEmpty($u.Authority)) { return $false }
        return ($u.Authority.ToLower() -eq ([string]$Request.UserHostName).ToLower())
    } catch { return $false }
}

function Send-Reject {
    param($Response, [int]$Code, [string]$Message, $Extra = @{}, $Headers = @{})
    try {
        $Response.StatusCode = $Code
        $Response.ContentType = "application/json"
        $obj = [ordered]@{ status = "error"; message = $Message }
        foreach ($k in $Extra.Keys) { $obj[[string]$k] = $Extra[$k] }
        foreach ($k in $Headers.Keys) { $Response.Headers.Add([string]$k, [string]$Headers[$k]) }
        $buf = [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Compress))
        $Response.ContentLength64 = $buf.Length
        $Response.OutputStream.Write($buf, 0, $buf.Length)
    } catch {}
    try { $Response.Close() } catch {}
}

# Setup HttpListener supporting port 5050
$Listener = New-Object System.Net.HttpListener
$bound = $false

# Bind directly to strong wildcard '+' so it matches the URLACL created by netsh
try {
    $Listener.Prefixes.Add("http://+:$Port/")
    $bound = $true
    Write-Host "  -> Bound: http://+:$Port/ (Strong wildcard - accepts phone and PC connections)" -ForegroundColor Green
} catch {
    Write-Host "  -> Wildcard '+' failed on port $Port ($($_)). Falling back to explicit IP prefixes..." -ForegroundColor Yellow
}

if (-not $bound) {
    $tryList = @("http://localhost:$Port/", "http://127.0.0.1:$Port/") + ($displayIps | ForEach-Object { "http://${_}:$Port/" })
    foreach ($prefix in $tryList) {
        try {
            $Listener.Prefixes.Add($prefix)
            Write-Host "  -> Bound: $prefix" -ForegroundColor DarkGray
        } catch {}
    }
}

try {
    $startCode = Get-PairingCode      # made before the first request can arrive
    $Listener.Start()
    Write-Host ""
    Write-Host "===================================================" -ForegroundColor Green
    Write-Host " Bridge Server started successfully!" -ForegroundColor Green
    Write-Host " 🟢 Active listening on Port $Port." -ForegroundColor Green
    Write-Host " Keep this window open while playing EA FC. Press Ctrl+C to stop." -ForegroundColor White
    if (-not $startCode) {
        Write-Host " No pairing code could be made, so no phone can connect. Check that the folder $(Get-StateDir) can be written to." -ForegroundColor Red
    } elseif ($env:FC26_HIDDEN) {
        Write-Host " Pairing code: shown by FC26_Launcher.bat and on the dashboard (never written to this log)." -ForegroundColor White
    } else {
        Write-Host " Pairing code for the app: $(Format-PairCode $startCode)" -ForegroundColor Yellow
    }
    Write-Host " Only a phone that knows the pairing code can read from the bridge. Everything else works from this PC only." -ForegroundColor DarkGray
    Write-Host "===================================================" -ForegroundColor Green
    Write-Host ""
} catch {
    Write-Host ""
    Write-Host "===================================================" -ForegroundColor Red
    Write-Host " ERROR: Failed to bind to port ${Port}: $_" -ForegroundColor Red
    Write-Host " Please close any other app using that port." -ForegroundColor Yellow
    Write-Host "===================================================" -ForegroundColor Red
    # The launcher starts the bridge with no window (FC26_HIDDEN); waiting for a key there would hang unseen.
    if (-not $env:FC26_HIDDEN) { pause }
    exit 1
}


# =========================================================================
# PROJECT ATLAS & FC26 AUTO-CAPTURE HOT-FOLDER AUTO-INGESTER
# Automatically watches Documents\FC26 Export for newly published match exports
# =========================================================================
$ProcessedAtlasFile = Join-Path $PSScriptRoot "processed_atlas_folders.json"
$StaleExportSeconds = 600        # exports older than this at first start are never replayed
$MaxAtlasAttempts = 3
$global:ProcessedAtlasFolders = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$global:AtlasAttempts = @{}
$global:AtlasFirstScan = $true
$global:AtlasHaveState = $false
$global:LastAtlasError = ""

function Import-AtlasState {
    try {
        if (Test-Path -LiteralPath $ProcessedAtlasFile) {
            $items = @(Get-Content -LiteralPath $ProcessedAtlasFile -Raw -Encoding UTF8 | ConvertFrom-Json)
            foreach ($i in $items) { if ($i) { [void]$global:ProcessedAtlasFolders.Add([string]$i) } }
            $global:AtlasHaveState = $true
        }
    } catch {
        Write-Host "[PROJECT ATLAS] could not read $ProcessedAtlasFile : $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

function Save-AtlasState {
    try {
        $names = @($global:ProcessedAtlasFolders | Sort-Object)
        if ($names.Count -gt 500) { $names = $names[($names.Count - 500)..($names.Count - 1)] }
        $parts = foreach ($n in $names) { '"' + ($n -replace '\\', '\\' -replace '"', '\"') + '"' }
        $text = "[" + (@($parts) -join ",") + "]"
        [System.IO.File]::WriteAllText($ProcessedAtlasFile, $text, (New-Object System.Text.UTF8Encoding($false)))
    } catch {}
}

# Lua scripts and the Atlas exporters write to %USERPROFILE%\Documents\FC26 Export. Windows can redirect the real
# "Documents" folder to OneDrive, so every plausible location is watched, not just the shell's idea of Documents.
function Get-AtlasRoots {
    $cands = @()
    if ($env:USERPROFILE) { $cands += (Join-Path $env:USERPROFILE "Documents\FC26 Export") }
    try { $cands += (Join-Path ([Environment]::GetFolderPath("MyDocuments")) "FC26 Export") } catch {}
    if ($env:OneDrive) { $cands += (Join-Path $env:OneDrive "Documents\FC26 Export") }
    $roots = @()
    foreach ($c in $cands) {
        if ($c -and ($roots -notcontains $c) -and (Test-Path -LiteralPath $c)) { $roots += $c }
    }
    return $roots
}

# Reads a CSV as UTF-8 (BOM or not; Windows PowerShell 5.1 would otherwise read it as ANSI and mangle names).
# -Required makes a failure throw so the importer retries instead of building an empty match.
function Read-CsvRows {
    param([string]$Path, [switch]$Required)
    try {
        if (-not (Test-Path -LiteralPath $Path)) {
            if ($Required) { throw "missing file $Path" }
            return @()
        }
        return @(Import-Csv -LiteralPath $Path -Encoding UTF8 -ErrorAction Stop)
    } catch {
        if ($Required) { throw }
        Write-Host "  (could not read $(Split-Path $Path -Leaf): $($_.Exception.Message))" -ForegroundColor DarkYellow
        return @()
    }
}

# -------------------------------------------------------------------------
# Match log helpers (the same rules as the Live Editor script, so both senders describe a match the same way)
# -------------------------------------------------------------------------
$AtlasDeferSeconds = 45   # give the Live Editor script this long to report the match itself first (it knows more)
$global:AtlasFirstSeen = @{}
$global:AtlasDeferredNoted = [System.Collections.Generic.HashSet[string]]::new()

# A regular league (not a cup, European competition or play-off). "Championship" is a league: it must not be taken
# for the Champions League.
function Test-LeagueName {
    param([string]$Name)
    $c = ([string]$Name).ToLower().Replace("championship", "league")
    foreach ($k in @("cup", "trophy", "shield", "fa", "uefa", "champions", "conference", "europa", "play-off", "playoff")) {
        if ($c.Contains($k)) { return $false }
    }
    return $true
}

function New-HistoryRow {
    param([int]$Id, [int]$Minutes, $Pos, [bool]$Sub, [string]$Name)
    return [PSCustomObject]@{ Id = $Id; Minutes = $Minutes; Pos = $Pos; Sub = $Sub; Name = $Name }
}

function Search-SubstitutionPairs {
    param($Subs, $Outgoing, [int]$Index, $Used, $Current, [int]$Score, [int]$Length, $Found)
    if ($Index -ge $Subs.Count) {
        if ($Score -gt $Found.Score) { $Found.Score = $Score; $Found.List = @($Current) }
        return
    }
    $s = $Subs[$Index]
    for ($oi = 0; $oi -lt $Outgoing.Count; $oi++) {
        if ($Used.ContainsKey($oi)) { continue }
        $o = $Outgoing[$oi]
        $diff = [Math]::Abs($s.Minutes + $o.Minutes - $Length)
        if ($diff -le 1) {
            $penalty = 2
            if (($null -eq $s.Pos) -or ($null -eq $o.Pos) -or ($s.Pos -eq $o.Pos)) { $penalty = 0 }
            $Used[$oi] = $true
            $next = @($Current) + [PSCustomObject]@{ Sub = $s; Out = $o }
            Search-SubstitutionPairs -Subs $Subs -Outgoing $Outgoing -Index ($Index + 1) -Used $Used -Current $next -Score ($Score + 100 - $diff - $penalty) -Length $Length -Found $Found
            $Used.Remove($oi)
        }
    }
    Search-SubstitutionPairs -Subs $Subs -Outgoing $Outgoing -Index ($Index + 1) -Used $Used -Current $Current -Score $Score -Length $Length -Found $Found   # this substitute replaced nobody that can be identified
}

# History rows are the game's record of the club's last match: Id, Minutes, Pos (position id), Sub (came on as a
# substitute). A substitute's minutes plus the minutes of the player he replaced add up to the length of the match,
# and he takes over the same position. Returns Pairs (Minute, Out, In), Marks (player id -> In / Out minute), Length.
function Get-SubstitutionPairs {
    param($History)
    $starters = @($History | Where-Object { $_.Minutes -gt 0 -and -not $_.Sub })
    $subs = @($History | Where-Object { $_.Minutes -gt 0 -and $_.Sub })
    $counts = @{}
    foreach ($s in $starters) {
        $k = [string]$s.Minutes
        if ($counts.ContainsKey($k)) { $counts[$k] = $counts[$k] + 1 } else { $counts[$k] = 1 }
    }
    $full = 0
    $most = 0
    foreach ($k in $counts.Keys) {                  # players who stayed on all game share one number: the most common
        $minutes = [int]$k
        if (($counts[$k] -gt $most) -or (($counts[$k] -eq $most) -and ($minutes -gt $full))) { $full = $minutes; $most = $counts[$k] }
    }
    $outgoing = @($starters | Where-Object { $_.Minutes -lt $full })

    $bestScore = -1
    $bestList = @()
    $bestLength = $full
    if ($subs.Count -gt 0 -and $subs.Count -le 8 -and $outgoing.Count -gt 0) {
        for ($length = $full; $length -le ($full + 12); $length++) {
            $found = @{ Score = 0; List = @() }
            Search-SubstitutionPairs -Subs $subs -Outgoing $outgoing -Index 0 -Used @{} -Current @() -Score 0 -Length $length -Found $found
            if ($found.Score -gt $bestScore) { $bestScore = $found.Score; $bestList = @($found.List); $bestLength = $length }
        }
    }

    $marks = @{}
    $pairs = @()
    foreach ($pr in $bestList) {
        $pairs += [PSCustomObject]@{ Minute = $pr.Out.Minutes; Out = $pr.Out; In = $pr.Sub }
        $key = [string]$pr.Sub.Id
        if (-not $marks.ContainsKey($key)) { $marks[$key] = @{} }
        $marks[$key].In = $pr.Out.Minutes
    }
    foreach ($o in $outgoing) {                      # also a sending-off or an injury
        $key = [string]$o.Id
        if (-not $marks.ContainsKey($key)) { $marks[$key] = @{} }
        $marks[$key].Out = $o.Minutes
    }
    foreach ($s in $subs) {
        $key = [string]$s.Id
        if (-not $marks.ContainsKey($key)) { $marks[$key] = @{} }
        if ((-not $marks[$key].ContainsKey("In")) -and $bestLength -gt 0) { $marks[$key].In = [Math]::Max(1, $bestLength - $s.Minutes) }
    }
    $pairs = @($pairs | Sort-Object Minute, @{ Expression = { $_.Out.Id } })
    return [PSCustomObject]@{ Pairs = $pairs; Marks = $marks; Length = $bestLength }
}

# Is the game's last-match record about the same players as the export? ExportedIds: HashSet of string ids.
function Test-LineupMatchesExport {
    param($History, $ExportedIds)
    $played = @($History | Where-Object { $_.Minutes -gt 0 })
    if ($played.Count -lt 11) { return $false }
    if ($null -eq $ExportedIds -or $ExportedIds.Count -eq 0) { return $true }
    $hit = 0
    foreach ($h in $played) { if ($ExportedIds.Contains([string]$h.Id)) { $hit++ } }
    return (($hit / $played.Count) -ge 0.7) -and (($hit / $ExportedIds.Count) -ge 0.7)
}

# Position ids of the game's last-match record (EA's usual numbering) -> the short names the app sorts the line-up by.
# Bench / reserve ids (28, 29) have no name: no position is sent for them.
function Get-PositionLabel {
    param($Id)
    $labels = @{
        0 = "GK"; 1 = "SW"; 2 = "RWB"; 3 = "RB"; 4 = "RCB"; 5 = "CB"; 6 = "LCB"; 7 = "LB"; 8 = "LWB"; 9 = "RDM"; 10 = "CDM"
        11 = "LDM"; 12 = "RM"; 13 = "RCM"; 14 = "CM"; 15 = "LCM"; 16 = "LM"; 17 = "RAM"; 18 = "CAM"; 19 = "LAM"; 20 = "RF"
        21 = "CF"; 22 = "LF"; 23 = "RW"; 24 = "RS"; 25 = "ST"; 26 = "LS"; 27 = "LW"
    }
    if ($null -eq $Id) { return $null }
    $n = 0
    if (-not [int]::TryParse([string]$Id, [ref]$n)) { return $null }
    if ($labels.ContainsKey($n)) { return $labels[$n] }
    return $null
}

# Adds what the game's own record knows to the exported player lines (ordered hashtables): players the export left
# out (a player on loan, say), who played which position and every substitution.
# Returns Perfs (starters first, then the substitutes in the order they came on), Subs and PosById.
function Add-LineupToPerformances {
    param($Perfs, $History, [int]$GoalsAgainst)
    $info = Get-SubstitutionPairs $History
    $byId = @{}
    $list = New-Object System.Collections.ArrayList
    foreach ($p in $Perfs) { [void]$list.Add($p); $byId[[string]$p.playerId] = $p }
    $posById = @{}
    foreach ($h in $History) {
        if ($h.Minutes -le 0) { continue }
        $key = [string]$h.Id
        if (-not $byId.ContainsKey($key)) {
            $name = $h.Name
            if (-not $name) { $name = "Player $key" }
            $perf = [ordered]@{
                playerId = $key; playerName = $name; rating = 0.0; goals = 0; assists = 0; passes = 0; passAccuracy = 0
                shots = 0; shotAccuracy = 0; saves = 0; penaltySaves = 0; tackles = 0; tacklePercentage = 0; interceptions = 0
                cleanSheet = ($GoalsAgainst -eq 0); yellowCard = $false; redCard = $false; isMotm = $false
            }
            [void]$list.Add($perf)
            $byId[$key] = $perf
        }
        $posById[$key] = $h.Pos
        if ($info.Marks.ContainsKey($key)) {
            $m = $info.Marks[$key]
            if ($m.ContainsKey("In")) { $byId[$key].subInMinute = $m.In }
            if ($m.ContainsKey("Out")) { $byId[$key].subOutMinute = $m.Out }
        }
    }
    $starters = @($list | Where-Object { -not $_.Contains("subInMinute") })
    $bench = @($list | Where-Object { $_.Contains("subInMinute") } | Sort-Object @{ Expression = { $_.subInMinute } }, @{ Expression = { [string]$_.playerId } })
    $subs = @()
    foreach ($pr in $info.Pairs) {
        $outPerf = $byId[[string]$pr.Out.Id]
        $inPerf = $byId[[string]$pr.In.Id]
        if ($outPerf -and $inPerf) {
            $subs += [PSCustomObject]@{
                minute = $pr.Minute; playerOutName = $outPerf.playerName; playerInName = $inPerf.playerName
                playerOutId = [string]$pr.Out.Id; playerInId = [string]$pr.In.Id
            }
        }
    }
    return [PSCustomObject]@{ Perfs = @($starters + $bench); Subs = $subs; PosById = $posById }
}

# Only when the export has no saves at all: shots on target that were not goals were saved.
function Set-KeeperSaves {
    param($Perfs, $PosById, [int]$ShotsOnTargetAgainst, [int]$GoalsAgainst)
    $total = 0
    foreach ($p in $Perfs) { $total += [int]$p.saves }
    $saved = $ShotsOnTargetAgainst - $GoalsAgainst
    if ($total -gt 0 -or $saved -le 0) { return $false }
    foreach ($p in $Perfs) {
        $key = [string]$p.playerId
        if ($PosById.ContainsKey($key) -and $PosById[$key] -eq 0) { $p.saves = $saved; return $true }
    }
    return $false
}

# The club's last-match record from a snapshot (last_match_players.csv).
function Get-SnapshotLineup {
    param([string]$SnapshotDir, [int]$TeamId)
    $rows = @()
    foreach ($r in (Read-CsvRows (Join-Path $SnapshotDir "last_match_players.csv"))) {
        if ((ConvertTo-Int $r.team_id) -ne $TeamId) { continue }
        $pid2 = ConvertTo-Int $r.player_id
        if ($pid2 -le 0) { continue }
        $pos = $null
        if (([string]$r.actual_position_id).Trim() -ne "") { $pos = ConvertTo-Int $r.actual_position_id }
        $fact = ConvertTo-Int $r.player_fact_raw
        $isSub = (([int][Math]::Floor($fact / 8)) % 2) -eq 1
        $rows += New-HistoryRow -Id $pid2 -Minutes (ConvertTo-Int $r.minutes_played) -Pos $pos -Sub $isSub -Name ([string]$r.display_name)
    }
    return $rows
}

# Names of the wanted players from the snapshot's players.csv (every player in the game). The match export leaves the
# name out for some players (often regens and academy graduates); without it the app showed "Unknown player".
function Get-SnapshotPlayerNames {
    param([string]$SnapshotDir, $Ids)
    $names = @{}
    if ($null -eq $Ids -or $Ids.Count -eq 0) { return $names }
    foreach ($r in (Read-CsvRows (Join-Path $SnapshotDir "players.csv"))) {
        $key = [string](ConvertTo-Int $r.player_id)
        if ($Ids.Contains($key)) {
            $n = ([string]$r.display_name).Trim()
            if ($n) { $names[$key] = $n }
        }
    }
    return $names
}

# Position in the league on the day of the match, from the snapshot's season fixtures; $null when unknown.
function Get-SnapshotLeaguePosition {
    param([string]$SnapshotDir, [int]$TeamId, [int]$CompId, [int]$DateRaw)
    if ($CompId -le 0) { return $null }
    $teams = @{}
    function Get-Row($tid) {
        if (-not $teams.ContainsKey($tid)) { $teams[$tid] = [PSCustomObject]@{ TeamId = $tid; Pts = 0; Gf = 0; Ga = 0; Gd = 0 } }
        return $teams[$tid]
    }
    foreach ($f in (Read-CsvRows (Join-Path $SnapshotDir "fixtures.csv"))) {
        if ((ConvertTo-Int $f.competition_object_id) -ne $CompId) { continue }
        $hid = ConvertTo-Int $f.home_team_id
        $aid = ConvertTo-Int $f.away_team_id
        if ($hid -le 0 -or $aid -le 0) { continue }
        $h = Get-Row $hid
        $a = Get-Row $aid
        $done = (([string]$f.is_completed).Trim() -eq "1") -or (([string]$f.is_completed).Trim().ToLower() -eq "true")
        $date = ConvertTo-Int $f.date_raw
        if (-not $done -or ($DateRaw -gt 0 -and $date -gt $DateRaw)) { continue }
        $hs = ConvertTo-Int $f.home_score -1
        $as = ConvertTo-Int $f.away_score -1
        if ($hs -lt 0 -or $hs -ge 60 -or $as -lt 0 -or $as -ge 60) { continue }
        $h.Gf += $hs; $h.Ga += $as; $a.Gf += $as; $a.Ga += $hs
        if ($hs -gt $as) { $h.Pts += 3 } elseif ($hs -lt $as) { $a.Pts += 3 } else { $h.Pts += 1; $a.Pts += 1 }
    }
    if ($teams.Count -lt 2) { return $null }
    foreach ($t in $teams.Values) { $t.Gd = $t.Gf - $t.Ga }
    $sorted = @($teams.Values | Sort-Object @{ Expression = { $_.Pts }; Descending = $true }, @{ Expression = { $_.Gd }; Descending = $true }, @{ Expression = { $_.Gf }; Descending = $true }, @{ Expression = { $_.TeamId }; Descending = $false })
    for ($i = 0; $i -lt $sorted.Count; $i++) {
        if ($sorted[$i].TeamId -eq $TeamId) { return ($i + 1) }
    }
    return $null
}

# The team file's row for team_index $Idx (found by its column, not by its position in the file).
function Get-TeamRow {
    param($Rows, [int]$Idx)
    foreach ($r in $Rows) {
        if (([string]$r.team_index).Trim() -ne "" -and (ConvertTo-Int $r.team_index -1) -eq $Idx) { return $r }
    }
    if ($Rows.Count -gt $Idx) { return $Rows[$Idx] }
    if ($Rows.Count -gt 0) { return $Rows[0] }
    return $null
}

function Import-AtlasFolder {
    param([string]$Folder, [string]$FolderId, [string]$SnapshotsReady)

    $participantsCsv = Join-Path $Folder "16_match_participants_wide.csv"
    $teamsCsv = Join-Path $Folder "18_match_teams_wide.csv"

    Write-Host ""
    Write-Host "[PROJECT ATLAS] 🎯 New MatchStats export detected: $FolderId" -ForegroundColor Cyan

    # Match context from Snapshots
    $oppName = "Opponent"
    $managedClubName = "My Club"
    $managedTeamId = 0
    $currentLeagueName = "League"
    $compName = "League Match"
    $isHome = $true
    $managedScore = 0
    $opponentScore = 0
    $resultBadge = "W"
    $matchDate = (Get-Date -Format "yyyy-MM-dd")
    $leaguePos = 0
    $completedMatches = @()
    $latest = $null
    $managedSquadPlayerIds = [System.Collections.Generic.HashSet[int]]::new()
    $latestSnap = $null
    $leagueCompId = 0
    $matchDateRaw = 0

    if ($SnapshotsReady -and (Test-Path -LiteralPath $SnapshotsReady)) {
        $snapFolders = @(Get-ChildItem -LiteralPath $SnapshotsReady -Directory -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)
        if ($snapFolders.Count -gt 0) {
            $latestSnap = $snapFolders[0].FullName

            foreach ($m in (Read-CsvRows (Join-Path $latestSnap "career_metadata.csv"))) {
                if ($m.key -eq "managed_team_id" -and $m.value) { $managedTeamId = ConvertTo-Int $m.value $managedTeamId }
                if ($m.key -eq "managed_team_name" -and $m.value) { $managedClubName = [string]$m.value }
            }

            $snapMatches = @(Read-CsvRows (Join-Path $latestSnap "managed_team_matches.csv"))
            $completedMatches = @($snapMatches | Where-Object { ([string]$_.is_completed).Trim() -eq "1" })
            if ($completedMatches.Count -gt 0) {
                $latest = $completedMatches[-1]
                if ($latest.managed_team_id) { $managedTeamId = ConvertTo-Int $latest.managed_team_id $managedTeamId }
                if ($latest.managed_team_name) { $managedClubName = [string]$latest.managed_team_name }
                if ($latest.opponent_team_name) { $oppName = [string]$latest.opponent_team_name }
                $isHome = (([string]$latest.side).ToUpper() -eq "HOME") -or (([string]$latest.managed_team_side).ToUpper() -eq "HOME")
                $managedScore = ConvertTo-Int $latest.managed_score
                $opponentScore = ConvertTo-Int $latest.opponent_score
                if ($latest.result) { $resultBadge = [string]$latest.result }
                elseif ($latest.managed_team_result) { $resultBadge = [string]$latest.managed_team_result }
                elseif ($managedScore -gt $opponentScore) { $resultBadge = "W" }
                elseif ($managedScore -eq $opponentScore) { $resultBadge = "D" }
                else { $resultBadge = "L" }
                if ($latest.date) { $matchDate = [string]$latest.date }
                $matchDateRaw = ConvertTo-Int $latest.date_raw
            }

            # Primary domestic league = the managed team's row with the most matches played (team_leagues.csv)
            $bestPld = -1
            foreach ($tl in (Read-CsvRows (Join-Path $latestSnap "team_leagues.csv"))) {
                $tlTid = ConvertTo-Int $tl.team_id
                $tlTname = [string]$tl.team_name
                if ($tlTid -eq $managedTeamId -or ($managedClubName -and $tlTname.ToLower().Contains($managedClubName.ToLower()))) {
                    # only the league's name is taken from here: the stored table position is refreshed now and then
                    # (it can stay at the pre-season value), so it is not used
                    $pld = ConvertTo-Int $tl.matches_played
                    if ($pld -gt $bestPld) {
                        $bestPld = $pld
                        if ($tl.league_name) { $currentLeagueName = [string]$tl.league_name }
                    }
                }
            }

            # Managed squad player ids, used to work out which participant team index is ours
            foreach ($r in (Read-CsvRows (Join-Path $latestSnap "rosters.csv"))) {
                if ((ConvertTo-Int $r.team_id) -eq $managedTeamId -and $r.player_id) {
                    [void]$managedSquadPlayerIds.Add((ConvertTo-Int $r.player_id))
                }
            }
        }
    }

    # League position from the season's results (the competition of this match if it is a league match, else the
    # competition the club has played most league matches in)
    if ($latestSnap) {
        if ($latest -and (Test-LeagueName ([string]$latest.competition_name)) -and (ConvertTo-Int $latest.competition_object_id) -gt 0) {
            $leagueCompId = ConvertTo-Int $latest.competition_object_id
        } else {
            $seen = @{}
            foreach ($cm in $completedMatches) {
                $cid = ConvertTo-Int $cm.competition_object_id
                if ($cid -gt 0 -and (Test-LeagueName ([string]$cm.competition_name))) {
                    if ($seen.ContainsKey($cid)) { $seen[$cid] = $seen[$cid] + 1 } else { $seen[$cid] = 1 }
                }
            }
            $bestN = 0
            foreach ($cid in $seen.Keys) { if ($seen[$cid] -gt $bestN) { $bestN = $seen[$cid]; $leagueCompId = [int]$cid } }
        }
        try {
            $computedPos = Get-SnapshotLeaguePosition -SnapshotDir $latestSnap -TeamId $managedTeamId -CompId $leagueCompId -DateRaw $matchDateRaw
            if ($null -ne $computedPos) { $leaguePos = [int]$computedPos }
        } catch {
            Write-Host "  (league table could not be worked out: $($_.Exception.Message))" -ForegroundColor DarkYellow
        }
    }

    # Competition & stage
    $rawComp = if ($latest -and $latest.competition_name) { [string]$latest.competition_name } else { $currentLeagueName }
    $leagueKeys = @($currentLeagueName.ToLower(), "league", "championship", "division", "premier")
    $playoffKeys = @("play-off", "playoff", "promotion")
    $isPlayoff = Test-ContainsAny $rawComp $playoffKeys
    $isRegularLeague = (-not $isPlayoff) -and (Test-ContainsAny $rawComp $leagueKeys) -and (-not (Test-ContainsAny $rawComp @("cup", "fa", "carabao", "trophy")))

    if ($isPlayoff) {
        $compName = $currentLeagueName
        $playoffMatches = @($completedMatches | Where-Object { Test-ContainsAny ([string]$_.competition_name) $playoffKeys })
        $pCount = [Math]::Max(1, $playoffMatches.Count)
        if ($pCount -ge 3) { $stageVal = "Play-off Final" }
        elseif ($pCount -eq 2) { $stageVal = "Play-off Semi-Final (Leg 2)" }
        else { $stageVal = "Play-off Semi-Final (Leg 1)" }
    } elseif ($isRegularLeague) {
        $compName = $currentLeagueName
        $leagueMatches = @($completedMatches | Where-Object {
            $c = [string]$_.competition_name
            (Test-ContainsAny $c $leagueKeys) -and (-not (Test-ContainsAny $c @("play-off", "playoff", "cup", "fa", "carabao", "trophy")))
        })
        $stageVal = "Matchday $([Math]::Max(1, $leagueMatches.Count))"
    } else {
        $compName = $rawComp
        $cupMatches = @($completedMatches | Where-Object { ([string]$_.competition_name) -eq $rawComp })
        $cCount = [Math]::Max(1, $cupMatches.Count)
        $rcl = $rawComp.ToLower()
        if ($rcl.Contains("fa cup") -or $rcl.Contains("emirates")) {
            if ($cCount -ge 6) { $stageVal = "Final" }
            elseif ($cCount -eq 5) { $stageVal = "Semi-Final" }
            elseif ($cCount -eq 4) { $stageVal = "Quarter-Final" }
            elseif ($cCount -eq 3) { $stageVal = "Round 5" }
            elseif ($cCount -eq 2) { $stageVal = "Round 4" }
            else { $stageVal = "Round 3" }
        } elseif ($rcl.Contains("carabao") -or $rcl.Contains("efl cup")) {
            if ($cCount -ge 6) { $stageVal = "Final" }
            elseif ($cCount -eq 5) { $stageVal = "Semi-Final" }
            elseif ($cCount -eq 4) { $stageVal = "Quarter-Final" }
            else { $stageVal = "Round $cCount" }
        } else {
            $stageVal = "Round $cCount"
        }
    }

    # Participants (a failure here must throw so the folder is retried, not imported empty)
    $participants = @(Read-CsvRows $participantsCsv -Required)
    $history = @()
    $historyNames = @{}
    if ($latestSnap) {
        $history = @(Get-SnapshotLineup -SnapshotDir $latestSnap -TeamId $managedTeamId)
        foreach ($h in $history) {
            if ($h.Name) { $historyNames[[string]$h.Id] = $h.Name }
            if ($h.Minutes -gt 0) { [void]$managedSquadPlayerIds.Add($h.Id) }      # a loaned-in player counts too
        }
        # players the export gives no name for: from the snapshot's list of every player
        $nameless = New-Object 'System.Collections.Generic.HashSet[string]'
        foreach ($p in $participants) {
            $key = [string](ConvertTo-Int $p.player_id)
            if ($key -ne "0" -and -not ([string]$p.player_name).Trim() -and -not $historyNames.ContainsKey($key)) { [void]$nameless.Add($key) }
        }
        if ($nameless.Count -gt 0) {
            $found = Get-SnapshotPlayerNames -SnapshotDir $latestSnap -Ids $nameless
            foreach ($k in $found.Keys) { $historyNames[$k] = $found[$k] }
        }
    }
    $count0 = 0
    $count1 = 0
    $maxMinutes = 0
    foreach ($p in $participants) {
        $rowPlayerId = ConvertTo-Int $p.player_id
        $pTeam = ConvertTo-Int $p.team_index
        $pMins = ConvertTo-Int $p.minutes_played
        if ($pMins -gt $maxMinutes) { $maxMinutes = $pMins }
        if ($managedSquadPlayerIds.Contains($rowPlayerId)) {
            if ($pTeam -eq 0) { $count0++ } else { $count1++ }
        }
    }

    if ($count0 -gt $count1) { $managedTeamIndex = 0; $oppTeamIndex = 1 }
    elseif ($count1 -gt $count0) { $managedTeamIndex = 1; $oppTeamIndex = 0 }
    else {
        $managedTeamIndex = if ($isHome) { 0 } else { 1 }
        $oppTeamIndex = if ($managedTeamIndex -eq 0) { 1 } else { 0 }
    }

    # Team totals (18_match_teams_wide.csv)
    $teamRows = @(Read-CsvRows $teamsCsv -Required)
    $mTeam = Get-TeamRow $teamRows $managedTeamIndex
    $oTeam = Get-TeamRow $teamRows $oppTeamIndex

    # Extra time & penalties
    $isPlayoffLeg1 = $isPlayoff -and $stageVal.ToLower().Contains("leg 1")
    if ($isRegularLeague -or $isPlayoffLeg1) {
        $isExtraTime = $false
        $homePen = 0
        $awayPen = 0
    } else {
        $mShootout = ConvertTo-Int $mTeam.shootout_goals
        $oShootout = ConvertTo-Int $oTeam.shootout_goals
        if ($mShootout -ge 255 -or $mShootout -lt 0) { $mShootout = 0 }
        if ($oShootout -ge 255 -or $oShootout -lt 0) { $oShootout = 0 }
        # Extra time happened only if someone played >= 115 minutes or there was a shootout
        $isExtraTime = ($maxMinutes -ge 115) -or ($mShootout -gt 0) -or ($oShootout -gt 0)
        if ($isHome) { $homePen = $mShootout; $awayPen = $oShootout }
        else { $homePen = $oShootout; $awayPen = $mShootout }
    }
    if ($homePen -ge 255 -or $homePen -lt 0) { $homePen = 0 }
    if ($awayPen -ge 255 -or $awayPen -lt 0) { $awayPen = 0 }

    # Man of the match straight from the game's own flag (either team can win it), else the best rating
    function Get-ParticipantName($row) {
        if ($row.player_name) { return [string]$row.player_name }
        $key = [string](ConvertTo-Int $row.player_id)
        if ($historyNames.ContainsKey($key)) { return $historyNames[$key] }
        return "Player"
    }
    $motmRow = $null
    foreach ($p in $participants) {
        $motmFlag = ([string]$p.man_of_the_match).Trim().ToLower()
        if ($motmFlag -eq "true" -or $motmFlag -eq "1") { $motmRow = $p; break }
    }
    if ($null -eq $motmRow) {
        $highestRating = -1.0
        foreach ($p in $participants) {
            $r = ConvertTo-Dbl $p.game_rating 0.0
            if ($r -gt $highestRating) { $highestRating = $r; $motmRow = $p }
        }
    }
    $topPlayer = ""
    $motmIsOpponent = $false
    if ($null -ne $motmRow) {
        $topPlayer = Get-ParticipantName $motmRow
        $motmIsOpponent = ((ConvertTo-Int $motmRow.team_index) -ne $managedTeamIndex)
    }

    $shotsFor = ConvertTo-Int (Get-FirstValue $mTeam.shots $mTeam.shots_on_target)
    $shotsAgainst = ConvertTo-Int (Get-FirstValue $oTeam.shots $oTeam.shots_on_target)
    $xGF = ConvertTo-Dbl $mTeam.npk_expected_goals
    $xGA = ConvertTo-Dbl $oTeam.npk_expected_goals
    $passesFor = ConvertTo-Int $mTeam.passes
    $passesAgainst = ConvertTo-Int $oTeam.passes
    $passAcc = ConvertTo-Int (ConvertTo-Pct $mTeam.pass_accuracy_pct_derived)
    $possession = ConvertTo-Int (ConvertTo-Pct (Get-FirstValue $mTeam.possession $mTeam.possession_pct))
    $possessionEstimated = $false
    if ($possession -le 0) {
        # The exporter has no possession figure: the share of all passes is the closest thing it records.
        $possessionEstimated = $true
        if (($passesFor + $passesAgainst) -gt 0) {
            $possession = [Math]::Max(25, [Math]::Min(75, [int][Math]::Floor(($passesFor / ($passesFor + $passesAgainst)) * 100 + 0.5)))
        } else {
            $possession = 50
        }
    }

    # Player performances - only players who actually played (the export lists the whole matchday squad)
    $playerPerfs = @()
    $goalPlayers = @()
    $opponentGoalPlayers = @()   # who scored for the other side (the export lists every player of both teams)
    foreach ($p in $participants) {
        $pTeam = ConvertTo-Int $p.team_index
        $pMinutes = ConvertTo-Dbl $p.minutes_played -1.0
        $played = -not ($maxMinutes -gt 0 -and $pMinutes -ge 0 -and $pMinutes -le 0)
        if ($pTeam -ne $managedTeamIndex) {
            $theirGoals = ConvertTo-Int (Get-FirstValue $p.goals_derived $p.npk_goals)
            # "" when the export does not name him: the goal then stays "<club> Goal", never a made-up "Player"
            $theirScorerName = ""
            if ($p.player_name) { $theirScorerName = [string]$p.player_name }
            else {
                $theirKey = [string](ConvertTo-Int $p.player_id)
                if ($historyNames.ContainsKey($theirKey)) { $theirScorerName = $historyNames[$theirKey] }
            }
            for ($g = 0; $g -lt $theirGoals; $g++) { $opponentGoalPlayers += $theirScorerName }
            continue
        }
        if (-not $played) { continue }

        $pName = Get-ParticipantName $p
        $pGoals = ConvertTo-Int (Get-FirstValue $p.goals_derived $p.npk_goals)
        $pAssists = ConvertTo-Int $p.assists
        $pRating = ConvertTo-Dbl $p.game_rating 6.5
        $pPasses = ConvertTo-Int $p.passes
        $pPassAcc = ConvertTo-Int (ConvertTo-Pct $p.pass_accuracy_pct_derived)
        $pShots = ConvertTo-Int (Get-FirstValue $p.shots $pGoals)
        $pShotAcc = 0
        if ($pShots -gt 0) {
            $pShotAcc = [Math]::Min(100, [int][Math]::Floor(((ConvertTo-Int $p.shots_on_target) / [Math]::Max(1, $pShots)) * 100))
        }
        $pTackles = ConvertTo-Int $p.tackles
        if ((ConvertTo-Dbl $p.stand_tackle_success_pct_derived -1.0) -ge 0) {
            $pTacklePct = ConvertTo-Int (ConvertTo-Pct $p.stand_tackle_success_pct_derived)
        } else {
            # blank for a player with no stand tackles (slide tackles only): use all tackles won / attempted
            if (($null -ne $p.stand_tackles) -or ($null -ne $p.slide_tackles)) {
                $tAtt = (ConvertTo-Dbl $p.stand_tackles) + (ConvertTo-Dbl $p.slide_tackles)
                $tWon = (ConvertTo-Dbl $p.stand_tackles_won) + (ConvertTo-Dbl $p.slide_tackles_won)
            } else {
                $tAtt = [double]$pTackles
                $tWon = ConvertTo-Dbl $p.tackles_completed ([double]$pTackles)
            }
            if ($tAtt -gt 0) { $pTacklePct = [Math]::Min(100, [int][Math]::Floor(($tWon / $tAtt) * 100)) } else { $pTacklePct = 0 }
        }
        $isMotm = [object]::ReferenceEquals($p, $motmRow)

        $playerPerfs += [ordered]@{
            playerId = [string]$p.player_id
            playerName = $pName
            rating = [Math]::Round($pRating, 1)
            goals = $pGoals
            assists = $pAssists
            passes = $pPasses
            passAccuracy = $pPassAcc
            shots = $pShots
            shotAccuracy = $pShotAcc
            saves = (ConvertTo-Int $p.saves)
            penaltySaves = (ConvertTo-Int $p.penalty_saves)
            tackles = $pTackles
            tacklePercentage = $pTacklePct
            interceptions = (ConvertTo-Int $p.interceptions)
            cleanSheet = ($opponentScore -eq 0)
            yellowCard = ((ConvertTo-Int $p.yellow_card_events) -gt 0)
            redCard = ((ConvertTo-Int $p.red_dismissals) -gt 0)
            isMotm = $isMotm
        }

        for ($g = 0; $g -lt $pGoals; $g++) { $goalPlayers += $pName }
    }

    # The game's own record of the last match adds who replaced whom and any player the export left out (a loan
    # signing, say). It is only used when it is about the same players as the export.
    $substitutions = @()
    $posById = @{}
    if ($history.Count -gt 0) {
        $exportedIds = [System.Collections.Generic.HashSet[string]]::new()
        foreach ($pp in $playerPerfs) { [void]$exportedIds.Add([string]$pp.playerId) }
        if (Test-LineupMatchesExport $history $exportedIds) {
            $merged = Add-LineupToPerformances $playerPerfs $history $opponentScore
            $playerPerfs = @($merged.Perfs)
            $substitutions = @($merged.Subs)
            $posById = $merged.PosById
        } else {
            Write-Host "  (the game's last-match record is about different players: substitutions are not sent)" -ForegroundColor DarkYellow
        }
    }
    if (Set-KeeperSaves $playerPerfs $posById (ConvertTo-Int $oTeam.shots_on_target) $opponentScore) {
        Write-Host "  (the export has no saves: the goalkeeper's saves are worked out from shots on target)" -ForegroundColor DarkYellow
    }
    # the position each player played, so the app can line the team up from striker to goalkeeper
    foreach ($pp in $playerPerfs) {
        $posKey = [string]$pp.playerId
        if ($posById.ContainsKey($posKey)) {
            $posLabel = Get-PositionLabel $posById[$posKey]
            if ($posLabel) { $pp.position = $posLabel }
        }
    }

    # The export has no goal minutes: spread them evenly over the match and flag them as estimated.
    $goalDetails = @()
    for ($k = 1; $k -le $goalPlayers.Count; $k++) {
        $goalDetails += [PSCustomObject]@{
            playerName = $goalPlayers[$k - 1]
            minute = [Math]::Max(1, [Math]::Min(90, [int][Math]::Floor(90 * $k / ($goalPlayers.Count + 1))))
            isOpponent = $false
            minuteEstimated = $true
        }
    }
    for ($og = 1; $og -le $opponentScore; $og++) {
        # the export names the other side's scorers; a goal it cannot name (an own goal, say) stays "<club> Goal"
        $theirScorer = "$oppName Goal"
        if ($og -le $opponentGoalPlayers.Count -and $opponentGoalPlayers[$og - 1]) { $theirScorer = $opponentGoalPlayers[$og - 1] }
        $goalDetails += [PSCustomObject]@{
            playerName = $theirScorer
            minute = [Math]::Max(1, [Math]::Min(90, [int][Math]::Floor(90 * $og / ($opponentScore + 1))))
            isOpponent = $true
            minuteEstimated = $true
        }
    }

    $etStr = ""
    if ($homePen -gt 0 -or $awayPen -gt 0) { $etStr = " (Pens: $homePen-$awayPen)" }
    elseif ($isExtraTime) { $etStr = " (A.E.T.)" }
    $locSymbol = if ($isHome) { "vs" } else { "@" }
    $msg = "Full Time: $managedClubName $locSymbol $managedScore - $opponentScore $oppName ($compName - $stageVal)$etStr"

    $leaguePosOut = $null
    if ($leaguePos -gt 0) { $leaguePosOut = $leaguePos }

    $matchPayload = [PSCustomObject]@{
        dataType = "match"
        title = "Live Match Final"
        message = $msg
        opponentName = $oppName
        isHome = $isHome
        goalsFor = $managedScore
        goalsAgainst = $opponentScore
        resultBadge = $resultBadge
        date = $matchDate
        competition = $compName
        stage = $stageVal
        isExtraTime = $isExtraTime
        homePenalties = $homePen
        awayPenalties = $awayPen
        shotsFor = $shotsFor
        shotsAgainst = $shotsAgainst
        possession = $possession
        possessionEstimated = $possessionEstimated
        passingAccuracy = $passAcc
        expectedGoals = [Math]::Round($xGF, 2)
        expectedGoalsAgainst = [Math]::Round($xGA, 2)
        motmName = $topPlayer
        motmIsOpponent = $motmIsOpponent
        leaguePosition = $leaguePosOut
        detailSource = "atlas"
        playerPerformances = $playerPerfs
        substitutions = $substitutions
        goalDetails = $goalDetails
    }

    $jsonPayload = $matchPayload | ConvertTo-Json -Depth 6 -Compress
    $added = Add-MatchEvent -Title "Live Match Final" -Message $msg -RawPayload $jsonPayload -Opponent $oppName -GoalsFor $managedScore -GoalsAgainst $opponentScore -FixtureKey ""
    if ($added) {
        Write-Host "  -> ⚽ Imported Match: $msg" -ForegroundColor Green
        Write-Host "     xG: $([Math]::Round($xGF, 2)) - $([Math]::Round($xGA, 2)) | MOTM: $topPlayer | Lineup: $($playerPerfs.Count) players" -ForegroundColor Yellow
    }
}

function Check-AtlasExportFolder {
    try {
        foreach ($atlasRoot in (Get-AtlasRoots)) {
            $matchStatsReady = Join-Path $atlasRoot "MatchStats\ready"
            $snapshotsReady = Join-Path $atlasRoot "Snapshots\ready"
            if (-not (Test-Path -LiteralPath $matchStatsReady)) { continue }

            $folders = @(Get-ChildItem -LiteralPath $matchStatsReady -Directory -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)

            if ($global:AtlasFirstScan -and -not $global:AtlasHaveState) {
                # First start: exports from earlier matches must not be replayed into the app.
                $cutoff = (Get-Date).AddSeconds(-$StaleExportSeconds)
                foreach ($f in $folders) {
                    if ($f.LastWriteTime -lt $cutoff) { [void]$global:ProcessedAtlasFolders.Add($f.Name) }
                }
                Save-AtlasState
            }

            foreach ($folder in $folders) {
                $folderId = $folder.Name
                if ($global:ProcessedAtlasFolders.Contains($folderId)) { continue }

                $readyMarker = Join-Path $folder.FullName "MATCH_READY"
                $altMarker = Join-Path $folder.FullName "READY_FOR_MANUAL_ZIP_AND_UPLOAD.txt"
                $participantsCsv = Join-Path $folder.FullName "16_match_participants_wide.csv"
                $teamsCsv = Join-Path $folder.FullName "18_match_teams_wide.csv"

                if (((Test-Path -LiteralPath $readyMarker) -or (Test-Path -LiteralPath $altMarker)) -and (Test-Path -LiteralPath $participantsCsv) -and (Test-Path -LiteralPath $teamsCsv)) {
                    if (-not $global:AtlasFirstSeen.ContainsKey($folderId)) { $global:AtlasFirstSeen[$folderId] = [DateTime]::UtcNow }
                    if (([DateTime]::UtcNow - $global:AtlasFirstSeen[$folderId]).TotalSeconds -lt $AtlasDeferSeconds) {
                        if ($global:AtlasDeferredNoted.Add($folderId)) {
                            Write-Host "[PROJECT ATLAS] New export ${folderId}: waiting up to $AtlasDeferSeconds s for the Live Editor script to report the match itself (it adds more detail)." -ForegroundColor DarkCyan
                        }
                        continue
                    }
                    try {
                        Import-AtlasFolder -Folder $folder.FullName -FolderId $folderId -SnapshotsReady $snapshotsReady
                        [void]$global:ProcessedAtlasFolders.Add($folderId)
                        Save-AtlasState
                    } catch {
                        $n = 1
                        if ($global:AtlasAttempts.ContainsKey($folderId)) { $n = $global:AtlasAttempts[$folderId] + 1 }
                        $global:AtlasAttempts[$folderId] = $n
                        Write-Host "[PROJECT ATLAS] ⚠️ Could not import $folderId (attempt $n/$MaxAtlasAttempts): $($_.Exception.Message)" -ForegroundColor Yellow
                        Write-Host "   $($_.ScriptStackTrace)" -ForegroundColor DarkGray
                        if ($n -ge $MaxAtlasAttempts) {
                            Write-Host "[PROJECT ATLAS] Giving up on $folderId; the Lua script still sends the match directly." -ForegroundColor Yellow
                            [void]$global:ProcessedAtlasFolders.Add($folderId)
                            Save-AtlasState
                        }
                    }
                }
            }
        }
        $global:AtlasFirstScan = $false
    } catch {
        # Report each distinct error once instead of every 2 seconds (it used to be swallowed completely)
        $errText = "$($_.Exception.Message)"
        if ($errText -ne $global:LastAtlasError) {
            $global:LastAtlasError = $errText
            Write-Host "[PROJECT ATLAS] watcher error: $errText" -ForegroundColor Yellow
            Write-Host "   $($_.ScriptStackTrace)" -ForegroundColor DarkGray
        }
    }
}

# =========================================================================
# OUTBOX HOT-FOLDER
# The Live Editor script saves every transfer and match as a small .json file in Documents\FC26 Export\outbox.
# Reading those files here means the script does not have to start a helper program for each one (every helper
# program flashes a command window on top of the game). bridge_watching.txt tells the script that this bridge is
# reading the folder, so it only starts its own sender if a file is still waiting after a while.
# =========================================================================
$OutboxSettleSeconds = 1
$OutboxBadAfterSeconds = 90
$global:LastOutboxHeartbeat = [DateTime]::MinValue
$global:LastOutboxError = ""
$global:OutboxHandled = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

function Write-OutboxLog {
    param([string]$Root, [string]$Id, [string]$Text)
    try {
        $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') [$Id] $Text"
        [System.IO.File]::AppendAllText((Join-Path $Root "bridge_send_log.txt"), $line + "`r`n")
    } catch {}
}

function Check-OutboxFolder {
    try {
        $now = [DateTime]::UtcNow
        $beat = (($now - $global:LastOutboxHeartbeat).TotalSeconds -ge 5)
        foreach ($root in (Get-AtlasRoots)) {
            if ($beat) {
                try { [System.IO.File]::WriteAllText((Join-Path $root "bridge_watching.txt"), [string][DateTimeOffset]::UtcNow.ToUnixTimeSeconds()) } catch {}
            }
            $outbox = Join-Path $root "outbox"
            if (-not (Test-Path -LiteralPath $outbox)) { continue }

            $files = @(Get-ChildItem -LiteralPath $outbox -Filter "*.json" -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTimeUtc, Name)
            $present = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
            foreach ($file in $files) { [void]$present.Add($file.FullName) }
            foreach ($done in @($global:OutboxHandled)) {
                if (-not $present.Contains($done)) { [void]$global:OutboxHandled.Remove($done) }
            }

            foreach ($file in $files) {
                $id = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
                if ($global:OutboxHandled.Contains($file.FullName)) {
                    # stored earlier but the file could not be deleted then: never store it twice
                    try { Remove-Item -LiteralPath $file.FullName -Force -ErrorAction Stop } catch {}
                    continue
                }
                $age = ($now - $file.LastWriteTimeUtc).TotalSeconds
                if ($age -lt $OutboxSettleSeconds) { continue }   # the script may still be writing it

                $text = $null
                try {
                    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
                    try {
                        $text = (New-Object System.Text.UTF8Encoding($false, $true)).GetString($bytes)
                    } catch {
                        $text = [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($bytes)
                    }
                    $text = $text.TrimStart([char]0xFEFF)
                } catch {
                    continue   # locked: try again on the next pass
                }

                $result = Receive-PayloadText $text
                if ($result.Code -eq 200) {
                    [void]$global:OutboxHandled.Add($file.FullName)
                    try { Remove-Item -LiteralPath $file.FullName -Force -ErrorAction Stop } catch {}
                    Write-OutboxLog $root $id "HTTP 200 (read from the outbox folder by the bridge)"
                } elseif ($age -gt $OutboxBadAfterSeconds) {
                    # still unreadable after a minute and a half: it is not just half-written
                    try { Move-Item -LiteralPath $file.FullName -Destination ($file.FullName + ".bad") -Force -ErrorAction Stop } catch {}
                    Write-OutboxLog $root $id "REFUSED ($($result.Reason)) - renamed to .bad"
                }
            }
        }
        if ($beat) { $global:LastOutboxHeartbeat = $now }
    } catch {
        $errText = "$($_.Exception.Message)"
        if ($errText -ne $global:LastOutboxError) {
            $global:LastOutboxError = $errText
            Write-Host "[OUTBOX] watcher error: $errText" -ForegroundColor Yellow
        }
    }
}

# =========================================================================
# HEALTH: the self check shown on the bridge page and in the phone app, and the "re-send last match" request.
#
# The capture script writes script_status.json (a heartbeat every few seconds) into Documents\FC26 Export. The bridge
# combines that with what it sees itself (folders, waiting files, when the phone last asked) into one list of checks.
# =========================================================================
function ConvertTo-Long {
    param($Value)
    $d = ConvertTo-Dbl $Value 0.0
    if ($d -gt 9e15 -or $d -lt -9e15) { return [long]0 }
    return [long][Math]::Round($d, [MidpointRounding]::AwayFromZero)
}

function Get-EpochNow {
    return [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
}

function Get-VersionParts {
    param([string]$Text)
    $m = [regex]::Match([string]$Text, '^\s*v?(\d+)\.(\d+)(?:\.(\d+))?')
    if (-not $m.Success) { return $null }
    try {
        $patch = 0
        if ($m.Groups[3].Success) { $patch = [int]$m.Groups[3].Value }
        return @([int]$m.Groups[1].Value, [int]$m.Groups[2].Value, $patch)
    } catch { return $null }
}

function Test-VersionAtLeast {
    param([string]$Have, [string]$Need)
    $h = Get-VersionParts $Have
    $n = Get-VersionParts $Need
    if ($null -eq $h -or $null -eq $n) { return $false }
    for ($i = 0; $i -lt 3; $i++) {
        if ($h[$i] -gt $n[$i]) { return $true }
        if ($h[$i] -lt $n[$i]) { return $false }
    }
    return $true
}

function Format-Span {
    param([long]$Seconds)
    if ($Seconds -lt 0) { $Seconds = 0 }
    if ($Seconds -lt 90) { return "${Seconds}s" }
    if ($Seconds -lt 5400) { return "$([long][Math]::Floor($Seconds / 60)) min" }
    if ($Seconds -lt 172800) { return "$([long][Math]::Floor($Seconds / 3600)) h" }
    return "$([long][Math]::Floor($Seconds / 86400)) days"
}

function Format-Ago {
    param([long]$Seconds)
    return ((Format-Span $Seconds) + " ago")
}

# The phone (or any other reader that is not this PC) just asked for news.
function Set-PhoneSeen {
    param([string]$Ip, $Now = $null)
    if ($null -eq $Now) { $Now = Get-EpochNow }
    if ($Ip.StartsWith("::ffff:")) { $Ip = $Ip.Substring(7) }
    $global:PhoneSeen.Time = [long]$Now
    $global:PhoneSeen.Ip = $Ip
}

# True while the capture script keeps script_alive.lock open (script 2.12 and newer). The game runs the script only on
# career events, so its heartbeat pauses in matches and menus; the open file shows it is still there. Windows does not
# let another program rename an open file, so a rename that fails means the script holds it.
function Test-ScriptLockHeld {
    param([string]$Path)
    if (-not $IsWindowsHost) { return $false }
    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    $probe = $Path + ".probe"
    try { [System.IO.File]::Move($Path, $probe) }
    catch [System.IO.IOException] { return $true }
    catch { return $false }
    try { [System.IO.File]::Move($probe, $Path) } catch { try { [System.IO.File]::Delete($probe) } catch { } }
    return $false
}
# (the tests replace this to pretend the file is held)
$global:ScriptLockTest = ${function:Test-ScriptLockHeld}

# The freshest script_status.json found in the export folders, described for the status page.
function Read-ScriptStatus {
    param($Roots, [long]$Now)
    $best = $null
    foreach ($root in @($Roots)) {
        $path = Join-Path $root "script_status.json"
        if (-not (Test-Path -LiteralPath $path)) { continue }
        try {
            $text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8).TrimStart([char]0xFEFF)
            $data = ConvertFrom-JsonObject $text
            if ($null -eq $data) { continue }
            $mtime = (New-Object System.DateTimeOffset ((Get-Item -LiteralPath $path).LastWriteTimeUtc)).ToUnixTimeSeconds()
        } catch { continue }
        $stamp = ConvertTo-Long $data.time
        if ($stamp -le 0) { $stamp = $mtime }
        if ($null -eq $best -or $stamp -gt $best.Stamp) { $best = @{ Stamp = $stamp; Data = $data; Root = $root } }
    }
    if ($null -eq $best) { return [ordered]@{ found = $false } }
    $data = $best.Data
    $age = [long][Math]::Max(0, $Now - $best.Stamp)
    $fresh = ($age -le $ScriptAliveSeconds)
    # an old heartbeat with the file still held open: the script runs, the game just has not called it for a while
    $held = $false
    if (-not $fresh) {
        $lockPath = Join-Path ([string]$best.Root) "script_alive.lock"
        if ($global:ScriptLockTest) { $held = [bool](& $global:ScriptLockTest $lockPath) }
        elseif (Get-Command Test-ScriptLockHeld -ErrorAction SilentlyContinue) { $held = [bool](Test-ScriptLockHeld $lockPath) }
    }
    $version = [string]$data.version
    $resend = $null
    if ($data.resend -is [System.Management.Automation.PSCustomObject]) {
        $resend = [ordered]@{
            id = [string]$data.resend.id
            time = (ConvertTo-Long $data.resend.time)
            result = [string]$data.resend.result
            detail = [string]$data.resend.detail
        }
    }
    return [ordered]@{
        found = $true
        folder = [string]$best.Root
        version = $version
        protocol = (ConvertTo-Int $data.protocol)
        time = $best.Stamp
        ageSeconds = $age
        alive = ($fresh -or $held)
        quiet = $held
        versionOk = (Test-VersionAtLeast $version $MinScriptVersion)
        club = [string]$data.club
        careerDate = [string]$data.careerDate
        exporter = ($data.exporter -eq $true)
        scans = (ConvertTo-Int $data.scans)
        lastMatch = (ConvertTo-Long $data.lastMatch)
        lastTransfer = (ConvertTo-Long $data.lastTransfer)
        lastSquad = (ConvertTo-Long $data.lastSquad)
        pendingMatches = (ConvertTo-Int $data.pendingMatches)
        undelivered = (ConvertTo-Int $data.undelivered)
        resend = $resend
    }
}

# How many payload files are waiting in the outbox, how many were refused, and whether the bridge can write there.
$global:WriteTestCache = @{ Time = [long]0; Root = ""; Ok = $true }
function Get-OutboxState {
    param($Roots, [long]$Now)
    $pending = 0
    $bad = 0
    $oldest = [long]0
    $writable = $true
    foreach ($root in @($Roots)) {
        $outbox = Join-Path $root "outbox"
        if (-not (Test-Path -LiteralPath $outbox)) { continue }
        foreach ($f in @(Get-ChildItem -LiteralPath $outbox -File -ErrorAction SilentlyContinue)) {
            $low = $f.Name.ToLower()
            if ($low.EndsWith(".json")) {
                $pending++
                $age = $Now - (New-Object System.DateTimeOffset ($f.LastWriteTimeUtc)).ToUnixTimeSeconds()
                if ($age -gt $oldest) { $oldest = $age }
            } elseif ($low.EndsWith(".bad")) {
                $bad++
            }
        }
        # A real write test, at most every 30 seconds. The file is not a .json, so the outbox watcher ignores it.
        if (($global:WriteTestCache.Root -ne $outbox) -or (($Now - $global:WriteTestCache.Time) -gt 30)) {
            $ok = $true
            $probe = Join-Path $outbox ".bridge_write_test"
            try {
                [System.IO.File]::WriteAllText($probe, "ok")
                Remove-Item -LiteralPath $probe -Force -ErrorAction Stop
            } catch { $ok = $false }
            $global:WriteTestCache = @{ Time = [long]$Now; Root = $outbox; Ok = $ok }
        }
        $writable = $writable -and $global:WriteTestCache.Ok
    }
    return [ordered]@{ pending = $pending; bad = $bad; oldestSeconds = $oldest; writable = $writable }
}

# When the newest match / transfer / squad report arrived (epoch seconds, 0 = never).
function Get-EventTimes {
    $latest = @{ match = [long]0; transfer = [long]0; squad = [long]0 }
    foreach ($line in @($global:EventList)) {
        $ev = $null
        try { $ev = $line | ConvertFrom-Json } catch { $ev = $null }
        if ($null -eq $ev -or ($ev.message -isnot [string])) { continue }
        if (-not $ev.message.TrimStart().StartsWith("{")) { continue }
        $data = ConvertFrom-JsonObject $ev.message
        if ($null -eq $data) { continue }
        $kind = [string]$data.dataType
        if ($latest.ContainsKey($kind)) {
            $t = ConvertTo-Long $ev.time
            if ($t -gt $latest[$kind]) { $latest[$kind] = $t }
        }
    }
    return [ordered]@{ count = $global:EventList.Count; lastMatch = $latest.match; lastTransfer = $latest.transfer; lastSquad = $latest.squad }
}

function New-Check {
    param([string]$Id, [string]$Label, [string]$State, [string]$Detail, [string]$Fix = "")
    return [ordered]@{ id = $Id; label = $Label; state = $State; detail = $Detail; fix = $Fix }
}

# The list shown under "Self check". state: ok | warn | fail | info. "fix" says what to do about a warn or fail.
function Get-SelfCheck {
    param($ScriptInfo, $Outbox, $Ev, $Roots, [string]$LocalIp, [long]$Now)
    $checks = @()
    $checks += New-Check "bridge" "Bridge" "ok" "Version $BridgeVersion is running on $PcName (port $Port)."

    if (@($Roots).Count -eq 0) {
        $checks += New-Check "folder" "Export folder" "fail" "Documents\FC26 Export was not found." "Run FC26_Auto_Capture.lua in Live Editor once: it creates the folder."
    } else {
        $checks += New-Check "folder" "Export folder" "ok" ([string]@($Roots)[0])
        if (-not $Outbox.writable) {
            $checks += New-Check "outbox" "Outbox" "fail" "The bridge cannot write to the outbox folder." "Check that the folder is not read-only and that no program is locking it."
        } elseif ($Outbox.bad -gt 0) {
            $checks += New-Check "outbox" "Outbox" "warn" "$($Outbox.bad) file(s) could not be read and were set aside (.bad)." "The script sent something the bridge could not read. Re-run the script; if it keeps happening, send the bridge_send_log.txt file."
        } elseif ($Outbox.pending -gt 0 -and $Outbox.oldestSeconds -gt 30) {
            $checks += New-Check "outbox" "Outbox" "warn" "$($Outbox.pending) payload(s) have been waiting for $(Format-Span $Outbox.oldestSeconds)." "Keep this window open: the bridge delivers them. If it stays, restart the bridge."
        } else {
            $detail = if ($Outbox.pending -eq 0) { "Empty." } else { "$($Outbox.pending) payload(s) being delivered." }
            $checks += New-Check "outbox" "Outbox" "ok" $detail
        }
    }

    if (-not $ScriptInfo.found) {
        $checks += New-Check "script" "Capture script" "fail" "No heartbeat from the script yet." "In Live Editor open your career, then Lua Scripts > FC26_Auto_Capture.lua > Execute."
    } elseif (-not $ScriptInfo.alive) {
        $checks += New-Check "script" "Capture script" "fail" "Last heard from $(Format-Ago $ScriptInfo.ageSeconds)." "Live Editor was closed or the script stopped. Execute FC26_Auto_Capture.lua again."
    } else {
        $clubText = if ($ScriptInfo.club) { " for $($ScriptInfo.club)" } else { "" }
        $verText = if ($ScriptInfo.version) { $ScriptInfo.version } else { "?" }
        $quietText = if ($ScriptInfo.quiet) { " Waiting for the game: it runs the script on your next career action (heartbeat $(Format-Ago $ScriptInfo.ageSeconds))." } else { "" }
        $checks += New-Check "script" "Capture script" "ok" "Running$clubText, version $verText.$quietText"
    }

    if ($ScriptInfo.found) {
        if (-not $ScriptInfo.versionOk) {
            $verText = if ($ScriptInfo.version) { $ScriptInfo.version } else { "?" }
            $checks += New-Check "version" "Script version" "warn" "Script $verText is older than $MinScriptVersion, which this bridge expects." "Run FC26_Launcher.bat to install the current script, then Execute it again in Live Editor."
        } else {
            $checks += New-Check "version" "Script version" "ok" "$($ScriptInfo.version) (needs $MinScriptVersion or newer)."
        }
        if ($ScriptInfo.alive) {
            if ($ScriptInfo.exporter) {
                $checks += New-Check "exporter" "Match statistics" "ok" "The Project Atlas exporter is running."
            } else {
                $checks += New-Check "exporter" "Match statistics" "warn" "The Project Atlas exporter is not running, so matches arrive with estimated statistics." "Execute the Project Atlas exporter script in Live Editor too."
            }
            if ($ScriptInfo.undelivered -gt 0) {
                $checks += New-Check "undelivered" "Delivery" "warn" "$($ScriptInfo.undelivered) report(s) not delivered yet." "Keep this window open; the bridge picks them up."
            }
        }
    }

    if ($Ev.lastMatch -gt 0) {
        $checks += New-Check "matches" "Last match" "info" "Received $(Format-Ago ($Now - $Ev.lastMatch))."
    } else {
        $checks += New-Check "matches" "Last match" "info" "No match received yet. Play or sim a match to see one arrive."
    }

    if ($global:PhoneSeen.Time -le 0) {
        $checks += New-Check "phone" "Phone" "warn" "The phone has not connected yet." "In the app open PC Connection (in the menu), tap Find my PC (or enter http://${LocalIp}:${Port}) and type the pairing code shown on the dashboard at http://localhost:${Port}."
    } elseif (($Now - $global:PhoneSeen.Time) -gt $PhoneAliveSeconds) {
        $checks += New-Check "phone" "Phone" "warn" "The phone last asked $(Format-Ago ($Now - $global:PhoneSeen.Time)) ($($global:PhoneSeen.Ip))." "Open the app. Phone and PC need to be on the same Wi-Fi."
    } else {
        $checks += New-Check "phone" "Phone" "ok" "Connected ($($global:PhoneSeen.Ip)), last asked $(Format-Ago ($Now - $global:PhoneSeen.Time))."
    }

    $rej = $global:AuthStats
    if ($null -ne $rej -and [long]$rej.Time -gt 0 -and ($Now - [long]$rej.Time) -lt 300) {
        $why = switch ([string]$rej.Code) {
            "bad_signature" { "a wrong pairing code" }
            "unsigned" { "no pairing code" }
            "replay" { "a repeated request" }
            "locked" { "too many wrong attempts" }
            default { "not allowed" }
        }
        $checks += New-Check "security" "Network access" "warn" "A device on your network ($($rej.Ip)) was refused $(Format-Ago ($Now - [long]$rej.Time)): $why." "If that was not your phone, press New pairing code on the dashboard (open http://localhost:${Port} on this PC)."
    } else {
        $checks += New-Check "security" "Network access" "ok" "Only a phone with the pairing code can read from the bridge."
    }

    if ($LocalIp.StartsWith("127.")) {
        $checks += New-Check "address" "Network address" "fail" "This PC has no network address." "Connect the PC to the same Wi-Fi or network as the phone."
    } else {
        $checks += New-Check "address" "Network address" "ok" "http://${LocalIp}:${Port}"
    }
    return $checks
}

function Get-BridgeStatus {
    param($Now = $null, $Roots = $null)
    if ($null -eq $Now) { $Now = Get-EpochNow }
    $Now = [long]$Now
    if ($null -eq $Roots) { $Roots = @(Get-AtlasRoots) }
    $scriptInfo = Read-ScriptStatus $Roots $Now
    $outbox = Get-OutboxState $Roots $Now
    $ev = Get-EventTimes
    $checks = @(Get-SelfCheck $scriptInfo $outbox $ev $Roots $primaryIp $Now)
    $states = @($checks | ForEach-Object { $_.state })
    $summary = if ($states -contains "fail") { "fail" } elseif ($states -contains "warn") { "warn" } else { "ok" }
    $seen = [long]$global:PhoneSeen.Time
    $phoneAge = if ($seen -gt 0) { $Now - $seen } else { -1 }
    return [ordered]@{
        app = "fc26-bridge"
        now = $Now
        bridge = [ordered]@{
            version = $BridgeVersion; protocol = $BridgeProtocol; minScript = $MinScriptVersion; kind = "powershell"
            name = $PcName; port = $Port; address = "http://${primaryIp}:${Port}"; started = $StartedAt
        }
        script = $scriptInfo
        outbox = $outbox
        events = $ev
        phone = [ordered]@{ lastSeen = $seen; ageSeconds = $phoneAge; ip = [string]$global:PhoneSeen.Ip }
        checks = $checks
        summary = $summary
    }
}

# Asks the capture script to send its latest match again. This is a file the script reads, because the script (not
# the bridge) knows the match. Returns @{ Code; Reply } where Reply is an object to send back as JSON.
function Request-Resend {
    param($Now = $null, $Roots = $null)
    if ($null -eq $Now) { $Now = Get-EpochNow }
    $Now = [long]$Now
    if ($null -eq $Roots) { $Roots = @(Get-AtlasRoots) }
    $scriptInfo = Read-ScriptStatus $Roots $Now
    if (-not $scriptInfo.found -or -not $scriptInfo.alive) {
        return @{ Code = 409; Reply = [ordered]@{ status = "error"; code = "script_not_running"; message = "The capture script is not running, so it cannot send anything. Execute FC26_Auto_Capture.lua in Live Editor first." } }
    }
    if (-not $scriptInfo.versionOk) {
        $verText = if ($scriptInfo.version) { $scriptInfo.version } else { "?" }
        return @{ Code = 409; Reply = [ordered]@{ status = "error"; code = "script_too_old"; message = "The capture script ($verText) is older than $MinScriptVersion and cannot do this. Install the current script with FC26_Launcher.bat." } }
    }
    if (($Now - $global:ResendLast) -lt $ResendMinSeconds) {
        return @{ Code = 429; Reply = [ordered]@{ status = "error"; code = "too_soon"; message = "A re-send was just requested. Give it a few seconds." } }
    }
    $id = "r$Now"
    $target = Join-Path $scriptInfo.folder "resend_request.json"
    try {
        $tmp = "$target.tmp"
        [System.IO.File]::WriteAllText($tmp, ('{"id":"' + $id + '","time":' + $Now + '}'), (New-Object System.Text.UTF8Encoding($false)))
        Move-Item -LiteralPath $tmp -Destination $target -Force -ErrorAction Stop
    } catch {
        return @{ Code = 500; Reply = [ordered]@{ status = "error"; code = "write_failed"; message = "Could not write the request: $($_.Exception.Message)" } }
    }
    $global:ResendLast = $Now
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Re-send of the latest match requested [$id]" -ForegroundColor Yellow
    return @{ Code = 200; Reply = [ordered]@{ status = "requested"; id = $id } }
}

# Sends a body. When the request was the phone's (checked and signed), the answer carries a signature over the request's
# one-time number, the status and the body, so the phone can tell the answer comes from this bridge.
function Send-Body {
    param($Response, [int]$Code, [string]$ContentType, [byte[]]$Buf)
    try {
        $Response.StatusCode = $Code
        $Response.ContentType = $ContentType
        if ($global:SignWith) {
            $Response.Headers.Add("X-FC-Sig", (Get-ResponseSignature $global:SignWith.Key $global:SignWith.Nonce $Code $Buf))
        }
        $Response.ContentLength64 = $Buf.Length
        $Response.OutputStream.Write($Buf, 0, $Buf.Length)
    } catch {}
    try { $Response.Close() } catch {}
}

function Send-Json {
    param($Response, [int]$Code, $Object)
    Send-Body $Response $Code "application/json" ([System.Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Object -Depth 8 -Compress)))
}

# Reads a small request body as bytes. Returns $null when it is larger than $Limit.
function Read-BodyBytes {
    param($Request, [int]$Limit)
    $ms = New-Object System.IO.MemoryStream
    try {
        $buf = New-Object byte[] 4096
        while (($n = $Request.InputStream.Read($buf, 0, $buf.Length)) -gt 0) {
            $ms.Write($buf, 0, $n)
            if ($ms.Length -gt $Limit) { return $null }
        }
        return ,$ms.ToArray()
    } finally {
        $ms.Dispose()
    }
}

Import-AtlasState

$asyncResult = $Listener.BeginGetContext($null, $null)
$lastAtlasCheck = [DateTime]::MinValue

while ($Listener.IsListening) {
    try {
        $now = [DateTime]::UtcNow
        if (($now - $lastAtlasCheck).TotalSeconds -ge 2) {
            $lastAtlasCheck = $now
            Check-OutboxFolder
            Check-AtlasExportFolder
        }

        if ($asyncResult.AsyncWaitHandle.WaitOne(800)) {
            $Context = $Listener.EndGetContext($asyncResult)
            $asyncResult = $Listener.BeginGetContext($null, $null)

            $Request = $Context.Request
            $Response = $Context.Response

        $clientIp = if ($Request.RemoteEndPoint) { $Request.RemoteEndPoint.Address.ToString() } else { "client" }
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Request from $clientIp : $($Request.HttpMethod) $($Request.Url.AbsolutePath)" -ForegroundColor DarkCyan

        # Security headers. No CORS headers on purpose: the phone app is not a browser and
        # the dashboard page is served from this same origin.
        $Response.Headers.Add("X-Content-Type-Options", "nosniff")
        $Response.Headers.Add("X-Frame-Options", "DENY")
        $Response.Headers.Add("Cache-Control", "no-store")

        # Refuse requests addressed to unexpected host names (DNS-rebinding protection)
        if (-not (Test-AllowedHost $Request)) {
            Send-Reject $Response 403 "Unexpected Host header."
            continue
        }

        $method = $Request.HttpMethod.ToUpper()
        $rawPath = $Request.Url.AbsolutePath.ToLower()
        $isWriteRoute = ($rawPath -eq "/test-match") -or ($rawPath -eq "/test-transfer") -or ($rawPath -eq "/clear") -or ($rawPath -eq "/events" -and $method -eq "DELETE")
        $isLocal = Test-TrustedLocal $Request
        $remoteIp = if ($clientIp.StartsWith("::ffff:")) { $clientIp.Substring(7) } else { $clientIp }
        $global:SignWith = $null

        if ($method -eq "GET" -and $rawPath -eq "/ping") {
            # The only thing anybody on the network may ask: is a bridge here, and which one?
            Send-Json $Response 200 (Get-PingInfo)
            continue
        }

        if (-not $isLocal) {
            # From another machine only the phone's three requests are possible, and only with the pairing code.
            $wait = Get-LockSeconds $remoteIp (Get-EpochNow)
            if ($wait -gt 0) {
                Send-Reject $Response 429 "Too many wrong attempts. Try again in $wait seconds." @{ code = "locked" } @{ "Retry-After" = $wait }
                continue
            }
            if ($PhoneRoutes -notcontains "$method $rawPath") {
                Send-Reject $Response 403 "Only the PC that runs the bridge may do this. Open http://localhost:$Port on that PC." @{ code = "local_only" }
                continue
            }
        }

        if ($method -eq "GET") {
            # These used to fire on a plain GET, so a link or <img> tag on any web page could
            # trigger them. They are POST/DELETE-only now (use the dashboard buttons).
            if ($isWriteRoute) {
                $Response.Redirect("/")
                $Response.Close()
                continue
            }
        } elseif ($method -eq "POST" -or $method -eq "DELETE") {
            # The phone's "Re-send last match" button only asks the capture script to send its latest match again
            # (rate limited, and the bridge drops a duplicate); with the pairing code it is allowed from the local
            # network. The Host and Origin checks still keep web pages in a browser out. Every other write is for
            # this PC only (the check above already refused it from anywhere else).
            if (-not (Test-OriginAllowed $Request)) {
                Send-Reject $Response 403 "Cross-site request blocked."
                continue
            }
            if ($Request.ContentLength64 -gt $MaxBodyBytes) {
                Send-Reject $Response 413 "Payload too large."
                continue
            }
        } else {
            # OPTIONS (CORS preflight), HEAD, PUT and anything else is not supported
            Send-Reject $Response 405 "Method not allowed."
            continue
        }

        # The phone's requests (and any local request that brings a signature) are checked here.
        $isPhoneRequest = ($method -eq "GET") -or ($rawPath -eq "/resend")
        if ($isPhoneRequest -and ((-not $isLocal) -or (-not [string]::IsNullOrEmpty($Request.Headers["X-FC-Sig"])))) {
            $bodyBytes = New-Object byte[] 0
            if ($method -eq "POST") {
                $bodyBytes = Read-BodyBytes $Request 1024
                if ($null -eq $bodyBytes) { Send-Reject $Response 413 "Payload too large."; continue }
            }
            $key = Get-PairingKey
            $auth = Test-SignedRequest $remoteIp $method ([string]$Request.RawUrl) $bodyBytes ([string]$Request.Headers["X-FC-Time"]) ([string]$Request.Headers["X-FC-Nonce"]) ([string]$Request.Headers["X-FC-Sig"]) $key (Get-EpochNow)
            if (-not $auth.Ok) {
                $logKey = "$remoteIp|$($auth.Code)"
                $nowLog = Get-EpochNow
                if (-not $global:AuthLogged.ContainsKey($logKey) -or ($nowLog - [long]$global:AuthLogged[$logKey]) -gt 60) {
                    $global:AuthLogged[$logKey] = $nowLog
                    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Refused a request from ${remoteIp}: $($auth.Message)" -ForegroundColor Yellow
                }
                $extra = @{ code = $auth.Code }
                $headers = @{}
                foreach ($k in $auth.Extra.Keys) { $extra[$k] = $auth.Extra[$k] }
                if ($auth.Extra.ContainsKey("retryAfter")) { $headers["Retry-After"] = $auth.Extra["retryAfter"] }
                Send-Reject $Response $auth.Status $auth.Message $extra $headers
                continue
            }
            $global:SignWith = @{ Key = $key; Nonce = $auth.Nonce }
            if (-not $isLocal) { Set-PhoneSeen $remoteIp }
        }

        if ($rawPath -eq "/resend" -and $method -eq "POST") {
            $resendResult = Request-Resend
            Send-Json $Response $resendResult.Code $resendResult.Reply
            continue
        }

        if ($rawPath -eq "/pairing/new" -and $method -eq "POST") {
            # The dashboard's "New pairing code" button (this PC only, see the check above).
            try {
                $newCode = Reset-PairingCode
                Write-Host "[$(Get-Date -Format 'HH:mm:ss')] A new pairing code was made. Enter it in the app." -ForegroundColor Yellow
                Send-Json $Response 200 ([ordered]@{ status = "ok"; code = (Format-PairCode $newCode) })
            } catch {
                Send-Reject $Response 500 "Could not save the new pairing code: $_"
            }
            continue
        }

        if ($rawPath -eq "/test-match") {
            # Direct endpoint for generating a test match (GET or POST)
            $matchJson = Create-SampleMatchJson
            Add-TelemetryEvent -Title "Live Match Final" -Message "Full Time: vs 3 - 1 Arsenal (Premier League)" -RawPayload $matchJson
            Write-Host "  -> Queued sample match: Richmond 3 - 1 Arsenal" -ForegroundColor Yellow

            if ($Request.HttpMethod -eq "GET") {
                # Display friendly HTML redirect page
                $Response.StatusCode = 200
                $Response.ContentType = "text/html; charset=utf-8"
                $html = @"
<!DOCTYPE html>
<html>
<head><meta http-equiv="refresh" content="2;url=/" /><style>body{background:#0f172a;color:#f8fafc;font-family:sans-serif;padding:40px;text-align:center;}</style></head>
<body>
    <h2 style="color:#10b981;">✅ Test Match Sent! (Richmond 3 - 1 Arsenal)</h2>
    <p>Check your Android mobile app. Redirecting to dashboard in 2 seconds...</p>
    <p><a href="/" style="color:#38bdf8;">Click here if not redirected</a></p>
</body>
</html>
"@
                $buf = [System.Text.Encoding]::UTF8.GetBytes($html)
                $Response.ContentLength64 = $buf.Length
                $Response.OutputStream.Write($buf, 0, $buf.Length)
                $Response.Close()
            } else {
                $Response.StatusCode = 200
                $Response.ContentType = "application/json"
                $buf = [System.Text.Encoding]::UTF8.GetBytes('{"status":"ok","message":"Test match sent successfully!"}')
                $Response.ContentLength64 = $buf.Length
                $Response.OutputStream.Write($buf, 0, $buf.Length)
                $Response.Close()
            }
            continue
        }

        if ($rawPath -eq "/test-transfer") {
            # Direct endpoint for generating a test transfer (GET or POST)
            $transferJson = Create-SampleTransferJson
            Add-TelemetryEvent -Title "Transfer Finalized" -Message "Erling Haaland signed from Manchester City for €120.0M" -RawPayload $transferJson
            Write-Host "  -> Queued sample transfer: Erling Haaland €120.0M" -ForegroundColor Cyan

            if ($Request.HttpMethod -eq "GET") {
                $Response.StatusCode = 200
                $Response.ContentType = "text/html; charset=utf-8"
                $html = @"
<!DOCTYPE html>
<html>
<head><meta http-equiv="refresh" content="2;url=/" /><style>body{background:#0f172a;color:#f8fafc;font-family:sans-serif;padding:40px;text-align:center;}</style></head>
<body>
    <h2 style="color:#10b981;">✅ Test Transfer Sent! (€120M Haaland)</h2>
    <p>Check your Android mobile app. Redirecting to dashboard in 2 seconds...</p>
    <p><a href="/" style="color:#38bdf8;">Click here if not redirected</a></p>
</body>
</html>
"@
                $buf = [System.Text.Encoding]::UTF8.GetBytes($html)
                $Response.ContentLength64 = $buf.Length
                $Response.OutputStream.Write($buf, 0, $buf.Length)
                $Response.Close()
            } else {
                $Response.StatusCode = 200
                $Response.ContentType = "application/json"
                $buf = [System.Text.Encoding]::UTF8.GetBytes('{"status":"ok","message":"Test transfer sent successfully!"}')
                $Response.ContentLength64 = $buf.Length
                $Response.OutputStream.Write($buf, 0, $buf.Length)
                $Response.Close()
            }
            continue
        }

        if ($rawPath -eq "/clear" -or ($rawPath -eq "/events" -and $Request.HttpMethod -eq "DELETE")) {
            $global:EventList.Clear()
            Save-TelemetryEvents
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Cleared all telemetry events." -ForegroundColor Magenta
            if ($Request.HttpMethod -eq "GET") {
                $Response.Redirect("/")
                $Response.Close()
            } else {
                $Response.StatusCode = 200
                $Response.ContentType = "application/json"
                $buf = [System.Text.Encoding]::UTF8.GetBytes('{"status":"cleared"}')
                $Response.ContentLength64 = $buf.Length
                $Response.OutputStream.Write($buf, 0, $buf.Length)
                $Response.Close()
            }
            continue
        }

        if ($Request.HttpMethod -eq "POST") {
            $Body = Read-RequestBody $Request
            if ($null -eq $Body) {
                Send-Reject $Response 413 "Payload too large."
                continue
            }
            $result = Receive-PayloadText $Body
            if ($result.Code -ne 200) {
                Send-Reject $Response $result.Code $result.Reason
                continue
            }
            $reply = $result.Reply

            $Response.StatusCode = 200
            $Response.ContentType = "application/json"
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($reply)
            $Response.ContentLength64 = $buffer.Length
            $Response.OutputStream.Write($buffer, 0, $buffer.Length)
            $Response.Close()
        } elseif ($Request.HttpMethod -eq "GET") {
            if ($rawPath -eq "/status" -or $rawPath -eq "/api/status") {
                Send-Json $Response 200 (Get-BridgeStatus)
            } elseif ($rawPath -eq "/events" -or $rawPath -eq "/json" -or $rawPath -eq "/api/telemetry") {
                $jsonArray = "[" + ($global:EventList -join ",") + "]"
                Send-Body $Response 200 "application/json" ([System.Text.Encoding]::UTF8.GetBytes($jsonArray))
            } else {
                # Return Web UI Status Dashboard
                $Response.StatusCode = 200
                $Response.ContentType = "text/html; charset=utf-8"
                $Response.Headers.Add("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
                $eventCount = $global:EventList.Count
                $pairingText = Format-PairCode (Get-PairingCode)
                $html = @"
<!DOCTYPE html>
<html>
<head>
    <title>EA FC Telemetry Bridge</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 24px; margin: 0; }
        .card { background: #1e293b; border-radius: 14px; padding: 24px; max-width: 680px; margin: 0 auto; box-shadow: 0 10px 15px -3px rgba(0,0,0,0.5); }
        h1 { color: #38bdf8; margin-top: 0; font-size: 22px; display: flex; align-items: center; gap: 8px; }
        .status { display: inline-block; background: #059669; color: white; padding: 4px 12px; border-radius: 9999px; font-weight: bold; font-size: 13px; margin-bottom: 16px; }
        .info { background: #0f172a; padding: 14px; border-radius: 8px; font-family: monospace; margin: 14px 0; border: 1px solid #334155; line-height: 1.6; }
        .btn-group { display: flex; gap: 10px; margin: 16px 0; flex-wrap: wrap; }
        button, a.btn { background: #0284c7; color: white; border: none; padding: 10px 16px; border-radius: 8px; cursor: pointer; font-weight: bold; font-size: 13px; transition: background 0.2s; text-decoration: none; display: inline-block; }
        button:hover, a.btn:hover { background: #0369a1; }
        button.btn-green, a.btn-green { background: #10b981; }
        button.btn-green:hover, a.btn-green:hover { background: #059669; }
        button.btn-danger, a.btn-danger { background: #ef4444; }
        button.btn-danger:hover, a.btn-danger:hover { background: #dc2626; }
        .events-box { max-height: 320px; overflow-y: auto; background: #0f172a; border-radius: 8px; padding: 12px; border: 1px solid #334155; margin-top: 14px; }
        .event-item { border-bottom: 1px solid #1e293b; padding: 8px 0; font-size: 13px; }
        .event-item:last-child { border-bottom: none; }
        .event-time { color: #94a3b8; font-size: 11px; font-family: monospace; }
        .status-msg { margin: 10px 0; padding: 8px 12px; border-radius: 6px; font-size: 13px; min-height: 20px; }
        .check { display: flex; gap: 12px; padding: 10px 0; border-top: 1px solid #334155; }
        .check:first-child { border-top: 0; }
        .dot { flex: 0 0 12px; height: 12px; border-radius: 6px; margin-top: 5px; background: #64748b; }
        .dot.ok { background: #10b981; } .dot.warn { background: #f59e0b; } .dot.fail { background: #ef4444; }
        .check-label { font-weight: bold; font-size: 14px; }
        .check-detail { font-size: 13px; color: #cbd5e1; }
        .check-fix { font-size: 13px; color: #fcd34d; margin-top: 2px; }
        button:disabled { opacity: .5; cursor: default; }
    </style>
</head>
<body>
    <div class="card">
        <h1>⚽ EA Sports FC Telemetry Bridge <span style="font-size:13px;color:#94a3b8;font-weight:normal;">v$BridgeVersion (PowerShell)</span></h1>
        <div class="status">🟢 Bridge Active (Listening on Port $Port)</div>

        <div class="info">
            <strong>📱 IP Address for Android App:</strong><br>
            <span style="color:#10b981;font-size:16px;font-weight:bold;">http://${primaryIp}:${Port}</span><br>
        </div>

        <h3>Pairing code</h3>
        <p style="font-size:13px;color:#cbd5e1;margin-top:0;">Type this code into the app (menu, PC Connection). Only a phone that has it can read anything from this bridge. Do not share it, and do not post a screenshot of this page.</p>
        <div id="pairCode" style="font-family:monospace;font-size:26px;letter-spacing:2px;color:#facc15;">$pairingText</div>
        <div style="margin:12px 0 20px;">
            <button id="newCodeBtn" class="btn-danger" type="button">New pairing code</button>
            <span id="newCodeNote" style="font-size:13px;color:#cbd5e1;margin-left:6px;"></span>
        </div>

        <h3>Self check</h3>
        <div id="summary" style="font-size:15px;font-weight:bold;margin-bottom:8px;">Checking...</div>
        <div id="checks"></div>
        <div style="margin:14px 0 20px;">
            <button id="resendBtn" type="button">Re-send last match</button>
            <button id="recheckBtn" class="btn-green" type="button">Check again</button>
            <span id="resendNote" style="font-size:13px;color:#cbd5e1;margin-left:6px;"></span>
        </div>

        <div id="statusMsg" class="status-msg"></div>

        <h3>⚡ Quick Transmission Testing</h3>
        <p style="font-size:12px;color:#94a3b8;margin-top:-6px;">Click either button to immediately send a match or transfer to your phone.</p>
        <div class="btn-group">
            <button class="btn-green" onclick="sendTestMatch()">⚡ Send Test Match (3 - 1 vs Arsenal)</button>
            <button onclick="sendTestTransfer()">⚡ Send Test Transfer (€120M Haaland)</button>
            <button class="btn-danger" onclick="clearEvents()">🗑️ Clear Events</button>
        </div>

        <p style="font-size:11px;color:#64748b;">
            <a href="/events" target="_blank" style="color:#38bdf8;">[View Raw JSON]</a>
        </p>

        <h3>📡 Live Events Stream (<span id="eventCount">$eventCount</span> queued)</h3>
        <div class="events-box" id="eventsList">
            <div style="color:#64748b;text-align:center;padding:20px;">Loading events...</div>
        </div>
    </div>

    <script>
        // Escape anything that came from an event before it is put into the page
        function esc(v) {
            return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
                return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
            });
        }

        function sendTestMatch() {
            const msg = document.getElementById('statusMsg');
            msg.innerHTML = '<span style="color:#38bdf8;">Sending test match...</span>';
            fetch('/test-match', { method: 'POST' })
                .then(r => r.json())
                .then(data => {
                    msg.innerHTML = '<span style="color:#10b981;font-weight:bold;">✅ Test match sent successfully! Check your phone.</span>';
                    setTimeout(updateEvents, 300);
                })
                .catch(err => {
                    msg.innerHTML = '<span style="color:#ef4444;font-weight:bold;">❌ Error: ' + esc(err) + '</span>';
                });
        }

        function sendTestTransfer() {
            const msg = document.getElementById('statusMsg');
            msg.innerHTML = '<span style="color:#38bdf8;">Sending test transfer...</span>';
            fetch('/test-transfer', { method: 'POST' })
                .then(r => r.json())
                .then(data => {
                    msg.innerHTML = '<span style="color:#10b981;font-weight:bold;">✅ Test transfer sent successfully! Check your phone.</span>';
                    setTimeout(updateEvents, 300);
                })
                .catch(err => {
                    msg.innerHTML = '<span style="color:#ef4444;font-weight:bold;">❌ Error: ' + esc(err) + '</span>';
                });
        }

        function clearEvents() {
            if (confirm("Clear all recorded telemetry events?")) {
                fetch('/clear', { method: 'POST' })
                    .then(() => location.reload())
                    .catch(err => {
                        document.getElementById('statusMsg').innerHTML = '<span style="color:#ef4444;font-weight:bold;">❌ Error: ' + esc(err) + '</span>';
                    });
            }
        }

        function updateEvents() {
            fetch('/events')
                .then(r => r.json())
                .then(events => {
                    document.getElementById('eventCount').innerText = events.length;
                    const container = document.getElementById('eventsList');
                    if (events.length === 0) {
                        container.innerHTML = '<div style="color:#64748b;text-align:center;padding:20px;">No events recorded yet. Play a match in EA FC or click a test button above.</div>';
                        return;
                    }
                    container.innerHTML = events.slice().reverse().map(ev => {
                        const dateStr = new Date(ev.time * 1000).toLocaleTimeString();
                        let summary = ev.title;
                        try {
                            const parsed = JSON.parse(ev.message);
                            if (parsed.message) summary = parsed.message;
                        } catch {}
                        return '<div class="event-item">' +
                            '<span class="event-time">[' + dateStr + ']</span> ' +
                            '<strong>' + esc(ev.title || 'Update') + '</strong><br>' +
                            '<span style="color:#cbd5e1;">' + esc(summary) + '</span>' +
                        '</div>';
                    }).join('');
                })
                .catch(err => {
                    document.getElementById('eventsList').innerHTML = '<div style="color:#ef4444;text-align:center;">Failed to load events: ' + esc(err) + '</div>';
                });
        }

        // Self check: built with textContent only, because the details can contain club names from the game
        function checkRow(c) {
            var r = document.createElement('div'); r.className = 'check';
            var d = document.createElement('div'); d.className = 'dot ' + (c.state || '');
            var t = document.createElement('div');
            var l = document.createElement('div'); l.className = 'check-label'; l.textContent = c.label;
            var x = document.createElement('div'); x.className = 'check-detail'; x.textContent = c.detail;
            t.appendChild(l); t.appendChild(x);
            if (c.fix) { var f = document.createElement('div'); f.className = 'check-fix'; f.textContent = c.fix; t.appendChild(f); }
            r.appendChild(d); r.appendChild(t);
            return r;
        }
        function refreshStatus() {
            return fetch('/status').then(function (r) { return r.json(); }).then(function (s) {
                var box = document.getElementById('checks');
                box.textContent = '';
                s.checks.forEach(function (c) { box.appendChild(checkRow(c)); });
                var sum = document.getElementById('summary');
                sum.textContent = s.summary === 'ok' ? 'Everything is working.' : s.summary === 'warn' ? 'Working, with something to look at.' : 'Something needs fixing.';
                sum.style.color = s.summary === 'ok' ? '#10b981' : s.summary === 'warn' ? '#f59e0b' : '#ef4444';
                return s;
            }).catch(function () {
                document.getElementById('summary').textContent = 'The bridge did not answer.';
            });
        }
        document.getElementById('newCodeBtn').onclick = function () {
            if (!confirm('Make a new pairing code? The phone will stop working until you type the new code into the app.')) return;
            fetch('/pairing/new', { method: 'POST' }).then(function (r) { return r.json(); }).then(function (d) {
                if (d && d.code) { document.getElementById('pairCode').textContent = d.code; document.getElementById('newCodeNote').textContent = 'Done. Enter the new code in the app.'; }
                else { document.getElementById('newCodeNote').textContent = (d && d.message) || 'Could not make a new code.'; }
            }).catch(function () { document.getElementById('newCodeNote').textContent = 'The bridge did not answer.'; });
        };
        document.getElementById('recheckBtn').onclick = refreshStatus;
        document.getElementById('resendBtn').onclick = function () {
            var btn = this, note = document.getElementById('resendNote');
            btn.disabled = true; note.textContent = 'Asking the script...';
            fetch('/resend', { method: 'POST' }).then(function (r) { return r.json(); }).then(function (reply) {
                if (reply.status !== 'requested') { note.textContent = reply.message || 'Could not ask.'; btn.disabled = false; return; }
                var tries = 0;
                (function poll() {
                    refreshStatus().then(function (s) {
                        var rs = s && s.script && s.script.resend;
                        if (rs && rs.id === reply.id) {
                            note.textContent = rs.result === 'sent' ? 'Sent: ' + rs.detail : rs.result === 'nothing' ? rs.detail : 'The script could not do it: ' + rs.detail;
                            btn.disabled = false;
                        } else if (++tries > 12) {
                            note.textContent = 'No answer from the script. Is Live Editor still open?'; btn.disabled = false;
                        } else { setTimeout(poll, 1500); }
                    });
                })();
            }).catch(function () { note.textContent = 'The bridge did not answer.'; btn.disabled = false; });
        };

        updateEvents();
        setInterval(updateEvents, 4000);
        refreshStatus();
        setInterval(refreshStatus, 5000);
    </script>
</body>
</html>
"@
                $buffer = [System.Text.Encoding]::UTF8.GetBytes($html)
                $Response.ContentLength64 = $buffer.Length
                $Response.OutputStream.Write($buffer, 0, $buffer.Length)
                $Response.Close()
            }
        }
        }
    } catch {
        try {
            $Response.StatusCode = 500
            $Response.Close()
        } catch {}
        Start-Sleep -Milliseconds 50
    }
}
