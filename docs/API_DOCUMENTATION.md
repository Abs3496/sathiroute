# API documentation

The application exposes APIs under `/v1`. Routes require a bearer JWT from the configured OAuth2/OIDC issuer. Customer and partner APIs enforce the matching JWT role plus subject ownership. Accepted role claims include `roles`, `authorities`, `realm_access.roles`, and `resource_access.*.roles`. Configure `ROUTEMESH_ADMIN_SUBJECTS`, `ROUTEMESH_OPERATIONS_MANAGER_SUBJECTS`, `ROUTEMESH_SERVICE_PARTNER_SUBJECTS`, and the legacy full-privilege `ROUTEMESH_DISPATCHER_SUBJECTS` for trusted identities. Registration binds a profile to the token's `sub`; there is no local password or login endpoint. Authenticated requests are rate limited per subject using Redis (default 300/minute).

## Local showcase API

The Compose development stack enables an isolated, in-memory showcase API at `/dev/demo/**` so the dashboard can be tried without an OIDC token. Its records are intentionally synthetic, marked as **Demo data** in the UI, and never written to PostgreSQL. They reset when the app restarts or when `POST /dev/demo/reset` is called. The API is disabled by default (`ROUTEMESH_DEMO_API_ENABLED=false`) and must not be enabled in production.

| Method and path | Behavior |
|---|---|
| `GET /dev/demo/workspace` | Full dashboard dataset: bookings, partners, journeys, routes, shipments, services, metrics, and activity. |
| `GET /dev/demo/reports` | Summary report counts for the current demo records. |
| `POST /dev/demo/reset` | Restore the initial showcase dataset. |
| `POST /dev/demo/bookings`, `PATCH /dev/demo/bookings/{id}` | Create a demo booking or advance its status. |
| `POST /dev/demo/partners`, `PATCH /dev/demo/partners/{id}` | Add a pending demo partner or approve/activate it. |
| `POST /dev/demo/shipments`, `PATCH /dev/demo/shipments/{id}` | Create a demo shipment or update its delivery status. |
| `POST /dev/demo/services`, `PATCH /dev/demo/services/{id}` | Add an active demo service offering or enable/pause a demo service. |

## Partner management APIs

| Method and path | Access | Behavior |
|---|---|---|
| `POST /v1/partners` | Authenticated JWT | Register a pending profile. Body: `centreId`, `name`, `email`, `phone`, `qualificationDetails`, `serviceRadiusKm`, and `skills` (`category`, `code`, `qualificationDetails`). |
| `GET /v1/partners/me` | Own JWT subject | Read own profile, skills/review state, availability, and location. |
| `PATCH /v1/partners/me` | Own JWT subject | Update contact/profile, qualification summary, and service radius. Changing qualification details resets approval and active state. |
| `PUT /v1/partners/me/skills` | Own JWT subject | Replace category-specific skills; each skill edit resets review and deactivates the profile. Categories: `NURSING`, `AYURVEDIC_THERAPY`, `COUNSELLING`. |
| `PUT /v1/partners/me/availability` | Own JWT subject | Replace weekly windows; each window has `dayOfWeek` (1–7), `startsAt`, and `endsAt`. Overlapping windows are rejected. |
| `PATCH /v1/partners/me/availability/status` | Own JWT subject | Set `available`; becoming available requires an approved, active profile. |
| `PATCH /v1/partners/me/location` | Own active, approved profile | Update latitude/longitude and append a location history point. |
| `POST /v1/partners/me/time-off` | Own JWT subject | Add a future/today unavailable date and optional reason. |
| `DELETE /v1/partners/me/time-off/{date}` | Own JWT subject | Remove an unavailable date. |
| `GET /v1/partners/me/time-off?from=&to=` | Own JWT subject | List unavailable dates in an inclusive range. |
| `GET /v1/partners/me/history?limit=50` | Own JWT subject | Read assigned service booking history (up to 100). |
| `GET /v1/partners/me/workload` | Own JWT subject | Read upcoming and completed booking counts. |
| `POST /v1/partners/me/location:pings` | Own active, approved partner JWT | Queue timestamped GPS fixes to Kafka; accepted fixes are filtered and written to Redis/PostGIS. |
| `GET /v1/partners/me/location-history?limit=100` | Own partner JWT | Read recent persisted GPS history. |
| `GET /v1/admin/partners?status=PENDING` | Dispatcher subject | List profiles, optionally filtered by `PENDING`, `APPROVED`, or `REJECTED`. |
| `PATCH /v1/admin/partners/{id}/verification` | Dispatcher subject | Body `{"approved":true|false}`; records reviewer/time and toggles submitted skill verification. |
| `PATCH /v1/admin/partners/{id}/status` | Dispatcher subject | Body `{"active":true|false}`; activation requires approval. |

The identity provider handles partner login and token issuance. Qualification approval is a manual dispatcher attestation. Allocation consumes availability, time off, location, and workload when selecting an offer.

## Service catalogue APIs

| Method and path | Access | Behavior |
|---|---|---|
| `GET /v1/admin/services` | Admin, dispatcher, or operations manager | List active and paused Nursing, Ayurvedic Therapy, and Counselling offerings. |
| `POST /v1/admin/services` | Admin or dispatcher | Create an active offering with `category`, `name`, and optional `serviceCode` and `description`. Codes are unique. |
| `PATCH /v1/admin/services/{id}` | Admin or dispatcher | Update `name` and/or `description`, and set `active` to pause or reactivate an offering. |

Default category entries are seeded by migration. Changes are stored in PostgreSQL and creation records the caller subject. These catalogue controls do not replace partner skill verification or booking allocation.

## Customer and booking APIs

| Method and path | Access | Behavior |
|---|---|---|
| `POST /v1/customers` | Authenticated JWT | Create the caller's customer profile (`name`, `email`, `phone`). |
| `GET /v1/customers/me` | Own JWT subject | Read the caller's profile. |
| `PATCH /v1/customers/me` | Own JWT subject | Update profile fields; existing booking contact details remain a snapshot. |
| `POST /v1/bookings` | Own customer profile + JWT | Create `NEW` booking. Requires `Idempotency-Key`. Body: `serviceCategory`, `requiredSkill`, `appointmentDate`, `appointmentStart`, `serviceDurationMinutes`, `address`, `latitude`, `longitude`, optional `notes`. Date/time use configured planning timezone; duration is 1–1440 minutes; appointments must be in the future, within 180 days, and finish the same day. |
| `GET /v1/bookings?limit=50&offset=0` | Own JWT subject | Paginated customer booking history. |
| `GET /v1/bookings/{id}` | Booking owner | Read a booking; non-owned IDs return 404. |
| `PATCH /v1/bookings/{id}` | Booking owner | Update category/skill, slot, duration, address, coordinates, or notes while status is `NEW`/`PENDING`. Requires `expectedVersion`. |
| `PATCH /v1/bookings/{id}/cancel` | Booking owner | Cancel `NEW`, `PENDING`, `ASSIGNED`, or `CONFIRMED` booking. Requires `expectedVersion`; cancellation after start is rejected. |
| `POST /v1/admin/bookings/{id}/allocation-start` | Dispatcher subject | Move `NEW` or `REJECTED` booking to `PENDING`. |
| `POST /v1/admin/bookings/{id}/reject` | Dispatcher subject | Reject `PENDING` or `ASSIGNED` booking; requires `expectedVersion` and reason. |
| `PATCH /v1/admin/bookings/{id}/assign` | Dispatcher subject | Assign a specified `partnerId` to a `PENDING` booking; requires `expectedVersion`, and the partner must satisfy current skill, approval, availability, leave, radius and overlap checks. |
| `GET /v1/partners/me/bookings?limit=50&offset=0` | Assigned partner subject | List bookings assigned to this partner. |
| `GET /v1/partners/me/bookings/{id}` | Assigned partner subject | Read assigned booking details needed to serve the customer. |
| `POST /v1/partners/me/bookings/{id}/accept` | Assigned partner subject | Move `ASSIGNED` to `CONFIRMED`; requires `expectedVersion`. |
| `POST /v1/partners/me/bookings/{id}/reject` | Assigned partner subject | Move `ASSIGNED` to `REJECTED`; clears the assignment and requires `expectedVersion`. |
| `POST /v1/partners/me/bookings/{id}/arrive` | Assigned partner subject | Confirm arrival; advances journey status to `PARTNER_ARRIVED` and records arrival time. Requires `expectedVersion`. |
| `POST /v1/partners/me/bookings/{id}/start` | Assigned partner subject | Requires `PARTNER_ARRIVED`, records start time, and moves booking to `IN_PROGRESS`. |
| `POST /v1/partners/me/bookings/{id}/complete` | Assigned partner subject | Records completion timestamp/notes and asks the customer to confirm; booking closure waits for customer confirmation. Body: `expectedVersion`, optional `completionNotes`, optional `reason`. |
| `POST /v1/partners/me/bookings/{id}/evidence` | Assigned partner during service | Store a base64 JPEG, PNG, or PDF attachment (default max 2 MB). |
| `GET /v1/bookings/{id}/tracking` | Booking owner or dispatcher | Read customer-safe status, coarse partner area, distance, and fresh ETA. |
| `GET /v1/bookings/{id}/service-execution` | Booking owner, assigned partner, or dispatcher | Read arrival/start/completion timestamps, notes, event history, and evidence metadata. |
| `POST /v1/bookings/{id}/completion-confirmation` | Booking owner | Close the booking after confirmation; send `{ "otp": "123456" }` when OTP is enabled, otherwise `{ "confirmed": true }`. |
| `GET /v1/bookings/{id}/evidence` and `/evidence/{evidenceId}` | Booking owner, assigned partner, or dispatcher | List or download service evidence. |
| `GET /v1/admin/service-executions?status=IN_PROGRESS` | Dispatcher subject | Monitor assigned, active, and completed services (maximum 500). |
| `GET /v1/admin/tracking/partners` | Dispatcher subject | Read active journeys with exact partner coordinates. |
| `GET /v1/admin/partners/{partnerId}/location-history?limit=100` | Dispatcher subject | Read a partner's persisted location history. |
| `GET /v1/partners/{partnerId}/routes/{date}` | Own partner subject or dispatcher | Read the optimized daily visit order, appointment times, planned arrival, and distance. |
| `POST /v1/admin/partners/{partnerId}/routes/{date}/recalculate` | Dispatcher subject | Rebuild the day route using the shared time-window solver; returns conflict if visits cannot fit. |
| `GET /v1/admin/partner-routes/{routeId}/history` | Dispatcher subject | Read route status/version changes and their stop snapshots. |

Partner decision bodies use `{"expectedVersion": 2, "reason": "optional explanation"}`. Booking status changes are recorded in `service_booking_status_history`; execution-specific events and evidence metadata are stored separately. Set `ROUTEMESH_SERVICE_COMPLETION_OTP_REQUIRED=true` to require a customer code before closing a booking. OTP hashes are booking-scoped HMACs; wrong attempts are recorded and limited to five.

Booking skill codes remain submitted identifiers and are not automatically linked to catalogue entries. Allocation offers are pull-based through partner booking reads. Route reconciliation runs every 30 seconds for the next 30 days; strict route recalculation is available to dispatchers.

## Existing delivery APIs

| Area | Existing capability |
|---|---|
| Hubs and fleet | Create hubs, vehicles, drivers, and device credentials |
| Shipments | Idempotent delivery/return creation, lookup, and lifecycle commands |
| Routing | Daily plans, route insertion, and auto-dispatch |
| Driver device | Batch ping and sequenced shipment status events |
| Tracking | Public delivery tracking link and reschedule/OTP resend actions |
| POD | Delivery proof submission with OTP and location validation |

Consult Spring controller mappings in `src/main/java/com/routemesh/*/adapter` for request DTOs and exact paths. JWT or device token requirements vary by route and are enforced in `SecurityConfig`.

Partner GPS is keyed and consumed through a separate Kafka topic. Fresh locations are cached in Redis and stored in PostGIS history; booking tracking uses coarse H3 location for customers and exact location for dispatchers. Booking event email notifications use the transactional outbox and Kafka. Tracking thresholds and TTL are configurable under `routemesh.tracking`.

## Complete delivery and platform route index

The home-care routes and their request/access details are listed above. This index adds the exact paths for the existing delivery APIs. Driver device routes require the issued device credential; public tracking routes use an unguessable tracking token; remaining operational routes require their configured JWT roles. See `SecurityConfig` and each controller for the enforcement details.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v1/hubs` | Create an operating hub |
| `POST` | `/v1/hubs/{hubId}/vehicles` | Add a vehicle and shift/capacity data |
| `POST` | `/v1/hubs/{hubId}/drivers` | Add a driver |
| `POST` | `/v1/drivers/{driverId}/devices` | Issue a driver device credential |
| `POST` | `/v1/shipments` | Create an idempotent delivery/return shipment |
| `GET` | `/v1/shipments/{id}` | Read shipment details |
| `POST` | `/v1/shipments/{id}/cancel` | Cancel an eligible shipment |
| `POST` | `/v1/hubs/{hubId}/plans` | Plan a hub's service date |
| `GET` | `/v1/routes/{id}` | Read route and stops |
| `POST` | `/v1/routes/{id}/insertions` | Insert a late shipment into a planned route |
| `POST` | `/v1/routes/{id}/dispatch` | Assign a route to a driver |
| `POST` | `/v1/dispatch:auto` | Find a feasible route for one shipment |
| `GET` | `/v1/drivers/{driverId}/route` | Read the authenticated driver's active route |
| `POST` | `/v1/drivers/{driverId}/pings:batch` | Submit sequenced device GPS fixes |
| `POST` | `/v1/drivers/{driverId}/events:batch` | Submit ordered shipment status events |
| `POST` | `/v1/shipments/{shipmentId}/events` | Add an operational shipment event |
| `GET` | `/v1/shipments/{shipmentId}/timeline` | Read shipment event timeline |
| `POST` | `/v1/shipments/{shipmentId}/pod` | Submit proof of delivery |
| `GET` | `/v1/track/{token}` | Read customer-safe public delivery tracking |
| `POST` | `/v1/track/{token}/reschedule` | Reschedule through the public tracking token |
| `POST` | `/v1/track/{token}/otp:resend` | Request another delivery code |
| `POST` | `/dev/session` | Local development-only token convenience endpoint; disabled unless explicitly enabled |
| `GET` | `/actuator/health` | Application health |
| `GET` | `/actuator/health/readiness` | Container readiness probe |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

The bundled operations console is intentionally not a fixture generator: all displayed booking, partner, route, tracking, and summary data comes from API responses. Empty state means the connected account has no accessible records yet.
