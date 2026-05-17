@echo off
setlocal enabledelayedexpansion
title BDJB Cyberpunk Autoloader - Builder

echo.
echo  ==============================================
echo   BDJB // Sonic Loader  -  Cyberpunk Builder
echo  ==============================================
echo.

:: Check WSL
wsl --status >nul 2>&1
if errorlevel 1 (
    echo  ERROR: WSL2 not found.
    echo  Install: open PowerShell as Admin and run:  wsl --install
    echo  Then reboot and run this again.
    echo.
    pause
    exit /b 1
)

:: Get this batch file's directory and convert to WSL path
set "WIN_DIR=%~dp0"
if "%WIN_DIR:~-1%"=="\" set "WIN_DIR=%WIN_DIR:~0,-1%"

for /f "delims=" %%P in ('wsl wslpath -u "%WIN_DIR%"') do set "WSL_DIR=%%P"

echo  Folder  : %WIN_DIR%
echo  Output  : %WIN_DIR%\output
echo.

:: ── Git sync ──────────────────────────────────────────────────────────────────
echo  Syncing with remote...
git -C "%WIN_DIR%" pull --rebase origin main 2>&1
if errorlevel 1 (
    echo  WARNING: git pull failed - building with local files only
    echo.
) else (
    echo  Git sync OK
    echo.
)

:: ── Run build ─────────────────────────────────────────────────────────────────
echo  Running build inside WSL...
echo  (First run installs tools and clones bdj-sdk - takes a few minutes)
echo.

wsl bash -c "chmod +x '%WSL_DIR%/build.sh' && '%WSL_DIR%/build.sh'"

if errorlevel 1 (
    echo.
    echo  ==============================================
    echo   BUILD FAILED  -  see errors above
    echo  ==============================================
    echo.
    pause
    exit /b 1
)

:: Read summary written by build.sh
set "SUMMARY=%WIN_DIR%\output\.build_summary"
set "ISO_NAME="
set "SONIC_UPDATED=0"
set "SONIC_OLD=unknown"
set "SONIC_NEW=unknown"
set "DISC_TITLE=Sonic Loader"
set "PROJ_VER=unknown"
set "PROJ_VER_OLD=unknown"

if exist "!SUMMARY!" (
    for /f "usebackq tokens=1,* delims==" %%A in ("!SUMMARY!") do (
        if "%%A"=="ISO"           set "ISO_NAME=%%B"
        if "%%A"=="SONIC_UPDATED" set "SONIC_UPDATED=%%B"
        if "%%A"=="SONIC_OLD_VER" set "SONIC_OLD=%%B"
        if "%%A"=="SONIC_NEW_VER" set "SONIC_NEW=%%B"
        if "%%A"=="DISC_TITLE"    set "DISC_TITLE=%%B"
        if "%%A"=="PROJ_VER"      set "PROJ_VER=%%B"
        if "%%A"=="PROJ_VER_OLD"  set "PROJ_VER_OLD=%%B"
    )
)

echo.
echo  ==============================================
echo   BUILD COMPLETE
echo  ==============================================
echo.
echo   ISO    :  !ISO_NAME!
echo   Disc   :  !DISC_TITLE!
echo.
if "!PROJ_VER!"=="!PROJ_VER_OLD!" (
    echo   Project version:  !PROJ_VER!
) else (
    echo   Project version:  !PROJ_VER_OLD!  ^-^>  !PROJ_VER!  (auto-bumped)
)
echo.
if "!SONIC_UPDATED!"=="1" (
    echo   Sonic Loader UPDATED:  !SONIC_OLD!  ^-^>  !SONIC_NEW!
) else (
    echo   Sonic Loader:  !SONIC_NEW!  (already up to date)
)
echo.
echo   Output folder:  %WIN_DIR%\output\
echo.
echo  Burn to BD-RE with ImgBurn or InfraRecorder.
echo.
pause
