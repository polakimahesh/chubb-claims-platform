package com.chubb.claims.config;

import com.chubb.claims.events.ClaimEvent;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Keyed by claim id; 3 partitions let reporting scale out while per-claim order is preserved. */
    @Bean
    NewTopic claimEventsTopic() {
        return TopicBuilder.name(ClaimEvent.TOPIC).partitions(3).replicas(1).build();
    }
}
