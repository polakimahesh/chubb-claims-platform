package com.chubb.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.chubb.claims.events.ClaimEvent;
import com.chubb.claims.events.ClaimEventType;
import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import com.chubb.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

/** Real Kafka transport (embedded broker): events on the topic become notifications, exactly once. */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = {ClaimEvent.TOPIC, ClaimEvent.TOPIC + ".DLT"})
class NotificationKafkaTest {

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper json;
    @Autowired NotificationRepository repository;

    private ClaimEvent event(UUID claimId, ClaimEventType type) {
        return new ClaimEvent(UUID.randomUUID(), type, claimId, "CLM-KAFKA", 1, Instant.now(), Market.MY,
                ClaimType.MOTOR, ClaimStatus.APPROVED, "officer-1", "MYR", new BigDecimal("250.00"), null,
                Instant.now(), null, "kafka-user@example.com", "Kafka User", null);
    }

    @Test
    void consumesEventsDeduplicatesRedeliveryAndSurvivesPoisonMessages() throws Exception {
        UUID claimId = UUID.randomUUID();
        ClaimEvent approved = event(claimId, ClaimEventType.CLAIM_APPROVED);
        kafka.send(ClaimEvent.TOPIC, claimId.toString(), "this is not json");               // poison: dead-lettered
        kafka.send(ClaimEvent.TOPIC, claimId.toString(), json.writeValueAsString(approved));
        kafka.send(ClaimEvent.TOPIC, claimId.toString(), json.writeValueAsString(approved)); // duplicate delivery
        kafka.send(ClaimEvent.TOPIC, claimId.toString(), json.writeValueAsString(event(claimId, ClaimEventType.CLAIM_SETTLED)));

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(repository.findByRecipientEmailIgnoreCase("kafka-user@example.com",
                        org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2));
    }
}
