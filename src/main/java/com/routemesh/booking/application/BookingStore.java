/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Subject to verification of ownership and applicable third-party licenses.
 */
package com.routemesh.booking.application;

import com.routemesh.booking.domain.BookingStatus;
import com.routemesh.geo.domain.GeoPoint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingStore {
    record Customer(UUID id, String subject, String name, String email, String phone, Instant createdAt, Instant updatedAt) { }
    record Booking(UUID id, UUID customerId, String customerSubject, String customerName, String customerEmail,
                   String customerPhone, String category, String requiredSkill, LocalDate appointmentDate,
                   Instant appointmentStartAt, Instant appointmentEndAt,
                   int durationMinutes, String address, GeoPoint location, String notes, UUID assignedPartnerId,
                   BookingStatus status, long version, Instant createdAt, Instant updatedAt) { }
    record PartnerMatch(UUID partnerId, String name, String phone, double latitude, double longitude,
                        double distanceKm, boolean active, boolean available) { }

    Optional<Customer> customer(String subject);
    Customer insertCustomer(UUID id, String subject, String name, String email, String phone, Instant now);
    void updateCustomer(String subject, String name, String email, String phone, Instant now);
    void insertBooking(Booking booking);
    Optional<Booking> booking(UUID id);
    List<PartnerMatch> recommendPartners(UUID bookingId, Instant locationCutoff);
    List<Booking> bookings(String subject, int limit, int offset);
    List<Booking> adminBookings(int limit, int offset);
    List<Booking> partnerBookings(String partnerSubject, int limit, int offset);
    Optional<Booking> partnerBooking(String partnerSubject, UUID bookingId);
    Booking assign(UUID bookingId, long expectedVersion, UUID partnerId, LocalTime start, LocalTime end,
                   String actorSubject, Instant now);
    boolean partnerOwns(String partnerSubject, UUID bookingId);
    Booking transitionAsPartner(UUID id, long expectedVersion, BookingStatus from, BookingStatus to,
                                String partnerSubject, String reason, Instant now);
    Booking updateBooking(String subject, UUID id, long expectedVersion, String category, String requiredSkill,
                          LocalDate date, LocalTime start, Instant startAt, Instant endAt, int durationMinutes,
                          String address, GeoPoint location, String notes, Instant now);
    Booking transition(UUID id, long expectedVersion, BookingStatus from, BookingStatus to, String actorSubject,
                       String reason, Instant now);
}
