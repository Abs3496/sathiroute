# SathiHomeCare Partner Allocation & Route Management System

**SathiRoute** is a Java 17 backend for SathiHomeCare partner allocation, appointment scheduling, route optimization, tracking, and service execution. The existing delivery dispatch workflow remains available alongside home-care services.

## Current implementation

- Customers register profiles and create/update/cancel their own versioned, idempotent bookings for Nursing, Ayurvedic Therapy, and Counselling.
- Partners register using an authenticated identity-provider subject, submit category skills and qualifications, maintain availability/time off/location, and view their history/workload. Administrators review and activate partners.
- Allocation filters for approval, active/available state, verified skill, schedule/time off, radius, fresh location, workload, conflicts, and travel gaps. Eligible candidates are ranked, offered sequentially, and retried after rejection/timeout.
- Daily partner routes reuse the time-window solver with appointment windows, service durations, travel estimates, working hours, route snapshots, and cancellation/reassignment reconciliation.
- Partner location uses Kafka, Redis latest-fix caching, PostGIS history, stale/outlier filtering, geofence events, H3-coarsened customer views, and dispatcher live views.
- Partners record arrival/start/completion; customers confirm completion. Optional OTP and evidence attachments are supported. Booking and service events feed a transactional outbox and Kafka email notifications.
- JWT role/subject authorization, owner checks, Redis rate limiting, idempotency, database history, and security audit logs protect APIs.

Limits: service names/codes are request identifiers, not an admin-managed catalogue. Offer ranking does not incorporate route feasibility. ETA uses a straight-line travel model rather than road-network traffic. Push and SMS providers are not configured. Login, credential management, and password hashing are provided by the configured OIDC identity provider; there is no local `/auth` login API.

## Architecture and APIs

Spring Boot modules use domain/application/adapter boundaries, JDBC persistence, PostgreSQL/PostGIS, Flyway, Redis, Kafka, H3, and the configured OIDC issuer. APIs are under `/v1` (not `/api/v1`); see [API documentation](docs/API_DOCUMENTATION.md), [architecture](docs/ARCHITECTURE.md), [allocation](docs/ALLOCATION_ENGINE.md), and [route optimization](docs/ROUTE_OPTIMIZATION.md).

For a detailed end-to-end project walkthrough in Hindi/Hinglish—including architecture, home-care and delivery flows, routing, tracking, security, local setup, and current limits—see the [detailed project guide](docs/PROJECT_GUIDE_HI.md).

The booking lifecycle is `NEW → PENDING → ASSIGNED → CONFIRMED → IN_PROGRESS → COMPLETED`, with cancellation/rejection transitions. See [booking lifecycle](docs/BOOKING_LIFECYCLE.md).

## Local development

Prerequisites: Java 17, Maven 3.9+, Docker Engine and Compose v2. Copy `.env.example` to `.env`, set local values, then run:

```sh
docker compose --env-file .env up --build
```

The API and web console bind to `127.0.0.1:8970`, demo OAuth to `127.0.0.1:8971`, and Mailpit to `127.0.0.1:8972`. The OAuth issuer is only for development. On Windows, helper scripts require Git Bash or WSL.

Open `http://127.0.0.1:8970/` for the web console. Without credentials it shows labeled preview data. Use **Connect API** to paste a bearer access token for live data. A customer-role token can create bookings; admin/operator permissions are required for operations panels. Authentication remains with the configured OIDC provider.

To run against provisioned dependencies, configure the `ROUTEMESH_*` values documented in `src/main/resources/application.yml` and run `mvn spring-boot:run`.

## Verification

```sh
mvn clean test       # unit and architecture tests
mvn verify           # unit and Docker/Testcontainers integration tests
mvn clean package
```

Integration tests require Docker and include delivery scenarios and the end-to-end service booking workflow. Database migrations V1–V10 are exercised against PostgreSQL/PostGIS in integration tests.

## Security and publication

See [security](docs/SECURITY.md), [database schema](docs/DATABASE_SCHEMA.md), and [deployment](docs/DEPLOYMENT.md). Do not commit real credentials. Replace the local demo issuer before deployment.

Developer/adaptation attribution: Abhishek Pandey. This workspace has no `.git` directory and no `LICENSE` file. Source ownership/upstream licensing, commit history, remotes, and committed-secret history must be verified in the original Git checkout before publication. No license is asserted here.
