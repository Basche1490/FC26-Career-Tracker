# Runs the PowerShell bridge's Atlas importer (Import-AtlasFolder from pc-bridge/bridge.ps1) on one export folder and
# prints the match payload it would have stored, as a line starting with PAYLOAD:. Used by test_powershell_importer.py.
param([string]$Bridge, [string]$Export, [string]$Snapshots)
$ErrorActionPreference = "Stop"
$errors = $null
$tokens = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($Bridge, [ref]$tokens, [ref]$errors)
$names = @(
    "ConvertTo-Dbl", "ConvertTo-Int", "ConvertTo-Pct", "Get-FirstValue", "Test-ContainsAny", "Read-CsvRows",
    "Test-LeagueName", "New-HistoryRow", "Search-SubstitutionPairs", "Get-SubstitutionPairs", "Test-LineupMatchesExport",
    "Add-LineupToPerformances", "Get-PositionLabel", "Set-KeeperSaves", "Get-SnapshotLineup", "Get-SnapshotLeaguePosition", "Get-TeamRow",
    "Import-AtlasFolder"
)
$defs = $ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $names -contains $n.Name }, $true)
if ($defs.Count -ne $names.Count) { throw "expected $($names.Count) functions, found $($defs.Count)" }
foreach ($d in $defs) { Invoke-Expression $d.Extent.Text }

$global:Captured = $null
function Add-MatchEvent {
    param([string]$Title, [string]$Message, [string]$RawPayload, [string]$Opponent, [int]$GoalsFor, [int]$GoalsAgainst, [string]$FixtureKey)
    $global:Captured = $RawPayload
    return $true
}
Import-AtlasFolder -Folder $Export -FolderId (Split-Path $Export -Leaf) -SnapshotsReady $Snapshots
Write-Output ("PAYLOAD:" + $global:Captured)
