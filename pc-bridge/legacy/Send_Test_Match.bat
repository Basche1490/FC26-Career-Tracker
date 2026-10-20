@echo off
title Send Test Match to EA FC Bridge
cd /d "%~dp0"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0send_test_match.ps1"
echo.
pause
