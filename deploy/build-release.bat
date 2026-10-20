@echo off
rem Builds an offline release on a PC with Docker Desktop and internet (NOT on the client PC):
rem   deploy\build-release.bat
rem Result: release\LeadingIMS-<version>\ and release\LeadingIMS-<version>.zip containing the
rem images (images.tar), docker-compose.yml, .env.example, scripts and the Windows .bat files.
rem Copy the zip to the client PC (e.g. by USB) and follow INSTALL.txt.
setlocal
cd /d "%~dp0"

set /p VERSION=<VERSION
set "VERSION=%VERSION: =%"
set "OUT=..\release\LeadingIMS-%VERSION%"

docker info >nul 2>&1
if errorlevel 1 (
  echo Start Docker Desktop first.
  exit /b 1
)

rem docker-compose.yml requires these at run time; building needs only placeholders.
set "DB_PASSWORD=build-only"
set "ADMIN_INITIAL_PASSWORD=build-only"

echo === Building images for version %VERSION% ===
docker compose -f docker-compose.yml -f docker-compose.build.yml build --pull
if errorlevel 1 exit /b 1
docker pull postgres:17-alpine
if errorlevel 1 exit /b 1
docker tag leadingims/api:latest leadingims/api:%VERSION%
docker tag leadingims/web:latest leadingims/web:%VERSION%

echo === Writing %OUT% ===
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%\scripts"
docker save -o "%OUT%\images.tar" leadingims/api:latest leadingims/web:latest postgres:17-alpine
if errorlevel 1 exit /b 1
copy /y docker-compose.yml "%OUT%\" >nul
copy /y .env.example "%OUT%\" >nul
copy /y VERSION "%OUT%\" >nul
copy /y INSTALL.txt "%OUT%\" >nul
copy /y scripts\*.sh "%OUT%\scripts\" >nul
copy /y windows\*.bat "%OUT%\" >nul

echo === Zipping ===
powershell -NoProfile -Command "Compress-Archive -Path '%OUT%\*' -DestinationPath '%OUT%.zip' -Force"
if errorlevel 1 exit /b 1

echo.
echo Release ready: %OUT%.zip
