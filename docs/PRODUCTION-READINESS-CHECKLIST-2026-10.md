# Mireli Driver production-readiness checklist

**Reviewed:** 7 October 2026

**Purpose:** Turn the known legal, product, engineering and operating gaps into work that can be owned and verified.
**Release status:** **Not ready for commercial launch or Google Play production release.** This is an operational checklist, not a Kenyan legal opinion or regulatory approval.

## Current verified position

- [x] The Android pilot puts driver phone sign-in before the explanatory content and preserves the light/dark preference.
- [x] The app distinguishes a status check from an OTP request; the status check does not claim to send an SMS.
- [x] Local backend code separates existing-driver phone sign-in from new-driver application intake. Unit tests, typecheck and lint passed locally.
- [x] The pilot targets Android API 36.
- [ ] The live driver status URL is available. `https://mireli-tau.vercel.app/api/v1/driver/status` returned HTTP 404 on 7 October 2026.
- [ ] Real OTP, new-driver registration, private document submission/review, trip assignment, payment and payout have passed end-to-end tests.
- [ ] A production-signed Android App Bundle and Play Console submission are complete. The available APK is debug-signed and for testing only.
- [ ] Emulator UI instrumentation completed. The last run ended in a device startup ANR, so this evidence is still missing.
- [ ] The recent source commits have reached GitHub. Pushes failed with HTTP 500 and GitHub API requests returned 401; they are committed only in the local checkouts.

Do not enter fake, seeded, or demo drivers, trips, documents, earnings, payments or approvals into production. Keep test data and provider test credentials in a separate staging environment.

## P0 — establish the lawful operating model

**Owners:** Founders, Kenyan transport counsel, tax adviser, operations lead

**Release evidence:** Signed written advice/decision record, correct permits and insurance evidence, approved passenger and driver terms.

- [ ] Write down who contracts with the passenger, who owns or operates each vehicle, who sets/quotes fares, who accepts and assigns bookings, who collects money, who controls cancellations, and who handles complaints/refunds.
- [ ] Ask Kenyan transport counsel to classify Mireli’s actual SGR-transfer and charter model and identify required company, county, NTSA, PSV, operator/Sacco, route and driver approvals. Do not assume a “technology only” label decides the classification.
- [ ] Ask counsel to review the signed order and current status of *Bolt Operations OÜ v Cabinet Secretary for Roads and Transport & 3 others* [2026] KEHC 13383, including any appeal, stay or replacement rules. The judgment text describes a 12-month suspension of the declaration of invalidity and a separate interim prohibition on enforcing selected pricing and data provisions. The canonical Kenya Law page was unavailable during this review; the accessible full-text copy is [here](https://sheriahub.com/cases/ke/caselaw/o-v-cabinet-secretary-for-roads-and-transport-3-others-2026-kehc-13383-klr).
- [ ] Obtain confirmation for every active vehicle: correct registration/use, PSV or commercial permissions as applicable, inspection status, capacity and route/operator permissions.
- [ ] Verify every driver’s identity, correct class of valid driving licence and any PSV/professional authorisation required for that vehicle/service. Maintain a named reviewer and expiry/reminder process.
- [ ] Obtain written insurance confirmation from a licensed insurer for the actual vehicle use, passenger capacity and transfer/charter activity. Record policy limits, exclusions, effective dates, claims contact and proof of cover. Do not advertise a benefit or insurance partner without a signed agreement.
- [ ] Have counsel approve separate passenger, driver and vehicle-owner/Sacco terms if those are the actual contracting parties. Cover fare and fee presentation, cancellations, refunds, delays, missed SGR departures, damage, complaints, safety, liability allocation and dispute routes without promising to waive non-waivable rights.
- [ ] Have a Kenyan accountant map Mireli’s and drivers’ tax treatment from the real principal/agent and payment flow, including invoices, VAT/turnover tax applicability, withholding and records. Do not assume an 11% commission establishes compliance.
- [ ] Confirm company registration, beneficial ownership records, county business permits, KRA PIN/tax obligations and current business compliance documents.
- [ ] Make a dated, counsel/operations-approved driver and vehicle document matrix. For every document record who needs it, legal or contractual basis, issuing authority, acceptable format, expiry rule, reviewer, retention period and appeal/re-upload path. Uber/Bolt checklists are references, not a universal legal checklist.

Official starting points: [NTSA service portal](https://serviceportal.ntsa.go.ke/), [NTSA Transport Network Companies Legal Notice 120 of 2022](https://new.kenyalaw.org/akn/ke/act/ln/2022/120/eng%402022-07-01/source), [KRA eTIMS](https://www.kra.go.ke/online-services/etims), [KRA VAT guidance](https://www.kra.go.ke/individual/filing-paying/types-of-taxes/value-added-tax).

## P0 — make privacy and document handling lawful and safe

**Owners:** Privacy lead, Kenyan privacy counsel, engineering

**Release evidence:** ODPC registration evidence where applicable, DPIA, published notices, tested access/deletion controls, incident procedure.

- [ ] Determine and document Mireli’s roles as data controller and/or processor for driver, passenger, vehicle, booking, payment, identity-screening and location data.
- [ ] Confirm and complete ODPC registration. ODPC guidance lists transport services, including online passenger-hailing apps, among mandatory-registration categories; validate Mireli’s exact role and registration scope with ODPC/counsel. See [ODPC FAQs](https://www.odpc.go.ke/faqs/) and the [Transport Sector Guidance Note](https://www.odpc.go.ke/wp-content/uploads/2026/04/Guidance-Note-for-the-Transport-Sector.pdf).
- [ ] Complete and approve a Data Protection Impact Assessment before high-risk identity, screening, precise-location or trip-monitoring processing.
- [ ] Publish a Mireli-specific privacy notice in the app and on a stable HTTPS web URL before collecting data. State purposes, lawful bases, required/optional data, sharing recipients, international transfers, retention, rights and contact details.
- [ ] Build a data inventory and retention schedule. Avoid blanket retention copied from the 2022 TNC rules; have counsel validate the current judgment and keep only what has a current documented purpose/legal basis.
- [ ] Sign data-processing agreements with SMS, cloud, storage, mapping, analytics, support and payment vendors. Review subprocessors and cross-border transfers.
- [ ] Store identity and vehicle documents in private encrypted object storage, never public web storage. Use short-lived upload/download links, least-privilege reviewer roles, malware/type/size checks, quarantine, access audit logs and deletion/retention controls.
- [ ] Implement driver/passenger requests to access, correct, export or delete data, including lawful exceptions and disclosed retention.
- [ ] Maintain a breach response runbook, named 24-hour contacts and notification workflow. The Data Protection Act requires controller notification to ODPC within 72 hours for qualifying breaches; processors must notify controllers without delay and, where reasonably practicable, within 48 hours. See the [Act, section 43](https://new.kenyalaw.org/akn/ke/act/2019/24/eng%402019-11-15/source).

## P0 — deploy a real driver API and authentication service

**Owners:** Backend lead, DevOps, SMS provider owner

**Release evidence:** Live HTTPS health/status/auth routes, monitored SMS delivery and abuse tests in staging, signed deployment record.

- [ ] Decide and document the production hosting/data architecture. The passenger site currently returns 404 for the driver status route. Do not move from Vercel to AWS solely for trial credits; choose one production owner and prove backups, access controls, cost, support and recovery first.
- [ ] Deploy the backend routes required by the app: service status, request OTP, verify OTP, driver profile/application, document upload/status, driver assignments, support and account deletion.
- [ ] Make status responses explicit and independent: existing-driver sign-in availability, new-application intake availability, maintenance state and user-facing explanation.
- [ ] Configure a contracted SMS sender/provider and production secrets through a secret manager. Test delivery to Kenyan networks with consented internal numbers and operationally controlled test drivers.
- [ ] Enforce OTP expiry, one-time use, resend delay, attempt limits, IP/device/phone throttles, abuse monitoring, replay protection and enumeration-resistant responses. Never log OTP values or expose provider secrets to Android.
- [ ] Verify account/session lifecycle: secure token storage, refresh/revocation, logout, device loss and staff/admin authentication. Existing drivers must be able to sign in while new intake is closed; new applicants must not create records unless intake is explicitly open.
- [ ] Configure production database and migration procedure; prove migration/rollback on a production-like copy, connection limits, access isolation, backup and restore. Keep production empty of seeded demo accounts.
- [ ] Add health checks, structured privacy-safe logs, error monitoring, alerting, service ownership and a rollback switch. Never return passenger HTML/404 as a successful API response.
- [ ] Deploy to staging first and run real request/response tests against the Android build, then promote the same reviewed artifact to production.

## P0 — connect passenger bookings, dispatch and driver work

**Owners:** Product, passenger-web/backend lead, dispatch operations

**Release evidence:** End-to-end test evidence for individual and charter bookings using one authoritative booking store.

- [ ] Confirm whether Mireli Web and Driver share one authoritative production database/API. Do not maintain separate passenger and driver copies of booking truth.
- [ ] Define booking states and ownership for individual seats and charters: created, paid/confirmed, dispatchable, offered, accepted, en route, picked up, completed, cancelled, refunded and disputed.
- [ ] Define who may see passenger names, phone numbers, pickup/drop-off details and charter data, and at which state. Enforce this server-side for every request.
- [ ] Implement atomic assignment/acceptance so two drivers cannot claim the same job. Add idempotency and expiry for offers, cancellations, retries and webhooks.
- [ ] Define service-area and route rules for SGR pickups/drop-offs in Mombasa, passenger readiness, train delay handling, vehicle capacity and driver availability. Avoid promises of train connections that operations cannot guarantee.
- [ ] Decide how a driver receives work: foreground offers/push/polling, accept/decline timeout, reassignment and out-of-network fallback. Avoid dispatching to unapproved or expired drivers.
- [ ] Implement passenger/driver trip status updates, cancellations, no-show handling, refund ownership and support escalation from one state machine.
- [ ] Complete end-to-end staging runs for one individual seat and one charter: passenger booking → payment confirmation → dispatcher assignment → driver acceptance → completion/cancellation → correct receipt/refund/ledger.

## P0 — finance, collections and driver payouts

**Owners:** Finance lead, Kenyan tax adviser, payments engineer

**Release evidence:** Signed provider agreement, sandbox and controlled live reconciliation, documented accounting/tax treatment.

- [ ] Map who is merchant of record and who legally receives passenger funds. Have counsel/accounting advise if Mireli’s proposed funds flow creates a licensing or safeguarding obligation.
- [ ] Select a contracted provider and verify its current authorisation in the [CBK authorized PSP directory](https://www.centralbank.go.ke/2022/09/14/directory-of-payment-service-providers/). Do not build an unlicensed wallet or hold driver funds without advice.
- [ ] Document fare, platform fee/commission, driver share, provider fee, tax, refunds, chargebacks, cash collection (if any), payout timing and negative-balance handling.
- [ ] Implement an append-only transaction ledger with immutable provider references, idempotency, signed webhook verification, replay prevention, refund linkage and manual adjustments with audit history.
- [ ] Implement driver payout onboarding/KYC, destination validation, payout approval/limits, duplicate prevention, failed-payout recovery and driver-visible statements.
- [ ] Reconcile provider settlement reports to bookings, refunds, ledger and bank/mobile-money statements every day; alert and resolve mismatches before displaying earnings as payable.
- [ ] Complete sandbox tests for success, timeout, duplicate callback, callback forgery, reversal, refund, partial payment, insufficient balance and delayed settlement. Then run a low-value controlled live pilot and reconcile it manually.
- [ ] Configure eTIMS-compatible invoice/receipt processes and have the accountant validate tax, VAT and withholding handling. KRA says persons engaged in business must onboard eTIMS and issue electronic tax invoices; confirm Mireli’s specific flow with KRA/accountant.

## P0 — driver and passenger safety operations

**Owners:** Operations/safety lead, insurer, transport counsel

**Release evidence:** Written SOPs, staffed contact ownership, scenario drills and verified insurance.

- [ ] Name who is responsible during every active trip and publish actual support hours and response targets. Do not advertise 24/7 response until staffed and tested.
- [ ] Approve procedures for collision, breakdown, harassment, passenger not found, lost phone, missed train, ferry/road disruption, lost property, medical emergency, serious complaint and suspected fraud.
- [ ] If the app shows SOS/emergency contact, route it to a monitored owner, provide fallback call options, log/test delivery and run a drill. A visible button without a response operation is not a safety feature.
- [ ] Verify driver and vehicle approval before dispatch; block expired required credentials and show a clear secure renewal route.
- [ ] Define passenger/driver trip verification, emergency contact sharing, pickup location confirmation and safe handover for SGR arrivals. Collect only information needed for the trip.
- [ ] Keep safety incident access restricted, retain evidence under an approved schedule and train staff on escalation and confidentiality.
- [ ] Reconfirm current legal requirements for emergency-response arrangements and safety equipment with counsel/NTSA in light of the 2026 judgment and any revised rules.

## P1 — secure, fast, resilient product

**Owners:** Engineering/security lead, independent reviewer

**Release evidence:** Threat model, security test report, load/weak-network tests, restored backup, crash monitoring.

- [ ] Threat-model the public API, Android app, passenger site, reviewer console, storage, payments and webhooks.
- [ ] Verify authorization on every object and state transition; test IDOR, injection, XSS on web-rendered content, CSRF where cookie sessions are used, CORS, SSRF, mass assignment, file upload attacks, OTP brute force and webhook spoofing.
- [ ] Use parameterized Prisma/database access, strict server-side schemas and allowlists; escape output at web rendering boundaries. Do not rely on client validation for permissions or eligibility.
- [ ] Use TLS, managed secrets, encryption at rest, least-privilege service accounts, dependency updates, audit logs and secure Android token storage. Remove secrets from Git, APKs, logs and build output.
- [ ] Revoke and replace the GitHub token previously pasted into chat; use scoped short-lived credentials and secret storage for future access.
- [ ] Add automated dependency, secret and static scans; commission an independent penetration test before large-scale launch and resolve critical/high findings.
- [ ] Define availability, latency and crash targets before launch; test low-cost Android devices, slow/unstable data, airplane mode, app resume, battery use and interrupted uploads.
- [ ] Make retries idempotent, show honest offline/maintenance/errors and provide a safe manual dispatch fallback. Verify notifications do not disclose sensitive passenger or driver information on lock screens.
- [ ] Configure database backups and point-in-time recovery where supported; perform and record an actual restore. Define recovery time/point objectives and incident on-call ownership.

## P1 — Android and Google Play release

**Owners:** Android lead, privacy lead, Play Console owner

**Release evidence:** Production-signed AAB, completed Play Console declarations, device QA and internal/closed test sign-off.

- [ ] Decide the permanent production package ID and app signing ownership. The available `.pilot` package/APK is for testing; build a production variant that cannot be confused with pilot/staging.
- [ ] Build a release-signed Android App Bundle (AAB), protect the upload key, enable/verify Play App Signing and document key recovery and release access.
- [ ] Verify target API 36 at submission time. Google Play’s current policy requires API 36 or higher for new mobile apps and updates from 31 August 2026. [Policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).
- [ ] Publish one active HTTPS privacy-policy URL naming Mireli/Mireli Driver and link it in the app and Play listing. Complete accurate Data Safety and data-deletion declarations.
- [ ] Implement account deletion in the app and an external web request route. Delete associated data unless a documented legal/security retention exception applies; explain exceptions and timing. [Google Play account-deletion rules](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).
- [ ] Review every Android permission and SDK disclosure. Ask for location only when the driver feature needs it. If background location is truly essential, provide prominent pre-permission disclosure, privacy-policy/listing details and the Play Console declaration/video; prefer foreground-only collection where it serves the feature. [Background location policy](https://support.google.com/googleplay/android-developer/answer/9799150?hl=en).
- [ ] Complete Play Console developer/company verification, contact/support details, content rating, app access instructions and review credentials that do not expose real driver/passenger data.
- [ ] Run accessibility checks for text scaling, contrast, touch targets, screen readers, keyboard/physical navigation and both light/dark themes.
- [ ] Complete install/upgrade/uninstall testing on supported physical Android versions and at least one lower-end device. Resolve startup crashes/ANRs; repeat the currently incomplete UI instrumentation run on a healthy device.
- [ ] Prepare truthful store listing, screenshots, feature descriptions, data disclosures and support contact. Do not claim live payouts, SOS, navigation or driver availability until each is operational.

## P1 — launch operations and support

**Owners:** Operations manager, support lead, product owner

**Release evidence:** Named roster, playbooks, support channels, dashboards and successful launch rehearsal.

- [ ] Assign accountable owners for API, database, SMS, payment/payouts, privacy requests, document review, safety incidents and Play releases.
- [ ] Set onboarding review hours and an applicant communication SLA; provide clear received/in-review/action-needed/approved/rejected states and reasons.
- [ ] Train support staff; use role-based tools and audit access to identity, trip, payment and safety data.
- [ ] Publish driver and passenger help pages, complaints/refund route, privacy contact and emergency instructions. Verify each link on low-bandwidth mobile.
- [ ] Create a launch dashboard for API health, OTP send/verify/delivery, registration funnel, document review age, assignment failures, trip completion, cancellations, refunds, payout reconciliation, crash/ANR rate and support incidents.
- [ ] Prepare rollback/feature kill switches for OTP, new applications, dispatch, document uploads, payment collection and payouts. Define who can use them and how drivers/passengers are notified.
- [ ] Rehearse launch with a small invited driver cohort and real controlled passenger bookings. Capture issues, reconcile every payment and payout, and obtain safety/support sign-off before widening access.

## Release sign-off — all required before public launch

- [ ] Transport counsel signs off on the live operating model, applicable approvals, driver terms, passenger terms, insurance and current regulatory status.
- [ ] Privacy lead confirms ODPC role/registration, DPIA, notices, vendor contracts, retention, rights/deletion and breach-response readiness.
- [ ] Finance signs off on provider authorisation, tax/accounting model, invoices, collection/refund/payout ledger and reconciliation.
- [ ] Engineering signs off on production API, authentication, shared booking truth, security findings, backups/restore, monitoring, performance and rollback.
- [ ] Safety/operations signs off on staff coverage, driver/vehicle verification, incident drills, support and route readiness.
- [ ] Android/Play owner signs off on production AAB, signing, privacy/data declarations, location disclosures, account deletion, device QA and store listing.
- [ ] Product owner verifies production has no seed/demo data, all required service routes work and test bookings/payouts reconcile without manual database edits.
- [ ] Run the complete rehearsal: new driver application → secure document review → approval → passenger individual booking and charter → dispatch/acceptance → pickup/completion → receipt/refund path → driver payout → support/incident path → deletion request.
- [ ] Record named signers, evidence links, date, unresolved risks and the go/no-go decision. Any failed P0 gate means no public commercial launch.

## Execution order

1. Restore repository authentication and publish the already committed branches for review; revoke the exposed token.
2. Get the legal/tax/insurance operating-model decision and document requirements.
3. Bring up the real driver API, SMS and protected document workflow in staging.
4. Connect the driver app to the passenger booking source of truth and complete end-to-end dispatch.
5. Finish payments, refunds, payout reconciliation and eTIMS/accounting treatment.
6. Complete privacy, safety, operations, security, backup/restore and performance evidence.
7. Produce and test the production AAB, complete Play Console declarations and conduct the controlled launch rehearsal.

## Official reference links

- [ODPC FAQs](https://www.odpc.go.ke/faqs/) and [Transport Sector Guidance Note](https://www.odpc.go.ke/wp-content/uploads/2026/04/Guidance-Note-for-the-Transport-Sector.pdf)
- [Kenya Data Protection Act](https://new.kenyalaw.org/akn/ke/act/2019/24/eng%402019-11-15/source)
- [CBK directory of authorized payment service providers](https://www.centralbank.go.ke/2022/09/14/directory-of-payment-service-providers/)
- [KRA eTIMS](https://www.kra.go.ke/online-services/etims)
- [Google Play target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en), [User Data](https://support.google.com/googleplay/android-developer/answer/10144311), [account deletion](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en) and [background location](https://support.google.com/googleplay/android-developer/answer/9799150?hl=en)
