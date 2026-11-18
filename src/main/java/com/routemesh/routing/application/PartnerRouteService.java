package com.routemesh.routing.application;

import com.routemesh.geo.domain.GeoPoint;
import com.routemesh.routing.domain.Job;
import com.routemesh.routing.domain.Plan;
import com.routemesh.routing.domain.Problem;
import com.routemesh.routing.domain.Solver;
import com.routemesh.routing.domain.TravelModel;
import com.routemesh.routing.domain.Vehicle;
import com.routemesh.shared.config.RouteMeshProperties;
import com.routemesh.shared.error.ApiException;
import com.routemesh.shared.security.Caller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PartnerRouteService {
    public record StopView(int sequence, UUID bookingId, Instant appointmentStart, Instant appointmentEnd,
                           int serviceMinutes, Instant plannedArrival, double travelKmFromPrevious) { }
    public record RouteView(UUID id, UUID partnerId, LocalDate serviceDate, String status, double distanceKm,
                            Instant plannedReturn, long version, List<StopView> stops,
                            List<UUID> unassigned, boolean budgetExhausted) { }

    private final PartnerRouteStore store;
    private final RouteMeshProperties properties;
    private final Clock clock;
    private final int maxDailyWorkload;

    public PartnerRouteService(PartnerRouteStore store, RouteMeshProperties properties, Clock clock,
                              @Value("${routemesh.allocation.max-workload:8}") int maxDailyWorkload) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
        this.maxDailyWorkload = maxDailyWorkload;
    }

    @Scheduled(fixedDelayString = "${routemesh.routes.reconcile-delay-ms:30000}")
    public void reconcile() {
        LocalDate today = LocalDate.now(clock.withZone(properties.planning().zone()));
        for (var key : store.daysToReconcile(today, today.plusDays(30))) {
            try { calculate(key.partnerId(), key.date(), "route-reconciler", false); }
            catch (RuntimeException ignored) { /* An infeasible day remains unchanged for dispatcher review. */ }
        }
    }

    public RouteView recalculate(Caller caller, UUID partnerId, LocalDate date) {
        caller.requireOperations();
        return calculate(partnerId, date, caller.subject(), true);
    }

    public RouteView get(Caller caller, UUID partnerId, LocalDate date) {
        if (!caller.isDispatcher() && !caller.isOperationsManager()) caller.requireServicePartner();
        if (!caller.isDispatcher() && !caller.isOperationsManager()
                && !store.partnerForSubject(caller.subject()).filter(partnerId::equals).isPresent()) {
            throw ApiException.notFound("partner route");
        }
        var route = store.route(partnerId, date).orElseThrow(() -> ApiException.notFound("partner route"));
        var visits = store.visits(partnerId, date);
        var day = store.day(partnerId, date).orElse(null);
        GeoPoint previous = day == null ? null : day.location();
        var travel = new TravelModel(properties.planning().roadFactor(), properties.planning().planningKmh());
        var visitById = visits.stream().collect(java.util.stream.Collectors.toMap(PartnerRouteStore.Visit::bookingId, v -> v));
        var stops = new java.util.ArrayList<StopView>();
        for (var stop : route.stops()) {
            var visit = visitById.get(stop.bookingId());
            if (visit == null) continue;
            double leg = previous == null ? 0 : travel.km(previous, visit.location());
            stops.add(new StopView(stop.sequence(), stop.bookingId(), visit.start(), visit.end(), visit.serviceMinutes(),
                    stop.plannedArrival(), leg));
            previous = visit.location();
        }
        return new RouteView(route.id(), partnerId, date, route.status(), route.km(), route.plannedReturn(),
                route.version(), stops, List.of(), false);
    }

    public List<PartnerRouteStore.History> history(Caller caller, UUID routeId) {
        caller.requireOperations();
        if (store.routeForId(routeId).isEmpty()) throw ApiException.notFound("partner route");
        return store.history(routeId);
    }

    private RouteView calculate(UUID partnerId, LocalDate date, String actor, boolean strict) {
        var current = store.route(partnerId, date);
        List<PartnerRouteStore.Visit> visits = store.visits(partnerId, date);
        if (visits.isEmpty()) {
            if (current.isEmpty()) throw ApiException.notFound("partner route");
            return persistCancelledIfEmpty(partnerId, date, current, actor);
        }
        var day = store.day(partnerId, date).orElse(null);
        if (day == null) {
            if (strict) throw ApiException.conflict("partner-shift-unavailable", "partner has no approved working window or location for this date");
            throw ApiException.conflict("partner-shift-unavailable", "partner has no approved working window or location for this date");
        }
        if (visits.size() > maxDailyWorkload) {
            throw ApiException.conflict("daily-workload-exceeded", "bookings exceed the configured partner daily workload");
        }
        var zone = properties.planning().zone();
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        double shiftStart = Duration.between(dayStart, date.atTime(day.shiftStart()).atZone(zone).toInstant()).toSeconds() / 60.0;
        double shiftEnd = Duration.between(dayStart, date.atTime(day.shiftEnd()).atZone(zone).toInstant()).toSeconds() / 60.0;
        String vehicleId = partnerId.toString();
        var vehicle = new Vehicle(vehicleId, Integer.MAX_VALUE, day.location(), shiftStart, shiftEnd, 0);
        var jobs = visits.stream().map(v -> new Job(v.bookingId().toString(), Job.Kind.DELIVERY, v.location(),
                Duration.between(dayStart, v.start()).toSeconds() / 60.0,
                Duration.between(dayStart, v.start()).toSeconds() / 60.0,
                v.serviceMinutes(), 0)).toList();
        var problem = new Problem(day.location(), List.of(vehicle), jobs,
                new TravelModel(properties.planning().roadFactor(), properties.planning().planningKmh()));
        Set<String> live = new HashSet<>(visits.stream().map(v -> v.bookingId().toString()).toList());
        List<String> oldSequence = current.map(r -> r.stops().stream().map(s -> s.bookingId().toString())
                .filter(live::contains).toList()).orElse(List.of());
        Plan plan;
        if (current.isPresent() && live.containsAll(oldSequence) && oldSequence.size() < live.size()) {
            Set<String> existing = new HashSet<>(oldSequence);
            List<String> added = live.stream().filter(id -> !existing.contains(id)).toList();
            plan = Solver.insert(problem, Map.of(vehicleId, oldSequence), added, properties.planning().insertBudget());
        } else {
            plan = Solver.solve(problem, properties.planning().budget());
        }
        if (!plan.unassigned().isEmpty() && strict) {
            throw ApiException.conflict("route-infeasible", "appointment windows, travel time, service duration, and partner shift do not fit");
        }
        if (!plan.unassigned().isEmpty()) {
            if (current.isPresent()) return view(current.get(), visits, plan, dayStart);
            throw ApiException.conflict("route-infeasible", "appointment windows, travel time, service duration, and partner shift do not fit");
        }
        var routePlan = plan.routes().get(0);
        List<PartnerRouteStore.Stop> stops = routePlan.stops().stream().map(s -> new PartnerRouteStore.Stop(
                0, UUID.fromString(s.jobId()), dayStart.plusSeconds(Math.round(s.arrival() * 60)))).toList();
        String status = visits.stream().anyMatch(v -> "IN_PROGRESS".equals(v.status())) ? "ACTIVE" : "PLANNED";
        var saved = store.save(partnerId, date, status, routePlan.km(), dayStart.plusSeconds(Math.round(routePlan.returnAt() * 60)),
                stops, clock.instant(), actor, "solver recalculation");
        return view(saved, visits, plan, dayStart);
    }

    private RouteView persistCancelledIfEmpty(UUID partnerId, LocalDate date, java.util.Optional<PartnerRouteStore.Route> old,
                                               String actor) {
        old.orElseThrow(() -> ApiException.notFound("partner route"));
        boolean completed = store.hasCompletedVisits(partnerId, date);
        String status = completed ? "COMPLETED" : "CANCELLED";
        var saved = store.save(partnerId, date, status, 0, null, List.of(), clock.instant(), actor,
                completed ? "all scheduled services completed" : "no eligible scheduled bookings remain");
        return new RouteView(saved.id(), partnerId, date, saved.status(), 0, null, saved.version(), List.of(), List.of(), false);
    }

    private RouteView view(PartnerRouteStore.Route route, List<PartnerRouteStore.Visit> visits, Plan plan, Instant dayStart) {
        Map<UUID, PartnerRouteStore.Visit> byId = visits.stream().collect(java.util.stream.Collectors.toMap(
                PartnerRouteStore.Visit::bookingId, v -> v));
        var planned = plan.routes().get(0).stops();
        var stops = route.stops().stream().map(s -> {
            var v = byId.get(s.bookingId());
            if (v == null) return null;
            int index = s.sequence()-1;
            double legKm = index == 0 ? new TravelModel(properties.planning().roadFactor(), properties.planning().planningKmh())
                    .km(store.day(route.partnerId(), route.serviceDate()).orElseThrow().location(), v.location())
                    : new TravelModel(properties.planning().roadFactor(), properties.planning().planningKmh())
                    .km(byId.get(route.stops().get(index-1).bookingId()).location(), v.location());
            return new StopView(s.sequence(), s.bookingId(), v.start(), v.end(), v.serviceMinutes(), s.plannedArrival(), legKm);
        }).filter(java.util.Objects::nonNull).toList();
        List<UUID> unassigned = plan.unassigned().stream().map(UUID::fromString).toList();
        return new RouteView(route.id(), route.partnerId(), route.serviceDate(), route.status(), route.km(),
                route.plannedReturn(), route.version(), stops, unassigned, plan.budgetExhausted());
    }
}
