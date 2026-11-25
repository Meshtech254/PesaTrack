# Build verification

## XML/manual build (latest)

The XML/manual version is ready at `app/build/outputs/apk/manual/debug/app-manual-debug.apk`. Install this APK instead of the earlier `app-debug.apk`, which still contains SMS permissions.

- Version 1.0.1, version code 2, application ID `ke.pesatrail`.
- Full offline build: **BUILD SUCCESSFUL**, 155 tasks (153 executed, 2 up to date).
- All 27 JVM tests executed and passed, with no failures or skipped tests.
- Manual debug APK, unsigned manual release APK, and manual instrumentation APK packaged successfully.
- Manual debug lint: zero errors, one advisory recommending KSP instead of kapt. Manual release vital lint passed.
- Packaged debug APK inspected using `aapt`: no READ_SMS, RECEIVE_SMS, SEND_SMS or INTERNET permission, and no SmsReceiver or SMS_RECEIVED registration.
- Debug APK signature verified. Its signing certificate matches the earlier debug APK, so this build is eligible to replace that earlier installation without uninstalling.
- SMS flavor manifest merge passed; the SMS-only manifest remains separate for a later build. No SMS-flavor APK was built in this invocation.
- Installation, Play Protect acceptance, interactive UI checks, and update/data retention on a device remain unverified.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testManualDebugUnitTest :app:assembleManualDebug :app:assembleManualRelease :app:assembleManualDebugAndroidTest :app:lintManualDebug :app:processSmsDebugMainManifest --offline --console=plain
```

Build output: `build-manual.log`. Test report: `app/build/reports/tests/testManualDebugUnitTest/index.html`. Lint report: `app/build/reports/lint-results-manualDebug.html`. APK inspection logs: `apk-manual-permissions.log`, `apk-manual-manifest.log`, and `apk-manual-signature.log`.

Manual debug APK SHA-256: `A04121494AD833248364E871964F850A33E3C1D99139C8BDCCF4B8862F0B3FDF`.

Unsigned manual release: `app/build/outputs/apk/manual/release/app-manual-release-unsigned.apk`. Instrumentation APK: `app/build/outputs/apk/androidTest/manual/debug/app-manual-debug-androidTest.apk`.

The future SMS flavor is prepared with version code 3, the same application ID and the same database schema. A higher version code and the original signing certificate are both required for an update. Keep encrypted backups; do not uninstall or clear data to switch flavors. A differently signed production APK cannot update the provided debug build directly.

## Earlier SMS-enabled build verification

Verification date: 25 November 2026 (Africa/Nairobi).

This is the native Android PesaTrail application. The workspace already contained the app implementation; this verification uses those existing changes without resetting them.

## Environment

- Android Studio bundled JDK 21, compiling Java/Kotlin bytecode for Java 17.
- Android SDK platform 36; minimum supported Android API 26.
- Gradle wrapper 8.14.3 and Android Gradle Plugin 8.13.0.

## Command

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --rerun :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

## Results

- Full command: **BUILD SUCCESSFUL**, 152 tasks (23 executed, 129 up to date).
- Debug and release APK assembly: passed. The earlier Room constructor compilation error did not recur.
- JVM unit tests: freshly executed using `--rerun`; 27 tests, zero failures, zero errors, zero skipped tests.
- Instrumentation test APK compilation and packaging: passed. Tests were not executed on a device.
- Debug lint: zero errors, one advisory recommending KSP instead of kapt for Room. Release vital lint also passed.
- Rebuilt debug APK: `apksigner verify --verbose` passed with APK Signature Scheme v2.
- Packaged permissions: inspected using `aapt dump permissions`; no `INTERNET`, `SEND_SMS`, contacts, or broad storage permission.

Final output is in `build-final.log`. JVM test details are in `app/build/reports/tests/testDebugUnitTest/index.html`; lint details are in `app/build/reports/lint-results-debug.html`. Generated `.log` files are ignored by Git.

## Build fixes

The initial full build tried to download Compose UI 1.9.2 for the instrumentation compile classpath, while the app resolved UI 1.9.4. The download failed because `dl.google.com` could not be resolved. Updating the shared Compose BOM to cached version `2025.10.01` aligned the dependencies and allowed the complete build to finish offline. Fresh machines still need network access to download dependencies.

Lint also identified a local SDK path escape and missing optional telephony declaration. The ignored `local.properties` now escapes the drive separator; the manifest declares telephony optional because XML import and manual entry work without SMS hardware. No lint baseline or error suppression was added.

## Artifacts

- Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk` (signed with the development key).
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk` (requires private release signing before installation).
- Instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

Debug APK SHA-256: `3D3FDCE8EC1E52C67F7A26E5B6948FC1D8E93757375ECCDB2747D2F96043E01E`.

## Device verification

No Android device was connected when `adb devices -l` was checked. Instrumentation tests and interactive app checks have not been run in this session. APK compilation does not establish that SMS permission allowlisting, biometric unlock, encrypted database operation, or background delivery works on a particular device.

Use `docs/DEVICE_TESTS.md` for device checks and `docs/SMS_RESTRICTIONS.md` for the direct-SMS and XML import behavior. No SMS restrictions were bypassed.
