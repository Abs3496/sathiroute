/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Proprietary notice applies only where ownership is established; third-party licenses remain applicable.
 */
package com.routemesh.allocation.domain;

import com.routemesh.geo.domain.GeoPoint;

import java.time.LocalTime;
import java.util.Set;

/** Appointment constraints used for eligibility and candidate ordering. */
public record AllocationRequest(String serviceCategory, String requiredSkill, GeoPoint location, LocalTime start,
                                LocalTime end) {
    public AllocationRequest {
        if (serviceCategory == null
                || !Set.of("NURSING", "AYURVEDIC_THERAPY", "COUNSELLING").contains(serviceCategory)
                || requiredSkill == null || requiredSkill.isBlank() || location == null || start == null || end == null
                || !end.isAfter(start)) {
            throw new IllegalArgumentException("invalid allocation request");
        }
    }
}
