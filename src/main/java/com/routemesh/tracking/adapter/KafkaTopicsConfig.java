package com.routemesh.tracking.adapter;

import com.routemesh.outbox.application.Outbox;
import com.routemesh.tracking.application.PartnerPingPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
class KafkaTopicsConfig {
    @Bean
    NewTopic driverPingsTopic() {
        return topic("routemesh.pings");
    }

    @Bean
    NewTopic partnerLocationTopic() {
        return topic(PartnerPingPublisher.TOPIC);
    }

    @Bean
    NewTopic shipmentEventsTopic() {
        return topic(Outbox.SHIPMENT_EVENTS);
    }

    @Bean
    NewTopic serviceEventsTopic() {
        return topic(Outbox.SERVICE_EVENTS);
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
