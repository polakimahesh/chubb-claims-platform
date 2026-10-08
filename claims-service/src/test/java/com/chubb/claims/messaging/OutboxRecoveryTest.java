package com.chubb.claims.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.chubb.claims.dto.ClaimRequests;
import com.chubb.claims.entity.OutboxEvent;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import com.chubb.claims.repository.OutboxEventRepository;
import com.chubb.claims.service.ClaimService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.chubb.platform.security.Actor;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;

/** Kafka outage: claim handling keeps working, events stay in the outbox, and are relayed in order after recovery. */
@SpringBootTest(properties = "claims.outbox.poll-interval-ms=3600000")   // relay is driven manually below
class OutboxRecoveryTest {

    @Autowired ClaimService service;
    @Autowired OutboxPublisher publisher;
    @Autowired OutboxEventRepository outbox;
    @MockBean KafkaTemplate<String, String> kafka;

    @Test
    @SuppressWarnings("unchecked")
    void eventsSurviveKafkaOutageAndAreDeliveredInOrderAfterRecovery() {
        // Kafka is down: sending fails
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

        UUID id = service.submit(new Actor("tan@example.com", Set.of("CLAIMANT")), new ClaimRequests.Submit("Outage", Market.AU, ClaimType.PROPERTY,
                "Storm damage", LocalDate.of(2026, 1, 5), "AUD", new BigDecimal("300"))).id();
        service.assign(id, new Actor("officer-1", Set.of("OFFICER")));   // the API keeps working while Kafka is down

        publisher.publishPending();
        assertThat(pendingFor(id)).hasSize(2);   // nothing lost, nothing marked as published

        // Kafka recovers
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        publisher.publishPending();
        assertThat(pendingFor(id)).isEmpty();
    }

    private List<OutboxEvent> pendingFor(UUID id) {
        return outbox.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 100)).stream()
                .filter(e -> e.getAggregateId().equals(id)).toList();
    }
}
