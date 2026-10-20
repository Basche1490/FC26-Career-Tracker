@echo off
rem ============================================================================================
rem  FC26 Career Tracker - the one launcher for the PC side.
rem  Double-click it. It finds the bridge and FC Live Editor wherever they are, puts the two
rem  Live Editor scripts in place, makes Windows allow your phone, and starts the bridge in
rem  the background with no window. Run it again any time: it fixes what is missing and updates.
rem
rem  Options, typed after the name in a command prompt: stop, status, newcode, reset or nopause.
rem
rem  This file is a batch file followed by a PowerShell program, so it works from any folder.
rem ============================================================================================
setlocal
title FC26 Career Tracker - launcher
set "FC26_LAUNCHER=%~f0"
set "FC26_ARGS=%*"
rem Windows PowerShell by its full path: a program named only by its name would be looked for in the current folder
rem first, so a file called powershell.exe left next to this launcher (in Downloads, say) could run instead.
set "FC26_PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%FC26_PS%" (
  echo Windows PowerShell was not found at %FC26_PS%
  pause
  exit /b 1
)
"%FC26_PS%" -NoProfile -Command "$t=[IO.File]::ReadAllText($env:FC26_LAUNCHER); $i=$t.IndexOf('#'+'#PS-START'); & ([scriptblock]::Create($t.Substring($i)))"
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
  echo.
  echo The launcher stopped with an error ^(code %RC%^). The message above says why.
  pause
)
exit /b %RC%
##PS-START
# ---------------------------------------------------------------------------------------------
# FC26 Career Tracker launcher - the PowerShell part.
# Plain ASCII on purpose (this file is a .bat) and compatible with Windows PowerShell 5.1.
# ---------------------------------------------------------------------------------------------
$ErrorActionPreference = 'Stop'

$script:LauncherVersion = '1.1.0'
$script:BridgePort      = 5050
$script:FirewallRule    = 'EA FC Telemetry Bridge'
$script:BridgeFiles     = @('bridge.ps1', 'telemetry_bridge.py')
$script:ScriptFiles     = @('FC26_Auto_Capture.lua', 'FC26_AutoCapture_v1.1.1.lua')
# Scripts of earlier versions. They are replaced by the two above; if they sit in the Live Editor scripts
# folder they are renamed to <name>.old (never deleted).
$script:LegacyScripts   = @(
    'telemetry_sync.lua', 'telemetry_sender_v2.lua', 'telemetry_auto_loop.lua', 'telemetry_test_connection.lua',
    'send_latest_match.lua', 'FC26_MatchStatsExport.lua', 'FC26_Snapshot.lua'
)
$script:SkipNames = @(
    'Windows', 'Windows.old', '$Recycle.Bin', 'System Volume Information', 'ProgramData', 'AppData', 'node_modules',
    '.git', 'Documents and Settings', 'legacy', 'tools', 'steamapps', 'WindowsApps', 'Recovery', 'PerfLogs'
)
# Tests replace these to avoid asking a person (and to pretend Windows calls a network Public).
$script:ChooseHook = $null
$script:FolderHook = $null
$script:PublicNetworksHook = $null
$script:PairAlphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'   # the same as the bridge: no I, O, 0 or 1
$script:PairLength = 16

# ---- output --------------------------------------------------------------------------------------
function Say([string]$Text, [string]$Color = 'Gray') { Write-Host $Text -ForegroundColor $Color }
function Say-Ok([string]$Text)   { Write-Host '  [ OK ] ' -NoNewline -ForegroundColor Green;  Write-Host $Text }
function Say-Warn([string]$Text) { Write-Host '  [ !  ] ' -NoNewline -ForegroundColor Yellow; Write-Host $Text }
function Say-Fail([string]$Text) { Write-Host '  [ X  ] ' -NoNewline -ForegroundColor Red;    Write-Host $Text }
function Say-Step([string]$Number, [string]$Text) { Write-Host ''; Write-Host ("Step $Number - $Text") -ForegroundColor Cyan }

function Test-IsWindows { return ($env:OS -eq 'Windows_NT') }

# ---- Windows programs, always by their full path -------------------------------------------------------------------
# A program named only by its name can be found in the current folder or an odd PATH entry first, so a file called
# netsh.exe or powershell.exe left in Downloads could run instead (with administrator rights, even). These are always
# started from Windows' own folder.
function Get-SystemExe([string]$Relative, [string]$Fallback) {
    if ((Test-IsWindows) -and $env:SystemRoot) {
        $p = Join-Path $env:SystemRoot $Relative
        if (Test-Path -LiteralPath $p -PathType Leaf) { return $p }
    }
    return $Fallback
}
function Get-NetshExe { return (Get-SystemExe 'System32\netsh.exe' 'netsh') }
function Get-PowerShellExe { return (Get-SystemExe 'System32\WindowsPowerShell\v1.0\powershell.exe' 'powershell.exe') }

# ---- remembered choices --------------------------------------------------------------------------
function Get-StateDir {
    if ($env:FC26_STATE_DIR) { return $env:FC26_STATE_DIR }
    $base = $env:APPDATA
    if (-not $base) { $base = Join-Path $HOME '.config' }
    return (Join-Path $base 'FC26Tracker')
}

function New-LauncherState { return @{ bridgeDir = ''; scriptDirs = @(); allowPublic = $false; publicDeclined = @() } }

function Read-LauncherState {
    $state = New-LauncherState
    $path = Join-Path (Get-StateDir) 'launcher.json'
    try {
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            $j = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
            if ($j.bridgeDir) { $state.bridgeDir = [string]$j.bridgeDir }
            if ($j.scriptDirs) { $state.scriptDirs = @(@($j.scriptDirs) | ForEach-Object { [string]$_ }) }
            if ($j.allowPublic -eq $true) { $state.allowPublic = $true }
            if ($j.publicDeclined) { $state.publicDeclined = @(@($j.publicDeclined) | ForEach-Object { [string]$_ }) }
        }
    } catch { }
    return $state
}

function Save-LauncherState($State) {
    try {
        $dir = Get-StateDir
        if (-not (Test-Path -LiteralPath $dir -PathType Container)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        $obj = [pscustomobject]@{
            bridgeDir = [string]$State.bridgeDir; scriptDirs = @($State.scriptDirs)
            allowPublic = [bool]$State.allowPublic; publicDeclined = @($State.publicDeclined)
        }
        [IO.File]::WriteAllText((Join-Path $dir 'launcher.json'), ($obj | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
    } catch { }
}

# ---- the pairing code ----------------------------------------------------------------------------
# The phone has to prove it knows this code before the bridge tells it anything (docs/SECURITY.md). It lives in
# bridge.key next to launcher.json; the bridge reads the same file (and makes it itself if it was started some other
# way). A new code written here is used by a running bridge from its next request on.
function ConvertTo-PairCode([string]$Text) { return ([regex]::Replace([string]$Text, '[^A-Za-z0-9]', '')).ToUpperInvariant() }

function Test-PairCode([string]$Text) {
    $c = ConvertTo-PairCode $Text
    if ($c.Length -ne $script:PairLength) { return $false }
    foreach ($ch in $c.ToCharArray()) { if ($script:PairAlphabet.IndexOf($ch) -lt 0) { return $false } }
    return $true
}

function Format-PairCode([string]$Text) {
    $c = ConvertTo-PairCode $Text
    $parts = @()
    for ($i = 0; $i -lt $c.Length; $i += 4) { $parts += $c.Substring($i, [Math]::Min(4, $c.Length - $i)) }
    return ($parts -join '-')
}

function New-PairCode {
    $bytes = New-Object byte[] $script:PairLength
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $sb = New-Object Text.StringBuilder
    foreach ($b in $bytes) { [void]$sb.Append($script:PairAlphabet[[int]$b % 32]) }   # 256 is a multiple of 32: no bias
    return $sb.ToString()
}

function Get-PairingFile { return (Join-Path (Get-StateDir) 'bridge.key') }

function Save-PairingCode([string]$Code) {
    $dir = Get-StateDir
    if (-not (Test-Path -LiteralPath $dir -PathType Container)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $path = Get-PairingFile
    $tmp = $path + '.tmp'
    [IO.File]::WriteAllText($tmp, ((Format-PairCode $Code) + "`r`n"), (New-Object Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $tmp -Destination $path -Force
}

# The current code, made now if there is none (or the file is damaged). '' when it cannot be read or written.
function Get-PairingCode {
    $path = Get-PairingFile
    $code = ''
    try { if (Test-Path -LiteralPath $path -PathType Leaf) { $code = ConvertTo-PairCode ([IO.File]::ReadAllText($path)) } } catch { $code = '' }
    if (Test-PairCode $code) { return $code }
    try {
        $code = New-PairCode
        Save-PairingCode $code
        return $code
    } catch { return '' }
}

# ---- versions ------------------------------------------------------------------------------------
function Read-Head([string]$Path, [int]$Bytes) {
    $fs = [IO.File]::OpenRead($Path)
    try {
        $n = [int][Math]::Min([long]$Bytes, $fs.Length)
        $buf = New-Object byte[] $n
        [void]$fs.Read($buf, 0, $n)
        return [Text.Encoding]::UTF8.GetString($buf)
    } finally { $fs.Dispose() }
}

function Get-FirstMatch([string]$Text, [string]$Pattern) {
    $m = [regex]::Match($Text, $Pattern)
    if ($m.Success) { return $m.Groups[1].Value }
    return ''
}

function Get-LuaVersion([string]$Path) {
    try { return (Get-FirstMatch (Read-Head $Path 20000) '(?m)^\s*local\s+VERSION\s*=\s*"([0-9][0-9.]*)"') } catch { return '' }
}

function Get-BridgeVersion([string]$Dir) {
    $v = ''
    try {
        $py = Join-Path $Dir 'telemetry_bridge.py'
        if (Test-Path -LiteralPath $py -PathType Leaf) { $v = Get-FirstMatch (Read-Head $py 30000) '(?m)^BRIDGE_VERSION\s*=\s*"([0-9][0-9.]*)"' }
        if (-not $v) {
            $ps1 = Join-Path $Dir 'bridge.ps1'
            if (Test-Path -LiteralPath $ps1 -PathType Leaf) { $v = Get-FirstMatch (Read-Head $ps1 8000) '\$BridgeVersion\s*=\s*"([0-9][0-9.]*)"' }
        }
    } catch { }
    return $v
}

function ConvertTo-VersionParts([string]$Text) {
    $parts = @(0, 0, 0)
    $i = 0
    foreach ($p in ($Text -split '\.')) {
        if ($i -ge 3) { break }
        if ($p -match '^\d+$') { $parts[$i] = [int]$p }
        $i++
    }
    return $parts
}

# -1, 0 or 1; a missing version is the oldest.
function Compare-Versions([string]$A, [string]$B) {
    $x = ConvertTo-VersionParts $A
    $y = ConvertTo-VersionParts $B
    for ($i = 0; $i -lt 3; $i++) {
        if ($x[$i] -lt $y[$i]) { return -1 }
        if ($x[$i] -gt $y[$i]) { return 1 }
    }
    return 0
}

# ---- finding the project files -------------------------------------------------------------------
function Test-HasFiles([string]$Dir, [string[]]$Names) {
    if (-not $Dir) { return $false }
    if (-not (Test-Path -LiteralPath $Dir -PathType Container)) { return $false }
    foreach ($n in $Names) {
        if (-not (Test-Path -LiteralPath (Join-Path $Dir $n) -PathType Leaf)) { return $false }
    }
    return $true
}

# The folder with the bridge files, given the project folder or the bridge folder itself. $null if neither is.
function Resolve-BridgeDir([string]$Root) {
    if (Test-HasFiles $Root $script:BridgeFiles) { return $Root }
    $sub = Join-Path $Root 'pc-bridge'
    if (Test-HasFiles $sub $script:BridgeFiles) { return $sub }
    return $null
}

function Resolve-ScriptsDir([string]$Root) {
    if (Test-HasFiles $Root $script:ScriptFiles) { return $Root }
    $sub = Join-Path $Root 'live-editor-scripts'
    if (Test-HasFiles $sub $script:ScriptFiles) { return $sub }
    return $null
}

# Sub-folders in alphabetical order, so the search (and a list shown to the person) never depends on how the disk
# happens to enumerate them.
function Get-SubDirs([string]$Dir) {
    try {
        $dirs = [IO.Directory]::GetDirectories($Dir)
        [Array]::Sort($dirs, [StringComparer]::OrdinalIgnoreCase)
        return @($dirs)
    } catch { return @() }
}

# Looks for the project next to the launcher: its own folder, the folder above it, and one level of folders in
# either (a zip often unpacks into a folder inside a folder). Returns @{ BridgeDir; ScriptsDir }, each maybe $null.
function Find-ProjectNear([string]$Start) {
    $roots = New-Object System.Collections.ArrayList
    if ($Start) {
        [void]$roots.Add($Start)
        $parent = Split-Path -Parent $Start
        if ($parent) { [void]$roots.Add($parent) }
        foreach ($d in (Get-SubDirs $Start)) { [void]$roots.Add($d) }
        if ($parent) { foreach ($d in (Get-SubDirs $parent)) { [void]$roots.Add($d) } }
    }
    $bridgeOnly = $null
    $scriptsOnly = $null
    foreach ($r in $roots) {
        $b = Resolve-BridgeDir $r
        $s = Resolve-ScriptsDir $r
        if ($b -and $s) { return @{ BridgeDir = $b; ScriptsDir = $s } }
        if ($b -and -not $bridgeOnly) { $bridgeOnly = $b }
        if ($s -and -not $scriptsOnly) { $scriptsOnly = $s }
    }
    return @{ BridgeDir = $bridgeOnly; ScriptsDir = $scriptsOnly }
}

# Breadth-first search for folders that satisfy $Match, never deeper than $MaxDepth, with a cap on the number of
# folders looked at and on the time taken, so a big disk cannot make the launcher hang.
function Search-Directories {
    param(
        [string[]]$Roots,
        [int]$MaxDepth,
        [scriptblock]$Match,
        [int]$MaxVisited = 6000,
        [int]$TimeoutSeconds = 20
    )
    $found = New-Object System.Collections.ArrayList
    $seen = @{}
    $queue = New-Object System.Collections.Queue
    foreach ($r in $Roots) {
        if ($r -and (Test-Path -LiteralPath $r -PathType Container)) { $queue.Enqueue(@($r, 0)) }
    }
    $visited = 0
    $clock = [Diagnostics.Stopwatch]::StartNew()
    while ($queue.Count -gt 0) {
        $item = $queue.Dequeue()
        $dir = [string]$item[0]
        $depth = [int]$item[1]
        $key = $dir.TrimEnd('\', '/').ToLowerInvariant()
        if ($seen.ContainsKey($key)) { continue }
        $seen[$key] = $true
        $visited++
        if ($visited -gt $MaxVisited) { break }
        if ($clock.Elapsed.TotalSeconds -gt $TimeoutSeconds) { break }
        $hit = $false
        try { $hit = [bool](& $Match $dir) } catch { $hit = $false }
        if ($hit) { [void]$found.Add($dir) }
        if ($depth -ge $MaxDepth) { continue }
        foreach ($c in (Get-SubDirs $dir)) {
            $name = [IO.Path]::GetFileName($c.TrimEnd('\', '/'))
            if ($script:SkipNames -contains $name) { continue }
            $queue.Enqueue(@($c, ($depth + 1)))
        }
    }
    return @($found)
}

function Get-UserRoots {
    $roots = New-Object System.Collections.ArrayList
    $homeDir = $env:USERPROFILE
    if (-not $homeDir) { $homeDir = $HOME }
    foreach ($var in @('OneDrive', 'OneDriveConsumer', 'OneDriveCommercial')) {
        $v = [Environment]::GetEnvironmentVariable($var)
        if ($v) { foreach ($s in @('Desktop', 'Documents', 'Downloads')) { [void]$roots.Add((Join-Path $v $s)) } }
    }
    foreach ($s in @('Desktop', 'Documents', 'Downloads')) {
        [void]$roots.Add((Join-Path $homeDir $s))
        [void]$roots.Add((Join-Path (Join-Path $homeDir 'OneDrive') $s))
    }
    return @($roots | Select-Object -Unique)
}

function Get-DriveRoots {
    if (-not (Test-IsWindows)) { return @() }
    try {
        return @([IO.DriveInfo]::GetDrives() | Where-Object { $_.DriveType -eq 'Fixed' -and $_.IsReady } | ForEach-Object { $_.RootDirectory.FullName })
    } catch { return @() }
}

function Get-VersionKey([string]$Text) {
    $p = ConvertTo-VersionParts $Text
    return ('{0:D6}.{1:D6}.{2:D6}' -f $p[0], $p[1], $p[2])
}

# Folders that hold the bridge, best first: the one with the highest bridge version, then the latest bridge file.
function Find-BridgeDirs([string[]]$UserRoots, [string[]]$DriveRoots) {
    $match = { param($d) Test-HasFiles $d $script:BridgeFiles }
    $all = @()
    $all += @(Search-Directories -Roots $UserRoots -MaxDepth 4 -Match $match)
    if ($all.Count -eq 0) { $all += @(Search-Directories -Roots $DriveRoots -MaxDepth 3 -Match $match) }
    $rated = @(@($all | Select-Object -Unique) | ForEach-Object {
        $ver = Get-BridgeVersion $_
        $time = (Get-Item -LiteralPath (Join-Path $_ 'bridge.ps1')).LastWriteTimeUtc
        [pscustomobject]@{ Dir = $_; Key = (Get-VersionKey $ver); Time = $time }
    })
    $sorted = @($rated | Sort-Object -Property @{ Expression = 'Key'; Descending = $true }, @{ Expression = 'Time'; Descending = $true })
    return @($sorted | ForEach-Object { $_.Dir })
}

# A folder found by searching the disk (not next to this launcher, not used before) is only used when the person says
# so: the launcher runs the bridge from it and copies scripts from it into Live Editor.
function Confirm-FoundFolder([string]$What, [string]$Dir, [string]$Version) {
    $v = $(if ($Version) { " (version $Version)" } else { '' })
    $question = "Found {0} in:`n     {1}{2}`n  The launcher would run these files. Use this folder?" -f $What, $Dir, $v
    $pick = Read-Choice $question @('Yes, use this folder', 'No, let me pick the folder myself')
    return ($pick -eq 0)
}

# ---- finding Live Editor -------------------------------------------------------------------------
# A Live Editor folder has a lua folder with the imports its scripts require (and the scripts / autorun folders).
function Test-LiveEditorRoot([string]$Dir) {
    $lua = Join-Path $Dir 'lua'
    foreach ($sub in @('imports', 'scripts', 'autorun')) {
        if (Test-Path -LiteralPath (Join-Path $lua $sub) -PathType Container) { return $true }
    }
    return $false
}

function Get-ScriptsDirOf([string]$LiveEditorRoot) {
    return (Join-Path (Join-Path $LiveEditorRoot 'lua') 'scripts')
}

# What a person picked in the folder dialog (the Live Editor folder, its lua folder, or its scripts folder) as the
# folder the scripts go into. $null when it does not look like any of them.
function Resolve-PickedScriptsDir([string]$Picked) {
    if (-not $Picked) { return $null }
    $p = $Picked.TrimEnd('\', '/')
    if (-not (Test-Path -LiteralPath $p -PathType Container)) { return $null }
    $leaf = Split-Path -Leaf $p
    $parent = Split-Path -Parent $p
    if ($leaf -ieq 'scripts' -and $parent -and (Split-Path -Leaf $parent) -ieq 'lua') { return $p }
    if ($leaf -ieq 'lua') { return (Join-Path $p 'scripts') }
    if (Test-LiveEditorRoot $p) { return (Get-ScriptsDirOf $p) }
    if (Test-Path -LiteralPath (Join-Path $p 'lua') -PathType Container) { return (Get-ScriptsDirOf $p) }
    if (@(Get-ChildItem -LiteralPath $p -Filter '*.lua' -File -ErrorAction SilentlyContinue).Count -gt 0) { return $p }
    return $null
}

function Same-Path([string]$A, [string]$B) {
    if (-not $A -or -not $B) { return $false }
    return ($A.TrimEnd('\', '/') -ieq $B.TrimEnd('\', '/'))
}

# Where the scripts go. Returns @{ Dirs = @(...); Note = '...' }. In this order:
#  1. the folders remembered from the last run (an earlier install is updated in place);
#  2. Live Editor folders found on the disk: the ones that already hold FC26_Auto_Capture.lua first, then the only
#     one, then the one with 26 in its name, else the person is asked which;
#  3. a folder the person picks.
function Find-ScriptTargets {
    param(
        [string]$SourceDir,
        [string[]]$Remembered,
        [string[]]$UserRoots,
        [string[]]$DriveRoots,
        [string[]]$ExtraRoots
    )
    $remembered = @(@($Remembered) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) -and -not (Same-Path $_ $SourceDir) })
    if ($remembered.Count -gt 0) { return @{ Dirs = $remembered; Note = 'the folder used last time' } }

    $le = { param($d) Test-LiveEditorRoot $d }
    $roots = @(@($UserRoots) + @($ExtraRoots) | Where-Object { $_ })
    $found = @(Search-Directories -Roots $roots -MaxDepth 4 -Match $le)
    if ($found.Count -eq 0) { $found = @(Search-Directories -Roots $DriveRoots -MaxDepth 3 -Match $le) }
    $found = @($found | Select-Object -Unique | Sort-Object)

    if ($found.Count -gt 1) {
        $has = @($found | Where-Object { Test-Path -LiteralPath (Join-Path (Get-ScriptsDirOf $_) 'FC26_Auto_Capture.lua') -PathType Leaf })
        if ($has.Count -gt 0) { $found = $has }
    }
    if ($found.Count -eq 1) {
        return @{ Dirs = @((Get-ScriptsDirOf $found[0])); Note = 'found FC Live Editor' }
    }
    if ($found.Count -gt 1) {
        $with26 = @($found | Where-Object { (Split-Path -Leaf $_) -match '26' })
        if ($with26.Count -eq 1) { return @{ Dirs = @((Get-ScriptsDirOf $with26[0])); Note = 'found several Live Editors, chose the FC 26 one' } }
        $options = @($found)
        $pick = Read-Choice 'More than one Live Editor was found. Which one do you use for FC 26?' $options
        if ($pick -ge 0 -and $pick -lt $options.Count) { return @{ Dirs = @((Get-ScriptsDirOf $options[$pick])); Note = 'your choice' } }
    }

    $picked = Read-FolderChoice 'Select your FC Live Editor folder (the one that contains the "lua" folder)'
    $dir = Resolve-PickedScriptsDir $picked
    if ($dir) { return @{ Dirs = @($dir); Note = 'the folder you picked' } }
    return @{ Dirs = @(); Note = 'no Live Editor folder was chosen' }
}

function Read-Choice([string]$Question, [string[]]$Options) {
    if ($script:ChooseHook) { return (& $script:ChooseHook $Question $Options) }
    Say $Question 'Yellow'
    for ($i = 0; $i -lt $Options.Count; $i++) { Say ("   {0}) {1}" -f ($i + 1), $Options[$i]) }
    $answer = Read-Host 'Type the number (or press Enter for 1)'
    if (-not $answer) { return 0 }
    $n = 0
    if ([int]::TryParse($answer.Trim(), [ref]$n) -and $n -ge 1 -and $n -le $Options.Count) { return ($n - 1) }
    return -1
}

function Read-FolderChoice([string]$Description) {
    if ($script:FolderHook) { return (& $script:FolderHook $Description) }
    try {
        Add-Type -AssemblyName System.Windows.Forms
        $dlg = New-Object System.Windows.Forms.FolderBrowserDialog
        $dlg.Description = $Description
        $dlg.ShowNewFolderButton = $false
        if ($dlg.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { return $dlg.SelectedPath }
        return $null
    } catch {
        Say $Description 'Yellow'
        $typed = Read-Host 'Paste the folder path (or press Enter to skip)'
        if ($typed) { return $typed.Trim('"', ' ') }
        return $null
    }
}

# ---- installing the scripts ----------------------------------------------------------------------
function Test-SameFile([string]$A, [string]$B) {
    if ((Get-Item -LiteralPath $A).Length -ne (Get-Item -LiteralPath $B).Length) { return $false }
    return ((Get-FileHash -LiteralPath $A -Algorithm SHA256).Hash -eq (Get-FileHash -LiteralPath $B -Algorithm SHA256).Hash)
}

function Move-Aside([string]$Path) {
    $new = $Path + '.old'
    $i = 2
    while (Test-Path -LiteralPath $new) { $new = $Path + '.old' + $i; $i++ }
    Move-Item -LiteralPath $Path -Destination $new
    return $new
}

function Install-ScriptFiles([string]$SourceDir, [string]$TargetDir) {
    $r = [pscustomobject]@{ Target = $TargetDir; Copied = @(); Same = @(); KeptNewer = @(); Moved = @(); Errors = @() }
    if (-not (Test-Path -LiteralPath $TargetDir -PathType Container)) {
        try { New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null }
        catch { $r.Errors += "Cannot create $TargetDir : $($_.Exception.Message)"; return $r }
    }
    foreach ($name in $script:ScriptFiles) {
        $src = Join-Path $SourceDir $name
        $dst = Join-Path $TargetDir $name
        try {
            if (Test-Path -LiteralPath $dst -PathType Leaf) {
                if (Test-SameFile $src $dst) { $r.Same += $name; continue }
                if ($name -eq 'FC26_Auto_Capture.lua') {
                    $have = Get-LuaVersion $dst
                    $new = Get-LuaVersion $src
                    if ($have -and $new -and ((Compare-Versions $have $new) -gt 0)) { $r.KeptNewer += "$name (version $have)"; continue }
                }
                Copy-Item -LiteralPath $dst -Destination ($dst + '.bak') -Force
            }
            Copy-Item -LiteralPath $src -Destination $dst -Force
            $r.Copied += $name
        } catch { $r.Errors += "$name : $($_.Exception.Message)" }
    }
    foreach ($legacy in $script:LegacyScripts) {
        $old = Join-Path $TargetDir $legacy
        if (Test-Path -LiteralPath $old -PathType Leaf) {
            try { [void](Move-Aside $old); $r.Moved += $legacy } catch { $r.Errors += "$legacy : $($_.Exception.Message)" }
        }
    }
    return $r
}

# ---- the bridge ----------------------------------------------------------------------------------
# Asks a bridge on this PC how it is. Running = something that is this app's bridge answered. Old = it answered
# like a bridge from before the health report. Foreign = something else answers on the port.
function Get-BridgeStatus([int]$Port, [int]$TimeoutMs = 2500) {
    $result = @{ Running = $false; Status = $null; Old = $false; Foreign = $false }
    try {
        $req = [Net.HttpWebRequest]::Create("http://127.0.0.1:$Port/status")
        $req.Timeout = $TimeoutMs
        $req.ReadWriteTimeout = $TimeoutMs
        $req.Proxy = $null
        $resp = $req.GetResponse()
        $body = ''
        try {
            $reader = New-Object IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8)
            $body = $reader.ReadToEnd()
        } finally { $resp.Close() }
        $json = $null
        try { $json = $body | ConvertFrom-Json } catch { $json = $null }
        if ($json -and ($json.PSObject.Properties.Name -contains 'app') -and $json.app -eq 'fc26-bridge') {
            $result.Running = $true
            $result.Status = $json
        } elseif ($body -match 'Telemetry Bridge') {
            $result.Running = $true
            $result.Old = $true
        } else {
            $result.Foreign = $true
        }
    } catch [Net.WebException] {
        $resp = $_.Exception.Response
        if ($resp) { try { $resp.Close() } catch { }; $result.Foreign = $true }
    } catch { }
    return $result
}

function Find-Python {
    $probe = 'import sys; print(sys.version_info[0]*100+sys.version_info[1])'
    foreach ($name in @('py', 'python', 'python3')) {
        $cmd = Get-Command $name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $cmd) { continue }
        $prefix = @()
        if ($name -eq 'py') { $prefix = @('-3') }
        try {
            $out = & $cmd.Source @prefix '-c' $probe 2>$null
            $n = 0
            $first = [string]($out | Select-Object -First 1)
            # The Microsoft Store stub named python.exe prints nothing useful, so it is not accepted here.
            if ([int]::TryParse($first.Trim(), [ref]$n) -and $n -ge 308) { return @{ Path = $cmd.Source; Prefix = $prefix } }
        } catch { }
    }
    return $null
}

# 'ok', 'missing' or 'outdated'. Outdated: an earlier launcher made it for every kind of network (Public ones too), or
# it is not limited to devices on the local network. With $AllowPublic (the person chose it) Public networks are fine.
function Get-FirewallRuleState([bool]$AllowPublic = $false) {
    if (-not (Test-IsWindows)) { return 'ok' }
    try {
        $rules = @(Get-NetFirewallRule -DisplayName $script:FirewallRule -ErrorAction Stop)
        if ($rules.Count -eq 0) { return 'missing' }
        foreach ($r in $rules) {
            $prof = [string]$r.Profile
            if (-not $AllowPublic -and ($prof -eq 'Any' -or $prof -match 'Public')) { return 'outdated' }
            if ($AllowPublic -and $prof -ne 'Any' -and $prof -notmatch 'Public') { return 'outdated' }
            $remote = @(Get-NetFirewallAddressFilter -AssociatedNetFirewallRule $r -ErrorAction Stop | ForEach-Object { [string]$_.RemoteAddress })
            if ($remote -notcontains 'LocalSubnet') { return 'outdated' }
        }
        return 'ok'
    } catch {
        # Get-NetFirewallRule fails when no rule has that name (and on very old Windows): ask netsh whether it exists.
        try {
            & (Get-NetshExe) advfirewall firewall show rule name="$($script:FirewallRule)" *> $null
            if ($LASTEXITCODE -eq 0) { return 'ok' }
        } catch { }
        return 'missing'
    }
}

function Test-FirewallRule([bool]$AllowPublic = $false) { return ((Get-FirewallRuleState $AllowPublic) -eq 'ok') }

function Get-CurrentUserSid {
    if (-not (Test-IsWindows)) { return '' }
    try { return [string][Security.Principal.WindowsIdentity]::GetCurrent().User.Value } catch { return '' }
}

# The PowerShell bridge needs Windows' permission to listen on the port. 'ok' (for this Windows user), 'missing', or
# 'outdated' (an earlier launcher gave it to Everyone, which lets any program of any user take the port).
function Get-UrlAclState([int]$Port) {
    if (-not (Test-IsWindows)) { return 'ok' }
    try {
        $out = (& (Get-NetshExe) http show urlacl "url=http://+:$Port/" 2>&1) | Out-String
        if ($out -notmatch (':' + $Port + '/')) { return 'missing' }
        $me = [string][Security.Principal.WindowsIdentity]::GetCurrent().Name
        if ($me -and $out.IndexOf($me, [StringComparison]::OrdinalIgnoreCase) -ge 0) { return 'ok' }
        return 'outdated'
    } catch { return 'missing' }
}

function Test-UrlAcl([int]$Port) { return ((Get-UrlAclState $Port) -eq 'ok') }

# The networks Windows calls Public right now, by name. The firewall rule is for Private and Domain networks only.
function Get-PublicNetworks {
    if ($script:PublicNetworksHook) { return @(& $script:PublicNetworksHook) }
    if (-not (Test-IsWindows)) { return @() }
    try {
        return @(Get-NetConnectionProfile -ErrorAction Stop | Where-Object { [string]$_.NetworkCategory -eq 'Public' } | ForEach-Object { [string]$_.Name })
    } catch { return @() }
}

function Open-NetworkSettings {
    if (-not (Test-IsWindows)) { return }
    try { Start-Process 'ms-settings:network-status' } catch { }
}

# The commands that need administrator rights, one per line. Programs by full path; the URL permission is for this
# Windows user only (its SID), never for Everyone.
function Get-ElevatedCommands([bool]$NeedFirewall, [bool]$NeedUrlAcl, [int]$Port, [bool]$AllowPublic = $false, [string]$UserSid = '') {
    $rule = $script:FirewallRule
    $netsh = Get-NetshExe
    $profiles = $(if ($AllowPublic) { 'any' } else { 'private,domain' })
    $lines = @()
    if ($NeedFirewall) {
        $lines += "& '$netsh' advfirewall firewall delete rule name=`"$rule`" | Out-Null"
        $lines += "& '$netsh' advfirewall firewall add rule name=`"$rule`" dir=in action=allow protocol=TCP localport=$Port profile=$profiles remoteip=LocalSubnet | Out-Null"
    }
    if ($NeedUrlAcl -and ($UserSid -match '^S-1-[0-9]+(-[0-9]+)+$')) {
        $lines += "& '$netsh' http delete urlacl `"url=http://+:$Port/`" | Out-Null"
        $lines += "& '$netsh' http add urlacl `"url=http://+:$Port/`" `"sddl=D:(A;;GX;;;$UserSid)`" | Out-Null"
    }
    return ($lines -join "`r`n")
}

# Runs commands as administrator (Windows asks the person once). Returns $true when they ran.
function Invoke-Elevated([string]$Commands) {
    try {
        $enc = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Commands))
        $p = Start-Process -FilePath (Get-PowerShellExe) -Verb RunAs -WindowStyle Hidden -Wait -PassThru `
            -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', $enc)
        return ($p.ExitCode -eq 0)
    } catch { return $false }
}

# What to do about the bridge, from what answered on the port and the version in the files:
# start (nothing there), keep (this bridge, current), restart (this bridge, older than the files) or foreign
# (something else is using the port).
function Get-BridgePlan($Now, [string]$SourceVersion) {
    if ($Now.Foreign) { return @{ Action = 'foreign'; Running = '' } }
    if (-not $Now.Running) { return @{ Action = 'start'; Running = '' } }
    $running = ''
    if ($Now.Status) { $running = [string]$Now.Status.bridge.version }
    if ($Now.Old) { return @{ Action = 'restart'; Running = $running } }
    if ($SourceVersion -and ((Compare-Versions $running $SourceVersion) -lt 0)) { return @{ Action = 'restart'; Running = $running } }
    return @{ Action = 'keep'; Running = $running }
}

function Stop-Bridge {
    $stopped = 0
    $failed = 0
    if (-not (Test-IsWindows)) { return @{ Stopped = 0; Failed = 0 } }
    try {
        $procs = @(Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {
            $_.ProcessId -ne $PID -and $_.CommandLine -and (
                ($_.Name -match '^(py|pythonw?)[0-9.]*\.exe$' -and $_.CommandLine -match 'telemetry_bridge\.py') -or
                ($_.Name -match '^(powershell|pwsh)\.exe$' -and $_.CommandLine -match '[\\/]bridge\.ps1'))
        })
        foreach ($p in $procs) {
            try { Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop; $stopped++ } catch { $failed++ }
        }
    } catch { }
    return @{ Stopped = $stopped; Failed = $failed }
}

function Start-BridgeProcess([string]$BridgeDir, $Python, [int]$Port) {
    $logDir = Get-StateDir
    if (-not (Test-Path -LiteralPath $logDir -PathType Container)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
    $out = Join-Path $logDir 'bridge.log'
    $err = Join-Path $logDir 'bridge-errors.log'
    $env:PYTHONUTF8 = '1'
    $env:PYTHONIOENCODING = 'utf-8'
    $env:FC26_HIDDEN = '1'
    # No window at all: the bridge keeps running after this launcher closes. (-WindowStyle exists on Windows only.)
    $hidden = @{}
    if (Test-IsWindows) { $hidden['WindowStyle'] = 'Hidden' }
    if ($Python) {
        $script = Join-Path $BridgeDir 'telemetry_bridge.py'
        # -I: Python ignores PYTHON* settings and does not load modules from the bridge folder or the current folder;
        # -X utf8 keeps the bridge's output readable in the log.
        $argList = @($Python.Prefix) + @('-I', '-X', 'utf8', '-u', ('"' + $script + '"'))
        $proc = Start-Process -FilePath $Python.Path -ArgumentList $argList -WorkingDirectory $BridgeDir @hidden `
            -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    } else {
        $script = Join-Path $BridgeDir 'bridge.ps1'
        $argList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $script + '"'), '-Port', [string]$Port)
        $proc = Start-Process -FilePath (Get-PowerShellExe) -ArgumentList $argList -WorkingDirectory $BridgeDir @hidden `
            -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    }
    return @{ Process = $proc; Log = $out; ErrorLog = $err }
}

function Wait-Bridge([int]$Port, $Process, [int]$Seconds = 25) {
    $clock = [Diagnostics.Stopwatch]::StartNew()
    while ($clock.Elapsed.TotalSeconds -lt $Seconds) {
        $s = Get-BridgeStatus $Port 1500
        if ($s.Running) { return $s }
        if ($Process -and $Process.HasExited) { return $null }
        Start-Sleep -Milliseconds 500
    }
    return $null
}

function Get-LanAddresses {
    $list = @()
    try {
        foreach ($nic in [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
            if ($nic.OperationalStatus -ne 'Up') { continue }
            if ($nic.NetworkInterfaceType -eq 'Loopback' -or $nic.NetworkInterfaceType -eq 'Tunnel') { continue }
            if ($nic.Name -match 'WSL|vEthernet|VirtualBox|VMware|Docker|Tailscale|Hyper-V') { continue }
            foreach ($ua in $nic.GetIPProperties().UnicastAddresses) {
                if ($ua.Address.AddressFamily -ne 'InterNetwork') { continue }
                $ip = $ua.Address.ToString()
                if ($ip -match '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)') { $list += $ip }
            }
        }
    } catch { }
    return @($list | Select-Object -Unique)
}

# ---- the program ---------------------------------------------------------------------------------
function Get-Options {
    $o = @{ Stop = $false; Status = $false; Reset = $false; NoPause = $false; NewCode = $false }
    $text = [string]$env:FC26_ARGS
    foreach ($t in ($text -split '\s+')) {
        switch -Regex ($t.Trim('-', '/').ToLowerInvariant()) {
            '^stop$'    { $o.Stop = $true }
            '^status$'  { $o.Status = $true }
            '^reset$'   { $o.Reset = $true }
            '^nopause$' { $o.NoPause = $true }
            '^newcode$' { $o.NewCode = $true }
        }
    }
    return $o
}

function Show-Status([int]$Port) {
    $s = Get-BridgeStatus $Port
    if (-not $s.Running) { Say-Warn "The bridge is not running on this PC (port $Port)."; return }
    if ($s.Old) { Say-Warn 'An older bridge is running; it cannot report details. Run this launcher without options to update it.'; return }
    $st = $s.Status
    Say-Ok ("Bridge {0} on {1}: {2}" -f $st.bridge.version, $st.bridge.name, $st.bridge.address)
    foreach ($c in @($st.checks)) {
        $line = "{0}: {1}" -f $c.label, $c.detail
        switch ($c.state) {
            'ok'   { Say-Ok $line }
            'warn' { Say-Warn $line; if ($c.fix) { Say ("         " + $c.fix) 'DarkYellow' } }
            'fail' { Say-Fail $line; if ($c.fix) { Say ("         " + $c.fix) 'DarkYellow' } }
            default { Say ("  [ i  ] " + $line) }
        }
    }
    $code = Get-PairingCode
    if ($code) { Say ("  Pairing code for the app: {0}" -f (Format-PairCode $code)) 'Green' }
}

function Invoke-Launcher {
    $opt = Get-Options
    $port = $script:BridgePort
    $launcherDir = $null
    if ($env:FC26_LAUNCHER) { $launcherDir = Split-Path -Parent $env:FC26_LAUNCHER }
    if (-not $launcherDir) { $launcherDir = (Get-Location).Path }

    Say ''
    Say '================================================================' 'Cyan'
    Say ("  FC26 Career Tracker - PC launcher {0}" -f $script:LauncherVersion) 'Yellow'
    Say '================================================================' 'Cyan'

    if ($opt.Stop) {
        $r = Stop-Bridge
        if ($r.Stopped -gt 0) { Say-Ok "Stopped the bridge ($($r.Stopped) process(es))." }
        elseif ($r.Failed -gt 0) { Say-Fail 'Could not stop the bridge. If it was started as administrator, close its window.' }
        else { Say-Ok 'The bridge was not running.' }
        return 0
    }
    if ($opt.Status) { Show-Status $port; return 0 }
    if ($opt.NewCode) {
        try {
            $new = New-PairCode
            Save-PairingCode $new
            Say-Ok ("New pairing code: {0}" -f (Format-PairCode $new))
            Say '  Type it in the app (menu, PC Connection). The old code stops working right away, also on any other phone.'
            return 0
        } catch {
            Say-Fail ("Could not save a new pairing code: " + $_.Exception.Message)
            return 1
        }
    }

    $state = Read-LauncherState
    if ($opt.Reset) { $state = New-LauncherState; Save-LauncherState $state; Say-Ok 'Forgot the remembered folders and network choices.' }

    # ---- Step 1: the project files ----
    Say-Step '1 of 4' 'Finding the bridge and the Live Editor scripts'
    $project = Find-ProjectNear $launcherDir
    $bridgeDir = $project.BridgeDir
    $sourceScripts = $project.ScriptsDir
    $userRoots = Get-UserRoots
    $driveRoots = Get-DriveRoots
    if (-not $bridgeDir -and $state.bridgeDir) { $bridgeDir = Resolve-BridgeDir $state.bridgeDir }
    if (-not $bridgeDir) {
        Say '  Looking in Desktop, Downloads and Documents ...' 'DarkGray'
        $dirs = @(Find-BridgeDirs $userRoots $driveRoots)
        if ($dirs.Count -gt 0 -and (Confirm-FoundFolder 'the bridge files' $dirs[0] (Get-BridgeVersion $dirs[0]))) { $bridgeDir = $dirs[0] }
    }
    if (-not $bridgeDir) {
        $picked = Read-FolderChoice 'Select the folder that contains bridge.ps1 (the pc-bridge folder)'
        if ($picked) { $bridgeDir = Resolve-BridgeDir $picked }
    }
    if (-not $sourceScripts -and $bridgeDir) { $sourceScripts = Resolve-ScriptsDir (Split-Path -Parent $bridgeDir) }
    if (-not $sourceScripts) {
        $m = { param($d) Test-HasFiles $d $script:ScriptFiles }
        $cands = @(Search-Directories -Roots $userRoots -MaxDepth 4 -Match $m | Where-Object { $_ -notmatch '[\\/]lua[\\/]' })
        if ($cands.Count -gt 0 -and (Confirm-FoundFolder 'the capture scripts' $cands[0] (Get-LuaVersion (Join-Path $cands[0] 'FC26_Auto_Capture.lua')))) {
            $sourceScripts = $cands[0]
        }
    }
    if ($bridgeDir) {
        $bv = Get-BridgeVersion $bridgeDir
        Say-Ok ("Bridge files: {0}  (version {1})" -f $bridgeDir, $(if ($bv) { $bv } else { 'unknown' }))
        $state.bridgeDir = $bridgeDir
    } else {
        Say-Fail 'The bridge files (bridge.ps1 and telemetry_bridge.py) were not found. Unzip FC26-PC-Tools.zip and run this launcher from that folder.'
    }
    if ($sourceScripts) {
        $sv = Get-LuaVersion (Join-Path $sourceScripts 'FC26_Auto_Capture.lua')
        Say-Ok ("Capture scripts: {0}  (version {1})" -f $sourceScripts, $(if ($sv) { $sv } else { 'unknown' }))
    } else {
        Say-Fail 'The capture scripts (FC26_Auto_Capture.lua and FC26_AutoCapture_v1.1.1.lua) were not found.'
    }

    # ---- Step 2: put the scripts into Live Editor ----
    Say-Step '2 of 4' 'Putting the scripts into FC Live Editor'
    $installedAny = $false
    $installDirs = @()
    if ($sourceScripts) {
        $targets = Find-ScriptTargets -SourceDir $sourceScripts -Remembered $state.scriptDirs -UserRoots $userRoots -DriveRoots $driveRoots -ExtraRoots @($launcherDir, (Split-Path -Parent $launcherDir))
        if ($targets.Dirs.Count -eq 0) {
            Say-Warn 'No Live Editor folder was chosen, so the scripts were not installed. Run this launcher again, or copy the two .lua files into Live Editor''s lua\scripts folder yourself.'
        }
        foreach ($t in $targets.Dirs) {
            $res = Install-ScriptFiles $sourceScripts $t
            foreach ($e in $res.Errors) { Say-Fail $e }
            if ($res.Copied.Count -gt 0) { Say-Ok ("Installed {0} into {1}" -f ($res.Copied -join ', '), $t); $installedAny = $true }
            if ($res.Same.Count -gt 0 -and $res.Copied.Count -eq 0 -and $res.Errors.Count -eq 0) { Say-Ok ("Already up to date: {0}" -f $t) }
            foreach ($k in $res.KeptNewer) { Say-Warn ("Kept the newer {0} that is already in {1}" -f $k, $t) }
            if ($res.Moved.Count -gt 0) { Say-Ok ("Moved old scripts aside (renamed to .old): {0}" -f ($res.Moved -join ', ')) }
            if ($res.Errors.Count -eq 0) { $installDirs += $t }
        }
        if ($installDirs.Count -gt 0) { $state.scriptDirs = @($installDirs) }
    } else {
        Say-Warn 'Skipped: no capture scripts to install.'
    }
    Save-LauncherState $state

    # ---- Step 3: let the phone in ----
    Say-Step '3 of 4' 'Letting your phone reach this PC'
    $python = $null
    if ($bridgeDir) { $python = Find-Python }
    # Windows keeps the bridge closed on networks it calls Public (a cafe, a hotel, often a home network it was never
    # told about). The person decides; the launcher never changes the network type itself.
    $allowPublic = [bool]$state.allowPublic
    $publicNets = @(Get-PublicNetworks)
    if ($publicNets.Count -gt 0 -and -not $allowPublic) {
        $netName = [string]$publicNets[0]
        Say-Warn ("Windows treats the network '{0}' as Public. On Public networks the bridge stays closed, so your phone cannot connect." -f $netName)
        if (@($state.publicDeclined) -notcontains $netName) {
            $pick = Read-Choice 'What do you want to do?' @(
                'This is my home network: open Windows Settings so I can make it a Private network (recommended)',
                'Allow the bridge on Public networks too (less safe: people on such a network can reach it, though not without the pairing code)',
                'Leave it closed for now')
            if ($pick -eq 1) {
                $allowPublic = $true
                $state.allowPublic = $true
            } elseif ($pick -eq 2) {
                $state.publicDeclined = @(@($state.publicDeclined) + $netName)
            } else {
                Open-NetworkSettings
                Say '  In Settings open this network, choose "Private network", then run this launcher again.' 'Yellow'
            }
            Save-LauncherState $state
        } else {
            Say '         You chose to leave it closed. Make it a Private network in Windows Settings, or run "FC26_Launcher.bat reset" to be asked again.' 'DarkGray'
        }
    }
    $fwState = Get-FirewallRuleState $allowPublic
    $needFirewall = ($fwState -ne 'ok')
    $aclState = $(if ($null -eq $python) { Get-UrlAclState $port } else { 'ok' })
    $needAcl = ($aclState -ne 'ok')
    $sid = Get-CurrentUserSid
    if ($python) { Say-Ok 'Python found: the bridge runs with Python (no administrator rights needed for the bridge itself).' }
    elseif ($bridgeDir) { Say-Ok 'No Python found: the bridge runs with PowerShell.' }
    if ($fwState -eq 'outdated') { Say-Warn 'The Windows firewall rule from an earlier version allowed more than needed; it is replaced now.' }
    if ($aclState -eq 'outdated') { Say-Warn 'The port permission from an earlier version was for every user of this PC; it is now given to you only.' }
    $where = $(if ($allowPublic) { 'devices on the same network only' } else { 'home and work networks only, devices on the same network only' })
    if ($needFirewall -or $needAcl) {
        Say 'Windows needs one permission so that your phone can connect. It will ask once (the shield window).' 'Yellow'
        $ok = Invoke-Elevated (Get-ElevatedCommands $needFirewall $needAcl $port $allowPublic $sid)
        if ((-not (Test-FirewallRule $allowPublic)) -or (($null -eq $python) -and (-not (Test-UrlAcl $port)))) {
            Say-Warn 'The permission was not given. The phone may not be able to connect. Run this launcher again and choose Yes when Windows asks.'
        } else {
            Say-Ok ("Windows now lets your phone reach the bridge (port {0}, {1})." -f $port, $where)
        }
    } else {
        Say-Ok ("Windows already lets your phone reach the bridge (port {0}, {1})." -f $port, $where)
    }

    # ---- Step 4: the bridge ----
    Say-Step '4 of 4' 'Starting the bridge'
    $address = ''
    $bridgeOk = $false
    $pairCode = Get-PairingCode      # made before the bridge starts, so both use the same code
    if (-not $pairCode) { Say-Fail ("No pairing code could be saved in {0}, so no phone can connect." -f (Get-StateDir)) }
    if ($bridgeDir) {
        $source = Get-BridgeVersion $bridgeDir
        $now = Get-BridgeStatus $port
        $plan = Get-BridgePlan $now $source
        $go = $false
        switch ($plan.Action) {
            'foreign' { Say-Fail "Something that is not this bridge is using port $port. Close it, then run this launcher again." }
            'keep' {
                Say-Ok ("The bridge is already running (version {0})." -f $plan.Running)
                $bridgeOk = $true
                $address = [string]$now.Status.bridge.address
            }
            'restart' {
                $had = $(if ($plan.Running) { $plan.Running } else { 'old' })
                Say-Warn ("A bridge is already running but it is older ({0}); restarting it with version {1}." -f $had, $source)
                $stop = Stop-Bridge
                $stillUp = $false
                if ($stop.Stopped -eq 0) { $stillUp = [bool](Get-BridgeStatus $port).Running }
                if (($stop.Failed -gt 0) -or $stillUp) {
                    Say-Fail 'The old bridge could not be stopped (it was probably started as administrator). Close its window, then run this launcher again.'
                } else {
                    Start-Sleep -Seconds 1
                    $go = $true
                }
            }
            default { $go = $true }
        }
        if ($go) {
            $started = Start-BridgeProcess $bridgeDir $python $port
            $s = Wait-Bridge $port $started.Process
            if ($s) {
                $bridgeOk = $true
                if ($s.Status) { $address = [string]$s.Status.bridge.address }
                Say-Ok ("The bridge is running in the background (log: {0})." -f $started.Log)
            } else {
                Say-Fail 'The bridge did not start. What it said:'
                foreach ($f in @($started.ErrorLog, $started.Log)) {
                    if (Test-Path -LiteralPath $f -PathType Leaf) {
                        $tail = @(Get-Content -LiteralPath $f -Tail 12 -ErrorAction SilentlyContinue)
                        foreach ($l in $tail) { if ($l) { Say ("     " + $l) 'DarkYellow' } }
                    }
                }
                if ($started.Process -and -not $started.Process.HasExited) { try { $started.Process.Kill() } catch { } }
            }
        }
    } else {
        Say-Warn 'Skipped: no bridge files.'
    }

    # ---- summary ----
    if (-not $address) {
        $ips = @(Get-LanAddresses)
        if ($ips.Count -gt 0) { $address = "http://$($ips[0]):$port" }
    }
    Say ''
    Say '================================================================' 'Cyan'
    if ($bridgeOk) { Say '  Ready. What to do now:' 'Green' } else { Say '  Not ready yet - see the messages above. When it is fixed:' 'Yellow' }
    Say '================================================================' 'Cyan'
    if ($address) {
        Say ("  1. On your phone open the app, the menu, PC Connection, and tap Find my PC.")
        Say ("     (or type this address: {0})" -f $address) 'Green'
    } else {
        Say '  1. On your phone open the app, the menu, PC Connection, and tap Find my PC.'
    }
    if ($pairCode) {
        Say '  2. Type this pairing code in the same place:'
        Say ("        {0}" -f (Format-PairCode $pairCode)) 'Green'
        Say '     Keep it to yourself: only a phone with this code can read anything from this PC.'
    } else {
        Say '  2. Type the pairing code shown on the bridge page (http://localhost:5050) in the same place.'
    }
    Say '  3. In FC Live Editor load your career, open the Lua Scripts tab, pick'
    Say '     FC26_Auto_Capture.lua and press Execute. (If it is not in the list, restart Live Editor.)'
    Say '  4. Play. After each match press Advance to reach the Career Hub: the match then appears on your phone.'
    Say ''
    Say '  The bridge runs in the background. Run this launcher again any time to check or update.'
    Say '  Options:  FC26_Launcher.bat stop     stops the bridge'
    Say '            FC26_Launcher.bat status   shows what the bridge sees (phone, script, folders) and the code'
    Say '            FC26_Launcher.bat newcode  makes a new pairing code (the old one stops working)'
    if ($installedAny) { Say '  New scripts were installed: if Live Editor was open, restart it once.' 'Yellow' }

    if ($bridgeOk) { return 0 } else { return 1 }
}

if (-not $env:FC26_LAUNCHER_NOMAIN) {
    $code = 1
    try {
        $code = Invoke-Launcher
    } catch {
        Say ''
        Say-Fail ("Unexpected problem: " + $_.Exception.Message)
        $code = 1
    }
    $o = Get-Options
    if (-not $o.NoPause) {
        Say ''
        [void](Read-Host 'Press Enter to close this window')
    }
    exit $code
}
