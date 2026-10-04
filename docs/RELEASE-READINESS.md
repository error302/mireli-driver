# Release readiness

Status: native Android foundation and offline synthetic preview, not store-ready.

## Implemented in this increment

- Kotlin / Compose application, API 36 target and API 26 minimum.
- Today, trips, trip detail, manifest, earnings and account surfaces.
- Explicitly labelled demo flavor with persisted sample trip progress.
- Accept → arrive → boarding/no-show → start → complete rules.
- Wrong-code, duplicate-boarding, stale-version, empty-departure and capacity guards.
- Separate production source set with no sample trips and no live sign-in claims.
- Lifecycle-aware state observation, lazy keyed lists and IO-dispatched persistence.
- No sensitive permissions or network traffic in the current build.
- Shrunk unsigned production build configuration, CI and domain tests.
- Original Mireli Driver logo derived from supplied brand reference.

## Required before real drivers or Play submission

1. Audit and connect Mireli Web's authenticated backend.
2. Implement real identity, sessions, driver/vehicle approval and document handling.
3. Agree complete boarding/stop/reassignment rules; current group boarding is all-or-none.
4. Add a durable database-backed command outbox with server idempotency. The preview's
   in-memory replay map is not production synchronization.
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
