/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Subject to verification of ownership and applicable third-party licenses.
 */
package com.routemesh.partner.application;

import com.routemesh.partner.domain.PartnerAvailability;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerStore {
    record Skill(String category, String code, String qualificationDetails, boolean verified) { }
    record Profile(UUID id, String subject, UUID centreId, String code, String name, String email, String phone,
                   String qualificationDetails, boolean active, boolean available, String registrationStatus,
                   Double latitude, Double longitude, Instant locationUpdatedAt, double serviceRadiusKm,
                   Instant createdAt, String reviewedBy, Instant reviewedAt, List<Skill> skills,
                   List<PartnerAvailability> availability) { }
    record BookingHistory(UUID bookingId, String category, String requiredSkill, Instant startsAt, Instant endsAt,
                         String status) { }
    record Workload(int upcomingBookings, long completedBookings) { }

    boolean centreExists(UUID centreId);
    void create(UUID id, String subject, UUID centreId, String code, String name, String email, String phone,
                String qualificationDetails, double radiusKm, Instant now, List<Skill> skills);
    Optional<Profile> findBySubject(String subject);
    Optional<Profile> findById(UUID id);
    List<Profile> findAll(String status);
    void updateProfile(String subject, String name, String email, String phone, String qualificationDetails,
                       double radiusKm);
    void replaceSkills(String subject, List<Skill> skills);
    void replaceAvailability(String subject, List<PartnerAvailability> availability);
    void setAvailability(String subject, boolean available);
    void setLocation(String subject, double latitude, double longitude, Instant at);
    void decideVerification(UUID partnerId, boolean approved, String adminSubject, Instant at);
    void setActive(UUID partnerId, boolean active);
    void addTimeOff(String subject, LocalDate date, String reason, Instant now);
    void removeTimeOff(String subject, LocalDate date);
    List<LocalDate> timeOff(String subject, LocalDate from, LocalDate to);
    List<BookingHistory> history(UUID partnerId, int limit);
    Workload workload(UUID partnerId, Instant now);
}
