package com.routemesh.tracking.application;

import com.routemesh.booking.application.BookingStore;
import com.routemesh.booking.application.BookingStore.Booking;
import com.routemesh.booking.application.ServiceExecutionStore;
import com.routemesh.booking.domain.BookingStatus;
import com.routemesh.geo.domain.CellIndex;
import com.routemesh.geo.domain.GeoPoint;
import com.routemesh.geo.domain.Haversine;
import com.routemesh.geo.domain.Ping;
import com.routemesh.routing.domain.TravelModel;
import com.routemesh.shared.config.RouteMeshProperties;
import com.routemesh.shared.error.ApiException;
import com.routemesh.shared.security.Caller;
import com.routemesh.tracking.application.PartnerLocationLatest.Fix;
import com.routemesh.tracking.domain.PingFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PartnerTrackingService {
    public record GpsFix(Double latitude, Double longitude, Instant at) { }
    public record Ingested(int queued) { }
    public record CustomerTracking(UUID bookingId, String status, Instant appointmentStart, Instant appointmentEnd,
                                   String partnerName, GeoPoint partnerArea, Instant partnerSeenAt,
                                   Double distanceKm, Instant eta) { }
    public record AdminJourney(UUID bookingId, UUID partnerId, String partnerName, String status,
                               GeoPoint exactLocation, Instant partnerSeenAt, Double distanceKm, Instant eta) { }

    private static final int MAX_BATCH = 200;
    private static final int PUBLIC_RESOLUTION = 8;
    private final PartnerTrackingStore store;
    private final PartnerLocationLatest latest;
    private final PartnerPingPublisher publisher;
    private final BookingStore bookings;
    private final ServiceExecutionStore executions;
    private final CellIndex cells;
    private final RouteMeshProperties properties;
    private final Clock clock;
    private final PingFilter filter;
    private final Duration maxAge;
    private final Duration locationHistoryRetention;
    private final double nearbyMeters;
    private final double arrivedMeters;

    public PartnerTrackingService(PartnerTrackingStore store, PartnerLocationLatest latest,
            PartnerPingPublisher publisher, BookingStore bookings, ServiceExecutionStore executions,
            CellIndex cells, RouteMeshProperties properties,
            Clock clock, @Value("${routemesh.tracking.partner-max-speed-kmh:130}") double maxSpeedKmh,
            @Value("${routemesh.tracking.partner-max-location-age:PT15M}") Duration maxAge,
            @Value("${routemesh.tracking.partner-nearby-meters:500}") double nearbyMeters,
            @Value("${routemesh.tracking.partner-arrived-meters:100}") double arrivedMeters,
            @Value("${routemesh.tracking.partner-location-history-retention:P30D}") Duration locationHistoryRetention) {
        this.store = store; this.latest = latest; this.publisher = publisher; this.bookings = bookings;
        this.executions = executions;
        this.cells = cells; this.properties = properties; this.clock = clock;
        this.filter = new PingFilter(maxSpeedKmh, 0, maxAge);
        this.maxAge = maxAge;
        this.locationHistoryRetention = locationHistoryRetention;
        this.nearbyMeters = nearbyMeters; this.arrivedMeters = arrivedMeters;
    }

    public Ingested ingest(Caller caller, List<GpsFix> input) {
        caller.requireServicePartner();
        if (input == null || input.isEmpty() || input.size() > MAX_BATCH) {
            throw ApiException.invalid("send 1.." + MAX_BATCH + " location fixes");
        }
        var partner = store.partnerForSubject(caller.subject()).orElseThrow(() -> ApiException.notFound("partner profile"));
        if (!partner.active() || !partner.approved()) {
            throw ApiException.forbidden("partner-inactive", "only an active, approved partner can send GPS updates");
        }
        Instant now = clock.instant();
        var pings = new ArrayList<Ping>(input.size());
        for (var f : input) {
            if (f == null || f.latitude() == null || f.longitude() == null || f.at() == null
                    || f.at().isAfter(now.plusSeconds(30))) throw ApiException.invalid("each fix needs valid coordinates and a timestamp no more than 30 seconds in the future");
            try { pings.add(new Ping(partner.id().toString(), f.at(), new GeoPoint(f.latitude(), f.longitude()))); }
            catch (IllegalArgumentException e) { throw ApiException.invalid("bad-position", e.getMessage()); }
        }
        publisher.publish(partner.id(), pings);
        return new Ingested(pings.size());
    }

    @Transactional
    public void process(UUID partnerId, List<Ping> batch) {
        var partner = store.partner(partnerId).orElse(null);
        if (partner == null || !partner.active() || !partner.approved()) return;
        Ping previous = partner.location() == null || partner.locationAt() == null ? null
                : new Ping(partnerId.toString(), partner.locationAt(), partner.location());
        Instant now = clock.instant();
        var valid = batch.stream().filter(p -> !p.at().isAfter(now.plusSeconds(30))).toList();
        var cleaned = filter.apply(valid, previous, now);
        if (cleaned.accepted().isEmpty()) return;
        for (Ping fix : cleaned.accepted()) store.recordLocation(partnerId, fix.position(), fix.at());
        Ping lastFix = cleaned.last();
        latest.update(partnerId, new Fix(lastFix.at(), lastFix.position()));
        for (var journey : store.journeys(partnerId)) advance(journey, lastFix);
    }

    public CustomerTracking customer(Caller caller, UUID bookingId) {
        if (!caller.isDispatcher()) caller.requireCustomer();
        Booking booking = bookings.booking(bookingId).orElseThrow(() -> ApiException.notFound("booking"));
        if (!caller.isDispatcher() && !booking.customerSubject().equals(caller.subject())) throw ApiException.notFound("booking");
        String status = customerStatus(booking.status());
        var assignedPartner = booking.assignedPartnerId() == null ? null : store.partner(booking.assignedPartnerId()).orElse(null);
        if (booking.assignedPartnerId() == null || !locationVisible(booking.status())) {
            return new CustomerTracking(bookingId, status, booking.appointmentStartAt(), booking.appointmentEndAt(),
                    assignedPartner == null ? null : assignedPartner.name(), null, null, null, null);
        }
        var partner = assignedPartner;
        var fix = fresh(booking.assignedPartnerId());
        if (partner == null || fix == null) return new CustomerTracking(bookingId, status,
                booking.appointmentStartAt(), booking.appointmentEndAt(), partner == null ? null : partner.name(),
                null, null, null, null);
        double distance = Haversine.km(fix.position(), booking.location());
        var travel = new TravelModel(properties.planning().roadFactor(), properties.planning().planningKmh());
        Instant eta = fix.at().plusSeconds(Math.round(travel.minutes(travel.km(fix.position(), booking.location())) * 60));
        GeoPoint area = cells.center(cells.cell(fix.position(), PUBLIC_RESOLUTION));
        return new CustomerTracking(bookingId, status, booking.appointmentStartAt(), booking.appointmentEndAt(),
                partner.name(), area, fix.at(), distance, eta);
    }

    public List<AdminJourney> adminDashboard(Caller caller) {
        caller.requireOperations();
        return store.activeJourneys().stream().map(j -> {
            Fix fix = fresh(j.partnerId());
            if (fix == null) return new AdminJourney(j.bookingId(), j.partnerId(), j.partnerName(),
                    customerStatus(j.status()), null, null, null, null);
            double distance = Haversine.km(fix.position(), j.customerLocation());
            var travel = new TravelModel(properties.planning().roadFactor(), properties.planning().planningKmh());
            Instant eta = fix.at().plusSeconds(Math.round(travel.minutes(travel.km(fix.position(), j.customerLocation())) * 60));
            return new AdminJourney(j.bookingId(), j.partnerId(), j.partnerName(), customerStatus(j.status()),
                    fix.position(), fix.at(), distance, eta);
        }).toList();
    }

    public List<PartnerTrackingStore.Location> history(Caller caller, UUID partnerId, int limit) {
        if (limit < 1 || limit > 500) throw ApiException.invalid("limit must be 1..500");
        var partner = store.partner(partnerId).orElseThrow(() -> ApiException.notFound("partner profile"));
        if (!caller.isDispatcher()) caller.requireServicePartner();
        if (!caller.isDispatcher() && !caller.subject().equals(partner.subject())) throw ApiException.notFound("partner profile");
        return store.history(partnerId, limit);
    }

    public List<PartnerTrackingStore.Location> ownHistory(Caller caller, int limit) {
        var partner = store.partnerForSubject(caller.subject()).orElseThrow(() -> ApiException.notFound("partner profile"));
        return history(caller, partner.id(), limit);
    }

    @Scheduled(fixedDelayString = "${routemesh.tracking.location-history-purge-delay-ms:86400000}")
    public int purgeExpiredHistory() {
        return store.purgeHistoryBefore(clock.instant().minus(locationHistoryRetention));
    }

    private void advance(PartnerTrackingStore.Journey journey, Ping fix) {
        BookingStatus state = journey.status();
        if (state != BookingStatus.CONFIRMED && state != BookingStatus.PARTNER_ON_THE_WAY
                && state != BookingStatus.PARTNER_NEARBY) return;
        Instant appointmentStart = journey.appointmentStart();
        if (state == BookingStatus.CONFIRMED && fix.at().isBefore(appointmentStart.minus(Duration.ofHours(2)))) return;
        double meters = Haversine.km(fix.position(), journey.customerLocation()) * 1000;
        BookingStatus target = state == BookingStatus.CONFIRMED ? BookingStatus.PARTNER_ON_THE_WAY : state;
        if (meters <= nearbyMeters && target.ordinal() < BookingStatus.PARTNER_NEARBY.ordinal()) target = BookingStatus.PARTNER_NEARBY;
        if (meters <= arrivedMeters) target = BookingStatus.PARTNER_ARRIVED;
        long version = journey.version();
        while (state != target) {
            BookingStatus next = switch (state) {
                case CONFIRMED -> BookingStatus.PARTNER_ON_THE_WAY;
                case PARTNER_ON_THE_WAY -> BookingStatus.PARTNER_NEARBY;
                case PARTNER_NEARBY -> BookingStatus.PARTNER_ARRIVED;
                default -> state;
            };
            if (next == state || next.ordinal() > target.ordinal()) break;
            Booking changed = bookings.transitionAsPartner(journey.bookingId(), version, state, next,
                    journey.partnerSubject(), "partner GPS geofence update", clock.instant());
            state = changed.status(); version = changed.version();
            if (state == BookingStatus.PARTNER_ARRIVED) {
                executions.arrival(journey.bookingId(), journey.partnerId(), clock.instant(), journey.partnerSubject());
            }
        }
    }

    private Fix fresh(UUID partnerId) {
        Instant now = clock.instant();
        return latest.latest(partnerId).filter(f -> !f.at().isAfter(now.plusSeconds(30))
                && f.at().isAfter(now.minus(maxAge))).orElse(null);
    }

    private static boolean locationVisible(BookingStatus status) {
        return status == BookingStatus.PARTNER_ON_THE_WAY || status == BookingStatus.PARTNER_NEARBY
                || status == BookingStatus.PARTNER_ARRIVED || status == BookingStatus.IN_PROGRESS;
    }

    private static String customerStatus(BookingStatus status) {
        return switch (status) {
            case ASSIGNED -> "PARTNER_ASSIGNED";
            case CONFIRMED -> "PARTNER_ACCEPTED";
            case PARTNER_ON_THE_WAY -> "PARTNER_ON_THE_WAY";
            case PARTNER_NEARBY -> "PARTNER_NEARBY";
            case PARTNER_ARRIVED -> "PARTNER_ARRIVED";
            case IN_PROGRESS -> "SERVICE_STARTED";
            case COMPLETED -> "SERVICE_COMPLETED";
            default -> status.name();
        };
    }
}
