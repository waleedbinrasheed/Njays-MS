@echo off
rem Allows the other office PCs to reach the application (Windows Firewall, private/domain
rem networks only - never public Wi-Fi). Needs "Run as administrator".
setlocal
cd /d "%~dp0"

set "PORT=80"
for /f "tokens=1,* delims==" %%a in ('findstr /b /c:"HTTP_PORT=" ".env" 2^>nul') do if not "%%b"=="" set "PORT=%%b"

net session >nul 2>&1
if errorlevel 1 (
  echo.
  echo NOTE: Windows Firewall was not changed ^(not running as administrator^).
  echo       If other PCs cannot open the application, right-click open-firewall.bat and choose
  echo       "Run as administrator".
  if /i not "%~1"=="nopause" pause
  exit /b 0
)

netsh advfirewall firewall delete rule name="Leading Publishers Inventory" >nul 2>&1
netsh advfirewall firewall add rule name="Leading Publishers Inventory" dir=in action=allow protocol=TCP localport=%PORT% profile=private,domain >nul
if errorlevel 1 (
  echo Could not add the firewall rule.
) else (
  echo Windows Firewall: port %PORT% opened for the office network.
)
if /i not "%~1"=="nopause" pause
exit /b 0
