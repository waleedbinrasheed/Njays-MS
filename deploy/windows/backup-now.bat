@echo off
rem Makes a database backup immediately (in addition to the automatic daily one).
setlocal
cd /d "%~dp0"
docker compose exec -T backup sh /scripts/backup.sh now
if errorlevel 1 (
  echo Backup failed. Is the application running? Try start.bat, then backup-now.bat again.
  pause
  exit /b 1
)
echo Backups are in %~dp0backups
echo Tip: copy the newest file to a USB drive or another PC regularly.
if /i not "%~1"=="nopause" pause
exit /b 0
