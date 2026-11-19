# Caught_You v1.1 — reference application-matched location checks

This version replaces the original Developer-Options-only prototype with the location-spoofing signals confirmed in the supplied `reference application-sources.zip`.

## Detection rules implemented

1. **Developer Options**
   - Reads `Settings.Secure["development_settings_enabled"]`.
   - Source reference: `J1/m.java`, method `m()`; also `BasActivity.isMockSettingsON()`.
   - The source app's spoofing decision combines this setting with its mock-location flag.

2. **Mock location from Android LocationManager**
   - Checks the `Location.isFromMockProvider` flag on locations received from GPS/network LocationManager providers.
   - Source reference: `com/aebas/aebas_client/f0.java`, `onLocationChanged()`, which assigns the result to `h0.c0`.

3. **Mock location from Fused Location Provider**
   - On Android 12/API 31+, checks `Location.isMock()`.
   - On older Android versions, checks `Location.isFromMockProvider()`.
   - Source reference: `com/aebas/aebas_client/j0.java`.

4. **Decision**
   - `CAUGHT YOU` if Developer Options are enabled OR an Android location is marked as mock.
   - `SAFE` only if a usable location fix is obtained and none of those indicators is detected.
   - `UNABLE TO VERIFY` if permission is denied or no location fix is available. This prevents an unavailable check from being reported as SAFE.

## Deliberately excluded

No root, emulator, proxy, USB debugging, generic system-property, or Play Integrity checks have been added. The supplied Java source contains references to some of these mechanisms, but their participation in the location-spoofing decision path was not established. The archive also does not include the native `.so` implementation needed to inspect native-only checks.

## Runtime behaviour

- Checks run once when the app is opened.
- Location permission is requested at first launch.
- It reads last-known GPS/network locations, requests one fused current location, and briefly listens for GPS/network updates.
- Location updates are removed after the result is finalized; there is no ongoing background monitoring.
- Coordinates are Android-reported coordinates. If a mock provider supplies them, they may not represent the device's physical location.
- The app does not send location data to a server and does not request Internet permission.

## Build

1. Install Android Studio and Android SDK Platform 35.
2. Open this project folder in Android Studio.
3. Allow Gradle sync to finish (JDK 17 required).
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

From a terminal with JDK 17 and the Android SDK installed, `./gradlew assembleDebug` produces the same APK.

Application ID: `com.caughtyou.app`
Version: `1.1`

## Cloud build (useful on older Macs)

This project includes `.github/workflows/build-apk.yml`. To build without installing Android Studio or the Android SDK on your Mac:

1. Create a GitHub repository and upload the contents of this project (the `app/`, root Gradle files, and `.github/` folder).
2. Open the repository's **Actions** tab.
3. Select **Build Caught_You APK** and click **Run workflow** (or push to `main`).
4. Open the completed workflow run and download the `Caught_You-debug.apk` artifact. It downloads as the APK itself, not a zip.

The cloud workflow builds on Ubuntu with JDK 17, Android SDK 35 and Gradle 8.9.
