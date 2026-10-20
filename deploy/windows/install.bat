@echo off
rem Leading Publishers Inventory - first installation on this PC.
rem Run it from the release folder (e.g. C:\LeadingIMS). Right-click > "Run as administrator"
rem so it can also open port 80 in Windows Firewall for the other office PCs.
setlocal EnableDelayedExpansion
title Leading Publishers Inventory - install
cd /d "%~dp0"
echo.
echo === Leading Publishers Inventory: installation ===
echo.

docker info >nul 2>&1
if errorlevel 1 (
  echo Docker Desktop is not running. Start Docker Desktop, wait until it says "Engine running",
  echo then run install.bat again.
  pause
  exit /b 1
)

if not exist ".env" (
  copy /y ".env.example" ".env" >nul
  echo A settings file ".env" was created and will open in Notepad now.
  echo   1. Set DB_PASSWORD and ADMIN_INITIAL_PASSWORD - use letters and digits only.
  echo   2. Fill in BUSINESS_ADDRESS / BUSINESS_PHONE for the invoice header.
  echo   3. Save, close Notepad, and run install.bat again.
  notepad ".env"
  pause
  exit /b 0
)

findstr /c:"=CHANGE_ME" ".env" >nul
if not errorlevel 1 (
  echo ".env" still contains CHANGE_ME. Set the passwords, save, and run install.bat again.
  notepad ".env"
  pause
  exit /b 1
)

if not exist "backups" mkdir "backups"

echo Loading the application images. This takes a few minutes...
docker load -i images.tar
if errorlevel 1 (
  echo Loading images failed. Check that images.tar is complete and there is enough disk space.
  pause
  exit /b 1
)

echo.
echo Starting the application...
docker compose up -d
if errorlevel 1 (
  echo Start failed. Run logs.bat to see why.
  pause
  exit /b 1
)

call "%~dp0open-firewall.bat" nopause
call "%~dp0wait-ready.bat"

echo.
echo First sign-in: user "admin" with the ADMIN_INITIAL_PASSWORD from .env.
echo You will be asked to choose a new password straight away.
echo Daily backups go to: %~dp0backups
pause
