# Mireli Driver 0.4.2 pilot

## What changed

The signed-out screen now puts the driver phone form first and presents a compact,
route-specific SGR onboarding explanation beneath the useful controls. It keeps the
Mireli teal/navy palette and the existing light/dark appearance preference. The
service panel distinguishes an unreachable driver service from paused new-driver
applications, and says plainly that checking service status does not send an SMS.

The driver API contract now treats existing-driver phone sign-in and new application
intake as separate states. An existing driver can authenticate while recruitment is
paused. A verified unknown number cannot create a new driver record unless the
server has explicitly opened application intake. This backend change is in source
only; it has not been deployed to the production website.

## Pilot APK

- File: `deliverables/Mireli-Driver-0.4.2-pilot.apk`
- Package: `io.github.error302.mireli.driver.pilot`
- Version: `0.4.2-pilot` (version code 7)
- Android range: API 26 and newer; target API 36
- Build: debug-signed internal pilot, not a Play Store upload
- SHA-256: `160426B4834236A59B082ECFFEF188BBDEA07335D2D1A63C036E6A71EEF24CA1`

## Verification

- Android `:app:testPilotDebugUnitTest`: passed.
- Android `:app:lintPilotDebug`: passed.
- Android `:app:assemblePilotDebug` and `:app:assemblePilotDebugAndroidTest`: passed.
- Backend `npm test`: 38 tests passed; `npm run typecheck` and `npm run lint` passed.
- APK installed over the existing pilot on the connected emulator with `adb install -r` succeeding.
- Direct instrumentation did not complete: the emulator repeatedly crashed its UWB service and raised an app-start ANR after 60 seconds, before any test result. The device was rebooted once, but Android had not completed startup during the follow-up check.
- No production SMS, driver records, documents, payment or payout was created.

## Still blocked from production

The production host returned HTTP 404 for `/api/v1/driver/status` on 7 October
2026. Therefore the production app cannot reach driver sign-in or send a real OTP.
The backend source change is not deployed, production registration intentionally
fails closed until verified reviewer authentication, document storage/scanning,
SMS, published policy URLs and operating approvals are ready, and no Play release
signing key or Play Console release has been used. See
[the Kenya legal and UX review](KENYA-LEGAL-AND-UX-REVIEW-2026-10.md) for the
playbook corrections and release gates.

This build verifies the Android UI and local source checks. It does not prove live
authentication, onboarding, document review, emergency response, payment settlement,
legal compliance or Play review approval.
