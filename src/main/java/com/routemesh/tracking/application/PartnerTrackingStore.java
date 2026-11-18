package com.routemesh.tracking.application;

import com.routemesh.booking.domain.BookingStatus;
import com.routemesh.geo.domain.GeoPoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerTrackingStore {
    record Partner(UUID id, String subject, String name, String email, GeoPoint location,
                   Instant locationAt, boolean active, boolean approved) { }
    record Journey(UUID bookingId, UUID partnerId, String partnerName, String partnerSubject,
                   String customerSubject, String customerName, String customerEmail,
                   GeoPoint customerLocation, BookingStatus status, Instant appointmentStart,
                   Instant appointmentEnd, long version) { }
    record Location(Instant at, GeoPoint position) { }

    Optional<Partner> partnerForSubject(String subject);
    Optional<Partner> partner(UUID partnerId);
    List<Journey> journeys(UUID partnerId);
    List<Journey> activeJourneys();
    void recordLocation(UUID partnerId, GeoPoint point, Instant at);
    List<Location> history(UUID partnerId, int limit);
    int purgeHistoryBefore(Instant cutoff);
    boolean claimNotification(UUID bookingId, BookingStatus status, Instant at);
}
