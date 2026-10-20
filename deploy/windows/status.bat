@echo off
rem Shows whether each part is running and the most recent backups.
setlocal
cd /d "%~dp0"
echo Application parts (all four should be "running"; api/web should become "healthy"):
docker compose ps
echo.
echo Latest backups in %~dp0backups:
dir /b /o-d "backups\*.dump" 2>nul || echo   (none yet)
pause
