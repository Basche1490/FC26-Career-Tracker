@echo off
title EA FC Live Telemetry Bridge (Python Engine)
cd /d "%~dp0.."

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
        echo     Please use FC26_Launcher.bat in the main folder instead,
        echo     or install Python from https://www.python.org/
        echo.
        pause
        exit /b 1
    ) else (
        echo Found Python launcher (py)...
        py "%~dp0..\telemetry_bridge.py"
        pause
        exit /b
    )
)

python "%~dp0..\telemetry_bridge.py"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Bridge stopped with error.
    pause
)
