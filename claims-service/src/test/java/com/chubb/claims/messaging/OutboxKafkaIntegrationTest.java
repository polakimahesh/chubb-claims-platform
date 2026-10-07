package com.chubb.claims.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chubb.claims.events.ClaimEvent;
import com.chubb.claims.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** Real Kafka transport (embedded broker): a REST call ends up on the topic, keyed by claim id, and is marked published. */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "claims.outbox.poll-interval-ms=200"})
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 3, topics = ClaimEvent.TOPIC)
class OutboxKafkaIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OutboxEventRepository outbox;
    @Autowired EmbeddedKafkaBroker broker;

    @Test
    void submittedAndAssignedClaimAreRelayedToKafkaInOrder() throws Exception {
        String body = mvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content("""
                        {"claimantName":"Kafka Test","claimantEmail":"k@example.com","market":"HK","claimType":"PROPERTY",
                         "description":"Water damage","incidentDate":"2026-01-10","currency":"HKD","estimatedAmount":900.00}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String claimId = json.readTree(body).get("id").asText();
        mvc.perform(post("/api/staff/claims/" + claimId + "/assign").header("X-Officer-Id", "officer-9"))
                .andExpect(status().isOk());

        Map<String, Object> props = KafkaTestUtils.consumerProps("outbox-test", "true", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, ClaimEvent.TOPIC);
            List<ConsumerRecord<String, String>> mine = new java.util.ArrayList<>();
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500)).forEach(r -> {
                    if (r.key().equals(claimId)) {
                        mine.add(r);
                    }
                });
                assertThat(mine).hasSize(2);
            });
            ClaimEvent first = json.readValue(mine.get(0).value(), ClaimEvent.class);
            ClaimEvent second = json.readValue(mine.get(1).value(), ClaimEvent.class);
            assertThat(first.type().name()).isEqualTo("CLAIM_SUBMITTED");
            assertThat(second.type().name()).isEqualTo("CLAIM_ASSIGNED");
            assertThat(second.version()).isGreaterThan(first.version());
            assertThat(mine.get(0).partition()).isEqualTo(mine.get(1).partition());
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outbox.findByPublishedAtIsNullOrderByIdAsc(org.springframework.data.domain.PageRequest.of(0, 50)))
                        .noneMatch(e -> e.getAggregateId().toString().equals(claimId)));
    }
}
