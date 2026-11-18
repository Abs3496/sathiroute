/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Subject to verification of ownership and applicable third-party licenses.
 */
package com.routemesh.booking.application;

import com.routemesh.booking.application.BookingStore.Booking;
import com.routemesh.booking.application.BookingStore.Customer;
import com.routemesh.booking.domain.BookingStatus;
import com.routemesh.geo.domain.GeoPoint;
import com.routemesh.idempotency.application.IdempotencyService;
import com.routemesh.idempotency.application.Idempotent;
import com.routemesh.shared.config.RouteMeshProperties;
import com.routemesh.shared.error.ApiException;
import com.routemesh.shared.security.Caller;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BookingService {
    private static final Set<String> CATEGORIES = Set.of("NURSING", "AYURVEDIC_THERAPY", "COUNSELLING");
    private final BookingStore store;
    private final IdempotencyService idempotency;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final RouteMeshProperties props;

    public BookingService(BookingStore store, IdempotencyService idempotency, PlatformTransactionManager txManager,
                          Clock clock, RouteMeshProperties props) {
        this.store = store;
        this.idempotency = idempotency;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.props = props;
    }

    public Customer customer(Caller caller) {
        caller.requireCustomer();
        return store.customer(caller.subject()).orElseThrow(() -> ApiException.notFound("customer profile"));
    }

    public Customer registerCustomer(Caller caller, CustomerInput in) {
        caller.requireCustomer();
        validateCustomer(in);
        Instant now = clock.instant();
        try {
            return store.insertCustomer(UUID.randomUUID(), caller.subject(), in.name().trim(), in.email().trim(),
                    in.phone().trim(), now);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("customer-already-registered", "this login already has a customer profile");
        }
    }

    public Customer updateCustomer(Caller caller, CustomerInput in) {
        caller.requireCustomer();
        validateCustomer(in);
        store.updateCustomer(caller.subject(), in.name().trim(), in.email().trim(), in.phone().trim(), clock.instant());
        return customer(caller);
    }

    public Idempotent<Booking> create(Caller caller, String key, NewBooking in) {
        caller.requireCustomer();
        validateBooking(in);
        return idempotency.run("service-booking.create", caller, key, in, Booking.class,
                () -> tx.execute(status -> insertBooking(caller, in)));
    }

    public Booking get(Caller caller, UUID id) {
        caller.requireCustomer();
        return store.booking(id).filter(b -> b.customerSubject().equals(caller.subject()))
                .orElseThrow(() -> ApiException.notFound("booking"));
    }

    public List<Booking> history(Caller caller, int limit, int offset) {
        caller.requireCustomer();
        validatePage(limit, offset);
        customer(caller);
        return store.bookings(caller.subject(), limit, offset);
    }

    public List<Booking> adminBookings(Caller caller, int limit, int offset) {
        caller.requireOperations();
        validatePage(limit, offset);
        return store.adminBookings(limit, offset);
    }

    public List<Booking> partnerBookings(Caller caller, int limit, int offset) {
        caller.requireServicePartner();
        validatePage(limit, offset);
        return store.partnerBookings(caller.subject(), limit, offset);
    }

    public Booking partnerBooking(Caller caller, UUID id) {
        caller.requireServicePartner();
        return store.partnerBooking(caller.subject(), id).orElseThrow(() -> ApiException.notFound("assigned booking"));
    }

    public Booking update(Caller caller, UUID id, UpdateBooking in) {
        caller.requireCustomer();
        if (in == null || in.expectedVersion() < 0) {
            throw ApiException.invalid("booking body and non-negative expectedVersion are required");
        }
        validateBooking(in.toNewBooking());
        Booking existing = get(caller, id);
        if (!existing.status().editable()) {
            throw ApiException.conflict("booking-not-editable", "only NEW or PENDING bookings can be changed");
        }
        var times = times(in.appointmentDate(), in.appointmentStart(), in.serviceDurationMinutes());
        return store.updateBooking(caller.subject(), id, in.expectedVersion(), in.serviceCategory(), in.requiredSkill(),
                in.appointmentDate(), in.appointmentStart(), times.startAt(), times.endAt(),
                in.serviceDurationMinutes(), in.address().trim(), point(in.latitude(), in.longitude()), in.notes(),
                clock.instant());
    }

    public Booking cancel(Caller caller, UUID id, CancelBooking in) {
        caller.requireCustomer();
        if (in == null || in.expectedVersion() < 0 || in.reason() != null && in.reason().length() > 1000) {
            throw ApiException.invalid("non-negative expectedVersion and an optional reason up to 1000 characters are required");
        }
        Booking existing = get(caller, id);
        if (!existing.status().cancellableByCustomer()) {
            throw ApiException.conflict("booking-not-cancellable", "booking cannot be cancelled after service starts");
        }
        existing.status().checkTransitionTo(BookingStatus.CANCELLED);
        return store.transition(id, in.expectedVersion(), existing.status(), BookingStatus.CANCELLED,
                caller.subject(), in.reason(), clock.instant());
    }

    public Booking beginAllocation(Caller caller, UUID id) {
        caller.requireOperations();
        Booking existing = store.booking(id).orElseThrow(() -> ApiException.notFound("booking"));
        if (existing.status() != BookingStatus.NEW && existing.status() != BookingStatus.REJECTED) {
            throw ApiException.conflict("booking-not-queueable", "only NEW or REJECTED bookings can enter allocation queue");
        }
        existing.status().checkTransitionTo(BookingStatus.PENDING);
        return store.transition(id, existing.version(), existing.status(), BookingStatus.PENDING, caller.subject(),
                "allocation requested", clock.instant());
    }

    public Booking reject(Caller caller, UUID id, RejectBooking in) {
        caller.requireOperations();
        if (in == null || in.expectedVersion() < 0 || in.reason() == null || in.reason().isBlank()
                || in.reason().length() > 1000) {
            throw ApiException.invalid("expectedVersion and a rejection reason up to 1000 characters are required");
        }
        Booking existing = store.booking(id).orElseThrow(() -> ApiException.notFound("booking"));
        existing.status().checkTransitionTo(BookingStatus.REJECTED);
        return store.transition(id, in.expectedVersion(), existing.status(), BookingStatus.REJECTED,
                caller.subject(), in.reason(), clock.instant());
    }

    public Booking assign(Caller caller, UUID id, AssignBooking in) {
        caller.requireOperations();
        if (in == null || in.partnerId() == null || in.expectedVersion() < 0) {
            throw ApiException.invalid("partnerId and a non-negative expectedVersion are required");
        }
        Booking booking = store.booking(id).orElseThrow(() -> ApiException.notFound("booking"));
        if (booking.status() != BookingStatus.PENDING) {
            throw ApiException.conflict("booking-not-pending", "only PENDING bookings can be assigned");
        }
        if (booking.version() != in.expectedVersion()) {
            throw ApiException.conflict("version-conflict", "booking changed; reload and retry");
        }
        var zone = props.planning().zone();
        var start = booking.appointmentStartAt().atZone(zone);
        var end = booking.appointmentEndAt().atZone(zone);
        if (!start.toLocalDate().equals(booking.appointmentDate()) || !end.toLocalDate().equals(booking.appointmentDate())) {
            throw ApiException.conflict("unsupported-overnight-appointment", "partner work windows must contain the full appointment day");
        }
        return store.assign(id, in.expectedVersion(), in.partnerId(), start.toLocalTime(), end.toLocalTime(),
                caller.subject(), clock.instant());
    }

    public Booking accept(Caller caller, UUID id, PartnerDecision in) {
        return partnerTransition(caller, id, in, BookingStatus.CONFIRMED, "partner accepted booking");
    }

    public Booking partnerReject(Caller caller, UUID id, PartnerDecision in) {
        return partnerTransition(caller, id, in, BookingStatus.REJECTED,
                "partner rejected booking");
    }

    public Booking start(Caller caller, UUID id, PartnerDecision in) {
        return partnerTransition(caller, id, in, BookingStatus.IN_PROGRESS, "service started");
    }

    public Booking complete(Caller caller, UUID id, PartnerDecision in) {
        return partnerTransition(caller, id, in, BookingStatus.COMPLETED, "service completed");
    }

    private Booking partnerTransition(Caller caller, UUID id, PartnerDecision in, BookingStatus to, String defaultReason) {
        if (in == null || in.expectedVersion() < 0 || in.reason() != null && in.reason().length() > 1000) {
            throw ApiException.invalid("a non-negative expectedVersion is required");
        }
        Booking existing = store.partnerBooking(caller.subject(), id)
                .orElseThrow(() -> ApiException.notFound("assigned booking"));
        existing.status().checkTransitionTo(to);
        String reason = in.reason() == null || in.reason().isBlank() ? defaultReason : in.reason().trim();
        return store.transitionAsPartner(id, in.expectedVersion(), existing.status(), to, caller.subject(), reason,
                clock.instant());
    }

    private Booking insertBooking(Caller caller, NewBooking in) {
        Customer customer = customer(caller);
        Instant now = clock.instant();
        var times = times(in.appointmentDate(), in.appointmentStart(), in.serviceDurationMinutes());
        var point = point(in.latitude(), in.longitude());
        UUID id = UUID.randomUUID();
        Booking booking = new Booking(id, customer.id(), caller.subject(), customer.name(), customer.email(),
                customer.phone(), in.serviceCategory(), in.requiredSkill().trim(), in.appointmentDate(),
                times.startAt(), times.endAt(), in.serviceDurationMinutes(), in.address().trim(),
                point, in.notes(), null, BookingStatus.NEW, 0, now, now);
        store.insertBooking(booking);
        return booking;
    }

    private void validateBooking(NewBooking in) {
        if (in == null || in.appointmentDate() == null || in.appointmentStart() == null
                || in.serviceDurationMinutes() < 1 || in.serviceDurationMinutes() > 1440
                || in.latitude() == null || in.longitude() == null || in.address() == null || in.address().isBlank()
                || in.address().length() > 2000 || in.serviceCategory() == null
                || !CATEGORIES.contains(in.serviceCategory()) || in.requiredSkill() == null
                || in.requiredSkill().isBlank() || in.requiredSkill().length() > 100
                || in.notes() != null && in.notes().length() > 4000) {
            throw ApiException.invalid("invalid-booking", "service, appointment, duration, address and valid coordinates are required");
        }
        times(in.appointmentDate(), in.appointmentStart(), in.serviceDurationMinutes());
        point(in.latitude(), in.longitude());
    }

    private void validateCustomer(CustomerInput in) {
        if (in == null || in.name() == null || in.name().isBlank() || in.email() == null || in.email().isBlank()
                || !in.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || in.phone() == null
                || in.phone().isBlank() || in.name().length() > 200 || in.email().length() > 320
                || in.phone().length() > 50) {
            throw ApiException.invalid("invalid-customer-profile", "name, valid email and phone are required");
        }
    }

    private static void validatePage(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw ApiException.invalid("limit must be 1..100 and offset must not be negative");
        }
    }

    private AppointmentTimes times(LocalDate date, LocalTime start, int duration) {
        var zone = props.planning().zone();
        var today = LocalDate.now(clock.withZone(zone));
        if (date == null || start == null || duration < 1 || date.isBefore(today) || date.isAfter(today.plusDays(180))) {
            throw ApiException.invalid("bad-appointment", "appointment must be today or within 180 days and have a positive duration");
        }
        var startAt = date.atTime(start).atZone(zone).toInstant();
        if (!startAt.isAfter(clock.instant())) {
            throw ApiException.invalid("bad-appointment", "appointment must start in the future");
        }
        var endAt = startAt.plusSeconds(Math.multiplyExact((long) duration, 60));
        if (!endAt.atZone(zone).toLocalDate().equals(date)) {
            throw ApiException.invalid("bad-appointment", "an appointment must finish on its scheduled date");
        }
        return new AppointmentTimes(startAt, endAt);
    }

    private static GeoPoint point(double lat, double lng) {
        try {
            return new GeoPoint(lat, lng);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("bad-position", e.getMessage());
        }
    }

    public record CustomerInput(String name, String email, String phone) { }
    public record NewBooking(String serviceCategory, String requiredSkill, LocalDate appointmentDate,
                             LocalTime appointmentStart, int serviceDurationMinutes, String address,
                             Double latitude, Double longitude, String notes) { }
    public record UpdateBooking(long expectedVersion, String serviceCategory, String requiredSkill,
                                LocalDate appointmentDate, LocalTime appointmentStart, int serviceDurationMinutes,
                                String address, Double latitude, Double longitude, String notes) {
        private NewBooking toNewBooking() {
            return new NewBooking(serviceCategory, requiredSkill, appointmentDate, appointmentStart,
                    serviceDurationMinutes, address, latitude, longitude, notes);
        }
    }
    public record CancelBooking(long expectedVersion, String reason) { }
    public record RejectBooking(long expectedVersion, String reason) { }
    public record AssignBooking(UUID partnerId, long expectedVersion) { }
    public record PartnerDecision(long expectedVersion, String reason) { }
    private record AppointmentTimes(Instant startAt, Instant endAt) { }
}
