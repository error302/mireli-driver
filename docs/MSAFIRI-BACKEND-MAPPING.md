# Backend source established — 4 October 2026

The owner supplied `C:\Users\user\Desktop\msafiri` and the cofounder repository
https://github.com/demitriosojwang/msafiri. The official passenger URL is now `https://mireli.online` on the Mireli AWS EC2 VM (Vercel is
retired) and the company contact is mirelisgr001@gmail.com.

The local checkout is an older User/DriverProfile/Vehicle architecture with
extensive uncommitted additions. Current cofounder main is
`687ef7a0b7e14fc97628f578dfafe15a500cd0cc`, with Passenger/Driver/Trip/Booking and
a separate LedgerEntry/RefundRecord/PayoutRecord/Credit model. An aligned web
working copy was prepared at `C:\Users\user\Desktop\mireli driver\msafiri-web`.

Both inspected Git schemas declared SQLite and the original local environment
used a file URL. The aligned web copy now declares PostgreSQL. Production no longer runs on Vercel: it is the Mireli AWS EC2 VM (`eu-north-1`) behind Caddy and Cloudflare, with PostgreSQL 16 in the app container. Its deployed schema and migration history are still unverified from this checkout; no production database was accessed or changed.

## Android integration decisions

1. Use the cofounder Trip and Driver IDs as canonical assignment identities.
2. Connect through authenticated versioned server endpoints, never directly to Prisma.
3. Implement verified driver identity, suspension checks, assignment-scoped access
   and revoked/rotated sessions before enabling Android's production adapter.
4. Preserve FROM_TERMINUS / TO_TERMINUS and render instants in Africa/Nairobi.
5. Convert upstream integer KSh explicitly to Android minor units (multiply by 100
   with overflow validation); keep fare, home surcharge, payable and settled sums distinct.
6. Do not map scheduled/locked automatically to accepted/at-pickup. Driver execution
   states require a reviewed schema and API addition with optimistic versions.
7. Replace timer-driven departure/completion as driver evidence with authorized,
   durable idempotent commands. Use a transactional server outbox for notifications.
8. Return only the authorized minimal manifest. Do not copy identity numbers,
   nationality, payment secrets or the entire Passenger row into the driver app.

The upstream project explicitly has no driver authentication/API implementation.
Its prototype passenger/admin sign-in cannot substitute for driver verification.
The Android production repository therefore remains deliberately unconnected.
The web copy's `docs/DRIVER-INTEGRATION.md` defines the proposed endpoints and
required contract tests; those endpoints are not claimed as implemented.
