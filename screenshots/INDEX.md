# SmartRE — live system screenshots

Captured 2026-09-04 against the running local stack (frontend `localhost:3000`, gateway `localhost:8080`).
Chrome via Playwright, 1440x900 viewport at 2x DPR, full-page.

## Public (no auth)

| File | Page |
|---|---|
| 01-home.png | Landing page — hero, category tiles, newest verified listings, how-it-works |
| 02-properties-list.png | `/properties` browse + filters |
| 03-login.png | `/login` |
| 04-register.png | `/register` |
| 05-forgot-password.png | `/forgot-password` |
| 06-property-detail.png | `/properties/{id}` — 6 Bedroom Karen Bungalow |
| 07-seller-profile.png | `/sellers/{id}` — public seller profile |

## Buyer (demo.buyer@smartre.local)

| File | Page |
|---|---|
| 20-buyer-dashboard.png | `/dashboard` |
| 21-buyer-viewings.png | `/viewings` |
| 22-buyer-payments.png | `/payments` |
| 23-buyer-reviews.png | `/reviews` |
| 24-buyer-notifications.png | `/notifications` |
| 25-buyer-profile.png | `/profile` |

## Seller (demo.seller@smartre.local)

| File | Page |
|---|---|
| 30-seller-dashboard.png | `/dashboard` |
| 31-seller-listings.png | `/listings` |
| 32-seller-new-property.png | `/properties/new` |
| 33-seller-verification.png | `/verification` |
| 34-seller-ownership.png | `/ownership` |
| 35-seller-agent-application.png | `/agent-application` |

## Landlord (demo.landlord@smartre.local)

| File | Page |
|---|---|
| 40-landlord-dashboard.png | `/dashboard` |
| 41-landlord-portfolio.png | `/portfolio` — units / tenants / leases / rent / maintenance |
| 42-landlord-my-tenancy.png | `/my-tenancy` |
| 43-landlord-notifications.png | `/notifications` |

## Infrastructure

| File | Page |
|---|---|
| 10-prometheus-targets.png | Prometheus scrape targets, `localhost:9090/targets` |
| 11-kafka-ui.png | Kafka UI cluster dashboard, `localhost:8090` |
| 12-grafana-home.png | Grafana, `localhost:3001` |

## Notes

- The three demo accounts above were created for this capture and have no listings,
  payments or leases, so those pages show empty states.
- Admin pages are not included: admin self-registration is closed once an admin exists,
  so capturing them needs the existing admin account's credentials.
- Swagger UI through the gateway (`/docs/<service>/swagger-ui/index.html`) fails with
  "Failed to load remote configuration" — springdoc's `swagger-initializer.js` hardcodes
  `configUrl: /v3/api-docs/swagger-config`, which is unprefixed and 404s at the gateway root.
- The "E2E Test 3BR Bungalow" listing shows a broken image; its `imageUrls` points at
  `property_image/placeholder.jpg`, which returns 404. Test data, not a rendering bug.
