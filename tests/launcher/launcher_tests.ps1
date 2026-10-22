# Tests for FC26_Launcher.bat: loads the PowerShell program embedded in the .bat (nothing is copied by hand, so this
# cannot drift from the launcher) and runs its functions on pretend folder layouts. Prints one line per check:
#   RESULT|<name>|ok|<detail>     or     RESULT|<name>|FAIL|<detail>
# Used by test_launcher.py. Needs PowerShell (pwsh); the launcher itself runs on Windows PowerShell 5.1 as well.
param(
    [string]$Launcher,
    [string]$RealRepo,
    [string]$StatusPorts = ''
)
$ErrorActionPreference = 'Stop'
$env:FC26_LAUNCHER_NOMAIN = '1'
$text = [IO.File]::ReadAllText($Launcher)
$start = $text.IndexOf('#' + '#PS-START')
if ($start -lt 0) { throw 'marker not found' }
$psText = $text.Substring($start)

$tokens = $null
$errors = $null
[void][System.Management.Automation.Language.Parser]::ParseInput($psText, [ref]$tokens, [ref]$errors)

. ([scriptblock]::Create($psText))
$ErrorActionPreference = 'Stop'

$work = Join-Path ([IO.Path]::GetTempPath()) ('fc26-launcher-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
$env:FC26_STATE_DIR = Join-Path $work 'state'

function Check([string]$Name, $Condition, [string]$Detail = '') {
    $state = 'FAIL'
    if ($Condition) { $state = 'ok' }
    Write-Output ("RESULT|{0}|{1}|{2}" -f $Name, $state, ($Detail -replace '[\r\n|]+', ' '))
}

function Put([string]$Path, [string]$Content = 'x') {
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [IO.File]::WriteAllText($Path, $Content)
}

function New-Project([string]$Root, [string]$BridgeVer = '2.0.0', [string]$LuaVer = '2.8.0') {
    Put (Join-Path $Root 'pc-bridge/bridge.ps1') ('$BridgeVersion = "' + $BridgeVer + '"')
    Put (Join-Path $Root 'pc-bridge/telemetry_bridge.py') ("# bridge`nBRIDGE_VERSION = `"$BridgeVer`"`n")
    Put (Join-Path $Root 'live-editor-scripts/FC26_Auto_Capture.lua') ("-- script`nlocal VERSION = `"$LuaVer`"`n")
    Put (Join-Path $Root 'live-editor-scripts/FC26_AutoCapture_v1.1.1.lua') "local VERSION = `"1.1.1`"`n"
}

function New-LiveEditor([string]$Root) {
    New-Item -ItemType Directory -Path (Join-Path $Root 'lua/imports') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $Root 'lua/scripts') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $Root 'lua/autorun') -Force | Out-Null
    Put (Join-Path $Root 'FC 26 Live Editor.exe') 'exe'
}

function Names($List) { return (@($List) | ForEach-Object { Split-Path -Leaf $_ }) -join ',' }

# ---- 1. the program parses ---------------------------------------------------------------------------------------
Check 'parse.no_errors' ($errors.Count -eq 0) ($errors | ForEach-Object { $_.Message } | Out-String)
$nonAscii = ($text.ToCharArray() | Where-Object { [int]$_ -gt 127 }).Count
Check 'file.ascii_only' ($nonAscii -eq 0) "non-ascii chars: $nonAscii"
$crlfOnly = ($text -notmatch "(?<!`r)`n")
Check 'file.crlf_line_endings' $crlfOnly 'every line must end CRLF (a batch file with bare LF can misbehave)'
$head = $text.Substring(0, $start)
Check 'file.batch_header_exits_before_powershell' ($head -match '(?m)^exit /b %RC%\s*$') 'cmd must stop before the PowerShell text'
Check 'file.marker_once' (([regex]::Matches($text, '##PS-START')).Count -eq 1) 'the marker is a line of its own, and the command line only spells it split in two'

# ---- 2. versions -------------------------------------------------------------------------------------------------
Check 'version.real_lua' ((Get-LuaVersion (Join-Path $RealRepo 'live-editor-scripts/FC26_Auto_Capture.lua')) -match '^\d+\.\d+\.\d+$') (Get-LuaVersion (Join-Path $RealRepo 'live-editor-scripts/FC26_Auto_Capture.lua'))
Check 'version.real_exporter' ((Get-LuaVersion (Join-Path $RealRepo 'live-editor-scripts/FC26_AutoCapture_v1.1.1.lua')) -eq '1.1.3')
Check 'version.real_bridge' ((Get-BridgeVersion (Join-Path $RealRepo 'pc-bridge')) -match '^\d+\.\d+\.\d+$') (Get-BridgeVersion (Join-Path $RealRepo 'pc-bridge'))
$py = Select-String -LiteralPath (Join-Path $RealRepo 'pc-bridge/telemetry_bridge.py') -Pattern '^BRIDGE_VERSION = "(.*)"' | Select-Object -First 1
$ps = Select-String -LiteralPath (Join-Path $RealRepo 'pc-bridge/bridge.ps1') -Pattern '^\$BridgeVersion = "(.*)"' | Select-Object -First 1
Check 'version.both_engines_same' ($py.Matches[0].Groups[1].Value -eq $ps.Matches[0].Groups[1].Value) "py=$($py.Matches[0].Groups[1].Value) ps1=$($ps.Matches[0].Groups[1].Value)"
Check 'version.compare_less' ((Compare-Versions '2.0.0' '2.0.1') -eq -1)
Check 'version.compare_equal' ((Compare-Versions '2.8.0' '2.8.0') -eq 0)
Check 'version.compare_numeric_not_text' ((Compare-Versions '2.10.0' '2.9.0') -eq 1) '2.10 is newer than 2.9'
Check 'version.compare_missing_is_oldest' ((Compare-Versions '' '0.0.1') -eq -1)
Check 'version.compare_short' ((Compare-Versions '2.8' '2.8.0') -eq 0)
$noVer = Join-Path $work 'noversion'
Put (Join-Path $noVer 'a.lua') '-- nothing here'
Check 'version.lua_missing_is_empty' ((Get-LuaVersion (Join-Path $noVer 'a.lua')) -eq '')
Check 'version.lua_file_missing_is_empty' ((Get-LuaVersion (Join-Path $noVer 'nope.lua')) -eq '')
Check 'version.bridge_dir_missing_is_empty' ((Get-BridgeVersion (Join-Path $work 'nope')) -eq '')

# ---- 3. the project next to the launcher -------------------------------------------------------------------------
$a = Join-Path $work 'a-root/FC26-Career-Tracker'
New-Project $a
$r = Find-ProjectNear $a
Check 'near.at_repo_root' ((Split-Path -Leaf $r.BridgeDir) -eq 'pc-bridge' -and (Split-Path -Leaf $r.ScriptsDir) -eq 'live-editor-scripts') "$($r.BridgeDir) | $($r.ScriptsDir)"

$r = Find-ProjectNear (Join-Path $a 'pc-bridge')
Check 'near.launcher_moved_into_pc_bridge' ($r.BridgeDir -eq (Join-Path $a 'pc-bridge') -and $r.ScriptsDir -eq (Join-Path $a 'live-editor-scripts')) "$($r.BridgeDir) | $($r.ScriptsDir)"

$b = Join-Path $work 'b-zip/FC26-PC-Tools'
New-Project (Join-Path $b 'FC26-PC-Tools')
$r = Find-ProjectNear $b
Check 'near.zip_inside_zip_folder' ($r.BridgeDir -eq (Join-Path $b 'FC26-PC-Tools/pc-bridge')) "$($r.BridgeDir)"

$c = Join-Path $work 'c-desktop'
New-Project (Join-Path $c 'FC26 stuff/tracker')
Put (Join-Path $c 'FC26_Launcher.bat') 'x'
$r = Find-ProjectNear $c
Check 'near.too_deep_is_not_found_here' ($null -eq $r.BridgeDir) 'deeper folders are for the wider search'

$d = Join-Path $work 'd-flat'
Put (Join-Path $d 'bridge.ps1') '$BridgeVersion = "2.0.0"'
Put (Join-Path $d 'telemetry_bridge.py') 'BRIDGE_VERSION = "2.0.0"'
Put (Join-Path $d 'FC26_Auto_Capture.lua') 'local VERSION = "2.8.0"'
Put (Join-Path $d 'FC26_AutoCapture_v1.1.1.lua') 'local VERSION = "1.1.1"'
$r = Find-ProjectNear $d
Check 'near.everything_in_one_flat_folder' ($r.BridgeDir -eq $d -and $r.ScriptsDir -eq $d)

$e = Join-Path $work 'e-split'
Put (Join-Path $e 'bridge-bits/bridge.ps1') '$BridgeVersion = "2.0.0"'
Put (Join-Path $e 'bridge-bits/telemetry_bridge.py') 'BRIDGE_VERSION = "2.0.0"'
$r = Find-ProjectNear (Join-Path $e 'bridge-bits')
Check 'near.bridge_only_reports_no_scripts' ($r.BridgeDir -eq (Join-Path $e 'bridge-bits') -and $null -eq $r.ScriptsDir)

$r = Find-ProjectNear (Join-Path $work 'iso-one/empty/nowhere')
Check 'near.nothing_found' ($null -eq $r.BridgeDir -and $null -eq $r.ScriptsDir)

$partial = Join-Path $work 'iso-two/partial/pc-bridge'
Put (Join-Path $partial 'bridge.ps1') 'x'
$r = Find-ProjectNear (Join-Path $work 'iso-two/partial')
Check 'near.half_a_bridge_is_not_a_bridge' ($null -eq $r.BridgeDir) 'both bridge.ps1 and telemetry_bridge.py are needed'

# ---- 4. the wider search for the bridge --------------------------------------------------------------------------
$home1 = Join-Path $work 'home1'
$dl = Join-Path $home1 'Downloads'
$docs = Join-Path $home1 'Documents'
$desk = Join-Path $home1 'Desktop'
foreach ($f in @($dl, $docs, $desk)) { New-Item -ItemType Directory -Path $f -Force | Out-Null }
New-Project (Join-Path $dl 'FC26-Career-Tracker-main') '2.0.0'
New-Project (Join-Path $dl 'FC26-Career-Tracker-main (1)') '1.9.0'
New-Project (Join-Path $desk 'old copy') '1.0.0'
$dirs = @(Find-BridgeDirs @($desk, $docs, $dl) @())
Check 'bridge_search.finds_all_three' ($dirs.Count -eq 3) "found $($dirs.Count)"
Check 'bridge_search.highest_version_first' ($dirs[0] -like '*FC26-Career-Tracker-main/pc-bridge') ($dirs -join ' ; ')
Check 'bridge_search.lowest_last' ($dirs[-1] -like '*old copy/pc-bridge')

$same1 = Join-Path $work 'same/one'
$same2 = Join-Path $work 'same/two'
New-Project $same1 '2.0.0'
New-Project $same2 '2.0.0'
(Get-Item -LiteralPath (Join-Path $same1 'pc-bridge/bridge.ps1')).LastWriteTimeUtc = [datetime]'2026-01-01'
(Get-Item -LiteralPath (Join-Path $same2 'pc-bridge/bridge.ps1')).LastWriteTimeUtc = [datetime]'2026-09-01'
$dirs = @(Find-BridgeDirs @((Join-Path $work 'same')) @())
Check 'bridge_search.same_version_newest_file_first' ($dirs[0] -like '*two/pc-bridge') ($dirs -join ' ; ')

$drive = Join-Path $work 'fake-drive'
New-Project (Join-Path $drive 'Games/FC tools') '2.0.0'
$dirs = @(Find-BridgeDirs @((Join-Path $work 'no-such-home')) @($drive))
Check 'bridge_search.falls_back_to_drives' ($dirs.Count -eq 1 -and $dirs[0] -like '*Games/FC tools/pc-bridge') ($dirs -join ' ; ')

# ---- 5. the directory search itself ------------------------------------------------------------------------------
$s = Join-Path $work 'search'
New-Item -ItemType Directory -Path (Join-Path $s 'l1/l2/l3/l4') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $s 'Windows/target') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $s 'node_modules/target') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $s 'legacy/target') -Force | Out-Null
$byName = { param($d) (Split-Path -Leaf $d) -eq 'target' }
New-Item -ItemType Directory -Path (Join-Path $s 'l1/l2/target') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $s 'l1/l2/l3/l4/target') -Force | Out-Null
$hits = @(Search-Directories -Roots @($s) -MaxDepth 3 -Match $byName)
Check 'dirsearch.respects_depth' ($hits.Count -eq 1 -and $hits[0] -like '*l1/l2/target') ($hits -join ' ; ')
$hits = @(Search-Directories -Roots @($s) -MaxDepth 6 -Match $byName)
Check 'dirsearch.skips_windows_node_modules_legacy' ($hits.Count -eq 2) ($hits -join ' ; ')
$hits = @(Search-Directories -Roots @($s, $s) -MaxDepth 3 -Match $byName)
Check 'dirsearch.same_root_twice_is_not_duplicated' ($hits.Count -eq 1)
$many = Join-Path $work 'many'
1..60 | ForEach-Object { New-Item -ItemType Directory -Path (Join-Path $many "d$_/sub") -Force | Out-Null }
$cnt = 0
$counter = { param($d) $script:cnt++; $false }
[void](Search-Directories -Roots @($many) -MaxDepth 4 -Match $counter -MaxVisited 25)
Check 'dirsearch.visit_cap_stops_it' ($script:cnt -le 25) "looked at $($script:cnt)"
$t0 = Get-Date
$slow = { param($d) Start-Sleep -Milliseconds 400; $false }
[void](Search-Directories -Roots @($many) -MaxDepth 4 -Match $slow -TimeoutSeconds 1)
Check 'dirsearch.time_cap_stops_it' (((Get-Date) - $t0).TotalSeconds -lt 6) "took $(((Get-Date) - $t0).TotalSeconds)s"
$boom = { param($d) throw 'unreadable' }
$hits = @(Search-Directories -Roots @($many) -MaxDepth 1 -Match $boom)
Check 'dirsearch.a_failing_match_does_not_stop_it' ($hits.Count -eq 0)
$hits = @(Search-Directories -Roots @((Join-Path $work 'does-not-exist')) -MaxDepth 3 -Match $byName)
Check 'dirsearch.missing_root_is_fine' ($hits.Count -eq 0)

# ---- 6. choosing a folder in the dialog --------------------------------------------------------------------------
$le1 = Join-Path $work 'pick/FC 26 Live Editor'
New-LiveEditor $le1
$expect = Join-Path $le1 'lua/scripts'
Check 'picked.live_editor_root' ((Resolve-PickedScriptsDir $le1) -eq $expect) (Resolve-PickedScriptsDir $le1)
Check 'picked.lua_folder' ((Resolve-PickedScriptsDir (Join-Path $le1 'lua')) -eq $expect)
Check 'picked.scripts_folder' ((Resolve-PickedScriptsDir $expect) -eq $expect)
Check 'picked.trailing_slash' ((Resolve-PickedScriptsDir ($le1 + '/')) -eq $expect) (Resolve-PickedScriptsDir ($le1 + '/'))
Check 'picked.missing_is_null' ($null -eq (Resolve-PickedScriptsDir (Join-Path $work 'nope')))
Check 'picked.empty_is_null' ($null -eq (Resolve-PickedScriptsDir ''))
$random = Join-Path $work 'pick/random folder'
New-Item -ItemType Directory -Path $random -Force | Out-Null
Check 'picked.unrelated_folder_is_null' ($null -eq (Resolve-PickedScriptsDir $random))
$luaOnly = Join-Path $work 'pick/folder with lua files'
Put (Join-Path $luaOnly 'something.lua') '-- x'
Check 'picked.folder_of_lua_files_is_accepted' ((Resolve-PickedScriptsDir $luaOnly) -eq $luaOnly)
$leOnlyLua = Join-Path $work 'pick/LE no imports'
New-Item -ItemType Directory -Path (Join-Path $leOnlyLua 'lua') -Force | Out-Null
Check 'picked.root_with_bare_lua_folder' ((Resolve-PickedScriptsDir $leOnlyLua) -eq (Join-Path $leOnlyLua 'lua/scripts'))

# ---- 7. where the scripts go -------------------------------------------------------------------------------------
function Targets($Source, $Remembered, $User, $Drive, $Extra = @()) {
    return (Find-ScriptTargets -SourceDir $Source -Remembered $Remembered -UserRoots $User -DriveRoots $Drive -ExtraRoots $Extra)
}
$script:ChooseCalls = 0
$script:FolderCalls = 0
$script:ChooseHook = { param($q, $o) $script:ChooseCalls++; $script:ChooseOptions = @($o); return $script:ChooseAnswer }
$script:FolderHook = { param($q) $script:FolderCalls++; return $script:FolderAnswer }
$script:ChooseAnswer = 0
$script:FolderAnswer = $null
$source = Join-Path $RealRepo 'live-editor-scripts'

# one Live Editor under Documents
$h = Join-Path $work 'h-one'
New-LiveEditor (Join-Path $h 'Documents/FC 26 Live Editor')
$t = Targets $source @() @((Join-Path $h 'Desktop'), (Join-Path $h 'Documents'), (Join-Path $h 'Downloads')) @()
Check 'targets.single_live_editor' ($t.Dirs.Count -eq 1 -and $t.Dirs[0] -eq (Join-Path $h 'Documents/FC 26 Live Editor/lua/scripts')) ($t.Dirs -join ' ; ')
Check 'targets.single_asks_nobody' ($script:ChooseCalls -eq 0 -and $script:FolderCalls -eq 0)

# Live Editor in a different place than the tools (the case the user asked for)
$h2 = Join-Path $work 'h-two'
New-LiveEditor (Join-Path $h2 'Desktop/Mods/FC26-LE')
$t = Targets $source @() @((Join-Path $h2 'Desktop'), (Join-Path $h2 'Documents')) @()
Check 'targets.nested_under_desktop' ($t.Dirs.Count -eq 1 -and $t.Dirs[0] -like '*Mods/FC26-LE/lua/scripts') ($t.Dirs -join ' ; ')

# Live Editor on another drive
$h3 = Join-Path $work 'h-three'
New-Item -ItemType Directory -Path (Join-Path $h3 'Desktop') -Force | Out-Null
$driveD = Join-Path $work 'drive-d'
New-LiveEditor (Join-Path $driveD 'Games/Live Editor')
$t = Targets $source @() @((Join-Path $h3 'Desktop')) @($driveD)
Check 'targets.found_on_another_drive' ($t.Dirs.Count -eq 1 -and $t.Dirs[0] -like '*Games/Live Editor/lua/scripts') ($t.Dirs -join ' ; ')

# Live Editor right next to the launcher (the extra roots)
$h4 = Join-Path $work 'h-four'
$near = Join-Path $work 'h-four-tools'
New-LiveEditor (Join-Path $near 'LE')
$t = Targets $source @() @((Join-Path $h4 'Desktop')) @() @($near)
Check 'targets.live_editor_beside_the_launcher' ($t.Dirs.Count -eq 1 -and $t.Dirs[0] -like '*LE/lua/scripts') ($t.Dirs -join ' ; ')

# a copy of this repo in Downloads must never be mistaken for Live Editor
$h5 = Join-Path $work 'h-five'
New-Project (Join-Path $h5 'Downloads/FC26-Career-Tracker-main')
$script:FolderAnswer = $null
$before = $script:FolderCalls
$t = Targets $source @() @((Join-Path $h5 'Downloads')) @()
Check 'targets.repo_copy_is_not_live_editor' ($t.Dirs.Count -eq 0 -and $script:FolderCalls -eq ($before + 1)) "dirs=$($t.Dirs -join ';') folderCalls=$($script:FolderCalls)"

# two Live Editors, one for FC 26
$h6 = Join-Path $work 'h-six'
New-LiveEditor (Join-Path $h6 'Documents/FC 25 Live Editor')
New-LiveEditor (Join-Path $h6 'Documents/FC 26 Live Editor')
$c0 = $script:ChooseCalls
$t = Targets $source @() @((Join-Path $h6 'Documents')) @()
Check 'targets.prefers_fc26_folder' ($t.Dirs.Count -eq 1 -and $t.Dirs[0] -like '*FC 26 Live Editor/lua/scripts' -and $script:ChooseCalls -eq $c0) ($t.Dirs -join ' ; ')

# two Live Editors, neither says 26, one already has the script
$h7 = Join-Path $work 'h-seven'
New-LiveEditor (Join-Path $h7 'Documents/LE-a')
New-LiveEditor (Join-Path $h7 'Documents/LE-b')
Put (Join-Path $h7 'Documents/LE-b/lua/scripts/FC26_Auto_Capture.lua') 'local VERSION = "2.0.0"'
$c0 = $script:ChooseCalls
$t = Targets $source @() @((Join-Path $h7 'Documents')) @()
Check 'targets.prefers_the_one_with_the_script' ($t.Dirs.Count -eq 1 -and $t.Dirs[0] -like '*LE-b/lua/scripts' -and $script:ChooseCalls -eq $c0) ($t.Dirs -join ' ; ')

# two Live Editors, nothing to tell them apart: ask
$h8 = Join-Path $work 'h-eight'
New-LiveEditor (Join-Path $h8 'Documents/LE-b')
New-LiveEditor (Join-Path $h8 'Documents/LE-a')
$script:ChooseAnswer = 1   # the list is alphabetical, so the second entry is LE-b whatever order the disk lists them in
$c0 = $script:ChooseCalls
$t = Targets $source @() @((Join-Path $h8 'Documents')) @()
Check 'targets.options_are_alphabetical' ((Names $script:ChooseOptions) -eq 'LE-a,LE-b') (Names $script:ChooseOptions)
Check 'targets.asks_when_unclear' ($script:ChooseCalls -eq ($c0 + 1) -and $t.Dirs.Count -eq 1 -and $t.Dirs[0] -like '*LE-b/lua/scripts') ($t.Dirs -join ' ; ')
$script:ChooseAnswer = -1
$script:FolderAnswer = Join-Path $le1 'lua'
$t = Targets $source @() @((Join-Path $h8 'Documents')) @()
Check 'targets.bad_answer_falls_back_to_folder_dialog' ($t.Dirs.Count -eq 1 -and $t.Dirs[0] -eq (Join-Path $le1 'lua/scripts')) ($t.Dirs -join ' ; ')

# nothing found: the person picks
$h9 = Join-Path $work 'h-nine'
New-Item -ItemType Directory -Path (Join-Path $h9 'Documents') -Force | Out-Null
$script:FolderAnswer = $le1
$t = Targets $source @() @((Join-Path $h9 'Documents')) @()
Check 'targets.person_picks_the_live_editor_folder' ($t.Dirs.Count -eq 1 -and $t.Dirs[0] -eq (Join-Path $le1 'lua/scripts')) ($t.Dirs -join ' ; ')
$script:FolderAnswer = $random
$t = Targets $source @() @((Join-Path $h9 'Documents')) @()
Check 'targets.unrelated_pick_gives_nothing' ($t.Dirs.Count -eq 0)
$script:FolderAnswer = $null
$t = Targets $source @() @((Join-Path $h9 'Documents')) @()
Check 'targets.cancelled_dialog_gives_nothing' ($t.Dirs.Count -eq 0)

# remembered folder from the last run wins and asks nothing
$c0 = $script:ChooseCalls
$f0 = $script:FolderCalls
$t = Targets $source @((Join-Path $le1 'lua/scripts')) @((Join-Path $h6 'Documents')) @()
Check 'targets.remembered_folder_wins' ($t.Dirs.Count -eq 1 -and $t.Dirs[0] -eq (Join-Path $le1 'lua/scripts') -and $script:ChooseCalls -eq $c0 -and $script:FolderCalls -eq $f0)
$t = Targets $source @((Join-Path $work 'gone/lua/scripts')) @((Join-Path $h 'Documents')) @()
Check 'targets.remembered_but_deleted_is_searched_again' ($t.Dirs.Count -eq 1 -and $t.Dirs[0] -like '*FC 26 Live Editor/lua/scripts') ($t.Dirs -join ' ; ')
$t = Targets $source @($source) @((Join-Path $h 'Documents')) @()
Check 'targets.never_install_into_the_source_folder' ($t.Dirs.Count -eq 1 -and $t.Dirs[0] -ne $source) ($t.Dirs -join ' ; ')

# ---- 8. installing the scripts -----------------------------------------------------------------------------------
New-Project (Join-Path $work 'install/proj') '2.0.0' '2.8.0'
$src = Join-Path $work 'install/proj/live-editor-scripts'
$tgt = Join-Path $work 'install/LE/lua/scripts'
$res = Install-ScriptFiles $src $tgt
Check 'install.fresh_copies_both' ($res.Copied.Count -eq 2 -and $res.Errors.Count -eq 0 -and (Test-Path (Join-Path $tgt 'FC26_Auto_Capture.lua')) -and (Test-Path (Join-Path $tgt 'FC26_AutoCapture_v1.1.1.lua'))) "$($res.Copied -join ',') $($res.Errors -join ',')"
Check 'install.creates_missing_folder' (Test-Path $tgt)
$res = Install-ScriptFiles $src $tgt
Check 'install.second_run_changes_nothing' ($res.Copied.Count -eq 0 -and $res.Same.Count -eq 2 -and $res.Moved.Count -eq 0) "copied=$($res.Copied -join ',') same=$($res.Same -join ',')"
Check 'install.no_backup_when_identical' (-not (Test-Path (Join-Path $tgt 'FC26_Auto_Capture.lua.bak')))

Put (Join-Path $src 'FC26_Auto_Capture.lua') "-- script`nlocal VERSION = `"2.9.0`"`n"
$res = Install-ScriptFiles $src $tgt
Check 'install.update_replaces_old_version' ($res.Copied -contains 'FC26_Auto_Capture.lua' -and (Get-LuaVersion (Join-Path $tgt 'FC26_Auto_Capture.lua')) -eq '2.9.0')
Check 'install.update_keeps_a_backup' ((Get-LuaVersion (Join-Path $tgt 'FC26_Auto_Capture.lua.bak')) -eq '2.8.0')

Put (Join-Path $src 'FC26_Auto_Capture.lua') "-- script`nlocal VERSION = `"2.7.0`"`n"
$res = Install-ScriptFiles $src $tgt
Check 'install.keeps_a_newer_script' ($res.KeptNewer.Count -eq 1 -and (Get-LuaVersion (Join-Path $tgt 'FC26_Auto_Capture.lua')) -eq '2.9.0') "kept=$($res.KeptNewer -join ',')"

# old scripts are moved aside, never deleted
foreach ($n in @('telemetry_sync.lua', 'telemetry_sender_v2.lua', 'FC26_Snapshot.lua')) { Put (Join-Path $tgt $n) "-- $n" }
Put (Join-Path $tgt 'my_own_script.lua') '-- not ours'
Put (Join-Path $tgt 'telemetry_sync.lua.old') '-- earlier rename'
$res = Install-ScriptFiles $src $tgt
Check 'install.legacy_moved_aside' ($res.Moved.Count -eq 3 -and -not (Test-Path (Join-Path $tgt 'telemetry_sync.lua')) -and (Test-Path (Join-Path $tgt 'telemetry_sender_v2.lua.old'))) ($res.Moved -join ',')
Check 'install.old_rename_does_not_overwrite' ((Get-Content (Join-Path $tgt 'telemetry_sync.lua.old') -Raw).Trim() -eq '-- earlier rename' -and (Test-Path (Join-Path $tgt 'telemetry_sync.lua.old2')))
Check 'install.unknown_scripts_untouched' ((Test-Path (Join-Path $tgt 'my_own_script.lua')) -and (Get-Content (Join-Path $tgt 'my_own_script.lua') -Raw).Trim() -eq '-- not ours')

$tgt2 = Join-Path $work 'install/LE2/lua/scripts'
Put (Join-Path $src 'FC26_Auto_Capture.lua') "-- script`nlocal VERSION = `"2.8.0`"`n"
$res = Install-ScriptFiles $src $tgt2
$a1 = (Get-FileHash (Join-Path $src 'FC26_Auto_Capture.lua')).Hash
$a2 = (Get-FileHash (Join-Path $tgt2 'FC26_Auto_Capture.lua')).Hash
Check 'install.copy_is_identical' ($a1 -eq $a2)

# ---- 9. the bridge decision --------------------------------------------------------------------------------------
function Status-Of([string]$Version) { return @{ Running = $true; Old = $false; Foreign = $false; Status = [pscustomobject]@{ bridge = [pscustomobject]@{ version = $Version; address = 'http://192.168.1.5:5050' } } } }
Check 'plan.nothing_running_starts' ((Get-BridgePlan @{ Running = $false; Old = $false; Foreign = $false; Status = $null } '2.0.0').Action -eq 'start')
Check 'plan.same_version_keeps' ((Get-BridgePlan (Status-Of '2.0.0') '2.0.0').Action -eq 'keep')
Check 'plan.newer_running_keeps' ((Get-BridgePlan (Status-Of '2.1.0') '2.0.0').Action -eq 'keep')
Check 'plan.older_running_restarts' ((Get-BridgePlan (Status-Of '1.0.0') '2.0.0').Action -eq 'restart')
Check 'plan.pre_health_bridge_restarts' ((Get-BridgePlan @{ Running = $true; Old = $true; Foreign = $false; Status = $null } '2.0.0').Action -eq 'restart')
Check 'plan.something_else_on_the_port' ((Get-BridgePlan @{ Running = $false; Old = $false; Foreign = $true; Status = $null } '2.0.0').Action -eq 'foreign')
Check 'plan.unknown_source_version_keeps' ((Get-BridgePlan (Status-Of '1.0.0') '').Action -eq 'keep')

# ---- 10. firewall commands ---------------------------------------------------------------------------------------
$sid = 'S-1-5-21-1004336348-1177238915-682003330-1001'
$cmds = Get-ElevatedCommands $true $false 5050
Check 'elevated.firewall_rule' ($cmds -match 'add rule name="EA FC Telemetry Bridge"' -and $cmds -match 'localport=5050' -and $cmds -match 'remoteip=LocalSubnet') $cmds
Check 'elevated.firewall_home_and_work_networks_only' ($cmds -match 'profile=private,domain' -and $cmds -notmatch 'profile=any') $cmds
Check 'elevated.firewall_public_only_when_chosen' ((Get-ElevatedCommands $true $false 5050 $true) -match 'profile=any')
Check 'elevated.programs_by_full_path_call' (@($cmds -split "`r`n" | Where-Object { $_ -and $_ -notmatch "^& '" }).Count -eq 0) $cmds
Check 'elevated.no_urlacl_when_not_needed' ($cmds -notmatch 'urlacl')
$cmds2 = Get-ElevatedCommands $true $true 5050 $false $sid
Check 'elevated.urlacl_when_needed' ($cmds2 -match 'add urlacl')
Check 'elevated.urlacl_for_this_user_only' ($cmds2 -match [regex]::Escape("sddl=D:(A;;GX;;;$sid)") -and $cmds2 -notmatch ';;;WD\)') $cmds2
Check 'elevated.no_urlacl_without_a_user' ((Get-ElevatedCommands $false $true 5050 $false '') -eq '')
Check 'elevated.odd_user_text_is_not_put_in_a_command' ((Get-ElevatedCommands $false $true 5050 $false 'S-1-5"; Remove-Item C:\') -eq '')
Check 'elevated.nothing_needed_is_empty' ((Get-ElevatedCommands $false $false 5050) -eq '')
$perr = $null; $ptok = $null
[void][System.Management.Automation.Language.Parser]::ParseInput($cmds2, [ref]$ptok, [ref]$perr)
Check 'elevated.commands_are_valid_powershell' ($perr.Count -eq 0)
$enc = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($cmds2))
Check 'elevated.encoded_roundtrip' ([Text.Encoding]::Unicode.GetString([Convert]::FromBase64String($enc)) -eq $cmds2)
Check 'elevated.does_not_change_network_profile' ($cmds2 -notmatch 'Set-NetConnectionProfile' -and $cmds2 -notmatch 'NetworkCategory')
Check 'system.programs_named_by_path_on_windows_only' ((Get-NetshExe) -eq 'netsh' -and (Get-PowerShellExe) -eq 'powershell.exe')
Check 'firewall.not_windows_is_fine' ((Get-FirewallRuleState) -eq 'ok' -and (Get-UrlAclState 5050) -eq 'ok')

# ---- 10b. the pairing code ---------------------------------------------------------------------------------------
Check 'pair.format' ((Format-PairCode 'abcdefghjklmnpqr') -eq 'ABCD-EFGH-JKLM-NPQR')
Check 'pair.valid' ((Test-PairCode 'abcd efgh-jklm npqr') -and -not (Test-PairCode 'ABCD-EFGH-JKLM-NPQ0') -and -not (Test-PairCode 'ABCD-EFGH-JKLM-NPQI') -and -not (Test-PairCode 'ABCD'))
$codes = @(1..100 | ForEach-Object { New-PairCode })
Check 'pair.new_codes_are_valid_and_different' ((@($codes | Where-Object { -not (Test-PairCode $_) }).Count -eq 0) -and (@($codes | Select-Object -Unique).Count -eq 100))
$first = Get-PairingCode
Check 'pair.made_on_first_use' ((Test-PairCode $first) -and (Test-Path -LiteralPath (Get-PairingFile) -PathType Leaf))
Check 'pair.kept_afterwards' ((Get-PairingCode) -eq $first)
Check 'pair.file_has_the_shown_form' (([IO.File]::ReadAllText((Get-PairingFile))).Trim() -eq (Format-PairCode $first))
[IO.File]::WriteAllText((Get-PairingFile), 'broken')
$second = Get-PairingCode
Check 'pair.damaged_file_is_replaced' ((Test-PairCode $second) -and $second -ne $first)

# ---- 10c. asking before using what a search found, and Public networks -------------------------------------------
$script:ChooseAnswer = 0
$script:ChooseCalls = 0
Check 'confirm.yes' (Confirm-FoundFolder 'the bridge files' '/x/pc-bridge' '3.0.0')
Check 'confirm.asked_once_with_the_folder' ($script:ChooseCalls -eq 1 -and ($script:ChooseOptions -join '|') -match 'Yes')
$script:ChooseAnswer = 1
Check 'confirm.no' (-not (Confirm-FoundFolder 'the bridge files' '/x/pc-bridge' ''))
$script:ChooseAnswer = -1
Check 'confirm.unclear_answer_is_no' (-not (Confirm-FoundFolder 'the capture scripts' '/x' ''))
$script:ChooseAnswer = 0
Check 'public.none_off_windows' (@(Get-PublicNetworks).Count -eq 0)
$script:PublicNetworksHook = { @('Cafe WiFi') }
Check 'public.hook' ((@(Get-PublicNetworks) -join ',') -eq 'Cafe WiFi')
$script:PublicNetworksHook = $null

# ---- 11. python detection ----------------------------------------------------------------------------------------
$realPath = $env:PATH
$sep = [IO.Path]::PathSeparator
$binEmpty = Join-Path $work 'bin-stub'
New-Item -ItemType Directory -Path $binEmpty -Force | Out-Null
# the Microsoft Store stub is a python.exe that prints nothing useful and exits non-zero
Put (Join-Path $binEmpty 'python') "#!/bin/sh`necho 'Python was not found; run without arguments to install from the Microsoft Store'`nexit 9`n"
[IO.File]::SetUnixFileMode((Join-Path $binEmpty 'python'), 'UserRead,UserWrite,UserExecute')
$env:PATH = $binEmpty
$found = Find-Python
Check 'python.store_stub_is_rejected' ($null -eq $found)
$binReal = Join-Path $work 'bin-real'
New-Item -ItemType Directory -Path $binReal -Force | Out-Null
Put (Join-Path $binReal 'python') "#!/bin/sh`necho 311`n"
[IO.File]::SetUnixFileMode((Join-Path $binReal 'python'), 'UserRead,UserWrite,UserExecute')
$env:PATH = $binReal
$found = Find-Python
Check 'python.working_python_is_used' ($null -ne $found -and $found.Path -like '*bin-real/python') ($found | Out-String)
Put (Join-Path $binReal 'python') "#!/bin/sh`necho 307`n"
$env:PATH = $binReal
Check 'python.too_old_is_rejected' ($null -eq (Find-Python))
$env:PATH = Join-Path $work 'bin-none'
Check 'python.none_installed' ($null -eq (Find-Python))
$env:PATH = $realPath

# ---- 12. state file ----------------------------------------------------------------------------------------------
$st = Read-LauncherState
Check 'state.default_is_empty' ($st.bridgeDir -eq '' -and $st.scriptDirs.Count -eq 0 -and -not $st.allowPublic -and $st.publicDeclined.Count -eq 0)
$st.bridgeDir = '/some/where'
$st.scriptDirs = @('/a', '/b c')
Save-LauncherState $st
$back = Read-LauncherState
Check 'state.roundtrip' ($back.bridgeDir -eq '/some/where' -and $back.scriptDirs.Count -eq 2 -and $back.scriptDirs[1] -eq '/b c') ($back.scriptDirs -join ',')
$st.scriptDirs = @('/only')
Save-LauncherState $st
$back = Read-LauncherState
Check 'state.single_item_stays_a_list' ($back.scriptDirs.Count -eq 1 -and $back.scriptDirs[0] -eq '/only')
$st.allowPublic = $true
$st.publicDeclined = @('Cafe WiFi')
Save-LauncherState $st
$back = Read-LauncherState
Check 'state.network_choices_roundtrip' ($back.allowPublic -and $back.publicDeclined.Count -eq 1 -and $back.publicDeclined[0] -eq 'Cafe WiFi')
[IO.File]::WriteAllText((Join-Path (Get-StateDir) 'launcher.json'), '{ this is not json')
$back = Read-LauncherState
Check 'state.corrupt_file_is_ignored' ($back.bridgeDir -eq '' -and $back.scriptDirs.Count -eq 0)

# ---- 13. options -------------------------------------------------------------------------------------------------
$env:FC26_ARGS = ''
$o = Get-Options
Check 'options.none' (-not $o.Stop -and -not $o.Status -and -not $o.Reset -and -not $o.NoPause)
$env:FC26_ARGS = '/STOP --nopause'
$o = Get-Options
Check 'options.stop_and_nopause_any_style' ($o.Stop -and $o.NoPause -and -not $o.Reset)
$env:FC26_ARGS = 'status reset'
$o = Get-Options
Check 'options.status_and_reset' ($o.Status -and $o.Reset)
$env:FC26_ARGS = 'banana'
$o = Get-Options
Check 'options.unknown_is_ignored' (-not $o.Stop -and -not $o.Status -and -not $o.Reset -and -not $o.NoPause -and -not $o.NewCode)
$env:FC26_ARGS = 'newcode'
Check 'options.newcode' ((Get-Options).NewCode)
$env:FC26_ARGS = ''

# ---- 14. asking the bridge on a port (ports come from the python test) ------------------------------------------
if ($StatusPorts) {
    $p = $StatusPorts | ConvertFrom-Json
    $r = Get-BridgeStatus ([int]$p.bridge) 3000
    Check 'ask.bridge_with_health_report' ($r.Running -and -not $r.Old -and -not $r.Foreign -and $r.Status.bridge.version -eq '2.0.0') ($r | ConvertTo-Json -Depth 4 -Compress)
    $r = Get-BridgeStatus ([int]$p.old) 3000
    Check 'ask.bridge_from_before_the_health_report' ($r.Running -and $r.Old) ($r | ConvertTo-Json -Depth 4 -Compress)
    $r = Get-BridgeStatus ([int]$p.other) 3000
    Check 'ask.something_else_on_the_port' ($r.Foreign -and -not $r.Running) ($r | ConvertTo-Json -Depth 4 -Compress)
    $r = Get-BridgeStatus ([int]$p.html404) 3000
    Check 'ask.error_page_counts_as_something_else' ($r.Foreign -and -not $r.Running) ($r | ConvertTo-Json -Depth 4 -Compress)
    $r = Get-BridgeStatus ([int]$p.closed) 1500
    Check 'ask.nothing_listening' (-not $r.Running -and -not $r.Foreign) ($r | ConvertTo-Json -Depth 4 -Compress)
    $r = Get-BridgeStatus ([int]$p.hang) 800
    Check 'ask.hung_server_times_out' (-not $r.Running) ($r | ConvertTo-Json -Depth 4 -Compress)
}

Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
Write-Output 'DONE'
