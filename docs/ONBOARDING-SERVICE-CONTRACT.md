# Live onboarding connection and AWS boundary

This is the proposed service contract for the implemented Android draft flow.
These endpoints are NOT deployed or implemented by this document. The supplied
site's `/api/driver/me` returned 404 on 4 October 2026.

## Hosting decision

The owner reports Prisma Postgres on Vercel and intends to migrate to an AWS VM.
Keep PostgreSQL as the record system. Keep document bytes in a private object
store (proposed: S3), referenced by opaque object IDs in PostgreSQL. A VM's web
root, public bucket or passenger-upload folder is not an appropriate document store.
Do not move the live database merely to make the Android app work: first implement
and test the driver APIs against staging with migrations and a restore-tested backup.

The Android service boundary must use HTTPS and a stable host; its wire format must
not expose database credentials, Prisma URLs, AWS keys or internal object paths.
On a VM, use an instance role for object access rather than long-lived access keys.
Keep public bucket access blocked; scope reviewer access and record every read.

AWS's new-customer Free plan ends at six months OR when its credits are exhausted,
whichever happens first; it is not an unconditional six-month free production VM.
Confirm account eligibility, selected services, storage, network egress, IPv4 and
backup costs before deploying. No AWS resources or subscriptions were created here.
[AWS Free Tier FAQ](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier-FAQ.html)

## Required API sequence

Use a versioned specification shared by Android and Mireli Web; paths below are a
proposal to reconcile with the cofounder's unversioned `/api/driver` design.

| Endpoint | Required behavior |
| --- | --- |
| POST /api/v1/driver/auth/challenges | Generic response for registered and unknown phones; rate limits, cooldown, short-lived challenge; provider credentials stay on server |
| POST /api/v1/driver/auth/sessions | Verify a single-use challenge, bind session to driver/install, return scoped bearer and expiry; never accept arbitrary four-digit codes |
| DELETE /api/v1/driver/auth/session | Authenticated server revocation plus defined handling for owned pending commands |
| GET /api/v1/driver/onboarding | Own profile, application/version, catalogue/policy version, current document states and actionable reasons; no public file URLs |
| PUT /api/v1/driver/onboarding | Update allowed profile/vehicle fields with expectedVersion; owner derived from session, not body |
| POST /api/v1/driver/documents/upload-intents | Validate catalogue type, limits, declared size/hash/media; allocate private object ID and short-lived upload instruction bound to driver/application |
| POST /api/v1/driver/documents/{id}/finalize | Verify stored object, size and digest, run content/malware checks; move to pending review only after validation; replay-safe receipt |
| DELETE /api/v1/driver/documents/{id} | Ownership checks, retention/legal-hold rules, review/audit effects; clean abandoned upload objects |
| POST /api/v1/driver/onboarding/submissions | Idempotency key + expectedVersion + policy version; evaluate required evidence and current expiry in one transaction; return submitted state, never approval |
| GET /api/v1/driver/onboarding/submissions/{id} | Current review state, reviewer reason and replacement requirements; driver ownership only |
| POST /api/v1/admin/onboarding/{id}/reviews | Reviewer role, expectedVersion, per-document decision/reason, audit event and safe eligibility recomputation |

## State and validation rules

- Document: missing → local draft → upload pending → received/quarantined →
  awaiting review → approved or rejected; expired/replaced/revoked remain distinct.
- Application: draft → submitted → in review → needs changes or approved;
  suspension and withdrawal have reasons and timestamps. Approval is not a client field.
- Eligibility is computed server-side from current driver, vehicle and policy.
  Changed/expired evidence blocks new assignments; active-trip incidents require
  safe dispatch handling, rather than an app forcing a roadside stop.
- Catalogue requirements must be approved for Mireli's actual vehicle and operating
  model. Do not turn the test catalogue into a hard-coded legal assertion.
- OTP secrets, document content and bearer tokens must not appear in logs.
- Upload files directly to a tightly scoped signed destination or stream via an
  authenticated API. Confirm host/method/expiry; never forward the API bearer token
  to the object-storage URL. Prevent reusing an object for another driver's application.
- Finalization/approval must tolerate retries without duplicate evidence or decisions.
- Wrong-owner objects return a non-enumerating response; expired sessions never
  replay a previous driver's pending actions under another user's token.
- Record retention periods and deletion jobs are server policy, with an accessible
  request route. The current app's local delete button does not delete server records.

## Proof needed before enabling real uploads in the APK

1. Staging sign-in works with the actual OTP provider; brute-force and replay tests pass.
2. A driver uploads only to their allocated private object; wrong-owner reads and
   finalization are denied; oversized/renamed/corrupt files are rejected.
3. File scanning and reviewer access have auditable results. A driver cannot approve
   their own evidence or change review/expiry authority by editing request JSON.
4. A reviewer rejects one item; the driver sees the reason, replaces that item,
   resubmits, and the old evidence remains handled according to retention policy.
5. Approved eligibility gates a staging assignment and is rechecked on accept.
6. Backup/restore, secret rotation, lost-phone revocation and deletion are exercised.
7. Policies, operator requirements and store disclosures match the deployed behavior.

Until these pass, keep the production adapter closed and distribute only the
clearly labelled test APK. Migration to AWS does not itself implement these services.
