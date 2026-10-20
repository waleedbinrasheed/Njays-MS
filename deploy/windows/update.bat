@echo off
rem Installs a new version. First copy ALL files of the new release folder into this folder
rem (overwrite when asked) - your .env and the backups folder are not part of a release and stay.
rem Then run update.bat. Data is kept; database changes are applied automatically on start.
setlocal
cd /d "%~dp0"
docker info >nul 2>&1
if errorlevel 1 (
  echo Docker Desktop is not running. Start it, wait for "Engine running", then try again.
  pause
  exit /b 1
)
if not exist ".env" (
  echo No .env found here. update.bat must run in the folder where the application is installed.
  pause
  exit /b 1
)

set /p VERSION=<VERSION
echo Updating to version %VERSION% ...

docker compose ps --status running --services 2>nul | findstr /x "backup" >nul
if not errorlevel 1 (
  echo Backing up the current data first...
  call "%~dp0backup-now.bat" nopause
  if errorlevel 1 (
    echo Backup failed, so the update was not started.
    pause
    exit /b 1
  )
)

call "%~dp0load-images.bat"
if errorlevel 1 (
  echo Nothing was changed.
  pause
  exit /b 1
)
docker compose up -d
if errorlevel 1 (
  echo Start failed. Run logs.bat to see why.
  pause
  exit /b 1
)
rem Remove the previous version's images, which are no longer used.
docker image prune -f >nul
call "%~dp0wait-ready.bat"
echo Update to %VERSION% complete.
pause
