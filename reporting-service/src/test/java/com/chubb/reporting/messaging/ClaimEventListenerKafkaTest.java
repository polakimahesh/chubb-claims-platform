package com.chubb.reporting.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.chubb.claims.events.ClaimEvent;
import com.chubb.claims.events.ClaimEventType;
import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import com.chubb.reporting.repository.ClaimViewRepository;
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

/** Real Kafka transport (embedded broker): messages on the topic become rows in the read model. */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = {ClaimEvent.TOPIC, ClaimEvent.TOPIC + ".DLT"})
class ClaimEventListenerKafkaTest {

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper json;
    @Autowired ClaimViewRepository views;

    private ClaimEvent event(UUID id, ClaimEventType type, long version, ClaimStatus status) {
        return new ClaimEvent(UUID.randomUUID(), type, id, "CLM-KAFKA", version, Instant.now(), Market.MY,
                ClaimType.MOTOR, status, null, "MYR", new BigDecimal("250.00"), null, Instant.now(), null,
                "c@example.com", "Claimant", null);
    }

    @Test
    void consumesEventsAndIgnoresStaleRedelivery() throws Exception {
        UUID id = UUID.randomUUID();
        kafka.send(ClaimEvent.TOPIC, id.toString(), json.writeValueAsString(event(id, ClaimEventType.CLAIM_SUBMITTED, 0, ClaimStatus.SUBMITTED)));
        kafka.send(ClaimEvent.TOPIC, id.toString(), json.writeValueAsString(event(id, ClaimEventType.CLAIM_ASSIGNED, 1, ClaimStatus.UNDER_REVIEW)));
        // stale redelivery of v0 after v1 must not roll the view back
        kafka.send(ClaimEvent.TOPIC, id.toString(), json.writeValueAsString(event(id, ClaimEventType.CLAIM_SUBMITTED, 0, ClaimStatus.SUBMITTED)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(views.findById(id)).hasValueSatisfying(v -> assertThat(v.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW)));
        Thread.sleep(1500);
        assertThat(views.findById(id).orElseThrow().getVersion()).isEqualTo(1);
    }

    @Test
    void poisonMessageDoesNotBlockLaterMessages() throws Exception {
        UUID id = UUID.randomUUID();
        kafka.send(ClaimEvent.TOPIC, id.toString(), "this is not json");
        kafka.send(ClaimEvent.TOPIC, id.toString(), json.writeValueAsString(event(id, ClaimEventType.CLAIM_SUBMITTED, 0, ClaimStatus.SUBMITTED)));

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> assertThat(views.findById(id)).isPresent());
    }
}
