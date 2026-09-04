package com.kenyarealestate.pms.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {
    @Bean public NewTopic pmsEvents()    { return TopicBuilder.name("pms-events").partitions(3).replicas(1).build(); }
    @Bean public NewTopic pmsEventsDlt() { return TopicBuilder.name("pms-events.DLT").partitions(1).replicas(1).build(); }
}
