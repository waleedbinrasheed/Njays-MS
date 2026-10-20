@echo off
rem Replaces ALL current data with a backup from the backups folder.
rem   restore.bat                                   lists backups and asks which one
rem   restore.bat leadingims-2026-10-20_0200.dump   restores that file
rem A safety backup of the current data is made first.
setlocal
cd /d "%~dp0"

set "FILE=%~nx1"
if "%FILE%"=="" (
  echo Backups available ^(newest first^):
  dir /b /o-d "backups\*.dump" 2>nul
  if errorlevel 1 (
    echo   none found in %~dp0backups
    pause
    exit /b 1
  )
  echo.
  set /p "FILE=Type or paste the file name to restore: "
)
if "%FILE%"=="" exit /b 1
if not exist "backups\%FILE%" (
  echo Not found: backups\%FILE%
  pause
  exit /b 1
)

echo.
echo WARNING: all current data will be replaced by "%FILE%".
set /p "OK=Type YES to continue: "
if /i not "%OK%"=="YES" (
  echo Cancelled. Nothing was changed.
  pause
  exit /b 0
)

echo Making a safety backup of the current data first...
call "%~dp0backup-now.bat" nopause
if errorlevel 1 (
  echo Safety backup failed, so nothing was restored.
  pause
  exit /b 1
)

docker compose stop web api
docker compose exec -T backup sh /scripts/restore.sh "%FILE%"
set "RESULT=%ERRORLEVEL%"
docker compose start api web
if not "%RESULT%"=="0" (
  echo RESTORE FAILED. The application was restarted with the data it had.
  pause
  exit /b 1
)
call "%~dp0wait-ready.bat"
echo Restore complete. Everyone should sign in again.
pause
