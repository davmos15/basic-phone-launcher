#!/bin/bash
# =====================================================
#  Nothing Phone 2a Emulator Setup Script (macOS/Linux)
#  Creates a custom AVD profile matching the device
# =====================================================
#
#  Specs: 6.7" AMOLED, 1080x2412, 420dpi, 8GB RAM
#  OS: Android 14 (API 34) / Nothing OS 2.5
#
#  Prerequisites:
#    - Android Studio installed
#    - Android SDK with API 35 system image
#    - ANDROID_HOME environment variable set
# =====================================================

set -e

echo ""
echo "=== Nothing Phone 2a Emulator Setup ==="
echo ""

# Check for ANDROID_HOME
if [ -z "$ANDROID_HOME" ]; then
    echo "ERROR: ANDROID_HOME is not set."
    echo "Please set it, e.g.:"
    echo "  export ANDROID_HOME=\$HOME/Library/Android/sdk"
    exit 1
fi

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
EMULATOR="$ANDROID_HOME/emulator/emulator"

# Install required SDK components
echo "[1/4] Installing required SDK packages..."
$SDKMANAGER "system-images;android-35;google_apis;x86_64" "platform-tools" "platforms;android-35" "emulator"

# Create the AVD
echo ""
echo "[2/4] Creating Nothing Phone 2a AVD..."
$AVDMANAGER create avd \
    --name "NothingPhone2a" \
    --package "system-images;android-35;google_apis;x86_64" \
    --device "pixel_7" \
    --force

# Customize the AVD config
echo ""
echo "[3/4] Customizing AVD to match Nothing Phone 2a specs..."

AVD_DIR="$HOME/.android/avd/NothingPhone2a.avd"
CONFIG="$AVD_DIR/config.ini"

# Update display to match Nothing Phone 2a (6.7" 1080x2412 @ 420dpi)
if [[ "$OSTYPE" == "darwin"* ]]; then
    sed -i '' 's/hw.lcd.width=.*/hw.lcd.width=1080/' "$CONFIG"
    sed -i '' 's/hw.lcd.height=.*/hw.lcd.height=2412/' "$CONFIG"
    sed -i '' 's/hw.lcd.density=.*/hw.lcd.density=420/' "$CONFIG"
    sed -i '' 's/hw.ramSize=.*/hw.ramSize=8192/' "$CONFIG"
else
    sed -i 's/hw.lcd.width=.*/hw.lcd.width=1080/' "$CONFIG"
    sed -i 's/hw.lcd.height=.*/hw.lcd.height=2412/' "$CONFIG"
    sed -i 's/hw.lcd.density=.*/hw.lcd.density=420/' "$CONFIG"
    sed -i 's/hw.ramSize=.*/hw.ramSize=8192/' "$CONFIG"
fi

# Add properties if not present
grep -q "hw.lcd.width" "$CONFIG" || echo "hw.lcd.width=1080" >> "$CONFIG"
grep -q "hw.lcd.height" "$CONFIG" || echo "hw.lcd.height=2412" >> "$CONFIG"
grep -q "hw.lcd.density" "$CONFIG" || echo "hw.lcd.density=420" >> "$CONFIG"
grep -q "hw.ramSize" "$CONFIG" || echo "hw.ramSize=8192" >> "$CONFIG"

echo ""
echo "[4/4] Setup complete!"
echo ""
echo "To launch the emulator:"
echo "  $EMULATOR -avd NothingPhone2a"
echo ""
echo "Or from Android Studio:"
echo "  Tools > Device Manager > NothingPhone2a > Play"
echo ""
echo "AVD config location: $AVD_DIR"
echo ""
