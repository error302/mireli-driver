# Mireli Driver

Native Android driver application for Mireli's Mombasa SGR transfers.
Kotlin, Jetpack Compose, Android API 36 target, API 26 minimum.

## Current status

**0.4.0 is a fresh pilot build; live onboarding is not enabled yet.**
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

Connected trip cards open Google Maps directions to the route pickup, destination
or a selected stop, respecting inbound/outbound travel. This is external navigation;
embedded maps, continuous location sharing and push dispatch are not implemented.
See [0.4.0 verification and launch gates](docs/PILOT-0.4.0.md).

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
