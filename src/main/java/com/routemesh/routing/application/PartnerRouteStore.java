package com.routemesh.routing.application;

import com.routemesh.geo.domain.GeoPoint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerRouteStore {
    record Day(UUID partnerId, GeoPoint location, LocalTime shiftStart, LocalTime shiftEnd) { }
    record Visit(UUID bookingId, GeoPoint location, Instant start, Instant end, int serviceMinutes, String status) { }
    record Stop(int sequence, UUID bookingId, Instant plannedArrival) { }
    record Route(UUID id, UUID partnerId, LocalDate serviceDate, String status, double km,
                 Instant plannedReturn, long version, List<Stop> stops) { }
    record DayKey(UUID partnerId, LocalDate date) { }
    record History(long version, String status, Instant changedAt, String changedBy, String reason, String snapshot) { }

    Optional<Day> day(UUID partnerId, LocalDate date);
    List<Visit> visits(UUID partnerId, LocalDate date);
    boolean hasCompletedVisits(UUID partnerId, LocalDate date);
    Optional<Route> route(UUID partnerId, LocalDate date);
    Optional<Route> routeForId(UUID routeId);
    List<DayKey> daysToReconcile(LocalDate from, LocalDate to);
    Optional<UUID> partnerForSubject(String subject);
    List<History> history(UUID routeId);
    Route save(UUID partnerId, LocalDate date, String status, double km, Instant plannedReturn,
               List<Stop> stops, Instant now, String actor, String reason);
}
