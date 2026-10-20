@echo off
title Send Test Transfer to EA FC Bridge
cd /d "%~dp0"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0send_test_transfer.ps1"
echo.
pause
