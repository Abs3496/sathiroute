# SathiRoute: project ka detailed guide

Yeh guide repository ke current implementation ko explain karti hai. Product ko samajhne ka sabse aasaan tareeqa hai ise do connected workflows ke roop mein dekhna: (1) SathiHomeCare ke liye customer booking se partner service completion tak, aur (2) pehle se maujood parcel delivery dispatch se proof-of-delivery tak. Dono workflows ek Spring Boot application, routing/tracking ke shared components aur database infrastructure use karte hain, lekin unke booking/shipment records alag hain.

## 1. Project kya karta hai

SathiRoute Java 17 aur Spring Boot par bana backend hai. Home-care mein customer appointment book karta hai; system suitable partner dhoondh kar ek samay-seemit offer deta hai; partner accept karke customer ke paas jata hai; location/status record hota hai; partner service shuru aur complete karta hai; customer completion confirm kar sakta hai. Dispatcher/admin partner approval, operations aur route planning sambhalte hain.

Delivery side par hubs, vehicles, drivers, shipments, routes, GPS pings, public tracking aur proof-of-delivery ka workflow chalta hai. Isliye project sirf appointment scheduler nahi: yeh service operations aur delivery dispatch dono ko support karta hai.

## 2. Technology aur har component ka kaam

| Hissa | Technology | Is project mein zimmedari |
|---|---|---|
| HTTP API / application | Java 17, Spring Boot 3 | REST endpoints, validation, security, jobs aur application services |
| Persistence | JDBC, PostgreSQL 16 + PostGIS | Customers, bookings, partners, routes, shipments, audit history aur geospatial history ka durable record |
| Schema changes | Flyway V1–V10 | Database ko versioned, repeatable migrations se banana/update karna |
| Fast changing state | Redis 7 | Latest partner/driver location aur rate-limit counters |
| Async messaging | Kafka 3.8 | GPS streams aur transactional outbox se business events |
| Geographic indexing | Uber H3 + PostGIS | Customer ko coarse location dikhana aur spatial query/history rakhna |
| Authentication | OIDC JWT resource server | Identity provider se token verify; application passwords store nahi karti |
| Local email | Spring Mail + Mailpit | Local development mein email/OTP inspect karna |
| Local environment | Docker Compose | API aur uski DB, Redis, Kafka, OAuth demo aur mail dependencies chalana |

## 3. Code ko kaise padhein: domain/application/adapter

`src/main/java/com/routemesh` mein feature ke hisaab se packages hain. Aksar har feature mein teen layers dikhti hain:

1. **domain** – business rule ya model: jaise booking status, partner ranking, route solver, GPS filter. Is layer ko Spring/HTTP/SQL ke details se alag rakhne ka prayas hai.
2. **application** – use case orchestration: jaise `BookingService`, `PartnerService`, `AllocationService`, `ServiceExecutionService`. Yeh domain policy ko store interfaces ke zariye chalata hai.
3. **adapter** – bahari duniya se jodne wala code: REST controllers, JDBC stores, Kafka consumers/producers, Redis implementation. `*Store` interfaces application layer ke contracts hain; `Jdbc*Store` unka PostgreSQL adapter hai.

`shared` mein configuration, security, API error mapping, mail aur rate limiting jaise cross-cutting parts hain. `ArchitectureTest` ArchUnit ke through package dependency boundaries enforce karta hai. `src/main/resources/static` mein bundled operations/demo web console hai; yeh alag frontend build nahi hai.

## 4. Home-care ka end-to-end flow

### A. Partner onboarding

1. Partner authenticated OIDC token ke subject ke saath profile aur Nursing, Ayurvedic Therapy ya Counselling skills submit karta hai.
2. Profile pehle pending rehta hai; dispatcher qualification/skill ko manually review karta hai aur approve/reject karta hai.
3. Partner weekly availability, availability status, service radius, current location aur time-off dates set karta hai.
4. Skill ya qualification badalne par approval reset/deactivation hoti hai, taaki badli hui details dobara review hon.

Qualification approval ek administrative attestation hai; koi external licensing registry se automatic verification nahi hoti.

### B. Customer booking

1. Customer apne JWT subject se customer profile banata hai.
2. Booking request mein service category/skill code, address/coordinates, date/time, duration aur optional notes aate hain. Contact details booking par snapshot hoti hain; baad mein profile edit se purani booking snapshot nahi badalti.
3. `Idempotency-Key` duplicate create request ko safe banati hai: wahi subject/key/body dobara aaye to pehle response ko replay kiya ja sakta hai; same key par alag body conflict deti hai.
4. Booking row mein status aur version hota hai. Updates/status changes expected version ke saath hote hain, isliye stale client kisi naye change ko chupchaap overwrite nahi karta.

### C. Allocation aur offer

Allocation poller NEW bookings ko PENDING queue mein laata hai, expired offers ko clear karta hai, aur candidates nikalta hai. Candidate ko active, available, approved/verified exact skill, requested appointment ko cover karti availability, us din leave na hona, service radius, fresh location, workload, doosri appointment se overlap aur travel-gap checks pass karne hote hain. Domain ranking mein pehle seedhi-line distance, phir workload, phir stable partner ID tie-break hota hai.

Pehle eligible partner ko expiring ASSIGNED offer milta hai. Offer partner booking list se pull karta hai. Accept par CONFIRMED; reject ya timeout par booking dobara PENDING hokar agla candidate try kar sakti hai. Attempt cap hit hone par REJECTED hota hai. Local defaults YAML mein hain (5 minute offer TTL, 15 minute location freshness, workload 8, adhiktam 5 attempts, 15 minute travel buffer, 5 second polling); env vars se tune kiye ja sakte hain.

Operational caveat: candidate query aur offer persistence ke beech partner facts badal sakte hain; docs mein eligibility ko lock ke andar dobara validate karna hardening item bataya gaya hai. Offer ranking route solver nahi chalati. Manual dispatcher assignment ek alag direct path hai aur automated offer attempt history nahi banati.

### D. Partner route aur service execution

Daily partner route confirmed/offered/in-progress visits ko ek vehicle ke time-window problem ke roop mein model karti hai. Appointment start exact hai; service duration, shift bounds aur estimated travel time solver ko diye jaate hain. Route snapshots/history save hote hain. Cancellation, completion ya reassignment par reconciliation hoti hai; infeasible route ko overwrite karne ke bajay dispatcher review ke liye purana route reh sakta hai.

Partner arrival record karta hai; booking journey `PARTNER_ON_THE_WAY → PARTNER_NEARBY → PARTNER_ARRIVED` tak aa sakti hai. GPS geofence thresholds default 500 m (nearby) aur 100 m (arrived) hain. Arrival ke baad service start karke IN_PROGRESS hoti hai. Partner completion notes submit karta hai; optional photo/PDF evidence alag execution record mein jaata hai. Agar completion OTP enabled hai, customer ko code bheja jaata hai aur sahi code se booking COMPLETED hoti hai. OTP off ho to explicit customer confirmation se close hoti hai. OTP HMAC form mein store hota hai, plaintext mein nahi.

### E. Home-care status lifecycle

`NEW → PENDING → ASSIGNED → CONFIRMED → PARTNER_ON_THE_WAY → PARTNER_NEARBY → PARTNER_ARRIVED → IN_PROGRESS → COMPLETED`

Cancellation/rejection kuch pre-service states se allowed hai. Partner reject/offer expiry ASSIGNED ko PENDING mein la sakta hai. Har accepted status transition version check aur `service_booking_status_history` mein audit record ke saath hota hai. Service execution events/evidence, status history se alag tables mein hain.

## 5. Existing delivery flow

Delivery domain mein `fleet`, `shipment`, `routing`, `tracking`, aur `pod` packages milenge. Dispatcher hub, vehicle, driver aur driver device credentials provision karta hai; shipments create/track/cancel hote hain; routing daily plans, route insertion aur auto-dispatch support karti hai; driver device sequenced GPS/status batches bhejta hai; recipient public tracking token se status dekh sakta aur kuch reschedule/OTP actions kar sakta hai; delivery ke waqt POD location/OTP validation ke saath submit hota hai. Iska POD workflow home-care ke `service execution` se alag hai.

## 6. Routes/optimization ka logic

`routing.domain.Solver` time-window, capacity, delivery/pickup constraints wale VRP ko heuristic se solve karta hai. Scratch solve cheapest insertion aur nearest-neighbour starts banata hai, phir 2-opt aur routes ke beech relocate local search chala kar kam unassigned jobs, phir kam distance wala plan rakhta hai. Existing route mein naye jobs insert karne par pehle ka stop order preserve karke feasible insertion aur changed routes par local search hoti hai. Solve budget configurable hai, isliye yeh exact global optimum ki guarantee nahi deta.

Travel model Haversine straight-line distance ko road-factor se multiply karke assumed planning speed se divide karta hai. Yeh road-network route ya live traffic nahi hai; ETA/feasibility planning estimate hai. Partner daily route ki availability windows agar din mein multiple hon to current implementation earliest start aur latest end se envelope banati hai, beech ka gap model nahi karti.

## 7. GPS, tracking aur async events

Partner pings authenticated partner ke apne profile se linked hote hain. Pings Kafka ke zariye process hote hain; timestamp, ordering/duplicate aur implausible speed filters lagte hain. Latest location Redis mein TTL ke saath hoti hai; durable history PostGIS mein. Customer booking tracking owner check ke baad H3-coarsened area, distance, fresh ETA/journey status deta hai. Dispatcher dashboard exact live location dekh sakta; partner apni history dekh sakta hai. History retention/purge configurable hai.

Transactional outbox ka maksad business DB commit aur event publishing ke beech lost-event risk kam karna hai: business mutation aur outbox row saath commit hote hain; relay baad mein Kafka ko publish karta hai; consumer processed-event IDs se duplicate delivery ko deduplicate karta hai. Email consumer Mailpit/SMTP ke through deliver karta hai. Har workflow email nahi bhejta: allocation offers pull-based hain, SMS/push provider configured nahi hai.

## 8. Database aur consistency

V1 delivery ke core tables banata hai; V2–V10 home-care partner, booking, allocation, route-history, tracking, service-execution/evidence, qualifications aur route metrics add karte hain. Flyway migration files `src/main/resources/db/migration` mein hain. PostgreSQL system of record hai; Redis latest/read-optimized state hai. Booking status history, allocation attempts, route snapshots, service execution events aur security audit logs alag audit traces dete hain.

Correctness ke mukhya patterns: DB transaction, optimistic expected-version check, idempotency key, partner/booking row locks kuch assignment paths mein, outbox event, aur consumer deduplication. Har path mein same protection assume na karein—allocation documentation candidate selection/persistence race ko explicitly note karta hai.

## 9. API aur access control

REST endpoints `/v1` se shuru hote hain (`/api/v1` nahi). Exact method, path, request fields aur role ke liye [API documentation](API_DOCUMENTATION.md) dekhein. High-level groups:

- `/v1/customers`, `/v1/bookings`: customer profile, booking, cancel/update, tracking, completion confirmation.
- `/v1/partners/me`: partner profile, skill, availability, time off, location, workload/history/bookings.
- `/v1/admin/...`: partner review, assignment, route recalculation, service monitoring aur exact tracking.
- `/v1/hubs`, `/v1/shipments`, `/v1/routes`, `/v1/drivers`, `/v1/track`: legacy delivery operations.

OIDC provider login, credentials aur password hashing sambhalta hai. API bearer JWT verify karti hai; roles accepted claim shapes se map hote hain aur configured trusted subject allowlists se bhi role diya ja sakta hai. Customer/partner records token ke `sub` se bind hain; doosre user ki booking par aam taur par 404 milta hai. Driver device APIs opaque device token use karti hain. Rate limits Redis mein per subject/token bucket se hain. Demo OAuth sirf local development ke liye hai.

## 10. Local setup aur chalana

Prerequisites: Java 17, Maven 3.9+, Docker Engine aur Compose v2. `.env.example` ko `.env` copy karke local DB password, Redis password aur kam-se-kam 32 random bytes ka base64 OTP key set karein. Phir:

```sh
docker compose --env-file .env up --build
```

Local ports loopback par: API/web console `127.0.0.1:8970`, development OAuth issuer `:8971`, Mailpit UI `:8972`. `http://127.0.0.1:8970/` par console khulta hai. Local Compose par dashboard clearly-labelled demo dataset `/dev/demo/**` se auto-load karta hai; isse bookings, partners, shipments, routes, reports aur status actions bina OIDC token ke try kiye ja sakte hain. Demo changes memory-only hain aur restart/Settings reset par wapas default ho jaate hain. Real records ke liye **Connect API** se OIDC bearer token use karein; production OIDC issuer aur roles alag configure karein. Demo API ka config default false hai aur production mein enable na karein.

Dependencies pehle se chal rahi hon to `src/main/resources/application.yml` ke `ROUTEMESH_*` values configure karke `mvn spring-boot:run` chala sakte hain. Configuration mein DB URL/user/password, Redis, Kafka, SMTP, issuer, planning/tracking/allocation/security knobs hain.

## 11. Verification aur tests

`mvn clean test` unit aur architecture tests chalata hai. `mvn verify` Failsafe/Testcontainers integration flows bhi chalata hai, jisme Docker chahiye. `mvn clean package` packaged application banata hai. Integration tests PostgreSQL/PostGIS aur Kafka containers ke saath delivery, concurrency aur service booking flows exercise karte hain.

## 12. Deployment aur current boundaries

Dockerfile multi-stage Maven build karta hai aur non-root Java 17 runtime chalata hai; readiness health check actuator par hai. Compose single-node local development stack hai, production deployment nahi. Production mein real OIDC, TLS, secured/managed Postgres/Redis/Kafka, secrets manager, backup/restore, monitoring aur capacity plan chahiye.

Current product boundaries: service codes request identifiers hain, managed catalogue nahi; offer ranking road-route feasibility nahi chalati; travel estimate straight-line based hai; push/SMS integration nahi; demo identity provider production ke liye nahi. Workspace README ke mutabik yahan `.git` aur LICENSE nahi hai, isliye publish karne se pehle original checkout mein ownership, upstream license, history aur secrets verify karein.

## 13. Agla code-reading path

1. `Main.java`, `shared/config`, `shared/security` se app boot/config/auth samjhein.
2. `booking/adapter/BookingController.java` phir `booking/application/BookingService.java` aur `booking/adapter/JdbcBookingStore.java` dekhein.
3. `allocation/application/AllocationService.java`, `booking/adapter/JdbcBookingStore.java`, `allocation/domain/PartnerAllocator.java` padhein.
4. `routing/domain/Solver.java`, `routing/application/RoutingService.java` aur `PartnerRouteService.java` compare karein.
5. `tracking/application` ke services/filter, Kafka adapters, Redis latest store aur PostGIS JDBC store dekhein.
6. V1–V10 migrations aur sambandhit `*IT.java` integration tests se persistence aur full workflow trace karein.

Related reference docs: [API](API_DOCUMENTATION.md), [architecture](ARCHITECTURE.md), [allocation](ALLOCATION_ENGINE.md), [route optimization](ROUTE_OPTIMIZATION.md), [lifecycle](BOOKING_LIFECYCLE.md), [schema](DATABASE_SCHEMA.md), [security](SECURITY.md), [deployment](DEPLOYMENT.md), [partner tracking](PARTNER_TRACKING.md).
