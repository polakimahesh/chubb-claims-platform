package com.chubb.reporting.config;

import com.chubb.claims.events.ClaimEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    /** Retry 3 times, 1s apart, then publish the record to claims.events.DLT and move on. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3));
    }

    @Bean
    NewTopic claimEventsTopic() {
        return TopicBuilder.name(ClaimEvent.TOPIC).partitions(3).replicas(1).build();
    }

    /** Same partition count as the source topic: the recoverer publishes to the same partition number. */
    @Bean
    NewTopic claimEventsDeadLetterTopic() {
        return TopicBuilder.name(ClaimEvent.TOPIC + ".DLT").partitions(3).replicas(1).build();
    }
}
