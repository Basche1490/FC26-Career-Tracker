@echo off
title EA FC Live Telemetry Bridge (older starter)

:: The newer, simpler way: double-click FC26_Launcher.bat in the main folder. This file is kept for anyone who prefers it.

:: Check for Administrator privileges
net session >nul 2>&1
if %errorLevel% == 0 (
    goto :runBridge
) else (
    echo Requesting Administrator privileges to configure firewall and network ports...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

:runBridge
echo ===================================================
echo  Starting EA FC Live Telemetry Bridge (Port 5050)
echo  Keep this window open while playing!
echo ===================================================
cd /d "%~dp0.."

echo [1/3] Ensuring Windows Network Profile allows LAN connections...
powershell -NoProfile -Command "$profiles = Get-NetConnectionProfile -ErrorAction SilentlyContinue; foreach ($p in $profiles) { if ($p.NetworkCategory -eq 'Public') { Write-Host '  -> Switching ' $p.Name ' from Public to Private network...' -ForegroundColor Yellow; Set-NetConnectionProfile -InterfaceIndex $p.InterfaceIndex -NetworkCategory Private -ErrorAction SilentlyContinue } }"

echo [2/3] Configuring Windows Defender Firewall for Port 5050 (local network only)...
netsh advfirewall firewall delete rule name="EA FC Telemetry Bridge" >nul 2>&1
netsh advfirewall firewall add rule name="EA FC Telemetry Bridge" dir=in action=allow protocol=TCP localport=5050 profile=any remoteip=LocalSubnet >nul 2>&1

echo [3/3] Authorizing HTTP port 5050 for mobile network listening...
netsh http delete urlacl url=http://*:5050/ >nul 2>&1
netsh http delete urlacl url=http://+:5050/ >nul 2>&1
netsh http add urlacl url=http://+:5050/ sddl="D:(A;;GX;;;WD)" >nul 2>&1
netsh http add urlacl url=http://+:5050/ user=Everyone >nul 2>&1

echo.
echo Launching Bridge Server on Port 5050...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\bridge.ps1" -Port 5050
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo PowerShell bridge stopped with error.
    pause
)
