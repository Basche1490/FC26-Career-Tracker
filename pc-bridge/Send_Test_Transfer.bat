@echo off
title Send Test Transfer to EA FC Bridge
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0send_test_transfer.ps1"
echo.
pause
