/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Proprietary notice applies only where ownership is established; third-party licenses remain applicable.
 */
package com.routemesh.allocation.domain;

import com.routemesh.geo.domain.Haversine;

import java.util.Comparator;
import java.util.List;

/**
 * Deterministic eligibility and ranking policy. Ineligible partners are always removed before ranking.
 * Eligible partners are ordered by straight-line distance, then current workload, then stable partner ID.
 * No undocumented weighted score is used; road travel and existing appointment conflicts require richer route data.
 */
public final class PartnerAllocator {
    private PartnerAllocator() { }

    public static List<RankedPartner> rank(AllocationRequest request, List<PartnerCandidate> candidates) {
        return candidates.stream()
                .filter(p -> p.active() && p.available())
                .filter(p -> p.skills().stream().anyMatch(skill -> skill.qualificationVerified()
                        && skill.serviceCategory().equals(request.serviceCategory())
                        && skill.code().equals(request.requiredSkill())))
                .filter(p -> !request.start().isBefore(p.workingStart()) && !request.end().isAfter(p.workingEnd()))
                .map(p -> new RankedPartner(p,
                        Haversine.km(p.location(), request.location())))
                .filter(p -> p.distanceKm() <= p.partner().serviceRadiusKm())
                .sorted(Comparator.comparingDouble(RankedPartner::distanceKm)
                        .thenComparingInt(p -> p.partner().assignedBookings())
                        .thenComparing(p -> p.partner().partnerId()))
                .toList();
    }

    public record RankedPartner(PartnerCandidate partner, double distanceKm) { }
}
