# Mireli Driver: open-source maps and routing

**State:** Android map and authenticated routing integration are implemented. Live route guidance is not active until Mireli deploys and configures its Valhalla service.

## What the driver sees

For an active assignment, the app renders an in-app Mombasa map, the selected assigned stop, and the driver's current position after an in-app disclosure and foreground location permission. With a configured routing service, it also draws the route and shows the next written maneuver, route distance and estimated duration. The driver can select another stop from the assigned route, recalculate, end navigation, or open the selected stop in Google Maps as a fallback.

Location permission is not requested at sign-in or onboarding. Android location updates are requested only while the navigation screen is active, and the app does not request `ACCESS_BACKGROUND_LOCATION`. When the activity stops, local location updates stop. A route request sends the current point and selected trip stop to Mireli's authenticated backend; it does not persist a location history.

## Components and operating costs

- **Map renderer:** MapLibre Native for Android, an open-source renderer.
- **Map tiles:** OpenFreeMap's Liberty style is the pilot default. The style URL is build-configurable with Gradle property `MIRELI_MAP_STYLE_URL`.
- **Road data:** OpenStreetMap data. The app shows OpenStreetMap and OpenFreeMap attribution on the map.
- **Driving directions:** Valhalla, an open-source routing engine. Android calls Mireli's authenticated driver API. The server checks trip ownership and stop membership, rate-limits requests, then forwards only the route request to the configured Valhalla URL. The Valhalla token stays in the server environment and never ships in the APK.
- **Paid vendor request charges:** The code does not call Google Directions or Mapbox Directions for in-app routes. Google Maps remains an optional driver-selected fallback. Self-hosting removes per-route vendor API billing, but infrastructure, storage, bandwidth, OSM data refresh, monitoring, TLS, backups and incident response still cost money.

The public OpenFreeMap service avoids a per-request key or per-request bill, but a public community endpoint is not Mireli's service-level commitment. Before advertising always-available navigation to a large driver fleet, run a Mireli-owned tile service or contract a provider with an SLA. The map style can be changed without an Android code change by setting the Gradle property at build time. Do not direct app tile traffic to `tile.openstreetmap.org`: the Foundation describes its tile service as best-effort with no SLA and prohibits bulk/offline prefetching. Use OSM data with a service whose terms fit the app, or host the tiles.

## Backend configuration

Set these server-side environment variables in the same environment that runs `msafiri-web`:

```text
VALHALLA_URL=https://<mireli-owned-routing-host>/route
VALHALLA_API_TOKEN=<random secret with at least 32 characters>
```

The endpoint must be HTTPS, have a DNS hostname (not an IP, localhost or `.local`), have no embedded credentials/query/fragment, and end in `/route`. The backend sends the token as a bearer credential. Put an authenticated TLS reverse proxy in front of Valhalla and restrict the VM firewall to the API service's outbound path wherever the hosting platform supports a stable egress allowlist. Do not expose an unauthenticated Valhalla port to the public internet.

Deploy Valhalla with a Mombasa/coastal-Kenya OSM extract that covers every route Mireli actually offers. Keep route ingestion and tile generation separate from request-serving where practical; pin the container image by digest, update OSM extracts deliberately, measure RAM/disk/CPU for the chosen extract and concurrent routing load, and set alerts for disk pressure, request latency, failures and stale data. Test known SGR, North Coast and South Coast routes against the real roads before enabling `navigationOpen`.

The API reports `navigationOpen: true` only when both a valid HTTPS route URL and a sufficiently long server token are configured. This is configuration readiness only; it does not prove that Valhalla is reachable or that Kenyan route guidance is correct. The production feature should remain disabled until the route VM, TLS, authentication, route accuracy, load and failover checks pass.

## Build configuration

Pilot/test builds default to:

```text
https://tiles.openfreemap.org/styles/liberty
```

To point a local build at a Mireli-owned style, provide `MIRELI_MAP_STYLE_URL` as a Gradle project property. Do not place a secret in this value; map style URLs are visible to the app. Before public launch, verify the selected host's usage policy, attribution and operational support, and retain an accessible map fallback if tile requests fail.

## Validation required before production navigation

- [ ] Configure and secure a Mireli-owned Valhalla endpoint; verify its service account and firewall.
- [ ] Confirm `navigationOpen` becomes true, then make authenticated routes to each actual active assignment stop.
- [ ] Test Mombasa Terminus, North Coast and South Coast routes, roundabouts, ferry/bridge approaches, poor GPS, weak data, route recalculation, app resume and map outage on real Android devices.
- [ ] Compare guidance against a local driver who knows the route. OSM/Valhalla directions are advisory; dispatch, posted traffic controls and the driver's judgement take precedence.
- [ ] Stress-test concurrent route calls and establish an acceptable VM size, map source, cache, recovery plan and ongoing budget.
- [ ] Complete the Play location disclosure, privacy notice, Data Safety declaration and permission review against the final behavior.
- [ ] Decide whether the public OpenFreeMap endpoint has adequate support for the launch size or deploy Mireli-owned tiles before the public rollout.
