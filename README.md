# DumbPhone Launcher

A Nokia 3310-inspired minimal launcher for Android. Turn your smartphone into a dumb phone -- on demand.

[![Build](https://github.com/davmos15/basic-phone-launcher/actions/workflows/build.yml/badge.svg)](https://github.com/davmos15/basic-phone-launcher/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/davmos15/basic-phone-launcher?label=Download%20APK)](https://github.com/davmos15/basic-phone-launcher/releases/latest)

> **[Download the latest APK here](https://github.com/davmos15/basic-phone-launcher/releases/latest)**

## Features

- **Retro aesthetic** -- black background, monochrome text, monospace font
- **Customisable colour** -- choose from 10 colour presets for the clock and UI
- **Configurable app whitelist** -- choose exactly which apps appear in your menu
- **Adjustable icon sizes** -- Small, Medium, Large, or Extra Large app icons
- **Swipe-up app drawer** -- swipe up or tap MENU to open your apps
- **Clock tap** -- tap the clock to open your alarms
- **Optional weather widget** -- shows current weather on the home screen (requires internet and location)
- **Greyscale mode** -- system-wide greyscale toggle via Accessibility or Bedtime Mode
- **Do Not Disturb** -- auto-enable DND when on the home screen
- **Wallpaper override** -- temporarily sets wallpaper to black while the launcher is active
- **No distractions** -- only whitelisted apps are accessible
- **Security hardened** -- ProGuard/R8 obfuscation, backup disabled, cleartext blocked

## Requirements

- Android 8.0+ (API 26+)
- Any Android device

### For building from source

- JDK 17+
- Android SDK (API 35)

## Installation (End User)

### From APK

1. Download the latest `app-debug.apk` (or signed release APK) from the [Releases](../../releases) page
2. Transfer the APK to your phone
3. Open the APK on your phone -- you may need to enable **Install from unknown sources** in your device settings
4. Once installed, press the **Home button**
5. Android will ask which launcher to use -- select **DumbPhone** and choose **Always**
6. You're now in dumb mode!

### From Android Studio

1. Clone this repository
2. Open the `dumbphone-launcher` folder in Android Studio
3. Connect your phone via USB (with USB debugging enabled) or start an emulator
4. Click **Run** (green play button)

## How to Build

### Option A: Android Studio (Recommended)

1. Open this project in Android Studio
2. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**
3. Find the APK in `app/build/outputs/apk/debug/`

### Option B: Command Line

```bash
# macOS / Linux
chmod +x gradlew
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

> **Note:** If the `gradle-wrapper.jar` is missing, open the project in Android Studio first (it will generate it automatically), or run `gradle wrapper --gradle-version 8.9` if you have Gradle installed globally.

### Option C: GitHub Actions

1. Push this repo to GitHub
2. Go to **Actions** tab > **Build DumbPhone APK** > **Run workflow**
3. Download the APK from the workflow artifacts

## How to Use

### Home Screen

| Action | What it does |
|--------|-------------|
| Tap **MENU** | Opens your whitelisted apps |
| Tap **SETTINGS** | Opens the settings screen |
| Tap the **clock** | Opens your alarm/clock app |
| **Swipe up** | Opens your whitelisted apps |

### Settings

- **Display** -- clock colour, show seconds, 24-hour clock, greyscale mode, icon size, app labels
- **Allowed apps** -- check/uncheck any installed app to control what appears in the menu
- **Weather widget** -- toggle weather display on the home screen (needs location permission)
- **Do Not Disturb** -- auto-enable DND when on the home screen
- **Wallpaper override** -- set wallpaper to black while the launcher is active (restores original when disabled)
- **Exit Dumb Mode** -- opens Android settings to switch back to your normal launcher

### Exiting Dumb Mode

1. Open **SETTINGS** > scroll to **EXIT DUMB MODE** at the bottom
2. Or manually: **Android Settings > Apps > Default Apps > Home App** > select your normal launcher

## Customisation

### Changing the default colour

The default clock colour is classic green (`#7FBF3F`). Users can change this at any time from **Settings > Clock colour**. To change the default for new installations, edit the `DEFAULT_CLOCK_COLOUR` constant in `PrefsManager.kt`.

### Changing default whitelisted apps

Edit the `DEFAULT_APPS` set in `PrefsManager.kt` with the package names you want enabled by default on first run.

## Nothing Phone 2a Emulator Setup

To test on a Nothing Phone 2a emulator profile:

### Automated Setup

```bash
# Windows
scripts\setup-nothing-phone-2a-avd.bat

# macOS / Linux
chmod +x scripts/setup-nothing-phone-2a-avd.sh
./scripts/setup-nothing-phone-2a-avd.sh
```

### Manual Setup (Android Studio)

1. Open **Tools > Device Manager**
2. Click **Create Virtual Device**
3. **New Hardware Profile** with:
   - Screen size: 6.7"
   - Resolution: 1080 x 2412
   - Density: 420 dpi
4. Select **Android 15 (API 35)** system image
5. Name it `NothingPhone2a` and finish

### Launch

```bash
emulator -avd NothingPhone2a
```

## Security & Permissions

- **No data collection** -- zero analytics, crash reporting, or telemetry
- **Network security config** -- all cleartext traffic explicitly blocked as defence-in-depth
- **Backup disabled** -- `allowBackup=false` prevents data extraction via ADB
- **ProGuard/R8 enabled** -- release builds are obfuscated and optimised
- **Exported components locked** -- only the home launcher activity is exported
- **SharedPreferences in MODE_PRIVATE** -- app data is not accessible to other apps

### Permissions used

| Permission | Required | Purpose |
|---|---|---|
| `QUERY_ALL_PACKAGES` | Yes | List installed apps for whitelist |
| `INTERNET` | No | Weather widget (Open-Meteo API, no API key) |
| `ACCESS_COARSE_LOCATION` | No | Weather widget (approximate location) |
| `ACCESS_NOTIFICATION_POLICY` | No | Do Not Disturb toggle |
| `SET_WALLPAPER` | No | Wallpaper override feature |
| `WRITE_SECURE_SETTINGS` | No | Direct greyscale toggle (ADB-granted; falls back to Accessibility Settings if not granted) |

> **Note:** `WRITE_SECURE_SETTINGS` is a signature-level permission that can only be granted via ADB. If not granted, the greyscale toggle guides the user to system Accessibility settings instead. This permission may cause review flags on the Play Store -- consider removing it for store-distributed builds.

## Release Signing

To sign release APKs, set these environment variables before building:

```bash
export KEYSTORE_FILE=/path/to/your.keystore
export KEYSTORE_PASSWORD=your_store_password
export KEY_ALIAS=your_key_alias
export KEY_PASSWORD=your_key_password
```

To generate a keystore:

```bash
keytool -genkey -v \
  -keystore dumbphone-release.keystore \
  -alias dumbphone \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

> **Never commit keystore files to version control.** They are excluded via `.gitignore`.

## Tech Stack

- **Language:** Kotlin 2.0.21
- **Target SDK:** 35 (Android 15)
- **Min SDK:** 26 (Android 8.0)
- **Build:** Gradle 8.9 / AGP 8.7.3
- **Dependencies:** AndroidX Core, AppCompat, Material, RecyclerView, ConstraintLayout, Preference

## Project Structure

```
app/src/main/
  java/com/dumbphone/launcher/
    MainActivity.kt              -- Home screen (clock, date, weather, gestures)
    AppDrawerActivity.kt         -- App drawer grid (whitelisted apps)
    SettingsActivity.kt          -- Settings menu (features, wallpaper, DND)
    DisplaySettingsActivity.kt   -- Display settings (colour, clock, icons, greyscale)
    AppsSettingsActivity.kt      -- App whitelist manager with search
    PrefsManager.kt              -- SharedPreferences wrapper
    WeatherUtil.kt               -- Open-Meteo weather API client
  res/layout/
    activity_main.xml            -- Home screen layout
    activity_app_drawer.xml      -- App drawer layout
    activity_settings.xml        -- Settings layout
    activity_display_settings.xml -- Display settings layout
    activity_apps_settings.xml   -- Apps settings layout
    item_app.xml                 -- App grid item (icon + label)
    item_app_toggle.xml          -- App toggle item (icon + label + checkbox)
```

## Licence

MIT -- do whatever you want with it.
