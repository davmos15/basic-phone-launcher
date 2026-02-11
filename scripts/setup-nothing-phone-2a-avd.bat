@echo off
REM =====================================================
REM  Nothing Phone 2a Emulator Setup Script (Windows)
REM  Creates a custom AVD profile matching the device
REM =====================================================
REM
REM  Specs: 6.7" AMOLED, 1080x2412, 420dpi, 8GB RAM
REM  OS: Android 14 (API 34) / Nothing OS 2.5
REM
REM  Prerequisites:
REM    - Android Studio installed
REM    - Android SDK with API 35 system image
REM    - ANDROID_HOME environment variable set
REM =====================================================

echo.
echo === Nothing Phone 2a Emulator Setup ===
echo.

REM Check for ANDROID_HOME
if "%ANDROID_HOME%"=="" (
    echo ERROR: ANDROID_HOME is not set.
    echo Please set it to your Android SDK location, e.g.:
    echo   set ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk
    exit /b 1
)

set SDKMANAGER=%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat
set AVDMANAGER=%ANDROID_HOME%\cmdline-tools\latest\bin\avdmanager.bat
set EMULATOR=%ANDROID_HOME%\emulator\emulator.exe

REM Install required SDK components
echo [1/4] Installing required SDK packages...
call "%SDKMANAGER%" "system-images;android-35;google_apis;x86_64" "platform-tools" "platforms;android-35" "emulator"

REM Create the AVD
echo.
echo [2/4] Creating Nothing Phone 2a AVD...
call "%AVDMANAGER%" create avd ^
    --name "NothingPhone2a" ^
    --package "system-images;android-35;google_apis;x86_64" ^
    --device "pixel_7" ^
    --force

REM Customize the AVD config to match Nothing Phone 2a specs
echo.
echo [3/4] Customizing AVD to match Nothing Phone 2a specs...

set AVD_DIR=%USERPROFILE%\.android\avd\NothingPhone2a.avd
set CONFIG=%AVD_DIR%\config.ini

REM Update display to match Nothing Phone 2a (6.7" 1080x2412 @ 420dpi)
powershell -Command "(Get-Content '%CONFIG%') -replace 'hw.lcd.width=\d+', 'hw.lcd.width=1080' | Set-Content '%CONFIG%'"
powershell -Command "(Get-Content '%CONFIG%') -replace 'hw.lcd.height=\d+', 'hw.lcd.height=2412' | Set-Content '%CONFIG%'"
powershell -Command "(Get-Content '%CONFIG%') -replace 'hw.lcd.density=\d+', 'hw.lcd.density=420' | Set-Content '%CONFIG%'"

REM Update RAM to match (8GB)
powershell -Command "(Get-Content '%CONFIG%') -replace 'hw.ramSize=\d+', 'hw.ramSize=8192' | Set-Content '%CONFIG%'"

REM Append missing properties if not present
powershell -Command "if (-not (Select-String -Path '%CONFIG%' -Pattern 'hw.lcd.width')) { Add-Content '%CONFIG%' 'hw.lcd.width=1080' }"
powershell -Command "if (-not (Select-String -Path '%CONFIG%' -Pattern 'hw.lcd.height')) { Add-Content '%CONFIG%' 'hw.lcd.height=2412' }"
powershell -Command "if (-not (Select-String -Path '%CONFIG%' -Pattern 'hw.lcd.density')) { Add-Content '%CONFIG%' 'hw.lcd.density=420' }"
powershell -Command "if (-not (Select-String -Path '%CONFIG%' -Pattern 'hw.ramSize')) { Add-Content '%CONFIG%' 'hw.ramSize=8192' }"

echo.
echo [4/4] Setup complete!
echo.
echo To launch the emulator:
echo   %EMULATOR% -avd NothingPhone2a
echo.
echo Or from Android Studio:
echo   Tools ^> Device Manager ^> NothingPhone2a ^> Play
echo.
echo AVD config location: %AVD_DIR%
echo.

pause
