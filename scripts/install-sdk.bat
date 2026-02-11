@echo off
set ANDROID_HOME=C:\Users\NadavMoskow\AppData\Local\Android\Sdk
set JAVA_HOME=C:\Program Files\Java\jdk-21
echo Installing Android SDK Platform 35 and Build Tools...
echo y | "%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%ANDROID_HOME%" "platforms;android-35" "build-tools;35.0.0"
echo.
echo Done. Checking installed platforms:
dir "%ANDROID_HOME%\platforms" /B
