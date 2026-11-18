# Route optimization

## Partner service routes

Partner service visits use the existing `routing.domain.Solver` and its vehicle routing with time windows implementation. Each partner and service date is planned as one vehicle: the partner location is the start and return point, approved day availability supplies the shift bounds, and each confirmed/offered/in-progress booking becomes a zero-demand job with its customer coordinates, exact appointment start, and service duration. Travel is Haversine distance multiplied by the configured road factor, divided by the configured planning speed. The configured workload cap is checked before solving.

Initial daily routes are built with `Solver.solve`. When new bookings are added to an existing route, `Solver.insert` retains the prior stop order and inserts the new visits feasibly. Cancellation, completion, and reassignment alter the assigned visit set; the reconciler recalculates that day. Daily changes are persisted to `partner_route` and `partner_route_stop`; each route version stores a status and stop snapshot in `partner_route_history`. Infeasible strict recalculation returns a conflict and leaves the stored route as-is. Automatic reconciliation also leaves the prior route unchanged when it cannot fit the current visits, for dispatcher review.

The reconciler runs every 30 seconds for active routes and assigned bookings within the next 30 days. Dispatchers can recalculate any partner/date explicitly. Partner reads are limited to their own route. Endpoints:

- `POST /v1/admin/partners/{partnerId}/routes/{date}/recalculate`
- `GET /v1/partners/{partnerId}/routes/{date}`
- `GET /v1/admin/partner-routes/{routeId}/history`

## Limits

Appointment windows are treated as exact start times because booking records carry a scheduled start and duration, not a flexible window. A partner day’s multiple availability windows are currently collapsed to their earliest start and latest end; gaps between windows are not modeled. The partner’s latest stored location is used as both start and return point even for future service dates. Route feasibility does not yet feed back into allocation acceptance, and a route conflict requires dispatcher review. Traffic, road graphs, breaks, and location uncertainty are not modeled. The service route tables are separate from shipment routes and do not dispatch fleet vehicles.
