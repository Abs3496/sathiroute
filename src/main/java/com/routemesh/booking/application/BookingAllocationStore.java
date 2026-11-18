package com.routemesh.booking.application;

import com.routemesh.booking.application.BookingStore.Booking;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence port for timed partner offers on service bookings. */
public interface BookingAllocationStore {
    List<Booking> pendingBookings(int limit);
    List<UUID> eligiblePartners(UUID bookingId, Instant now, Instant locationCutoff, int maxWorkload,
                                double roadFactor, double planningKmh, int travelBufferMinutes);
    boolean offer(UUID bookingId, UUID partnerId, Instant now, Instant expiresAt, String actor);
    List<UUID> expireOffers(Instant now, String actor);
    Booking respond(UUID bookingId, String partnerSubject, long expectedVersion, boolean accepted, String reason, Instant now);
    boolean hasTried(UUID bookingId, UUID partnerId);
    int attemptCount(UUID bookingId);
    void exhaust(UUID bookingId, Instant now, String actor);
}
