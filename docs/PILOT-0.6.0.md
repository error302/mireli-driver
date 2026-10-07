# Mireli Driver 0.6.0 design pilot

Build date: 8 October 2026. Internal Android testing build, not a Play Store release.

## Design applied

The supplied twelve screen concepts informed the hierarchy, rounded surfaces, prominent actions, route information, account overview and earnings presentation. The existing original blue/teal M-and-road logo is unchanged. Mireli keeps its brand palette, Android system bars and native Compose controls instead of reproducing iPhone hardware or fictional reference content.

- Sign-in remains the first screen. A compact original-logo header and clear phone form lead to the existing six-digit SMS challenge, resend timer and change-number action.
- Home is a new signed-in overview with real approval status, returned active/recent trip counts, onboarding, account, earnings and support actions. Missing trip data shows a dash. No rating, incentive, demand hotspot or online-availability claim is fabricated.
- Accepted journeys can show the existing MapLibre navigation surface on Home as well as Trips. Foreground location disclosure, permission handling, assigned stops and authenticated routing remain in force.
- Trip cards emphasize assignment and departure details, primary actions and completion status. Passenger booking-code boarding, capacity validation, no-show reasons, version checks and confirmation dialogs remain intact.
- Account/profile and document submission use consistent headings and progress indicators. The application status and reviewer decision still determine eligibility.
- Earnings shows only backend settlement totals with status `completed` as confirmed paid earnings. The backend maps unverified completed records to `needs_review`; queued/processing/review amounts are excluded from the headline. The hero is a statement total, not a wallet balance or a new cash-out capability.
- Light, dark and system preferences remain saved on the phone. The original logo retains its white image canvas in dark mode.

No live data, database records or production credentials were created by this redesign. Reference names, fares, phone numbers, Nairobi routes, passengers and vehicle details were not inserted into the app. UI test fixtures are in-memory inputs inside the test APK only. Existing demo data remains restricted to the separately named demo flavor.

## Build and verification

The final build passed all four flavor unit-test suites, pilot lint, pilot/demo APK and instrumentation compilation, staging instrumentation compilation and optimized production release assembly. Production release remains unsigned. Pilot APKs retain the debug testing certificate.

The initial API 37 emulator run passed both new Home tests and both navigation tests, but its fresh-start assertion failed before the expected sign-in header appeared. An independent launch encountered a bind-application startup ANR; the same emulator was repeatedly crashing its Android UWB hardware service. Review found incorrectly encoded punctuation in the newly edited header and two other UI files. Those files were normalized to strict UTF-8, and all Kotlin sources were checked with strict decoding. The fresh test also clears its own test session and waits explicitly for the sign-in form. The emulator was restarted before the isolated repeat. The isolated final `FreshPilotTest` passed after correction and emulator boot completion (`OK (1 test)`, 32.726 seconds). It verifies the first visible sign-in form, no sample trips, light/dark switching and persistence after recreation, and exactly one Appearance action. The two in-memory Home tests and two navigation tests passed in the preceding run. There are 35 passing unit tests per flavor (140 total); the corrected pilot unit suite and lint were rerun successfully. Full staging sign-in/document/payout workflows were not rerun against a live backend in this design change.

## Delivery

The installable pilot uses `io.github.error302.mireli.driver.pilot`, version `0.6.0-pilot`, code 9. Install it over the previous pilot APK to preserve normal app settings. The delivered [testing APK](../../deliverables/Mireli-Driver-0.6.0-pilot.apk) passed APK Signature Scheme v2 verification with the Android Debug certificate. SHA-256: `69a55d6c149d83eaebab13956f15330d428b2d3d75853a205891167d96230464`. The [checksum file](../../deliverables/SHA256SUMS-0.6.0.txt) accompanies it.

App-window screenshots captured by the Compose test: [light sign-in](../../deliverables/Mireli-Driver-0.6.0-light.png) and [dark sign-in](../../deliverables/Mireli-Driver-0.6.0-dark.png). These capture the actual application window and API availability response. They exclude other Android windows. Whole-display captures exposed an emulator System UI ANR dialog, so physical-device/system interaction remains a release gate. This is not a simulated working SMS service. Native-library stripping/package-size optimization and physical-device road navigation remain unverified.

## Remaining live dependencies

This change improves the Android UI. It does not deploy or activate the driver API, SMS, private document storage, authenticated route hosting, beneficiary review, real M-Pesa transfers or Play signing. Those remain deployment/release prerequisites documented in the production readiness checklist. The live status endpoint `https://mireli-tau.vercel.app/api/v1/driver/status` returned HTTP 404 during this verification. A disabled service action remains explicit until the backend confirms availability. Production-ready end-to-end onboarding and road/payout testing cannot be claimed from these local UI and build results.
