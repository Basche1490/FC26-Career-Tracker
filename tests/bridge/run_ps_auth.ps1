# Runs the pairing-code and signing functions of the PowerShell bridge (pc-bridge/bridge.ps1) on the shared test vectors
# (tests/vectors/auth_vectors.json) and prints the result as a line starting with RESULT:. Used by test_powershell_auth.py.
param([string]$Bridge, [string]$Vectors)
$ErrorActionPreference = "Stop"
$errors = $null
$tokens = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($Bridge, [ref]$tokens, [ref]$errors)

# the same functions and constants the bridge uses (nothing is copied by hand, so this cannot drift from bridge.ps1)
$names = @(
    "ConvertTo-PairCode", "Test-PairCode", "Format-PairCode", "New-PairCode", "ConvertTo-HexText", "Get-Sha256Hex",
    "Get-KeyFromCode", "Get-HmacHex", "Get-RequestSignature", "Get-ResponseSignature", "Test-SameText"
)
$defs = $ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $names -contains $n.Name }, $true)
if ($defs.Count -ne $names.Count) { throw "expected $($names.Count) functions, found $($defs.Count)" }
foreach ($d in $defs) { Invoke-Expression $d.Extent.Text }
$constants = @("PairAlphabet", "PairLength", "KeyLabel")
$assigns = $ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.AssignmentStatementAst] -and
        $n.Left -is [System.Management.Automation.Language.VariableExpressionAst] -and $constants -contains $n.Left.VariablePath.UserPath }, $true)
if ($assigns.Count -ne $constants.Count) { throw "expected $($constants.Count) constants, found $($assigns.Count)" }
foreach ($a in $assigns) { Invoke-Expression $a.Extent.Text }

$v = Get-Content -LiteralPath $Vectors -Raw -Encoding UTF8 | ConvertFrom-Json
$key = Get-KeyFromCode $v.code
$requests = @()
foreach ($r in @($v.requests)) {
    $requests += (Get-RequestSignature $key $r.method $r.target ([string]$r.time) $r.nonce ([System.Text.Encoding]::UTF8.GetBytes([string]$r.body)))
}
$responses = @()
foreach ($r in @($v.responses)) {
    $responses += (Get-ResponseSignature $key $r.nonce ([int]$r.status) ([System.Text.Encoding]::UTF8.GetBytes([string]$r.body)))
}
$generated = @()
for ($i = 0; $i -lt 60; $i++) { $generated += (New-PairCode) }
$result = [ordered]@{
    normalized = (ConvertTo-PairCode $v.code)
    normalizedLoose = (ConvertTo-PairCode (([string]$v.code).ToLower().Replace("-", " ")))
    keyHex = (ConvertTo-HexText $key)
    requests = $requests
    responses = $responses
    formatted = (Format-PairCode "abcdefghjklmnpqr")
    valid = @((Test-PairCode "abcd-efgh-jklm-npqr"), (Test-PairCode "ABCD EFGH JKLM NPQR"))
    invalid = @((Test-PairCode ""), (Test-PairCode "ABCD"), (Test-PairCode "ABCD-EFGH-JKLM-NPQ0"), (Test-PairCode "ABCD-EFGH-JKLM-NPQI"), (Test-PairCode "ABCD-EFGH-JKLM-NPQRS"))
    generated = $generated
    sameText = @((Test-SameText "abc" "abc"), (Test-SameText "abc" "abd"), (Test-SameText "abc" "abcd"))
}
Write-Output ("RESULT:" + (ConvertTo-Json -InputObject $result -Depth 6 -Compress))
