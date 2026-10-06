# Mireli Driver

Native Android driver application for Mireli's Mombasa SGR transfers.
Kotlin, Jetpack Compose, Android API 36 target, API 26 minimum.

## Current status

**0.3.0 is a tested development build; the shared backend is not deployed yet.**
The connected native screens implement phone verification, private document uploads,
application submission and reviewer feedback, assigned trips, passenger-code boarding,
partial boarding/no-show reports, journey completion, settlement statements,
beneficiary review and support cases. Sensitive sessions and uncertain trip actions
are encrypted on the phone. Every trip command has a server receipt and version check.

The separate local backend uses the existing passenger Driver/Trip/Booking/ledger
records. Its PostgreSQL migration and concurrent command/settlement tests passed on
an isolated database. No real funds, SMS or identity documents were used.
Live storage, SMS, M-Pesa, administrator authentication and deployment remain required.
The demo flavor preserves the offline sample journey for testing on a physical phone.

Passenger website: https://mireli-tau.vercel.app/

![Mireli Driver logo](assets/brand/mireli-driver-logo.png)

## Build

Open this repository in Android Studio. Install Android SDK 36 and build tools
36.0.0. Use JDK 21 or another Gradle-compatible JDK. Configure local.properties
with your local SDK path; never commit that file.

```sh
./gradlew :app:assembleDemoDebug
./gradlew :app:testDemoDebugUnitTest :app:lintDemoDebug
./gradlew :app:connectedDemoDebugAndroidTest
./gradlew :app:assembleProductionRelease
./gradlew :app:assembleStagingDebug :app:assembleStagingDebugAndroidTest
```

On Windows use gradlew.bat. The preview APK is at
app/build/outputs/apk/demo/debug/app-demo-debug.apk. The production release is
unsigned and targets the official website. The driver API still returned 404 on
6 October 2026; do not submit this build to Play. Staging uses the Android emulator's
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
- [Build validation and test limits](docs/BUILD-VALIDATION.md)
- [Brand provenance](assets/brand/README.md)

The permanent application ID, privacy/terms, business verification, backend,
maps/location, support coverage, payments and field-test evidence must be
completed before commercial use. No parity with Uber/Bolt is claimed.
