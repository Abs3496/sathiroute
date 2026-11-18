/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Proprietary notice applies only where ownership is established; third-party licenses remain applicable.
 */
package com.routemesh.allocation.domain;

import com.routemesh.geo.domain.GeoPoint;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/** Immutable facts needed to assess a partner for one requested appointment. */
public record PartnerCandidate(UUID partnerId, GeoPoint location, Set<Skill> skills, boolean active,
                               boolean available, LocalTime workingStart, LocalTime workingEnd,
                               double serviceRadiusKm, int assignedBookings) {
    public PartnerCandidate {
        skills = Set.copyOf(skills);
        if (partnerId == null || location == null || workingStart == null || workingEnd == null
                || !workingEnd.isAfter(workingStart) || !Double.isFinite(serviceRadiusKm)
                || serviceRadiusKm <= 0 || assignedBookings < 0) {
            throw new IllegalArgumentException("invalid partner candidate");
        }
    }

    public record Skill(String serviceCategory, String code, boolean qualificationVerified) {
        public Skill {
            if (serviceCategory == null || serviceCategory.isBlank() || code == null || code.isBlank()) {
                throw new IllegalArgumentException("invalid partner skill");
            }
        }
    }
}
