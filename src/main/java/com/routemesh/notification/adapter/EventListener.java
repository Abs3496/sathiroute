package com.routemesh.notification.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routemesh.notification.application.Notifier;
import com.routemesh.outbox.application.EventEnvelope;
import com.routemesh.outbox.application.Outbox;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "routemesh.jobs.enabled", havingValue = "true", matchIfMissing = true)
class EventListener {

    private final Notifier notifier;
    private final com.routemesh.notification.application.ServiceEventNotifier serviceNotifier;
    private final ObjectMapper json;
    private final MeterRegistry meters;

    EventListener(Notifier notifier, com.routemesh.notification.application.ServiceEventNotifier serviceNotifier,
                  ObjectMapper json, MeterRegistry meters) {
        this.notifier = notifier;
        this.serviceNotifier = serviceNotifier;
        this.json = json;
        this.meters = meters;
    }

    @KafkaListener(topics = Outbox.SHIPMENT_EVENTS, groupId = "routemesh-notifier")
    void on(String payload) throws java.io.IOException {
        EventEnvelope e = json.readValue(payload, EventEnvelope.class);
        if (notifier.handle(e)) {
            meters.counter("routemesh.notifications.sent", "type", e.type()).increment();
        }
    }

    @KafkaListener(topics = Outbox.SERVICE_EVENTS, groupId = "routemesh-service-notifier")
    void onService(String payload) throws java.io.IOException {
        EventEnvelope event = json.readValue(payload, EventEnvelope.class);
        if (serviceNotifier.handle(event)) {
            meters.counter("routemesh.notifications.sent", "type", event.type(), "channel", "email").increment();
        }
    }
}
