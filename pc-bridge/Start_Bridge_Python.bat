@echo off
title EA FC Live Telemetry Bridge (Python Engine)
cd /d "%~dp0"

echo ===================================================
echo  Starting EA FC Telemetry Bridge (Python Engine)
echo  Runs pure sockets - Zero HTTP.SYS / Zero 503 errors!
echo ===================================================
echo.

where python >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    where py >nul 2>&1
    if %ERRORLEVEL% NEQ 0 (
        echo [!] Python was not found in your system PATH.
        echo     Please use Start_Bridge.bat (PowerShell engine) instead,
        echo     or install Python from https://www.python.org/
        echo.
        pause
        exit /b 1
    ) else (
        echo Found Python launcher (py)...
        py "%~dp0telemetry_bridge.py"
        pause
        exit /b
    )
)

python "%~dp0telemetry_bridge.py"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Bridge stopped with error.
    pause
)
