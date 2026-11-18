# Partner tracking and ETA

## Location ingestion

An active, approved partner posts up to 200 timestamped GPS fixes to `POST /v1/partners/me/location:pings`. The API verifies the JWT subject against the partner profile and publishes fixes to the `sathihomecare.partner-location.v1` Kafka topic, keyed by partner ID so each partner's fixes remain ordered. The consumer rejects stale, future, duplicate, out-of-order, and impossible-speed fixes using the shared `PingFilter`; accepted locations update `service_partner`, append `partner_location_history`, and refresh a Redis latest-location key with a 15-minute TTL. A daily job purges location history after the configurable 30-day retention window.

## Booking statuses and notifications

For a confirmed booking, GPS movement within two hours of the appointment can advance `CONFIRMED` to `PARTNER_ON_THE_WAY`. Entering the 500m nearby geofence changes it to `PARTNER_NEARBY`; entering the 100m arrival geofence changes it to `PARTNER_ARRIVED`. Each transition is version-checked and recorded in the normal booking status history. Customer emails for on-the-way, nearby and arrival transitions are published through the service notification outbox and deduplicated by event id. The partner starts and completes service with the service execution endpoints.

The customer tracking response maps booking states to `PARTNER_ASSIGNED`, `PARTNER_ACCEPTED`, `PARTNER_ON_THE_WAY`, `PARTNER_NEARBY`, `PARTNER_ARRIVED`, `SERVICE_STARTED`, and `SERVICE_COMPLETED`. It validates booking ownership (dispatchers may also read), returns the partner's coarse H3 area rather than exact coordinates, and includes distance and a model-based ETA only while the service journey is visible and the location is fresh. Dispatchers can see exact locations and active journeys. Partners can read their own location history.

## Endpoints

- `POST /v1/partners/me/location:pings` — `{ "fixes": [{ "latitude": 12.9, "longitude": 77.6, "at": "2026-11-18T08:00:00Z" }] }`
- `GET /v1/partners/me/location-history?limit=100`
- `GET /v1/bookings/{bookingId}/tracking` — booking owner or dispatcher
- `GET /v1/admin/tracking/partners` — live dispatcher view with exact location
- `GET /v1/admin/partners/{partnerId}/location-history?limit=100`

ETA uses the configured road factor and planning speed. There is no traffic-aware road graph. Tracking pings update partner position and journey status; `PARTNER_ARRIVED` is not a service-start signal, which remains an explicit partner action. Mail delivery depends on the configured SMTP service.
