# Mireli Driver first-driver pilot

## Pilot route

The owner selected the South Coast stages **Likoni Ferry Container**, **Kona Mpya (Fayaz)** and **Shika Adabu (Checkpoint)**. The Android app displays the stages and coordinates returned by Mireli Web. For the physical rehearsal, the web owner supplies the verified pickup/drop-off pins, route order and dispatch assignment. The local fixture uses these stage names with synthetic coordinates.

The app uses a scheduled-transfer board and East Africa Time for departure. The visual hierarchy draws on Uber Driver's navigation structure, Lyft's scheduled-ride details, and Bolt Driver's separation of navigation from earnings. [Uber Driver](https://www.uber.com/us/en/drive/driver-app/) · [Lyft scheduled rides](https://help.lyft.com/hc/en-us/all/articles/115012924387) · [Bolt Driver](https://play.google.com/store/apps/details?id=ee.mtakso.driver)

## Live status on 7 November 2026

The [health endpoint](https://mireli.online/api/health) returned HTTP 200.
The [driver status endpoint](https://mireli.online/api/v1/driver/status) returned API `v1` in strict mode with phone sign-in, email sign-in, registration and navigation closed.
The [driver supply endpoint](https://mireli.online/api/driver-supply) reported no eligible drivers.
The [route endpoint](https://mireli.online/api/routes) reported zero route points, the [stage endpoint](https://mireli.online/api/stages) returned an empty list, and the [payment options endpoint](https://mireli.online/api/pay/options) returned HTTP 503.

Android source was checked against Mireli Web `3795e33d1545558ac5e58e7f1419c6bac99a914e`.
That revision still disables production reviewer authentication and requires it for new applications. A separate local web checkout contains uncommitted staff authentication and scanning work that needs review and verification before promotion.

## Android 0.8.0 baseline evidence

- The version 11 pilot APK is debug-signed and installable for QA. It uses the live HTTPS API with simulation disabled.
- The optimized production AAB passed `bundletool validate` and requires the owner's upload key. Pilot and production have distinct package IDs.
- The 35 pilot unit tests passed, Android lint reported zero errors, and five emulator tests passed. The emulator reached the live API and verified the closed sign-in screen, no sample trips, theme persistence and map fallback behavior.
- The pilot APK passed the 16 KB ZIP alignment check. The production bundle requests `PAGE_ALIGNMENT_16K`, and its `arm64-v8a` and `x86_64` shared libraries have 16 KB load-segment alignment. The test emulator reported a 16 KB page size. [Android page-size verification guide](https://developer.android.com/guide/practices/page-sizes)
- The matching web source passed 251 automated tests, including driver supply, manifest filtering, payment assignment and Daraja callback handling.
- Four connected staging tests passed on the Android 17 emulator. The local Next.js server used an isolated PGlite database with the Mireli Web migrations and simulated providers. The run exercised wrong-code recovery, profile save, support, sign-out and revocation, plus encrypted session and saved-action storage.
- In the charter rehearsal, the local API accepted synthetic application documents and staff approval. An approved driver accepted an assigned charter, arrived, boarded two passengers by code, started and completed the trip in Android. The unpaid reservation stayed out of the manifest. The backend queued one KSh 1,800 settlement from the KSh 2,000 fixture. The fixture began with a confirmed booking and used no payment-provider calls.

## Android release and web handoff

1. **Web owner.** Verify Brevo delivery to an authorized inbox, deploy reviewer authentication and private document scanning, and publish the approved driver terms and privacy notice. Confirm the first eligible driver and the approved South Coast route data through the shared API. The account-deletion request is available inside Android through Support. Publish the corresponding external driver deletion resource for a Play submission and assign staff to process requests. [Google Play account-deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)
2. **Android owner and first driver.** Sign the production release using the owner key. On the driver's phone, verify email and submit evidence through the app. Record the reviewer decision and confirm the app reports the driver eligible. Check account recovery and the action after the app returns from the background.
3. **Prepare the first departure.** Publish the owner-approved route and stages, then confirm the pickup point and timetable with dispatch. Verify Google Maps handoff on the driver's phone if in-app routing remains closed. Rehearse an assigned trip, boarding, no-show handling, offline recovery, completion and support before relying on the app in service.
4. **Verify money before taking passenger payments.** BRS registration is underway according to the owner. The web backend's STK integration expects the Daraja consumer key and secret, collection shortcode, Lipa na M-Pesa Online passkey and HTTPS callback base URL. Use the shared backend's secret store. Payouts require B2C setup and an approved beneficiary. Verify sandbox collection and callback correlation, then reconcile a controlled live booking, fare split, refund and eligible payout before opening paid trips. Check that only the confirmed booking reaches the driver manifest and that the earnings statement distinguishes a queued payout from confirmed money. [Safaricom Daraja APIs](https://developer.safaricom.co.ke/apis)

Record the app version, backend revision, authorized reviewer, device, departure and reconciled booking and payout references for the first-driver sign-off. Keep real identity documents, OTPs and provider credentials out of test fixtures and evidence logs.
