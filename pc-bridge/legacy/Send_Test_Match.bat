@echo off
title Send Test Match to EA FC Bridge
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0send_test_match.ps1"
echo.
pause
