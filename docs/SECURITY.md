# Security

## Existing controls

Spring Security protects `/v1/**` with JWT authentication except intentionally public health and delivery tracking routes. JWT role claims are allowlisted and mapped to `CUSTOMER`, `SERVICE_PARTNER`, `OPERATIONS_MANAGER`, or `ADMIN`. Trusted subject allowlists can additionally assign admin and operations roles; the existing dispatcher subject setting remains a compatibility administrator role. Driver device endpoints use opaque device tokens whose SHA-256 hashes are stored. Delivery tracking uses unguessable tokens; delivery and service OTPs are HMAC-protected. Idempotency and processed-event records support safe replays.

The Compose OAuth issuer is explicitly a demo service. It issues development tokens and is not a production identity provider. Customer and service-partner sign-in, credential lifecycle, and password hashing are owned by the configured OIDC identity provider; this application does not accept or store passwords. JWT roles may come from `roles`, `authorities`, `realm_access.roles`, or `resource_access.*.roles`. Configure `ROUTEMESH_ADMIN_SUBJECTS`, `ROUTEMESH_OPERATIONS_MANAGER_SUBJECTS`, and `ROUTEMESH_SERVICE_PARTNER_SUBJECTS` for trusted operator/partner subjects. `ROUTEMESH_DISPATCHER_SUBJECTS` remains mapped to the legacy full-privilege dispatcher role. Tokens without an accepted role default to customer access.

Customer profiles and bookings also bind to the caller's JWT subject. Customer reads, updates, and cancellation use that owner key. Partner booking actions require the profile subject to match the booking's assigned partner. Dispatcher-only assignment validates partner eligibility before it writes the assignment. The identity provider remains responsible for customer/partner authentication and credential lifecycle.

## Service booking and location access

Customer booking actions require customer role and booking ownership. Partner self-service and assigned booking actions require service-partner role and subject ownership. Partner approval, account activation, and fleet management require admin; allocation and operations monitoring allow admin or operations manager. Qualification approval records the actor and time, and remains an administrative attestation rather than automated credential validation. Partner-assigned booking responses include customer contact/address details, so assignment ownership checks must remain intact. Customer tracking checks booking ownership and exposes only a coarse H3 area; exact live locations are restricted to operations/admin dashboards. Partner GPS writes and history reads resolve the authenticated subject to its own active, approved partner profile.

Location pings are filtered for future/stale timestamps, duplicates, ordering, and impossible implied speed. Redis latest-location keys expire after 15 minutes. PostGIS history is purged daily after the configurable 30-day retention period.

Service completion OTPs are HMACed with booking-specific context, never stored in plaintext, and mailed only after commit. Invalid attempts are committed to the event history and limited to five. Evidence uploads accept only signature-checked JPEG, PNG, or PDF data below the configured size limit; evidence reads require booking ownership, assigned-partner ownership, or dispatcher authority.

Use environment or managed secret storage, never commit credentials, and replace demo OAuth configuration before deployment.

Authenticated API calls are rate limited per JWT subject using Redis (`ROUTEMESH_API_REQUESTS_PER_MINUTE`, default 300/minute). Public delivery tracking and token-based reschedule/OTP resend have separate token-bucket limits. Authenticated write requests produce security audit log entries with a SHA-256 subject identifier, role, method, path, and response status; request bodies and bearer tokens are not logged. Booking status histories and partner verification records retain business-level audit data.
Service booking tracking verifies customer ownership and returns only a coarse H3 partner area; exact partner coordinates are dispatcher-only. Partners may publish locations only for their own active, approved profile and read only their own history. Location pings are timestamp- and speed-filtered, retained in the location history table, and latest Redis entries expire after 15 minutes.
