# PadStudio

A native Android controller mapper with a Steam Input inspired layout editor.

## Install and update

Download **PadStudio.apk** from [GitHub Releases](https://github.com/BrianCodez/PadStudio/releases/latest).

In Obtainium, add `https://github.com/BrianCodez/PadStudio` as the app source. Releases contain one signed universal APK. The package name and signing certificate stay stable across updates.

Android 11 or newer is required. Stick and analog trigger capture requires Android 14 or newer.

## First use

1. Open **Setup** and enable **PadStudio controller mapping** in Android accessibility settings. Android may require **Allow restricted settings** from the app info menu for a sideloaded app.
2. Select a profile. Choose a button and an action. Touch actions support a target picker and percentage coordinates.
3. Save the layout and start mapping. Use **Touch playground** to verify that a mapped button delivers a screen touch.
4. Enable **Capture analog controller input** for stick-to-touch or two-stage trigger mappings. This redirects joystick motion to PadStudio, so keep it off for native gamepad games.
5. Optionally link a game in Setup to switch profiles when that app opens. Export a profile backup before replacing profiles by import.

## Version 0.1

- Visual controller editor with accessible input buttons and portrait/landscape layouts.
- Profiles, two action layers, game associations, and JSON backup/import.
- Screen taps, held touches, directional swipes, Android Back/Home/Recents, and menu layer switching.
- Press, hold, double-press, and release activation. Repeat while held for screen taps.
- Stick deadzones, response curves, sensitivity, touch joystick output, and touch camera output.
- Soft/full analog trigger bindings.
- Handheld or phone gyroscope touch aiming, with sensitivity and smoothing.
- Live controller tester, touch playground, and accessibility overlay target picker.

This first version does not implement native gamepad-to-gamepad remapping, keyboard/mouse injection, controller-specific gyro protocols, Steam config import, flick stick, arbitrary macros, or radial menus. Games that reject accessibility-generated gestures may be incompatible. Emulator tests cover synthetic input and delivered gestures. Physical controller compatibility and device gyro aiming need validation on the target handheld.

## Privacy

PadStudio has no network permission. Profiles stay on the device. The accessibility service receives controller input and foreground app package events, sends gestures, and displays the target placement overlay. It does not request access to screen content. The optional GitHub button opens an external browser.

## Build

Use JDK 17 and Android SDK platform 35 with Build Tools 36.0.0.

```sh
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

For release signing, create a local, ignored `signing.properties` file with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Retain the same key for every update. Never commit signing material.

GitHub Actions checks the app and runs instrumentation tests on Android 11 and 15. A `v*` tag publishes a release after those checks pass. Configure the repository secrets `ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD`, using alias `padstudio`. Increase `versionCode` and `versionName` in `app/build.gradle`, update `RELEASE_NOTES.md`, then push the matching version tag.

## License

Application code is MIT licensed. Barlow fonts use the SIL Open Font License, included in `app/src/main/assets/Barlow-OFL.txt`.
