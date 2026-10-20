@echo off
rem Waits until the application answers (the API needs about a minute after starting), then
rem prints the addresses the office PCs should open. Used by install/start/update.
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "PORT=80"
for /f "tokens=1,* delims==" %%a in ('findstr /b /c:"HTTP_PORT=" ".env" 2^>nul') do if not "%%b"=="" set "PORT=%%b"
set "SUFFIX="
if not "%PORT%"=="80" set "SUFFIX=:%PORT%"

echo Waiting for the application to be ready...
for /l %%n in (1,1,60) do (
  curl -s -f -o nul "http://localhost%SUFFIX%/api/health" && goto ready
  timeout /t 5 /nobreak >nul
)
echo The application did not answer within 5 minutes. Run logs.bat to see what is wrong.
exit /b 1

:ready
echo.
echo The application is running. Open it in a browser:
echo   On this PC:       http://localhost%SUFFIX%
for /f "tokens=2 delims=:" %%i in ('ipconfig ^| findstr /c:"IPv4"') do (
  set "IP=%%i"
  set "IP=!IP: =!"
  echo   On office PCs:    http://!IP!%SUFFIX%
)
exit /b 0
