/* Copyright (c) 2026 SathiHomeCare. Project: SathiRoute | Developer: Abhishek Pandey. */
package com.routemesh.allocation.application;

import com.routemesh.booking.application.BookingStore;
import com.routemesh.booking.application.BookingAllocationStore;
import com.routemesh.booking.application.BookingStore.Booking;
import com.routemesh.booking.domain.BookingStatus;
import com.routemesh.shared.config.RouteMeshProperties;
import com.routemesh.shared.error.ApiException;
import com.routemesh.shared.security.Caller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AllocationService {
    private static final String SYSTEM_ACTOR = "allocation-system";
    private final BookingAllocationStore allocations;
    private final BookingStore bookings;
    private final RouteMeshProperties properties;
    private final Clock clock;
    private final Duration offerTtl;
    private final Duration maxLocationAge;
    private final int maxWorkload;
    private final int maxAttempts;
    private final int travelBufferMinutes;

    public AllocationService(BookingAllocationStore allocations, BookingStore bookings, RouteMeshProperties properties,
            Clock clock, @Value("${routemesh.allocation.offer-ttl:PT5M}") Duration offerTtl,
            @Value("${routemesh.allocation.max-location-age:PT15M}") Duration maxLocationAge,
            @Value("${routemesh.allocation.max-workload:8}") int maxWorkload,
            @Value("${routemesh.allocation.max-attempts:5}") int maxAttempts,
            @Value("${routemesh.allocation.travel-buffer-minutes:15}") int travelBufferMinutes) {
        this.allocations = allocations;
        this.bookings = bookings;
        this.properties = properties;
        this.clock = clock;
        this.offerTtl = offerTtl;
        this.maxLocationAge = maxLocationAge;
        this.maxWorkload = maxWorkload;
        this.maxAttempts = maxAttempts;
        this.travelBufferMinutes = travelBufferMinutes;
    }

    @Scheduled(fixedDelayString = "${routemesh.allocation.poll-delay-ms:5000}")
    public void poll() {
        Instant now = clock.instant();
        allocations.expireOffers(now, SYSTEM_ACTOR);
        for (Booking booking : allocations.pendingBookings(100)) {
            if (booking.status() == BookingStatus.NEW) {
                try {
                    bookings.transition(booking.id(), booking.version(), BookingStatus.NEW, BookingStatus.PENDING,
                            SYSTEM_ACTOR, "queued for partner allocation", now);
                } catch (RuntimeException ignored) {
                    continue;
                }
            }
            tryOffer(booking.id(), now);
        }
    }

    public Booking respond(Caller caller, UUID bookingId, long expectedVersion, boolean accepted, String reason) {
        caller.requireServicePartner();
        if (expectedVersion < 0 || reason != null && reason.length() > 1000) {
            throw ApiException.invalid("non-negative expectedVersion and an optional reason up to 1000 characters are required");
        }
        return allocations.respond(bookingId, caller.subject(), expectedVersion, accepted, reason, clock.instant());
    }

    private void tryOffer(UUID bookingId, Instant now) {
        Booking booking = bookings.booking(bookingId).orElse(null);
        if (booking == null || booking.status() != BookingStatus.PENDING) return;
        var candidates = allocations.eligiblePartners(bookingId, now, now.minus(maxLocationAge), maxWorkload,
                properties.planning().roadFactor(), properties.planning().planningKmh(), travelBufferMinutes);
        int attempts = allocations.attemptCount(bookingId);
        if (attempts >= maxAttempts) {
            allocations.exhaust(bookingId, now, SYSTEM_ACTOR);
            return;
        }
        for (UUID partnerId : candidates) {
            if (allocations.hasTried(bookingId, partnerId)) continue;
            if (attempts >= maxAttempts) break;
            if (allocations.offer(bookingId, partnerId, now, now.plus(offerTtl), SYSTEM_ACTOR)) return;
            attempts++;
        }
    }
}
