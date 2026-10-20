$ErrorActionPreference = "Continue"
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  EA FC Mobile Sync - Test Match Transmitter" -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Transmitting test match to bridge..." -ForegroundColor Gray

$portsToTry = @(5050)
$transmitted = $false

foreach ($p in $portsToTry) {
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:$p/test-match" -Method Post -TimeoutSec 3 -ErrorAction Stop
        Write-Host ""
        Write-Host "========================================================" -ForegroundColor Green
        Write-Host " [SUCCESS] Test match transmitted to Bridge on port 5050!" -ForegroundColor Green
        Write-Host "========================================================" -ForegroundColor Green
        Write-Host " Match Details: Richmond 3 - 1 Arsenal (Premier League)" -ForegroundColor White
        Write-Host " - Bukayo Saka: 2 Goals, 1 Assist (Rating: 8.9)" -ForegroundColor DarkGray
        Write-Host " - Declan Rice: 1 Goal (Rating: 7.8)" -ForegroundColor DarkGray
        Write-Host " - Martin Odegaard: 2 Assists (Rating: 8.4)" -ForegroundColor DarkGray
        Write-Host ""
        Write-Host "Mobile app verification:" -ForegroundColor Yellow
        Write-Host " 1. Check the Live Telemetry Feed on your phone." -ForegroundColor White
        Write-Host " 2. Check the Recent Matches tab on your phone." -ForegroundColor White
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
    Write-Host " 1. Run FC26_Launcher.bat first (it starts the bridge)!" -ForegroundColor White
    Write-Host " 2. Open http://localhost:5050 in your browser to verify it is online." -ForegroundColor White
    Write-Host ""
}
