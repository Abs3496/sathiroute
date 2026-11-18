package com.routemesh.tracking.application;

import com.routemesh.geo.domain.Ping;
import java.util.List;
import java.util.UUID;

public interface PartnerPingPublisher {
    String TOPIC = "sathihomecare.partner-location.v1";
    void publish(UUID partnerId, List<Ping> pings);
}
