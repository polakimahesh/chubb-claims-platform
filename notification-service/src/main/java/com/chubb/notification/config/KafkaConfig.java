package com.chubb.notification.config;

import com.chubb.claims.events.ClaimEvent;
import java.time.Clock;
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

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Retry 3 times, 1s apart, then publish the record to claims.events.DLT and move on. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3));
    }

    /** Declared identically by every service on the topic, so whichever starts first creates it with 3 partitions. */
    @Bean
    NewTopic claimEventsTopic() {
        return TopicBuilder.name(ClaimEvent.TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic claimEventsDeadLetterTopic() {
        return TopicBuilder.name(ClaimEvent.TOPIC + ".DLT").partitions(3).replicas(1).build();
    }
}
