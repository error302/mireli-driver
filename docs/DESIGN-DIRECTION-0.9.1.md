# Mireli Driver design direction — 9 November 2026

Mireli is a scheduled Mombasa SGR transfer product. Its main feature is the next
assigned transfer: departure, booked pickup, destination, boarding and the action
the driver can take now. The existing original Mireli logo remains the brand anchor.

## References reviewed

- [Jagath Selvan — Taxi Booking Driver App](https://dribbble.com/shots/9011135-Taxi-Booking-Driver-App): pickup/drop-off grouping and a prominent trip action.
- [Chinedu Okeke — ProTaxi trip tracker and earnings](https://dribbble.com/shots/26714354-ProTaxi-Drivers-Trip-Tracker-and-Earnings-View): readable journey and settlement information.
- [Mahesh Katti — Driver Side Android App](https://www.behance.net/gallery/58649681/Driver-Side-Android-App): a simple request-to-active-trip progression.

Pinterest was reached this session, but only its aggregate "ideas" feeds, which list pin
titles without exposing the pins themselves to a text reader. The sign-in layout below was
therefore driven by the Uber and Bolt patterns those feeds collect. The original designers'
public project pages supplied the remaining reference material. No reference screenshots,
logos or UI assets are included in Mireli.

## Applied choices

- Navy `#063555` anchors the transfer header and earnings total; teal `#007F79`
  identifies actions. Mint `#8CE1D4` is used against navy, with theme-aware text
  colours on light and dark surfaces.
- Use a clean light surface, or the existing dark palette. A small native vector
  route with two station nodes appears in the sign-in header only. It is an
  abstract brand motif, never a geographic map or promised route.
- Departure time is larger than secondary trip metadata. Times and monetary
  values use tabular numbers. The pickup shown is the booked stage, rather than
  an arbitrary endpoint of the overall route.
- Status is an icon and a short phrase, without a decorative badge. Labels such
  as pickup/drop-off stay because they explain direction.
- Use the original consistent Mireli line-icon family: train, calendar, origin,
  destination, passengers, vehicle, wallet and support.
- Shared control radius is 8 dp. A 24 dp panel with 16 dp padding uses an 8 dp
  inner control radius. Main trip actions and the assignment CTA share a minimum
  52 dp target and a subtle 0.97 press response with a 150 ms release.
- Route details expand over 180 ms; their exit is 144 ms. Motion communicates
  an interaction. No continuous animation or decorative texture is added.
- The logo is lossless WebP: 891,131 bytes to 651,404 bytes, with decoded pixel
  equality checked against the original PNG. No new image/font/network package
  is required for the background or these UI changes.

## Verification boundaries

Review native screenshots in both themes. Use Android lint, unit tests and
instrumentation for the Android app. Lighthouse, JavaScript reduction, web font
loading and browser LCP apply to Mireli Web and are not Android performance
measurements. A smaller logo does not itself establish startup or frame-time gains.

Test data may show Ferry, Kona Mpya (Fayaz) and Shika Adabu, but publishing stages
requires the shared backend's actual approved coordinates and dispatch setup.

## Sign-in surface — 10 November 2026

The first screen a driver sees was rebuilt to look and behave like Uber's and Bolt's driver
sign-in, instead of an explanatory page with a form card inside it.

Pinterest references (aggregate idea feeds; each page collects many pins):

- [Driver app UI design](https://www.pinterest.com/ideas/driver-app-ui-design/943868248447)
- [Uber driver app home screen](https://www.pinterest.com/ideas/uber-driver-app-home-screen/895414042219)
- [Uber app interface](https://www.pinterest.com/ideas/uber-app-interface/905178765245)

Applied rules:

- One screen, one decision. A navy hero carries the logo, the headline and the product
  line; the form is a sheet that overlaps the hero by 28 dp and fills the rest of the screen.
- Email first, then the six-digit code. No password, no social sign-in and no competing
  provider buttons beside the primary action.
- One filled, full-width action per step: "Continue", then "Verify and continue".
  "Change", "Resend", "Appearance" and support stay as text actions.
- The service gate is stated before the field. While sign-in is closed, the notice sits
  above the disabled field and the button reads "Sign-in unavailable".
- Secondary content, the three application steps, is one tinted card below the action.
  It never pushes the form down or sits above it.
- Theme remains reachable before sign-in: the Dark mode switch and the Appearance dialog
  sit below the form, above the support row.
- The hero motif stays the abstract two-node route. It is not a map and promises no route.

Implemented in Compose with no new dependency. The screen is a lazy list of three items
(hero, sheet, footer) so it scrolls as one surface. Verified on a physical device in light
and dark themes and in the closed, open and verification-code states.
