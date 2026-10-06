# Mireli Driver

Native Android driver application for Mireli's Mombasa SGR transfers.
Kotlin, Jetpack Compose, Android API 36 target, API 26 minimum.

## Current status

**0.2.0 is a working test build, not a live service or Play-ready release.**
It includes Today, Trips, assignment details, boarding/no-show confirmation,
trip progression, partial-party boarding, assignment decline reasons, shared/charter
sample earnings and driver onboarding screens. The onboarding flow saves encrypted
document drafts locally, checks expiry/completeness and supports deleting its copies.
It does not upload documents, verify a phone, submit an application or approve a driver.
Sample progress
is saved across restarts. Production builds contain no demo repository and block
live operations until the existing website backend is connected.

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
```

On Windows use gradlew.bat. The preview APK is at
app/build/outputs/apk/demo/debug/app-demo-debug.apk. The production release is
unsigned and unconnected; do not submit it to Play.

## Architecture

- domain/: immutable models and guarded, versioned trip transitions.
- data/DriverRepository: integration seam shared by the UI.
- src/demo/: local synthetic repository, fixtures and persistence.
- src/production/: fail-closed adapter pending real authenticated integration.
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
