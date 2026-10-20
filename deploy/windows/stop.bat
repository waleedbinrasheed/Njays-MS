@echo off
rem Stops the application. All data and backups are kept; start.bat brings it back.
setlocal
cd /d "%~dp0"
docker compose down
echo The application is stopped. Data and backups are kept.
pause
