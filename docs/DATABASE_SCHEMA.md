# Database schema

Flyway `V1__core.sql` creates the delivery schema: hubs, vehicles, drivers, routes, shipments, route stops, GPS history, tracking events, proof of delivery, geofences, ETA speeds, outbox, processed events and idempotency records. PostGIS geography columns and spatial indexes support location operations.

Additive migrations `V2`–`V10` provide the home-care domains:

- `service_partner`, `partner_skill`, `partner_availability`, `partner_time_off`, and partner location history/current location.
- `customer_profile`, `service_booking`, and `service_booking_status_history`.
- `allocation_attempt` for sequential offers, expiry, response and attempt history.
- `partner_route`, `partner_route_stop`, and route snapshots/history.
- Partner journey tracking state and notification deduplication.
- `service_completion`, `service_completion_event`, and `service_completion_evidence`.
- V9 adds `qualification_details` to each `partner_skill`; the partner adapter persists and reads category-specific qualification records.
- V10 adds route distance and planned-return metrics to `partner_route`, matching the route store's persistence contract.

Customers are keyed by OIDC subject. Bookings retain a customer profile foreign key and assigned-partner relationship, appointment slot and coordinates. Constraints and indexes enforce valid category/status data, ownership joins and common operational lookups. No separate admin service-catalogue or notification table exists: service identifiers are booking values; notifications are emitted as outbox events and delivered by Kafka consumers. Audit data lives in booking status history, partner review metadata, execution events, route history and security logs.

Migrations are additive and preserve delivery records. Flyway V1–V10 have been exercised by the Docker-backed PostgreSQL/PostGIS Testcontainers suite. The live local PostGIS service also reported successful Flyway migrations V1–V8 before these forward migrations were added.
