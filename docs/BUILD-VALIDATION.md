# Build validation — 4 October 2026

This is a synthetic Android preview. These results do not establish production
readiness, legal compliance, live web integration or performance parity with Uber/Bolt.

## Verified locally

- Gradle 9.4.1, Android SDK 36, Android Studio bundled JBR on Windows.
- Demo debug APK builds and installs on the Pixel 7 API 37 emulator.
- Production release APK builds with R8/resource shrinking; unsigned and unconnected.
- Instrumentation test APK compiles.
- All 16 trip-domain tests pass.
- Android lint completes with no errors; advisory warnings remain for dependency
  updates, legacy backup configuration, local resource directory and persistence style.
- Emulator repository tests pass: command replay, conflicting command-ID reuse,
  and persisted trip state after repository recreation.
- Emulator navigation test passes: preview disclosure, Trips and Earnings.
- Complete shared journey test passes: reset, accept, arrive, board three parties
  with their codes, start, complete, and verify KSh 4,200 sample gross fares.
- Final direct instrumentation run: **OK (5 tests)** in 99.331 seconds on API 37.
- Preview APK: 11,713,314 bytes. This debug build size is not a release performance metric.

## Verification limits

The first run encountered an Android test-runtime incompatibility; AndroidX Test
was updated using the [official release notes](https://developer.android.com/jetpack/androidx/releases/test).
The journey test now waits for asynchronous saves and their transient notifications
before tapping the next control. All five tests subsequently passed.

Visual verification remains incomplete: the host-rendered emulator captured blank
screens, while software graphics displayed the app with a persistent **System UI
isn't responding** dialog over it. Do not use these captures as clean product
screenshots or interpret the passing semantic interaction tests as visual approval.
Repeat on a stable emulator and physical fleet phones before release.

CI builds both app variants, compiles instrumentation tests, and runs unit tests
and lint. It does not execute the emulator tests. Physical-device, release
performance, process-death, accessibility, network recovery and field tests remain
outstanding. See [release readiness](RELEASE-READINESS.md).

## Reproduce

```sh
./gradlew :app:testDemoDebugUnitTest :app:lintDemoDebug :app:assembleDemoDebug :app:assembleProductionRelease :app:assembleDemoDebugAndroidTest
./gradlew :app:connectedDemoDebugAndroidTest
```

Use the generated HTML unit-test/lint reports under `app/build/reports`.
The preview APK is under `app/build/outputs/apk/demo/debug`.
Never upload the unconnected production artifact to Play.
