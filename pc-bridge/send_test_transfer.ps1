$ErrorActionPreference = "Continue"
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  EA FC Mobile Sync - Test Transfer Transmitter" -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Transmitting test transfer to bridge..." -ForegroundColor Gray

$portsToTry = @(5050)
$transmitted = $false

foreach ($p in $portsToTry) {
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:$p/test-transfer" -Method Post -TimeoutSec 3 -ErrorAction Stop
        Write-Host ""
        Write-Host "========================================================" -ForegroundColor Green
        Write-Host " [SUCCESS] Test transfer transmitted to Bridge on port 5050!" -ForegroundColor Green
        Write-Host "========================================================" -ForegroundColor Green
        Write-Host " Transfer Details: Erling Haaland signed for €120.0M" -ForegroundColor White
        Write-Host ""
        Write-Host "Mobile app verification:" -ForegroundColor Yellow
        Write-Host " 1. Check the Live Telemetry Feed on your phone." -ForegroundColor White
        Write-Host " 2. Check the Transfers tab on your phone." -ForegroundColor White
        Write-Host ""
        $transmitted = $true
        break
    } catch {}
}

if (-not $transmitted) {
    Write-Host ""
    Write-Host "========================================================" -ForegroundColor Red
    Write-Host " [FAILED] Could not reach bridge on port 5050" -ForegroundColor Red
    Write-Host "========================================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Troubleshooting:" -ForegroundColor Yellow
    Write-Host " 1. Make sure 'Start_Bridge.bat' is running in another window!" -ForegroundColor White
    Write-Host ""
}
