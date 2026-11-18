# Customer booking lifecycle

## States and permitted transitions

| From | To | Actor/action |
|---|---|---|
| `NEW` | `PENDING` | Allocation poller queues booking |
| `NEW` | `CANCELLED` | Owning customer cancels |
| `PENDING` | `ASSIGNED` | Allocation poller creates a timed partner offer; dispatcher can also directly assign |
| `PENDING` | `REJECTED` | Dispatcher rejects the booking |
| `PENDING` | `CANCELLED` | Owning customer cancels |
| `ASSIGNED` | `CONFIRMED` | Assigned partner accepts |
| `ASSIGNED` | `PENDING` | Partner rejects or offer expires; assignment is cleared and next candidate can be tried |
| `ASSIGNED` | `CANCELLED` | Owning customer cancels |
| `CONFIRMED` | `PARTNER_ON_THE_WAY` | Fresh partner GPS arrives within two hours of the appointment |
| `PARTNER_ON_THE_WAY` | `PARTNER_NEARBY` | Partner enters the 500m geofence |
| `PARTNER_NEARBY` | `PARTNER_ARRIVED` | Partner enters the 100m geofence |
| `PARTNER_ON_THE_WAY` / `PARTNER_NEARBY` / `PARTNER_ARRIVED` | `CANCELLED` | Owning customer cancels before service starts |
| `PARTNER_ARRIVED` | `IN_PROGRESS` | Partner starts service; arrival record is required |
| `CONFIRMED` | `CANCELLED` | Owning customer cancels |
| `IN_PROGRESS` | `COMPLETED` | Customer confirms partner-submitted completion; OTP is required only when configured |
| `REJECTED` | `PENDING` | Dispatcher returns the booking to the queue |

Other transitions are rejected. Each accepted status change uses an expected version and appends a `service_booking_status_history` row in the same transaction. Customer edits are also version-checked and limited to `NEW`/`PENDING`.

## Booking create and ownership

Customers first create a profile tied to the authenticated OIDC JWT subject. Booking creation snapshots customer name/email/phone, stores category and skill code, address, WGS84 coordinates, local appointment date/time, duration, and optional notes. `Idempotency-Key` is required; the existing idempotency service replays an identical response for the same subject/key and rejects a different body under that key.

Customer list/get/update/cancel operations are filtered by that authenticated subject; non-owned bookings return 404. Partner booking reads/actions are filtered by the partner's authenticated subject and assigned partner ID. Dispatch-only actions use the configured dispatcher-subject list.

## Assignment validation

Manual dispatcher assignment accepts only `PENDING` bookings. The partner must be approved, active and currently available, have a verified exact category/skill match, have an active weekly availability window covering the appointment, have no leave for that date, have a current point within their radius, and have no overlapping assigned appointment. A partner row lock serializes assignments to the same partner; booking version/status checks protect concurrent modifications.

Automated candidate search applies location freshness, workload, service radius, skill, hours, leave, overlap, and travel-gap filters. It orders by geographic distance then workload, and records expiring offers in `allocation_attempt`. Travel gap uses a straight-line distance estimate adjusted by the configured road factor and planning speed. Offer delivery is pull-based through partner booking reads; route feasibility is not part of offer confirmation. Partner GPS is keyed through Kafka, filtered, cached in Redis, and recorded in PostGIS location history. Customer tracking is owner-scoped and returns only coarse location, distance, fresh ETA, and journey status; dispatchers can view exact GPS. See [partner tracking](PARTNER_TRACKING.md). The legacy manual assignment API remains a direct assignment path and does not write allocation-attempt history. Service codes remain identifiers rather than an admin-managed service catalogue. Appointment windows use the configured planning timezone and cannot cross midnight.

Service execution records arrival, start, completion submission, customer confirmation, and completion events in a separate service domain. When `ROUTEMESH_SERVICE_COMPLETION_OTP_REQUIRED=true`, partner completion is timestamped but booking closure waits for the customer OTP. Otherwise the partner closes the booking and customer confirmation remains optional. JPEG/PNG/PDF evidence is separately stored and access-controlled; parcel proof-of-delivery is not used for service completion. See [API documentation](API_DOCUMENTATION.md).

## APIs

Implemented paths and request shapes are listed in [API documentation](API_DOCUMENTATION.md). The demo OAuth issuer is only for local development; production identity must be configured separately.
