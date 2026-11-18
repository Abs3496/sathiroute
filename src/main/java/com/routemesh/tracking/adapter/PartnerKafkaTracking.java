package com.routemesh.tracking.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routemesh.geo.domain.GeoPoint;
import com.routemesh.geo.domain.Ping;
import com.routemesh.tracking.application.PartnerPingPublisher;
import com.routemesh.tracking.application.PartnerTrackingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Partner GPS has its own ordered Kafka stream, isolated from delivery driver positions. */
class PartnerKafkaTracking {
    private PartnerKafkaTracking() { }
    record Fix(double latitude, double longitude, Instant at) { }

    @Component
    static class Publisher implements PartnerPingPublisher {
        private final KafkaTemplate<String, String> kafka;
        private final ObjectMapper json;
        Publisher(KafkaTemplate<String, String> kafka, ObjectMapper json) { this.kafka = kafka; this.json = json; }

        @Override
        public void publish(UUID partnerId, List<Ping> pings) {
            try {
                String payload = json.writeValueAsString(pings.stream()
                        .map(p -> new Fix(p.position().lat(), p.position().lng(), p.at())).toList());
                kafka.send(TOPIC, partnerId.toString(), payload).get(5, TimeUnit.SECONDS);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while publishing partner locations", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("broker did not acknowledge partner location batch", e);
            }
        }
    }

    @Component
    @ConditionalOnProperty(name = "routemesh.jobs.enabled", havingValue = "true", matchIfMissing = true)
    static class Listener {
        private final PartnerTrackingService tracking;
        private final ObjectMapper json;
        Listener(PartnerTrackingService tracking, ObjectMapper json) { this.tracking = tracking; this.json = json; }

        @KafkaListener(topics = PartnerPingPublisher.TOPIC, groupId = "sathihomecare-partner-tracking", concurrency = "3")
        void locations(ConsumerRecord<String, String> record) throws IOException {
            UUID partnerId = UUID.fromString(record.key());
            List<Fix> fixes = json.readValue(record.value(), new TypeReference<>() { });
            tracking.process(partnerId, fixes.stream().map(f -> new Ping(partnerId.toString(), f.at(),
                    new GeoPoint(f.latitude(), f.longitude()))).toList());
        }
    }
}
