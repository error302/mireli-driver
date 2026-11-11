# Mireli Driver

Native Android driver application for Mireli's Mombasa SGR transfers.
Kotlin, Jetpack Compose, Android API 36 target, API 26 minimum.

## Current status

**0.9.3 targets the live production backend at `https://mireli.online`.**
The domain cutover from legacy preview hosts to `https://mireli.online` is complete and verified
end-to-end behind Cloudflare (Full strict SSL, Caddy reverse proxy on EC2 in `eu-north-1`).
The live driver API status route `https://mireli.online/api/v1/driver/status` responds with
`apiVersion: "v1"`, `simulation: false`, and fail-closed security flags.

The connected native screens implement Brevo email verification, private document uploads,
application submission and reviewer feedback, assigned trips, passenger-code boarding,
partial boarding/no-show reports, journey completion, settlement statements,
beneficiary review and support cases. Sensitive sessions and uncertain trip actions
are encrypted on the phone. Every trip command has a server receipt and version check.

The shared backend uses the existing passenger Driver/Trip/Booking/ledger
records. The pilot and production flavors connect to `https://mireli.online`.
They use the API status to show whether email sign-in, applications and in-app routing are open.
The separate demo flavor provides an offline preview. See the [first-driver release gates](docs/FIRST-DRIVER-PILOT.md)
for the live service status and the remaining onboarding and paid-trip checks.

The driver Home and Trips screens use a scheduled-transfer board with local departure time,
route stops and the next available action. A trip change is retained until the app receives a
matching server receipt or a definite rejection. The app offers an account-deletion request
under Account and routes it through Mireli Support with a case reference.

The [0.9.1 design direction](docs/DESIGN-DIRECTION-0.9.1.md) records the driver-app
references, Mireli palette, route motif, shared controls and Android performance scope.

Use the **Dark mode** switch, or tap **Appearance** for **Light**, **Dark** or
**System**. New installs default to the original light palette. The choice is saved on the
phone, survives app restarts and applies to trips, onboarding, sign-in, earnings,
support, dialogs and system bars. Appearance is available before sign-in.

Connected trip cards now include an in-app MapLibre map with OpenStreetMap data,
foreground-only location permission after an explicit disclosure, assigned-stop
selection, route geometry, written maneuvers, estimated arrival and off-route
recalculation when Mireli's authenticated Valhalla route service is configured.
Google Maps remains an explicit fallback. In-app navigation is currently fail-closed
until Valhalla route service is configured. See the [open-source maps and routing setup](docs/OPEN-SOURCE-NAVIGATION.md),
the [production-readiness checklist](docs/PRODUCTION-READINESS-CHECKLIST-2026-10.md),
the [0.4.2 verification and launch gates](docs/PILOT-0.4.2.md), the
[Kenya legal and UX review](docs/KENYA-LEGAL-AND-UX-REVIEW-2026-10.md), and the
[0.6.0 design verification record](docs/PILOT-0.6.0.md).

Passenger website: https://mireli.online

![Mireli Driver logo](assets/brand/mireli-driver-logo.png)

## Build

Open this repository in Android Studio. Install Android SDK 36 and build tools
36.0.0. Use JDK 17/21. Configure `local.properties` with your local SDK path; never commit that file.

```sh
./gradlew :app:assemblePilotDebug :app:testPilotDebugUnitTest :app:lintPilotDebug
./gradlew :app:assemblePilotDebugAndroidTest
./gradlew :app:assembleDemoDebug
./gradlew :app:testDemoDebugUnitTest :app:lintDemoDebug
./gradlew :app:connectedDemoDebugAndroidTest
./gradlew :app:bundleProductionRelease
./gradlew :app:assembleStagingDebug :app:assembleStagingDebugAndroidTest
```

On Windows use `gradlew.bat`. The preview APK is at
`app/build/outputs/apk/demo/debug/app-demo-debug.apk`. The pilot APK is at
`app/build/outputs/apk/pilot/debug/app-pilot-debug.apk`. The production release targets
`https://mireli.online`. Its bundle is at
`app/build/outputs/bundle/productionRelease/app-production-release.aab`.
Staging uses the Android emulator's `10.0.2.2:3000` bridge to a local backend in demo mode
(`DRIVER_SERVICE_TEST=true`). If another service owns port 3000, publish the isolated backend
on a different loopback port and pass the matching `-PMIRELI_STAGING_PORT=3100` (example) to Gradle.

For owner signing, set `MIRELI_UPLOAD_KEYSTORE` to the absolute path of the Mireli upload
keystore and provide `MIRELI_UPLOAD_STORE_PASSWORD`, `MIRELI_UPLOAD_KEY_ALIAS` and
`MIRELI_UPLOAD_KEY_PASSWORD` in the build process environment. Set all four together.
Gradle signs the production release with that key. With the variables unset, the bundle
is unsigned. Keep the keystore and passwords in the owner's release environment.

## Architecture

- `domain/`: immutable models and guarded, versioned trip transitions.
- `data/DriverRepository`: integration seam shared by the UI.
- `src/demo/`: local synthetic repository, fixtures and persistence.
- `DriverServicesViewModel` / `DriverApi`: connected account, trip and money screens.
- `src/production/`: no sample trips; the connected service UI is the entry point.
- `DriverViewModel`: lifecycle state and serialized user actions.
- `ui/`: Compose screens, keyed lazy lists and confirmation dialogs.

The demo never performs payments, collects identity documents or tracks location.
Client rules provide feedback; the live backend enforces every rule.
The demo persists command IDs, receipts and pending boarding in SQLite. Account
offers an offline simulator; queued boarding is not counted until sync confirms
it. This is a local test authority, not a production network outbox or WorkManager service.

## Project documents

- [Full product and release plan](MIRELI-DRIVER-MASTER-PLAN.md)
- [Website integration findings](docs/WEB-INTEGRATION.md)
- [Cofounder driver comparison and adoption plan](docs/COFOUNDER-DRIVER-COMPARISON.md)
- [Version 0.2 test guide and production blockers](docs/TEST-BUILD-0.2.md)
- [Release blockers and performance work](docs/RELEASE-READINESS.md)
- [Kenya legal claims and driver UX review](docs/KENYA-LEGAL-AND-UX-REVIEW-2026-10.md)
- [Build validation and test limits](docs/BUILD-VALIDATION.md)
- [Brand provenance](assets/brand/README.md)

The permanent application ID, privacy/terms, business verification, backend,
Valhalla/tile hosting, location field tests, support coverage, payments and field-test evidence must be
completed before commercial use. No parity with Uber/Bolt is claimed.
