# Mireli Driver

Native Android driver application for Mireli's Mombasa SGR transfers.
Kotlin, Jetpack Compose, Android API 36 target, API 26 minimum.

## Current status

**0.6.0 adds the reference-inspired driver UI; live onboarding still requires the deployed service.**
The connected native screens implement phone verification, private document uploads,
application submission and reviewer feedback, assigned trips, passenger-code boarding,
partial boarding/no-show reports, journey completion, settlement statements,
beneficiary review and support cases. Sensitive sessions and uncertain trip actions
are encrypted on the phone. Every trip command has a server receipt and version check.

The separate local backend uses the existing passenger Driver/Trip/Booking/ledger
records. Its PostgreSQL migration and concurrent command/settlement tests passed on
an isolated database. No real funds, SMS or identity documents were used.
Live storage, SMS, M-Pesa, administrator authentication and deployment remain required.
The pilot flavor opens the real service sign-in with no sample drivers or journeys.
The separate demo flavor remains available only for explicit offline demonstrations.

Use the **Dark mode** switch, or tap **Appearance** for **Light**, **Dark** or
**System**. New installs default to the original light palette. The choice is saved on the
phone, survives app restarts and applies to trips, onboarding, sign-in, earnings,
support, dialogs and system bars. Appearance is available before sign-in.

Connected trip cards now include an in-app MapLibre map with OpenStreetMap data,
foreground-only location permission after an explicit disclosure, assigned-stop
selection, route geometry, written maneuvers, estimated arrival and off-route
recalculation when Mireli's authenticated Valhalla route service is configured.
Google Maps remains an explicit fallback. The live API currently returns 404 and
the Valhalla service is not configured, so this pilot does not yet have live route
instructions. It does not continuously upload driver location or request background
location permission. See the [open-source maps and routing setup](docs/OPEN-SOURCE-NAVIGATION.md),
the [production-readiness checklist](docs/PRODUCTION-READINESS-CHECKLIST-2026-10.md),
the [0.4.2 verification and launch gates](docs/PILOT-0.4.2.md), the
[Kenya legal and UX review](docs/KENYA-LEGAL-AND-UX-REVIEW-2026-10.md), and the
[0.4.1 sign-in diagnosis](docs/PILOT-0.4.1.md), as well as the
[0.4.0 implementation record](docs/PILOT-0.4.0.md).

The [0.6.0 design verification record](docs/PILOT-0.6.0.md) describes the refreshed sign-in, approval-gated Home, account, earnings and journey screens. The original logo, light/dark choice and real-data-only pilot are retained.

The [0.5.0 pilot APK and verification record](docs/PILOT-0.5.0.md) include the
debug-signed test build, emulator results, checksum and remaining release gates.

Passenger website: https://mireli-tau.vercel.app/

![Mireli Driver logo](assets/brand/mireli-driver-logo.png)

## Build

Open this repository in Android Studio. Install Android SDK 36 and build tools
36.0.0. Use JDK 21 or another Gradle-compatible JDK. Configure local.properties
with your local SDK path; never commit that file.

```sh
./gradlew :app:assemblePilotDebug :app:testPilotDebugUnitTest :app:lintPilotDebug
./gradlew :app:assemblePilotDebugAndroidTest
./gradlew :app:assembleDemoDebug
./gradlew :app:testDemoDebugUnitTest :app:lintDemoDebug
./gradlew :app:connectedDemoDebugAndroidTest
./gradlew :app:assembleProductionRelease
./gradlew :app:assembleStagingDebug :app:assembleStagingDebugAndroidTest
```

On Windows use gradlew.bat. The preview APK is at
app/build/outputs/apk/demo/debug/app-demo-debug.apk. The production release is
unsigned and targets the official website. The driver API status route returned 404 on
7 October 2026; do not submit this build to Play. Staging uses the Android emulator's
10.0.2.2 bridge to the isolated web preview on port 3100; it is not a phone deployment.

## Architecture

- domain/: immutable models and guarded, versioned trip transitions.
- data/DriverRepository: integration seam shared by the UI.
- src/demo/: local synthetic repository, fixtures and persistence.
- DriverServicesViewModel / DriverApi: connected account, trip and money screens.
- src/production/: no sample trips; the connected service UI is the entry point.
- DriverViewModel: lifecycle state and serialized user actions.
- ui/: Compose screens, keyed lazy lists and confirmation dialogs.

The demo never performs payments, collects identity documents or tracks location.
Client rules provide feedback; the live backend must enforce every rule again.
The demo now persists command IDs, receipts and pending boarding in SQLite. Account
offers an offline simulator; queued boarding is not counted until sample sync confirms
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
