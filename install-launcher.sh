#!/bin/sh
# Mac/Linux version of install-launcher.ps1: build S3 Home (debug APK) and install it on the box.
# Usage: ./install-launcher.sh [--build-only] [--set-home]
#   TV_SERIAL=192.168.x.x:<port> ./install-launcher.sh --set-home
# Needs: adb on PATH (Android SDK platform-tools), the Android SDK (ANDROID_HOME or
# launcher/local.properties), and a JDK 17+ (JAVA_HOME; Android Studio's bundled one is found by itself).
set -e
cd "$(dirname "$0")"
PKG=com.launcher.mi.box.s3
BUILD_ONLY=0
SET_HOME=0
for a in "$@"; do
    case "$a" in
        --build-only) BUILD_ONLY=1 ;;
        --set-home) SET_HOME=1 ;;
        *) echo "unknown option: $a" >&2; exit 2 ;;
    esac
done

# JDK: JAVA_HOME if set, else the one bundled with Android Studio.
if [ -z "$JAVA_HOME" ]; then
    for j in "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
             "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
             /opt/android-studio/jbr "$HOME/android-studio/jbr" /snap/android-studio/current/jbr; do
        if [ -x "$j/bin/java" ]; then export JAVA_HOME="$j"; break; fi
    done
fi
[ -n "$JAVA_HOME" ] || { echo "Set JAVA_HOME to a JDK 17+ (or install Android Studio)" >&2; exit 1; }

# SDK: Gradle reads launcher/local.properties (sdk.dir=...) or ANDROID_HOME.
if [ ! -f launcher/local.properties ] && [ -z "$ANDROID_HOME" ]; then
    for s in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        if [ -d "$s" ]; then export ANDROID_HOME="$s"; break; fi
    done
fi
[ -f launcher/local.properties ] || [ -n "$ANDROID_HOME" ] || {
    echo "Android SDK not found: set ANDROID_HOME or write sdk.dir=... in launcher/local.properties" >&2; exit 1; }

chmod +x launcher/gradlew
(cd launcher && ./gradlew -q assembleDebug)
APK=launcher/app/build/outputs/apk/debug/app-debug.apk
echo "Built $APK"
[ "$BUILD_ONLY" = 1 ] && exit 0

: "${TV_SERIAL:?set TV_SERIAL=192.168.x.x:<port> (adb connect first)}"
ADB="adb -s $TV_SERIAL"
# adb install can hang on Android 14 waiting for an on-screen confirmation: push + pm install instead.
$ADB push "$APK" /data/local/tmp/s3home.apk >/dev/null
$ADB shell pm install -r /data/local/tmp/s3home.apk
$ADB shell rm /data/local/tmp/s3home.apk
# Wi-Fi name in the pill (location) and Quick settings > Remotes & accessories (Nearby devices).
$ADB shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
$ADB shell pm grant $PKG android.permission.BLUETOOTH_CONNECT
# Optional, NOT done here: WRITE_SECURE_SETTINGS for the device list page (see README).
if [ "$SET_HOME" = 1 ]; then
    $ADB shell cmd package set-home-activity $PKG/.HomeActivity
fi
$ADB shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
