# EA Sports FC Live Telemetry Bridge (Native PowerShell - No Python Required)
param(
    [int]$Port = 5050
)

$ErrorActionPreference = "Continue"
$EventsFile = Join-Path $PSScriptRoot "telemetry_events.json"

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host " EA SPORTS FC TELEMETRY BRIDGE (Native PowerShell)" -ForegroundColor Yellow
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
        [string]$RawPayload
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
# ACCESS CONTROL
# The phone only READS (GET /events). Everything that writes - the Lua scripts,
# the test buttons and /clear - runs on this same PC. So reads are open to the
# local network, but writes are only accepted from the PC itself.
# Host / Origin checks keep web pages open in a browser from reaching the bridge.
# =========================================================================
$MaxBodyBytes = 5MB
$PcName = "$env:COMPUTERNAME".ToLower()

function Test-LocalClient {
    param($Request)
    try {
        $remote = $Request.RemoteEndPoint.Address
        if ($null -eq $remote) { return $false }
        if ($remote.IsIPv4MappedToIPv6) { $remote = $remote.MapToIPv4() }
        if ([System.Net.IPAddress]::IsLoopback($remote)) { return $true }
        # Connecting to the PC's own LAN address: remote and local address are identical
        $local = $Request.LocalEndPoint.Address
        if ($null -eq $local) { return $false }
        if ($local.IsIPv4MappedToIPv6) { $local = $local.MapToIPv4() }
        return $remote.Equals($local)
    } catch { return $false }
}

function Test-AllowedHost {
    # Allow IP addresses, localhost and this PC's name. Other host names are refused
    # so a malicious web page cannot reach the bridge through DNS rebinding.
    param($Request)
    $h = [string]$Request.UserHostName
    if ([string]::IsNullOrWhiteSpace($h)) { return $true }   # HTTP/1.0 client without Host header - not a browser
    $h = $h.Trim().ToLower()
    if ($h.StartsWith("[")) {
        $end = $h.IndexOf("]")
        if ($end -gt 0) { $name = $h.Substring(1, $end - 1) } else { $name = $h }
    } elseif (($h.Split(":").Count - 1) -eq 1) {
        $name = $h.Split(":")[0]
    } else {
        $name = $h
    }
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
    param($Response, [int]$Code, [string]$Message)
    try {
        $Response.StatusCode = $Code
        $Response.ContentType = "application/json"
        $buf = [System.Text.Encoding]::UTF8.GetBytes((@{ status = "error"; message = $Message } | ConvertTo-Json -Compress))
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
    $Listener.Start()
    Write-Host ""
    Write-Host "===================================================" -ForegroundColor Green
    Write-Host " Bridge Server started successfully!" -ForegroundColor Green
    Write-Host " 🟢 Active listening on Port $Port." -ForegroundColor Green
    Write-Host " Keep this window open while playing EA FC. Press Ctrl+C to stop." -ForegroundColor White
    Write-Host "===================================================" -ForegroundColor Green
    Write-Host ""
} catch {
    Write-Host ""
    Write-Host "===================================================" -ForegroundColor Red
    Write-Host " ERROR: Failed to bind to port ${Port}: $_" -ForegroundColor Red
    Write-Host " Please close any other app using that port." -ForegroundColor Yellow
    Write-Host "===================================================" -ForegroundColor Red
    pause
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
            }

            # Primary domestic league = the managed team's row with the most matches played (team_leagues.csv)
            $bestPld = -1
            foreach ($tl in (Read-CsvRows (Join-Path $latestSnap "team_leagues.csv"))) {
                $tlTid = ConvertTo-Int $tl.team_id
                $tlTname = [string]$tl.team_name
                if ($tlTid -eq $managedTeamId -or ($managedClubName -and $tlTname.ToLower().Contains($managedClubName.ToLower()))) {
                    $pld = ConvertTo-Int $tl.matches_played
                    $pos = ConvertTo-Int $tl.current_table_position
                    if ($pld -gt $bestPld -and $pos -gt 0) {
                        $bestPld = $pld
                        $leaguePos = $pos
                        if ($tl.league_name) { $currentLeagueName = [string]$tl.league_name }
                    } elseif ($bestPld -eq -1 -and $pos -gt 0) {
                        $leaguePos = $pos
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
    $mTeam = $null
    $oTeam = $null
    if ($teamRows.Count -gt $managedTeamIndex) { $mTeam = $teamRows[$managedTeamIndex] } elseif ($teamRows.Count -gt 0) { $mTeam = $teamRows[0] }
    if ($teamRows.Count -gt $oppTeamIndex) { $oTeam = $teamRows[$oppTeamIndex] } elseif ($teamRows.Count -gt 1) { $oTeam = $teamRows[1] }

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

    # Man of the match straight from the game's own flag, else the best rating
    $topPlayer = ""
    $highestRating = -1.0
    foreach ($p in $participants) {
        $r = ConvertTo-Dbl $p.game_rating 6.0
        $motmFlag = ([string]$p.man_of_the_match).Trim().ToLower()
        if (($motmFlag -eq "true" -or $motmFlag -eq "1") -and $p.player_name) {
            $topPlayer = [string]$p.player_name
            break
        }
        if ($r -gt $highestRating -and $p.player_name) {
            $highestRating = $r
            $topPlayer = [string]$p.player_name
        }
    }

    $shotsFor = ConvertTo-Int (Get-FirstValue $mTeam.shots $mTeam.shots_on_target)
    $shotsAgainst = ConvertTo-Int (Get-FirstValue $oTeam.shots $oTeam.shots_on_target)
    $xGF = ConvertTo-Dbl $mTeam.npk_expected_goals
    $xGA = ConvertTo-Dbl $oTeam.npk_expected_goals
    $passesFor = ConvertTo-Int $mTeam.passes
    $passesAgainst = ConvertTo-Int $oTeam.passes
    $passAcc = ConvertTo-Int (ConvertTo-Pct $mTeam.pass_accuracy_pct_derived)
    $possession = ConvertTo-Int (ConvertTo-Pct (Get-FirstValue $mTeam.possession $mTeam.possession_pct))
    if ($possession -le 0 -and ($passesFor + $passesAgainst) -gt 0) {
        $possession = [int][Math]::Floor(($passesFor / ($passesFor + $passesAgainst)) * 100)
    }
    if ($possession -le 0) { $possession = 52 }

    # Player performances - only players who actually played (the export lists the whole matchday squad)
    $playerPerfs = @()
    $goalPlayers = @()
    foreach ($p in $participants) {
        $pTeam = ConvertTo-Int $p.team_index
        $pMinutes = ConvertTo-Dbl $p.minutes_played -1.0
        $played = -not ($maxMinutes -gt 0 -and $pMinutes -ge 0 -and $pMinutes -le 0)
        if ($pTeam -ne $managedTeamIndex -or -not $played) { continue }

        $pName = if ($p.player_name) { [string]$p.player_name } else { "Player" }
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
        $pMotmFlag = ([string]$p.man_of_the_match).Trim().ToLower()
        $isMotm = ($pName -eq $topPlayer) -or ($pMotmFlag -eq "true") -or ($pMotmFlag -eq "1")

        $playerPerfs += [PSCustomObject]@{
            playerId = [string]$p.player_id
            playerName = $pName
            rating = [Math]::Round($pRating, 1)
            goals = $pGoals
            assists = $pAssists
            passes = $pPasses
            passAccuracy = $pPassAcc
            shots = $pShots
            shotAccuracy = $pShotAcc
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
        $goalDetails += [PSCustomObject]@{
            playerName = "$oppName Goal"
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
        passingAccuracy = $passAcc
        expectedGoals = [Math]::Round($xGF, 2)
        expectedGoalsAgainst = [Math]::Round($xGA, 2)
        motmName = $topPlayer
        leaguePosition = $leaguePosOut
        detailSource = "atlas"
        playerPerformances = $playerPerfs
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

Import-AtlasState

$asyncResult = $Listener.BeginGetContext($null, $null)
$lastAtlasCheck = [DateTime]::MinValue

while ($Listener.IsListening) {
    try {
        $now = [DateTime]::UtcNow
        if (($now - $lastAtlasCheck).TotalSeconds -ge 2) {
            $lastAtlasCheck = $now
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

        if ($method -eq "GET") {
            # These used to fire on a plain GET, so a link or <img> tag on any web page could
            # trigger them. They are POST/DELETE-only now (use the dashboard buttons).
            if ($isWriteRoute) {
                $Response.Redirect("/")
                $Response.Close()
                continue
            }
        } elseif ($method -eq "POST" -or $method -eq "DELETE") {
            if (-not (Test-LocalClient $Request)) {
                Send-Reject $Response 403 "Writes are only accepted from the PC running the bridge."
                continue
            }
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
            $text = $Body.Trim()
            if ($text -eq "") {
                Send-Reject $Response 400 "Empty body."
                continue
            }

            $reply = '{"status":"ok"}'
            if ($text.StartsWith("{") -or $text.StartsWith("[")) {
                # JSON from the Live Editor scripts. A malformed one is refused with HTTP 400 instead of being stored:
                # a broken event that looks like JSON would stall the phone app for every event after it.
                $jsonObj = ConvertFrom-JsonObject $text
                if ($null -eq $jsonObj) {
                    $preview = if ($text.Length -gt 120) { $text.Substring(0, 120) } else { $text }
                    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] REJECTED malformed JSON payload ($($text.Length) bytes): $preview" -ForegroundColor Red
                    Send-Reject $Response 400 "Payload is not a valid JSON object."
                    continue
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
                    Add-TelemetryEvent -Title $title -Message $msg -RawPayload $text
                    if ($jsonObj.playerName) {
                        Write-Host "  -> Transfer: $($jsonObj.playerName) ($($jsonObj.type))" -ForegroundColor Cyan
                    }
                }
            } else {
                Add-TelemetryEvent -Title "Match Update" -Message (Remove-PenaltyNoise $text)
            }

            $Response.StatusCode = 200
            $Response.ContentType = "application/json"
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($reply)
            $Response.ContentLength64 = $buffer.Length
            $Response.OutputStream.Write($buffer, 0, $buffer.Length)
            $Response.Close()
        } elseif ($Request.HttpMethod -eq "GET") {
            if ($rawPath -eq "/events" -or $rawPath -eq "/json" -or $rawPath -eq "/api/telemetry") {
                $Response.StatusCode = 200
                $Response.ContentType = "application/json"
                $jsonArray = "[" + ($global:EventList -join ",") + "]"
                $buffer = [System.Text.Encoding]::UTF8.GetBytes($jsonArray)
                $Response.ContentLength64 = $buffer.Length
                $Response.OutputStream.Write($buffer, 0, $buffer.Length)
                $Response.Close()
            } else {
                # Return Web UI Status Dashboard
                $Response.StatusCode = 200
                $Response.ContentType = "text/html; charset=utf-8"
                $Response.Headers.Add("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
                $eventCount = $global:EventList.Count
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
    </style>
</head>
<body>
    <div class="card">
        <h1>⚽ EA Sports FC Telemetry Bridge</h1>
        <div class="status">🟢 Bridge Active (Listening on Port $Port)</div>

        <div class="info">
            <strong>📱 IP Address for Android App:</strong><br>
            <span style="color:#10b981;font-size:16px;font-weight:bold;">http://${primaryIp}:${Port}</span><br>
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

        updateEvents();
        setInterval(updateEvents, 4000);
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
