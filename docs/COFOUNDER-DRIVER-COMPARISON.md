# Mireli Driver — cofounder comparison and adoption plan

Reviewed 4 October 2026. This is a source audit, not a claim of production parity.

## Conclusion

Keep our buildable Compose application as the Android foundation. Adopt and refine
the cofounder's API vocabulary, richer operational models and compliance workflow.
Do not replace our app wholesale or assume his declared endpoints are deployed.
Most of these capabilities were already required in our master plan; the gap is
implementation in our current preview, not the absence of product planning.

The two repositories complement each other: ours has an executable demo journey
and test evidence; his has a broader data-layer skeleton and compliance UI source.
Neither currently provides a verified live driver-to-passenger service.

## Exact evidence and scope

| Item | Inspected state |
| --- | --- |
| Cofounder driver | [demitriosojwang/mireli-](https://github.com/demitriosojwang/mireli-/tree/e3bde4a3bb503cc2865665553dd01d8a2085624b), main commit `e3bde4a3bb503cc2865665553dd01d8a2085624b` |
| Our Android | Local `repository`, HEAD `2ed6dd996966b8e9e5cc54faf290ac479c0b3fd5`, plus pre-existing integration document edits |
| Shared web reference | Local aligned `msafiri-web`, based on web commit `687ef7a0b7e14fc97628f578dfafe15a500cd0cc`; no driver API directory in that copy |
| Access | Driver repository is public; existing GitHub authentication worked. The PAT supplied in chat was not used or stored |
| Validation this review | Source/file inventory; focused TypeScript check of `console/lib/api.ts`; official ODPC and Android documentation checks |
| Not performed | Cofounder Android build, console deployment, live API requests, database migration, legal certification or fresh performance benchmarks |

Our prior Android checks are recorded in [BUILD-VALIDATION.md](BUILD-VALIDATION.md):
16 domain tests and 5 emulator tests. Those are previous recorded results, not
new tests from this audit. His README explicitly labels his project uncompiled
and untested. His compliance document says the backend additions were lost and
need rebuilding; that account of their loss was not independently investigated.

## Missing implementation to adopt

Priority P0 means required before real operational use; P1 means the next product
increment; P2 means optional until the business approves the feature.

| Priority | Capability in his source/design | Our current implementation | Adoption decision and proof needed |
| --- | --- | --- | --- |
| P0 | Retrofit API, typed DTOs, stable error codes, device bearer sessions | Production repository intentionally rejects commands; no real sign-in or networking | Agree one versioned driver API; implement verified OTP, revocation and driver-only authorization. Test wrong-driver access and expired sessions |
| P0 | Pending/accepted/declined/withdrawn/expired assignments, version and decline reason | Accept exists, but assignment acceptance is combined with trip stage; no decline/expiry/revocation | Separate assignment lifecycle from trip lifecycle. Test stale acceptance, withdrawal and reassignment; decline must notify dispatch through the backend |
| P0 | Train event, report time, departure time, direction and ordered stops | Display-oriented report time and origin/destination | Use authoritative timestamp fields and Africa/Nairobi rendering; test midnight, return journeys and changed train times |
| P0 | Room action queue and WorkManager replay | Demo state persistence, with command replay memory limited to the process | Implement a durable outbox before sending. Show pending/confirmed/failed separately. Test process death, timeout-after-server-success and duplicate delivery |
| P0 | Partial boarding counts, e.g. two of four passengers | A party is entirely WAITING, BOARDED or NO_SHOW | Model boarded and unresolved seat counts; preserve our code verification and state guards. Test partial parties, remaining no-shows and excess-count rejection |
| P0 | Manifest stops, pickup/drop-off labels, approved contact, home pickup/address, cache expiry | Small synthetic passenger model: ID, name, seats, code, boarding state | Minimize live data, enforce ownership server-side, purge expired/revoked cache, mask contact when no longer needed |
| P0 | Eligibility reasons, document review/expiry states, availability intent | Account readiness placeholders | Add documents/vehicle screens and backend eligibility checks. Upload must not grant approval. Handle a mid-trip eligibility change through safe dispatch procedures |
| P0 | Compliance queue with individual review, rejection reason, history and expectedVersion | No equivalent operational review workflow in our Android or aligned web copy | Adapt console workflow into the shared admin system, with restricted roles, private evidence storage and audited decisions |
| P1 | Gross, commission, surcharge, net, paid/queued/failed settlements, shared/charter split, ride counts | Explicitly synthetic gross fares and unconnected payout message | Build a read-only statement from reconciled finance records; test refunds and failed/retried payouts. Keep money movement out of the driver app |
| P1 | Driver notices and actionable next assignment DTOs | Local next-trip guidance, no remote inbox | Add server notices, read state, refresh and assignment invalidation; define push delivery separately because it is absent in his code |
| P1 | Resource-based UI strings and version catalog | Most Compose text is inline; versions largely declared in build files | Extract strings and add reviewed Kiswahili translations. His repo does not contain a `values-sw` translation despite a build-file comment suggesting it does |
| P2 | Charter subscription interest request and entitlement DTOs | Charter trips supported in the demo; no subscription | Keep this distinct from booking a charter. Only add after business terms, entitlement rules and payment-policy treatment are agreed; no automatic charging |

Relevant upstream source groups, pinned to the reviewed commit:

- [API interface](https://github.com/demitriosojwang/mireli-/blob/e3bde4a3bb503cc2865665553dd01d8a2085624b/android/app/src/main/java/co/ke/mireli/driver/data/remote/MireliDriverApi.kt)
- [Assignment and manifest DTOs](https://github.com/demitriosojwang/mireli-/blob/e3bde4a3bb503cc2865665553dd01d8a2085624b/android/app/src/main/java/co/ke/mireli/driver/data/remote/dto/TripDtos.kt)
- [Driver/eligibility DTOs](https://github.com/demitriosojwang/mireli-/blob/e3bde4a3bb503cc2865665553dd01d8a2085624b/android/app/src/main/java/co/ke/mireli/driver/data/remote/dto/DriverDtos.kt)
- [Earnings DTOs](https://github.com/demitriosojwang/mireli-/blob/e3bde4a3bb503cc2865665553dd01d8a2085624b/android/app/src/main/java/co/ke/mireli/driver/data/remote/dto/EarningsDtos.kt)
- [Compliance review UI](https://github.com/demitriosojwang/mireli-/blob/e3bde4a3bb503cc2865665553dd01d8a2085624b/console/components/review-sheet.tsx)

## Problems to fix before borrowing code

Paths below are relative to his repository. These findings concern the exact
snapshot, not an uninspected local branch or deployed service.

1. **The outbox is written too late.** `BoardingRepository.kt:63` calls the API;
   lines 91–95 enqueue only after IOException. A killed process between sending
   and saving loses its recovery record, contrary to `MireliDatabase.kt` comments.
   Persist the full immutable command before the first attempt, then reconcile
   its server result using the same ID. Preserve the original observedAt too.
2. **Confirmation can be invented from an HTTP status.**
   `BoardingRepository.kt:65–75` accepts any successful response and falls back to
   the requested count when its body is absent; it does not require `ok=true`.
   The worker likewise marks a row done on HTTP success. Require a valid,
   action-bound acknowledgment and authoritative counts. A malformed response is
   an unknown result, not proof that boarding succeeded.
3. **Retry classification differs between immediate and background paths.** The
   repository treats server errors as terminal except IOException; the worker
   treats all 4xx, including 401 and 429, as terminal. Use one response policy:
   authentication pauses pending work for the same driver; rate limits respect
   Retry-After; retryable server/network failures retain the action ID; definite
   business refusals are surfaced for resolution.
4. **Logout cannot send its bearer token as written.**
   `AuthInterceptor.kt:35–40` skips Authorization for every `/api/driver/auth`
   request, while logout uses that same route and has no token field in its DTO.
   Design authenticated revocation explicitly; local token deletion alone does
   not revoke a server session. The interceptor also does not implement its
   comment's claimed 401 sign-out behavior.
5. **Queued work is not scoped to its owning driver.** `PendingActionEntity` has
   no driver/account owner, and token clearing does not quarantine queued rows.
   Prevent an old driver's actions replaying under a new driver's credentials on
   a shared phone. Add account binding, sign-out behavior, retention and restart tests.
6. **Sync is not wired into an app lifecycle.** `schedulePeriodic` is defined but
   has no caller in the snapshot; foreground/immediate replay is described but
   absent. Add unique one-time sync and a periodic safety net, concurrency control
   and proper worker initialization. Periodic work has a 15-minute minimum and
   inexact execution; it does not guarantee immediate execution on reconnection.
   [Android WorkManager guidance](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
7. **The Android skeleton has concrete missing pieces.** Manifest references
   `.MireliDriverApp`, `.MainActivity`, `.service.TripTrackingService`, XML backup/
   network configurations, launcher resources and a theme absent from the file
   inventory. No Gradle wrapper is present. `AuthInterceptor.kt` references
   IOException without importing java.io.IOException. `BoardingRepository.kt:112`
   uses TYPE_BOARDING without qualifying/importing the worker companion constant.
   These are source findings; no full build was attempted.
8. **The console has a verified type error.** A focused strict TypeScript check
   of `console/lib/api.ts` reports TS2322 at line 73: unknown body is passed to
   RequestInit.body. Give the wrapper its own JSON-body input type and serialize
   exactly once. Also reject invalid success payloads, rather than returning an
   empty object for non-JSON 2xx. This was not a full console compilation.
9. **A console interface is not an operational compliance service.** The review
   queue calls `/api/admin/onboarding`; backend approval/authorization cannot be
   demonstrated from this repo. `/offers` and `/register` are navigation targets
   without pages. Document upload is described as a placeholder. Keep these visibly
   unavailable until backed by authenticated, tested services.
10. **Contract details conflict or are incomplete.** The API markdown has a broken
    code fence around assignment decisions. It says manifests contain no fare
    data while its payload includes homeSurcharge. Boarding has method="qr"/"code"
    but no submitted proof field; the server must define how proof is verified,
    or how an audited manual exception works. Our demo code-check must not vanish
    during migration. No trip start/complete API is declared in his interface.

Primary implementation evidence:
[boarding repository](https://github.com/demitriosojwang/mireli-/blob/e3bde4a3bb503cc2865665553dd01d8a2085624b/android/app/src/main/java/co/ke/mireli/driver/data/repository/BoardingRepository.kt),
[sync worker](https://github.com/demitriosojwang/mireli-/blob/e3bde4a3bb503cc2865665553dd01d8a2085624b/android/app/src/main/java/co/ke/mireli/driver/data/sync/ActionSyncWorker.kt),
[auth interceptor](https://github.com/demitriosojwang/mireli-/blob/e3bde4a3bb503cc2865665553dd01d8a2085624b/android/app/src/main/java/co/ke/mireli/driver/data/remote/AuthInterceptor.kt),
[console client](https://github.com/demitriosojwang/mireli-/blob/e3bde4a3bb503cc2865665553dd01d8a2085624b/console/lib/api.ts).

## Shared decisions to settle in one contract

- **One backend and database:** extend Mireli Web with authenticated driver APIs;
  Room is only an on-device cache/outbox. Do not build a competing booking ledger.
- **One API specification:** publish OpenAPI plus fixtures in the backend repo,
  version it before the first live app release, and run client/server contract tests.
  His endpoint shapes are proposals until backend code and staging tests prove them.
- **Independent states:** Assignment (pending/accepted/declined/withdrawn/expired),
  Trip (scheduled/locked/departed/completed/cancelled), Boarding counts, and Sync
  status must not be collapsed into our current TripStage enum.
- **Money units:** ours uses Long minor units; his DTO comment says Int KSh and
  also calls them minor units. Agree `currency=KES` and explicit `amountMinor`
  fields, with checked conversion from the existing whole-shilling ledger. Never
  copy ambiguous numbers into the app. Do not infer net pay from gross fares.
- **Command receipts:** bind idempotency to driver, assignment, command type and
  payload; reject reuse with a different payload. Both foreground and worker must
  consume the same receipt. Test concurrent requests in PostgreSQL.
- **Acceptance semantics:** his document says acceptance locks a trip and freezes
  seats. Confirm this business rule explicitly: driver acknowledgment and booking
  sales cutoff need not be the same event. Do not silently change passenger sales.
- **No-show authority:** our demo permits immediate party no-show marking at pickup;
  production must enforce the shared cutoff/contact/grace policy, account for pending
  offline boarding and partial parties, and require dispatch exceptions when needed.
- **Security boundary:** enforce driver/vehicle eligibility and assignment ownership
  on the server; unknown enum values, absent authorization fields and expired cache
  must not default into actionable work.
- **Identity and branding:** our placeholder package is `io.github.error302.mireli.driver`;
  his is `co.ke.mireli.driver`. Choose one company-owned final application ID and
  signing owner before Play publication. Reconcile our blue/teal driver branding
  with his navy/orange web/console theme through shared tokens and approved assets.
- **Admin deployment:** prefer the existing authenticated admin application for
  the compliance workflow. If a separate console is retained, specify its origin,
  cookie/proxy/CSRF boundaries and deployment ownership; credentials=include alone
  does not establish shared authentication across different hosts.

## Compliance content needs correction before implementation

Do not encode his document validity table as established law. Its own rows include
VERIFY flags; its stated count differs from the table, and a badge source presented
as NTSA evidence links to a third-party procedure site. Store actual document
expiry plus requirement version and reviewer evidence, rather than adding a fixed
730 days and treating that as legal validation. Recheck each applicable requirement
against current issuing-authority rules and Mireli's actual transport model.

One verified correction: his document says all entities have no small-business
registration exemption. ODPC describes a general exemption below KSh 5 million
turnover and fewer than ten employees, with excluded categories including transport
services and online passenger hailing. Mireli's transport category is the relevant
reason to treat registration as required; the blanket statement is inaccurate.
[ODPC FAQs, questions 6–8](https://www.odpc.go.ke/faqs/)

The official [transport-sector guidance](https://www.odpc.go.ke/wp-content/uploads/2026/04/Guidance-Note-for-the-Transport-Sector.pdf)
is available for the detailed privacy review. This audit did not validate every
licence period, fee, DPIA deadline, subscription classification or transport rule.

## Implementation sequence and acceptance gates

1. **Contract and backend recovery:** locate/rebuild missing driver and onboarding
   APIs on a protected branch; agree states, units, ownership and OpenAPI fixtures.
   Gate: verified driver sign-in plus cross-account denial tests in staging.
2. **Assignments and manifest:** connect our existing Compose screens, then add
   decline, withdrawal/expiry, report/departure times, stops and eligibility reasons.
   Gate: admin assigns a real staging booking; only its driver sees it; reassignment
   removes access and updates the passenger view.
3. **Boarding and outbox:** add partial counts, proof validation, durable commands,
   explicit pending UI and account-bound sync. Gate: kill/restart, airplane mode,
   timeout-after-commit and duplicate requests each preserve exactly one outcome.
4. **Compliance:** private upload storage, reviewer role, expiry decisions and
   audit trail. Gate: driver cannot approve evidence or bypass expired eligibility;
   concurrent reviews conflict cleanly and active-trip safety remains available.
5. **Earnings and operational completion:** complete trip commands, read-only
   settlement statement and shared/charter totals. Gate: booking → assignment →
   boarding → completion → settlement matches finance records, including refunds.
6. **Release verification:** real low-end Android devices, unreliable networks,
   accessibility, process death, battery/location behavior, privacy/deletion,
   monitoring and current Play submission requirements. Neither repo supplies all
   of this evidence today.

Proposed responsibility split for discussion: Android owner integrates the Compose
app/outbox; web owner implements shared APIs and migrations; operations/compliance
owner confirms evidence requirements and reviewer permissions; both founders accept
one end-to-end staging journey. These are proposed roles, not messages or assignments
sent to the cofounder.

## Changes made by this audit

Added this comparison and linked it from our README. Preserved all existing app
code and integration work. No upstream code was merged, no branches were pushed,
and no production configuration, booking, payment or database was changed.
