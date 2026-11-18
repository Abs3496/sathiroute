package com.routemesh.tracking.application;

import com.routemesh.geo.domain.GeoPoint;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PartnerLocationLatest {
    record Fix(Instant at, GeoPoint position) { }
    void update(UUID partnerId, Fix fix);
    Optional<Fix> latest(UUID partnerId);
}
