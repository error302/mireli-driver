# Mireli Driver 0.5.0 pilot verification

**Build date:** 7 October 2026
**State:** Driver sign-in pilot APK for internal device testing; not a production or Play Store release.

## Install

Install [Mireli-Driver-0.5.0-pilot.apk](../../deliverables/Mireli-Driver-0.5.0-pilot.apk) on an Android device or emulator. It uses package ID `io.github.error302.mireli.driver.pilot`, so it can be installed beside other Mireli variants. This APK is signed with the Android debug certificate. Check its SHA-256 in [SHA256SUMS-0.5.0.txt](../../deliverables/SHA256SUMS-0.5.0.txt).

The first screen is the driver sign-in form, followed by an explicit service availability state and the SGR onboarding explanation. The live service status check returned HTTP 404 during this build, so the disabled sign-in button is expected. No SMS is sent by opening the app or checking availability. Do not enter real identity documents or payment details in this pilot.

## Verification

- `:app:testPilotDebugUnitTest` passed.
- `:app:lintPilotDebug` passed.
- `:app:assemblePilotDebug` passed; APK signature verified with APK Signature Scheme v2. The signing certificate is Android Debug, for internal testing only.
- `:app:bundleProductionRelease` passed with minification. The resulting AAB is unsigned because no Mireli production upload keystore is configured; it is not ready for Play Console upload.
- The Android UI test classes completed all three test methods via `adb shell am instrument` on the Android 17 / API 37 emulator. They verify no seeded trips, the first-screen driver sign-in fields and the saved light/dark theme choice.
- The Gradle `:app:connectedPilotDebugAndroidTest` wrapper failed before collecting any tests (zero recorded tests). Direct AndroidJUnitRunner execution passed. Repeat the connected Gradle run on a stable supported emulator and physical Android devices before release.
- [Emulator screenshot](../../deliverables/Mireli-Driver-0.5.0-emulator.png) shows the initial sign-in form and the current unavailable-service response.

## Navigation status

The pilot contains the MapLibre in-app map and a backend-proxied Valhalla route request for an active assigned trip. It does not yet provide live road guidance: the production driver API returns 404 and `VALHALLA_URL` / `VALHALLA_API_TOKEN` are not configured. The OpenFreeMap public style is suitable for this pilot, not a Mireli uptime commitment. See [OPEN-SOURCE-NAVIGATION.md](OPEN-SOURCE-NAVIGATION.md) for the service setup, OSM attribution, privacy behavior and route-device tests required before launch.

## Play release gates still open

Restore and deploy the live API, configure SMS and private document storage, complete legal/privacy and transport approvals, connect passenger-booking dispatch, test payouts and support workflows, establish reliable tile/routing hosting, verify the final Android application ID and release signing, complete Play Console declarations, and pass physical-device and launch-rehearsal checks. The current verification is local build and emulator evidence only.
