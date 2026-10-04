# Mireli Web integration discovery — 4 October 2026

Public site: https://mireli-tau.vercel.app/

## Observed public UI (not backend verification)

The site is titled “Mi-Reli — Reliable rides from the SGR terminus”.
It exposes From Terminus / To Terminus, dates, train selection, pooled cabs,
door-to-door drop-off, whole-vehicle charter, booking history and credits.
It states shared fares and that door surcharges go to the driver.
It describes full refunds before lock and 30-day travel credit after lock.

The inspected page reported an unavailable timetable and zero cabs. A subsequent
visual inspection displayed the correct 4 October date. Empty/unavailable states
may reflect configuration or backend issues; no cause has been established.

No real booking, payment, account or customer data was created or altered.

## Required next input

Website repository access (or its path on this machine), API documentation and
staging access. A public URL alone does not establish booking table ownership,
payment callback authenticity, authenticated driver authorization or usable APIs.

## Reconcile with the master plan

- Map posted cabs to departure/vehicle/driver assignments.
- Preserve From/To Terminus direction and train connection identifiers.
- Separate base fare, door-to-door surcharge, charter price and driver entitlement.
- Carry lock timestamp and accepted policy version on every booking.
- Audit travel credits: expiry, refunds, liability accounting and legal treatment.
  Do not implement a second competing wallet inside the driver application.
- Clarify group boarding, partial arrivals, stop ordering and multiple drop-offs.
- Verify both shared and charter inventory concurrency before live sales.
- Do not hardcode public displayed prices in the Android client.

## Adapter boundary

DriverRepository is the Android integration seam. Production currently returns
no assignments and rejects every command. The demo implementation is compiled
only into the demo flavor. Replace the production adapter only after agreeing:
authenticated session/refresh/revocation, assigned trips and minimal manifest,
versioned commands, durable idempotency, conflicts, trip status events and
privacy requests. Server enforcement remains mandatory even where client domain
rules provide immediate feedback.

Do not point the mobile client at guessed endpoints or embed a service-role key.
Use an approved test driver and synthetic staging records for contract tests.
