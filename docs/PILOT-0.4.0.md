# Mireli Driver 0.4.0 — verified scope, 6 October 2026

## Fresh pilot

Build `assemblePilotDebug`. Package `io.github.error302.mireli.driver.pilot`,
version code 5. It connects to https://mireli-tau.vercel.app/api/v1/driver,
contains no demo repository, and opens sign-in with no sample assignments.
It installs alongside the old sample app. The default light theme keeps the
original teal/navy palette; Dark mode is a saved preference, not a rebrand.

Navigation hands an explicit route point to Google Maps, with browser fallback.
Inbound trips reverse the route stages. Missing coordinates never substitute a
different stop. Address search requires confirmation and is not a verified pin.
No background GPS tracking, embedded map or automatic multi-stop optimization is claimed.

## Checks completed

- Pilot APK, pilot instrumentation APK, staging APK and production release compile.
- 33 domain tests pass; Android lint: zero errors, 11 warnings.
- Three Android emulator tests pass: empty non-demo startup, light/dark persistence
  across activity recreation, navigation target selection and missing-handler error.
- Light and dark sign-in screenshots inspected; the live unavailable state is visible.
- Shared backend: 37 tests, typecheck, lint and production build pass.
- Local production HTTP: nonce appears on Next scripts and changes between HTML
  responses; cross-origin mutation returns 403; status reports registration closed.
- Browser admin login renders and its controlled input hydrates with no captured
  CSP or JavaScript errors. No login form was submitted.

Tests do not prove real SMS delivery, document scanning, staff approval, payment
settlement, road navigation, low-end phone performance or live account creation.
No seed script, production database migration, real SMS or payment was executed.

## Backend protections in this update

Nonce-based script CSP, no inline script handlers, anti-framing and MIME-sniffing
headers; same-origin checks for browser mutations; bounded JSON inputs and safe
error responses. Driver queries use Prisma parameters, not concatenated SQL.
Text remains escaped by React/native Text. These checks reduce risk; they are not
a penetration-test certificate or a promise that every attack is impossible.

Normal local startup no longer inserts samples. Missing operating configuration
returns an explicit unavailable response instead of silently creating fare settings.

## Gates before drivers can work

1. Deploy the shared API after reconciling the actual PostgreSQL schema, migration
   history, backup and restore. Never reset the live database or run sample seeds.
2. Configure and verify real phone OTP, private S3 document storage, malware scanning,
   approved policy URLs and revocable staff authentication/role permissions.
   Production passenger/admin prototype sign-in is deliberately blocked.
3. Enter approved real routes, coordinates, fares, operating rules and staff assignments.
   Approve legitimate drivers, vehicles and documents. No artificial jobs or balances.
4. Verify M-Pesa collection and B2C settlement, beneficiary review, callback
   authentication, retries, reconciliation and failed-payment support end to end.
5. Complete push dispatch, location-consent/tracking needs, account deletion,
   incident operations, monitoring, backups, retention and physical-device field tests.
6. Obtain business compliance review for Kenyan transport operations, driver/vehicle
   documents, insurance and data protection. ODPC specifically includes transport and
   passenger-hailing businesses in its registration guidance:
   https://www.odpc.go.ke/faqs/
7. Prepare the signed Play AAB, store listing, privacy policy, Data safety declaration,
   reviewer access and applicable testing. Apps offering account creation must provide
   the required deletion paths: https://support.google.com/googleplay/android-developer/answer/13327111
   New personal accounts may require 12 testers continuously for 14 days:
   https://support.google.com/googleplay/android-developer/answer/14151465

At verification the official driver status URL returned 404. The owning Vercel
dashboard is accessible and confirms Prisma Postgres variables, but driver provider
configuration was not present. This APK is for testing, not a Play-ready release.
