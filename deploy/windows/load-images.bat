@echo off
rem Loads the application images from images.tar (the release file). Used by install/update.
rem On the build machine there is no images.tar, but the freshly built images are already in
rem Docker, so those are used instead.
setlocal
cd /d "%~dp0"

if exist "images.tar" (
  echo Loading the application images from images.tar. This takes a few minutes...
  docker load -i images.tar
  if errorlevel 1 (
    echo Loading images failed. Check that images.tar was copied completely and that the disk
    echo has enough free space.
    exit /b 1
  )
  exit /b 0
)

docker image inspect leadingims/api:latest >nul 2>&1
if errorlevel 1 goto missing
docker image inspect leadingims/web:latest >nul 2>&1
if errorlevel 1 goto missing
echo images.tar not found - using the images already in Docker on this PC ^(build machine^).
exit /b 0

:missing
echo.
echo images.tar was not found in %~dp0
echo and the application images are not in Docker on this PC.
echo.
echo   - Run this script from the RELEASE folder (unzipped LeadingIMS-^<version^>.zip), which
echo     contains images.tar - not from the project's deploy\windows folder.
echo   - To make a release, run deploy\build-release.bat on the build machine and check that it
echo     ends with "Release ready".
exit /b 1
