# Runs the PowerShell bridge's health functions (status, self check, re-send request) from pc-bridge/bridge.ps1 on a
# pretend export folder and prints the result as a line starting with RESULT:. Used by test_powershell_status.py.
param([string]$Bridge, [string]$Scenario)
$ErrorActionPreference = "Stop"
$errors = $null
$tokens = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($Bridge, [ref]$tokens, [ref]$errors)

# the same helper functions the bridge uses (nothing is copied by hand, so this cannot drift from bridge.ps1)
$names = @(
    "ConvertTo-Dbl", "ConvertTo-Int", "ConvertFrom-JsonObject", "ConvertTo-Long", "Get-EpochNow", "Get-VersionParts",
    "Test-VersionAtLeast", "Format-Span", "Format-Ago", "Set-PhoneSeen", "Read-ScriptStatus", "Get-OutboxState",
    "Get-EventTimes", "New-Check", "Get-SelfCheck", "Get-BridgeStatus", "Request-Resend"
)
$defs = $ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $names -contains $n.Name }, $true)
if ($defs.Count -ne $names.Count) { throw "expected $($names.Count) functions, found $($defs.Count)" }
foreach ($d in $defs) { Invoke-Expression $d.Extent.Text }

# and the constants the bridge declares
$constants = @("BridgeVersion", "BridgeProtocol", "MinScriptVersion", "ScriptAliveSeconds", "PhoneAliveSeconds", "ResendMinSeconds")
$assigns = $ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.AssignmentStatementAst] -and
        $n.Left -is [System.Management.Automation.Language.VariableExpressionAst] -and $constants -contains $n.Left.VariablePath.UserPath }, $true)
if ($assigns.Count -ne $constants.Count) { throw "expected $($constants.Count) constants, found $($assigns.Count)" }
foreach ($a in $assigns) { Invoke-Expression $a.Extent.Text }

$sc = Get-Content -LiteralPath $Scenario -Raw -Encoding UTF8 | ConvertFrom-Json
$PcName = [string]$sc.pcName
$Port = [int]$sc.port
$primaryIp = [string]$sc.localIp
$StartedAt = [long]0
$global:PhoneSeen = @{ Time = [long]$sc.phone.time; Ip = [string]$sc.phone.ip }
$global:ResendLast = [long]0
$global:WriteTestCache = @{ Time = [long]0; Root = ""; Ok = $true }
$global:EventList = [System.Collections.Generic.List[string]]::new()
foreach ($e in @($sc.events)) { $global:EventList.Add(($e | ConvertTo-Json -Compress)) }
$roots = @()
foreach ($r in @($sc.roots)) { if ($r) { $roots += [string]$r } }

if ($sc.action -eq "resend") {
    $out = @()
    foreach ($n in @($sc.nows)) {
        $res = Request-Resend -Now ([long]$n) -Roots $roots
        $out += [ordered]@{ code = $res.Code; reply = $res.Reply }
    }
    Write-Output ("RESULT:" + (ConvertTo-Json -InputObject $out -Depth 8 -Compress))
} else {
    $st = Get-BridgeStatus -Now ([long]$sc.now) -Roots $roots
    Write-Output ("RESULT:" + (ConvertTo-Json -InputObject $st -Depth 8 -Compress))
}
