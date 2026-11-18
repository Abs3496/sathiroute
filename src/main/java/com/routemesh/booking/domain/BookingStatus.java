/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Subject to verification of ownership and applicable third-party licenses.
 */
package com.routemesh.booking.domain;

import com.routemesh.shared.domain.DomainConflictException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum BookingStatus {
    NEW, PENDING, ASSIGNED, CONFIRMED, PARTNER_ON_THE_WAY, PARTNER_NEARBY, PARTNER_ARRIVED,
    IN_PROGRESS, COMPLETED, CANCELLED, REJECTED;

    private static final Map<BookingStatus, Set<BookingStatus>> TRANSITIONS = Map.ofEntries(
            Map.entry(NEW, EnumSet.of(PENDING, CANCELLED)),
            Map.entry(PENDING, EnumSet.of(ASSIGNED, CANCELLED, REJECTED)),
            Map.entry(ASSIGNED, EnumSet.of(CONFIRMED, CANCELLED, REJECTED)),
            Map.entry(CONFIRMED, EnumSet.of(PARTNER_ON_THE_WAY, PARTNER_NEARBY, PARTNER_ARRIVED, CANCELLED)),
            Map.entry(PARTNER_ON_THE_WAY, EnumSet.of(PARTNER_NEARBY, PARTNER_ARRIVED, CANCELLED)),
            Map.entry(PARTNER_NEARBY, EnumSet.of(PARTNER_ARRIVED, CANCELLED)),
            Map.entry(PARTNER_ARRIVED, EnumSet.of(IN_PROGRESS, CANCELLED)),
            Map.entry(IN_PROGRESS, EnumSet.of(COMPLETED)),
            Map.entry(REJECTED, EnumSet.of(PENDING)),
            Map.entry(COMPLETED, EnumSet.noneOf(BookingStatus.class)),
            Map.entry(CANCELLED, EnumSet.noneOf(BookingStatus.class)));

    public void checkTransitionTo(BookingStatus next) {
        if (!TRANSITIONS.getOrDefault(this, Set.of()).contains(next)) {
            throw new DomainConflictException("illegal-booking-transition", "booking cannot move from " + this + " to " + next);
        }
    }

    public boolean editable() {
        return this == NEW || this == PENDING;
    }

    public boolean cancellableByCustomer() {
        return this == NEW || this == PENDING || this == ASSIGNED || this == CONFIRMED
                || this == PARTNER_ON_THE_WAY || this == PARTNER_NEARBY || this == PARTNER_ARRIVED;
    }
}
