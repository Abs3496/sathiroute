# Booking notifications

Service booking lifecycle changes append notification envelopes to `routemesh.service-events` in the same PostgreSQL transaction as the booking change. The outbox relay publishes committed rows to Kafka; the service notifier consumer records each `eventId` in `processed_event` before sending SMTP email. This keeps rolled-back booking changes from producing notifications and makes Kafka redelivery idempotent in the normal case.

| Event | Recipient |
|---|---|
| Booking created | Customer (booking received) |
| Partner allocation offer | Offered partner |
| Partner reassigned | New partner, customer and operations |
| Partner accepted | Customer (booking confirmed) |
| Partner rejected | Customer and operations |
| Offer expired | Operations |
| Allocation exhausted / admin rejection | Customer and operations |
| Partner on the way, nearby or arrived | Customer |
| Service started | Customer |
| Completion submitted | Customer (asks them to confirm) |
| Service completed after customer confirmation | Customer |
| Booking cancelled | Customer and operations |

Booking emails use the configured SMTP mailer. Completion OTP email remains a separate, post-commit message so the code is not placed in Kafka or the general notification event. Shipment notifications continue on their existing topic and consumer.

Push notifications and SMS delivery are not configured because this repository has no provider credentials or provider adapter. The booking events are provider-neutral and can be consumed by future channel adapters. SMS OTP also requires such a provider; current completion OTP delivery is email.

SMTP is an external side effect and cannot share a database transaction. A process failure after SMTP accepts a message but before `processed_event` commits can cause a duplicate email on redelivery. Monitor `routemesh.notifications.sent` by `type` and `channel`, `routemesh.outbox.published`, Kafka consumer failures, and the SMTP provider's delivery reports.
