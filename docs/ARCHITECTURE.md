# Architecture

SathiRoute keeps the existing delivery dispatch product and adds a distinct service booking domain. Spring Boot modules separate domain policies, application services, and adapters; ArchUnit tests enforce dependency boundaries.

## Platform components

PostgreSQL/PostGIS is the system of record and Flyway owns schema changes. Redis stores current locations and rate-limit buckets. Kafka carries tracking and business events. A transactional outbox publishes committed events; consumers record processed event IDs for deduplication. JDBC is used for persistence.

## Service domains

- `partner`: OIDC-subject-bound partner profile, category qualifications, review, weekly availability, time off, location and history.
- `booking`: customer profile, owned/idempotent bookings, status transitions, ownership and manual operations.
- `allocation`: eligibility and ranking policy; partner booking adapter coordinates sequential offers, expiry/retry and allocation history.
- `routing`: shared time-window solver used for delivery routes and partner daily visit routes.
- `tracking`: device and partner GPS ingestion, filtering, current-location read model and ETA.
- `service execution`: arrival, start, completion submission/confirmation, OTP and evidence.
- `outbox`: transactional event dispatch and consumer deduplication.

## Service workflow and limits

Bookings are allocated using active/admin-approved state, verified requested skills, availability/time off, radius, location freshness, workload, appointment overlap, and estimated travel gap. Offers are sequential and expire; rejection/timeout returns the booking to allocation. Ranking does not call the route solver, so route feasibility is validated during route planning rather than offer ranking/acceptance.

The route solver models work windows, service duration and straight-line travel estimates. It does not use road-network traffic. Customers see a coarse H3 location; exact partner coordinates are restricted to operations/admin. Service completion is separate from parcel POD. Email uses the outbox/Kafka path; SMS and push require provider integrations.

Authentication and credential lifecycle are delegated to OIDC. APIs are under `/v1`; there is no application-owned login/password API.
