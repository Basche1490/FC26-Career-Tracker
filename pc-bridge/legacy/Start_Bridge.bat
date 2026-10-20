@echo off
rem Older starter, kept so that an old shortcut still works. It now runs FC26_Launcher.bat, which sets everything up
rem the safe way: firewall rule for home networks only, port permission for your Windows user only, the bridge without
rem administrator rights and without a window, and the pairing code your phone needs.
title EA FC Live Telemetry Bridge (older starter)
set "FC26_LAUNCHER_FILE=%~dp0..\..\FC26_Launcher.bat"
if exist "%FC26_LAUNCHER_FILE%" (
  call "%FC26_LAUNCHER_FILE%" %*
  exit /b %ERRORLEVEL%
)
echo FC26_Launcher.bat was not found next to the pc-bridge folder.
echo Unzip FC26-PC-Tools.zip again and double-click FC26_Launcher.bat in its main folder.
pause
exit /b 1
