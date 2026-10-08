package com.chubb.notification.messaging;

import com.chubb.claims.events.ClaimEvent;
import com.chubb.notification.service.NotificationService;
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
    private final NotificationService notifications;

    /** Failures are retried then dead-lettered by the error handler in KafkaConfig. */
    @KafkaListener(topics = ClaimEvent.TOPIC)
    public void onMessage(String payload) throws Exception {
        ClaimEvent event = objectMapper.readValue(payload, ClaimEvent.class);
        boolean sent = notifications.handle(event);
        log.info("Consumed {} v{} for claim {} (notified={})", event.type(), event.version(), event.claimNumber(), sent);
    }
}
