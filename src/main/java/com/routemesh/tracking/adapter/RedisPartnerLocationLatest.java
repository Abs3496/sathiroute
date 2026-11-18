package com.routemesh.tracking.adapter;

import com.routemesh.geo.domain.GeoPoint;
import com.routemesh.tracking.application.PartnerLocationLatest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class RedisPartnerLocationLatest implements PartnerLocationLatest {
    private static final String PREFIX = "sathihomecare:partner-location:";
    private final StringRedisTemplate redis;
    private final Duration ttl;

    RedisPartnerLocationLatest(StringRedisTemplate redis,
            @Value("${routemesh.tracking.partner-location-ttl:PT15M}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public void update(UUID partnerId, Fix fix) {
        String value = fix.at() + "|" + fix.position().lat() + "|" + fix.position().lng();
        redis.opsForValue().set(PREFIX + partnerId, value, ttl);
    }

    @Override
    public Optional<Fix> latest(UUID partnerId) {
        String value = redis.opsForValue().get(PREFIX + partnerId);
        if (value == null) return Optional.empty();
        try {
            String[] parts = value.split("\\|", 3);
            return Optional.of(new Fix(Instant.parse(parts[0]), new GeoPoint(
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]))));
        } catch (RuntimeException ex) {
            redis.delete(PREFIX + partnerId);
            return Optional.empty();
        }
    }
}
