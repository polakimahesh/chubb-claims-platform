package com.chubb.claims.messaging;

import com.chubb.claims.entity.OutboxEvent;
import com.chubb.claims.events.ClaimEvent;
import com.chubb.claims.repository.OutboxEventRepository;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays outbox rows to Kafka in insertion order. Delivery is at-least-once (a crash between send and commit
 * re-sends); consumers de-duplicate on the claim version carried in each event. The claim id is the record key,
 * so all events of one claim land on one partition and stay ordered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "claims.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${claims.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outbox.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, BATCH_SIZE));
        for (OutboxEvent e : batch) {
            try {
                kafka.send(ClaimEvent.TOPIC, e.getAggregateId().toString(), e.getPayload()).get(5, TimeUnit.SECONDS);
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                // Stop at the first failure to preserve ordering; rows already sent are committed below.
                log.warn("Kafka publish failed for outbox event {} - will retry: {}", e.getId(), ex.toString());
                break;
            }
            e.markPublished(clock.instant());
        }
    }
}
