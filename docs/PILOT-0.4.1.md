# Mireli Driver 0.4.1 — sign-in first, service status clarified

## What changed

The app now opens on the driver sign-in form. The Kenyan phone field and SMS sign-in
action appear before the promotional onboarding panel. When the API does not enable
phone verification, the action stays disabled and the screen explains that no code
was sent. “Check service status” only repeats the status request; it neither sends an
OTP nor decides a driver's eligibility. Eligibility is returned after sign-in, using
the driver's account, documents and compliance review.

HTTP 404 errors now say that driver sign-in is not connected at the configured server
address. The unavailable panel uses the app's neutral surface palette instead of the
error red. Light/dark appearance remains available on the signed-out screen.

## Live service finding

On 7 October 2026, `GET https://mireli-tau.vercel.app/api/v1/driver/status` returned
HTTP 404 with the passenger website's Next.js 404 page. Therefore the production APK
cannot reach a driver status or OTP endpoint at that hostname. No OTP can be sent by
this build today.

The backend driver-services change remains a preview pull request, not a production
deployment. Even after deployment, live onboarding must stay closed until production
SMS delivery, private document storage and scanning, verified reviewer authentication,
reviewer roster, and published driver policy URLs are configured and verified. In
live mode, a successful OTP request sends a code through the server's SMS provider;
the app must not receive or display the real OTP in its response.

## Pilot APK

- File: `deliverables/Mireli-Driver-0.4.1-pilot.apk`
- Package: `io.github.error302.mireli.driver.pilot`
- Version: `0.4.1-pilot` (version code 6), minimum Android 8 / API 26, target API 36
- It is signed with the same local Android debug certificate as the 0.4.0 pilot, so
  it can update that test installation without deleting its local app data.
- This is a debug-signed internal test build. It is not a Play Store release.

## Verification

- `:app:testPilotDebugUnitTest`: 33 tests passed.
- `:app:lintPilotDebug`: passed with no errors (11 dependency/version warnings).
- `:app:assemblePilotDebug` and `:app:assemblePilotDebugAndroidTest`: passed.
- `FreshPilotTest.startsWithoutSampleDataAndKeepsAnExplicitThemeChoice`: passed on a
  Pixel 7 Android virtual device through direct instrumentation. It confirms the
  sign-in heading, phone field and action are visible on the initial screen and that
  the light/dark preference survives activity recreation.
- The Gradle `connectedPilotDebugAndroidTest` wrapper did not run its cases: Android's
  UTP runner exited before test execution while opening its generated runner config.
  The test above was invoked directly on the emulator and passed.
- No sample driver data or live OTP was created or sent.

This validates the local pilot build and its sign-in presentation only. It does not
prove live driver registration, SMS delivery, eligibility, document review, payout
settlement, Play policy compliance or readiness for commercial operations.
