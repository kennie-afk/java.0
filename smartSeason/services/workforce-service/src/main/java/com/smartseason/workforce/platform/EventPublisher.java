package com.smartseason.workforce.platform;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final boolean enabled;
    private final String topicPrefix;
    private final String producer;

    public EventPublisher(KafkaTemplate<String, Object> kafka,
                          @Value("${smartseason.events.enabled:false}") boolean enabled,
                          @Value("${smartseason.events.topic-prefix:ss}") String topicPrefix,
                          @Value("${spring.application.name}") String producer) {
        this.kafka = kafka;
        this.enabled = enabled;
        this.topicPrefix = topicPrefix;
        this.producer = producer;
    }

    public void publish(String domain, String type, UUID aggregateId, Object payload) {
        DomainEvent<Object> event = DomainEvent.of(type, aggregateId, producer, payload);
        String topic = "%s.%s.%s.v1".formatted(topicPrefix, domain, camelToKebab(type));

        if (!enabled) {
            log.debug("[events disabled] would publish {} to {}", type, topic);
            return;
        }

        kafka.send(topic, aggregateId == null ? null : aggregateId.toString(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Failed to publish {} to {}", type, topic, error);
                    } else {
                        log.debug("Published {} to {}", type, topic);
                    }
                });
    }

    private static String camelToKebab(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}
