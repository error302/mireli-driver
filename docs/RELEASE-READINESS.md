# Release readiness

Status: 0.3 native connected account/trip/settlement features and local backend
validated, not store-ready. Live driver API still returns HTTP 404 on 6 October 2026.

## Current release gates

1. Backend write/deployment access; inspect the actual PostgreSQL baseline, take a
   backup, prove restoration and apply the additive migration on staging first.
2. Configure and verify private S3 storage/scanning, Africa's Talking SMS, reviewed
   driver terms/privacy and strong production administrator authentication.
3. Verify M-Pesa sandbox/provider callbacks, approved beneficiaries, reconciliation
   and live refunds. Live payouts default to disabled; unknown transfers cannot retry.
4. Staff document, support, no-show and dispatch review. Partial-party fares remain
   held pending a reviewed settlement decision.
5. Implement consented foreground GPS, push alerts/escalation and production
   background sync. The current saved action requires explicit online confirmation.
6. Fix inherited concurrent schedule/allocation generation; complete driver
   reassignment/cancellation and account deletion/retention workflows.
7. Physical fleet phones, offline/process-death/TalkBack/large-font and release
   startup/jank/battery/data measurements; Kiswahili and copy/resource extraction.
8. Confirm company/package ownership, signing, Kenyan operating requirements,
   published policies, store forms/assets, reviewer access and Play closed testing.

## Earlier foundation scope

## Implemented in this increment

- Kotlin / Compose application, API 36 target and API 26 minimum.
- Today, trips, trip detail, manifest, earnings and account surfaces.
- Explicitly labelled demo flavor with persisted sample trip progress.
- Accept → arrive → boarding/no-show → start → complete rules.
- Wrong-code, duplicate-boarding, stale-version, empty-departure and capacity guards.
- Separate production source set with no sample trips and no live sign-in claims.
- Lifecycle-aware state observation, lazy keyed lists and IO-dispatched persistence.
- The earlier 0.1 build had no network traffic; 0.3 adds Internet/network-state access.
- Shrunk unsigned production build configuration, CI and domain tests.
- Original Mireli Driver logo derived from supplied brand reference.

## Required before real drivers or Play submission

1. Audit and connect Mireli Web's authenticated backend.
2. Implement real identity, sessions, driver/vehicle approval and document handling.
3. Agree live boarding/stop/reassignment/no-show rules. Partial boarding and decline
   are now implemented and tested in the local preview, but have no server authority.
4. Connect a production outbox with server idempotency, account binding and background
   scheduling. The preview now has a durable SQLite command queue and atomic receipts;
   its local simulator is not production synchronization.
5. Implement approved GPS tracking, maps, Android permissions and foreground service.
6. Add push delivery, acknowledgement escalation and staffed support.
7. Integrate verified earnings/refunds/payouts from the shared ledger.
8. Publish reviewed privacy/terms/deletion flows; finish Kenyan operational approvals.
9. Extract all UI copy to resources, implement Kiswahili and dark-theme polish,
   and perform TalkBack/large-text/contrast testing.
10. Device/field testing, process-death tests, security review, restore drills.
11. Startup/jank/battery/data measurements and baseline-profile optimization.
12. Confirm permanent package name, business Play account, signing and store assets.
13. Complete Play forms, reviewer access, closed testing and production approval.

Version 0.2 adds native onboarding and encrypted device-local attachment drafts,
file-type/size checks, expiry/completeness checks and local deletion. It deliberately
does not label those files uploaded or the driver approved. On 4 October 2026,
the supplied live website's `/api/driver/me` returned HTTP 404. Live onboarding,
private storage, phone verification and audited compliance review remain blockers.
See [test guide](TEST-BUILD-0.2.md).

## Performance acceptance work

Measure release builds on actual fleet phones. Record cold/warm startup, frame
timing during list/manifest navigation, ANRs, memory, full-duty battery use, data
traffic and recovery after process death. Compare to an agreed device baseline;
do not label performance “Uber/Bolt standard” from compilation or unit tests.
The generated logo master is large; density-specific launcher assets and image
size optimization remain release work.

## Build commands

Use Android SDK 36 and build tools 36.0.0, JDK 21 (or compatible installed JDK),
and the checked-in Gradle wrapper.

    ./gradlew :app:assembleDemoDebug
    ./gradlew :app:testDemoDebugUnitTest :app:lintDemoDebug
    ./gradlew :app:assembleProductionRelease

Production release is unsigned and intentionally unconnected. Do not submit it.
Never sign production with the debug key. Store upload credentials outside Git.
