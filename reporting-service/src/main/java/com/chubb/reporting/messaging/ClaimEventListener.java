package com.chubb.reporting.messaging;

import com.chubb.claims.events.ClaimEvent;
import com.chubb.reporting.service.ClaimProjectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimEventListener {

    private final ObjectMapper objectMapper;
    private final ClaimProjectionService projection;

    /**
     * Failures (including unparseable payloads) are retried and then dead-lettered to claims.events.DLT by the
     * error handler in KafkaConfig, so one poison message never blocks a partition.
     */
    @KafkaListener(topics = ClaimEvent.TOPIC)
    public void onMessage(String payload) throws Exception {
        ClaimEvent event = objectMapper.readValue(payload, ClaimEvent.class);
        boolean applied = projection.apply(event);
        log.info("Consumed {} v{} for claim {} (applied={})", event.type(), event.version(), event.claimNumber(), applied);
    }
}
