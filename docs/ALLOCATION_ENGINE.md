# Partner allocation engine

## Implemented workflow

`AllocationService` polls NEW/PENDING future bookings, moves NEW bookings to PENDING, expires elapsed offers and selects the next candidate. `JdbcBookingStore` searches approved, active, available partners with a fresh location, verified exact skill, weekly availability covering the appointment, no leave, radius eligibility, workload below the configured cap, and no overlapping appointment or insufficient estimated travel gap. Candidates are ordered by geographic distance, current workload and UUID. The first candidate receives a persisted ASSIGNED offer and an `allocation_attempt` row with expiry. Partner accept confirms the booking; reject or timeout returns it to PENDING, and later polling tries another partner. Attempts and booking status changes are persisted transactionally.

Settings are under `routemesh.allocation`: offer TTL (5m), max location age (15m), workload cap (8), attempt cap (5), travel buffer (15m), and poll interval (5s). Environment overrides use the corresponding `ROUTEMESH_ALLOCATION_*` names in `application.yml`.

## Limits and operational notes

Offer delivery is pull-based: an assigned partner sees the offer through their booking list. No push, SMS, email, or Kafka notification is emitted by this workflow. ETA/travel compatibility uses straight-line distance multiplied by `planning.road-factor` and divided by `planning.planning-kmh`; it is a conservative scheduling estimate, not a road-network route. If there are no eligible partners, a booking remains PENDING for later polling. Reaching the configured offer attempt cap changes it to REJECTED.

The automated loop does not yet expose allocation attempt history through an API or provide an admin override with a reason. The legacy dispatcher manual-assignment endpoint remains a direct assignment path and does not create an offer attempt. Allocation locks the booking and candidate partner row while persisting an offer; selection and insertion are separate steps, so partner facts can change between the candidate query and offer transaction. A production hardening pass should revalidate all eligibility facts under those locks and route bookings through route feasibility before confirmation.
