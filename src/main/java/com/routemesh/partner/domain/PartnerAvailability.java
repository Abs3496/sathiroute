/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Subject to verification of ownership and applicable third-party licenses.
 */
package com.routemesh.partner.domain;

import java.time.LocalTime;

public record PartnerAvailability(int dayOfWeek, LocalTime startsAt, LocalTime endsAt) {
    public PartnerAvailability {
        if (dayOfWeek < 1 || dayOfWeek > 7 || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("availability requires weekday 1..7 and a positive time range");
        }
    }
}
