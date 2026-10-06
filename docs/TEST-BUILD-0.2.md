# Mireli Driver 0.2.0 — testing and release status

Prepared 4 October 2026. This is a local test APK, not a production release.

## What changed

- Native driver onboarding: driver name, Kenyan phone formatting, vehicle plate,
  passenger capacity, licence class and vehicle ownership.
- Document checklist: identity, driving licence, PSV badge, good conduct, KRA PIN,
  PSV insurance, inspection, speed governor, logbook, vehicle permit, conditional
  lease/authorization and optional medical evidence.
- PDF/JPEG/PNG attachments through Android's document picker; 10 MB per file limit,
  content signature checks, randomized internal filenames and AES-GCM encryption
  using an Android Keystore key. Metadata is encrypted too, in no-backup storage.
- Date and completeness checks use the Nairobi calendar date. Actual expiry is
  entered rather than computed from unverified fixed validity periods. Corrupted
  or missing encrypted attachments fail the local completeness check.
- Local draft deletion removes this app's copies and its encryption key. It does
  not erase the original files selected from another app or storage provider.
- Partial boarding preserves boarded and no-show seat counts separately. Trip
  departure requires all passenger outcomes resolved and at least one boarded seat.
- Assignment decline requires a reason. Withdrawn/expired assignments are modeled
  as non-actionable; actual dispatch delivery is not implemented.
- Durable SQLite sample command queue: save before delivery, atomic state/receipt
  writes, replay IDs surviving repository recreation, and conflicting ID rejection.
- Offline simulation queues boarding only. Acceptance, decline, arrival, no-show,
  departure and completion require the simulator to be connected. Only one pending
  action is allowed at a time in this increment; additional actions wait for sync.
- Earnings show completed shared/charter counts, boarded seats and sample gross fares.
  No commission, payout or available balance is fabricated.

## Install and test

Install `Mireli-Driver-0.2.0-test.apk` from the deliverables directory on a test
Android phone (Android 8/API 26 or newer). It is debug-signed and uses package
`io.github.error302.mireli.driver.demo`, separate from the production placeholder.
Use sample documents only. Do not submit it to Google Play or use it for passengers.

1. On Today choose **Start driver onboarding**; alternatively open Account →
   **Driver onboarding & documents**.
2. Enter sample driver/vehicle details, acknowledge the local test-data notice,
   and press **Save driver & vehicle** before continuing.
3. Open Documents. Attach a sample PDF/image; enter and save expiry where requested.
   A company-provided sample PDF is included with the build for testing.
4. Choose Review application → Check application. Missing or expired evidence must
   produce actionable errors. Filling the checklist yields **Local checklist complete**,
   never submitted, verified or approved. No external service is contacted.
5. Close/reopen the app and confirm saved profile/attachments remain. Delete the
   local application, then reopen onboarding and confirm it is empty.
6. Reset sample trips from Account. Accept SGR-1042 and arrive. Board one of Amina's
   two seats using sample code 1042. The other seat must remain waiting; starting
   the journey must remain blocked until all parties are resolved.
7. Return to Account and enable Simulate offline. Reopen the manifest and record
   another valid boarding. Confirm **waiting to sync**, with confirmed counts unchanged.
8. Restart the app. The queued action must remain pending. Resume sample sync;
   it should be applied once. Retries with the same ID must not add another passenger.
9. Decline the remaining sample assignment with a reason. It must disappear from
   actionable next work and cannot then be accepted from its detail screen.

## Production blockers — still open

The owner confirmed `https://mireli-tau.vercel.app/`. A read-only HTTP request to
`/api/driver/me` returned **404** during this increment. Cofounder web main remains
`687ef7a0b7e14fc97628f578dfafe15a500cd0cc` and has no driver API directory.

The APK cannot safely submit identity documents until there is verified driver
authentication, a private storage service, authenticated upload/finalization APIs,
malware/content review and a compliance decision service. An upload or a completeness
check must never grant eligibility. The production flavor remains intentionally
unconnected and does not expose the local test-document collection flow.

Also outstanding: production account-bound sync/WorkManager, live assignments and
reassignment, authentic boarding receipts, GPS/permissions, push, staffed support,
settlement integration, server-side deletion/retention, reviewed privacy/terms,
localization/accessibility, fleet-device performance and Play release configuration.
Neither compiling nor passing local tests closes these operational requirements.

## Requirements and legal scope

The checklist is a configurable product requirement proposal, not a declaration
that every listed document is legally mandatory for every driver or vehicle.
In particular, medical evidence is optional here; owner authorization is conditional.
Vehicle class, route and operator approvals must be confirmed before the live
requirements catalogue is frozen. Review actual expiry and issuing-authority evidence.

The PSV regulations identify driver identity/licence/good-conduct and vehicle
roadworthiness/ownership/insurance evidence in operator licensing requirements.
They do not make our client-side checklist a legal clearance mechanism.
[Kenya Law — PSV regulations](https://new.kenyalaw.org/akn/ke/act/ln/2014/23/eng@2014-03-14)

Private evidence handling, retention and data-subject rights must be designed with
the operator's privacy review. [ODPC data-subject rights](https://www.odpc.go.ke/rights-of-a-data-subject/)

## Validation

The 0.2 source passes 29 domain tests (21 journey rules, 8 onboarding policy tests),
Android lint (0 errors, 9 warnings), demo APK compilation, instrumentation-package
compilation and shrunk unsigned production compilation. Device instrumentation
results are recorded separately when available; compilation is not a device pass.
Previous 0.1 emulator results do not prove this increment.

Fresh verification on 6 October 2026 rebuilt the latest source and again passed
all 29 domain tests and lint (0 errors, 9 warnings). APK signature verification
passed, and the app plus instrumentation APK both installed successfully.
The direct Android instrumentation runner passed **12 tests in 107.604 seconds**
on the API 37 emulator: full shared journey, onboarding form/review errors, encrypted
draft restart/deletion, tamper rejection, file signatures/size limits, offline
boarding restart/replay, acceptance refusal offline, and command-ID replay/conflict.

The Gradle connected-test harness failed before reporting app tests; direct
instrumentation was used successfully. This harness failure remains a tooling
issue, not a clean connectedDemoDebugAndroidTest result. Physical-device,
document-picker/provider, actual process-death and live-backend tests remain open;
repository recreation is narrower than killing the Android process mid-upload.

Delivered APK: `Mireli-Driver-0.2.0-test.apk`, 11,853,494 bytes. SHA-256:
`a2cc060853519aa110f9f6f3be7e8a803d9e2338d8d6d0ff11220a81aee67950`.
The live driver endpoint was rechecked on 6 October and still returned 404.

No live document upload, OTP, compliance approval, payment, Vercel deployment,
database migration or Play submission was performed.
