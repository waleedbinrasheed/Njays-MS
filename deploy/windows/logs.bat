@echo off
rem Shows the latest log lines and keeps following them. Press Ctrl+C to stop.
rem   logs.bat          API and web app
rem   logs.bat backup   one part only (db, api, web or backup)
setlocal
cd /d "%~dp0"
if "%~1"=="" (
  docker compose logs --tail 200 -f api web
) else (
  docker compose logs --tail 200 -f %1
)
