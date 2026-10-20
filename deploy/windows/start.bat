@echo off
rem Starts the application (Docker Desktop must be running).
setlocal
cd /d "%~dp0"
docker info >nul 2>&1
if errorlevel 1 (
  echo Docker Desktop is not running. Start it, wait for "Engine running", then try again.
  pause
  exit /b 1
)
docker compose up -d
if errorlevel 1 (
  echo Start failed. Run logs.bat to see why.
  pause
  exit /b 1
)
call "%~dp0wait-ready.bat"
pause
