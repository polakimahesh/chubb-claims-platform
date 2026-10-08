package com.chubb.claims.service;

import com.chubb.claims.entity.Claim;
import com.chubb.claims.entity.OutboxEvent;
import com.chubb.claims.events.ClaimEvent;
import com.chubb.claims.events.ClaimEventType;
import com.chubb.claims.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes domain events to the outbox table inside the caller's transaction. */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(ClaimEventType type, Claim c, String note, Instant now) {
        ClaimEvent event = new ClaimEvent(UUID.randomUUID(), type, c.getId(), c.getClaimNumber(), c.getVersion(), now,
                c.getMarket(), c.getClaimType(), c.getStatus(), c.getAssignedOfficerId(), c.getCurrency(),
                c.getEstimatedAmount(), c.getAssessedAmount(), c.getSubmittedAt(), c.getClosedAt(),
                c.getClaimantEmail(), c.getClaimantName(), note);
        try {
            outbox.save(new OutboxEvent(c.getId(), objectMapper.writeValueAsString(event), now));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + type + " event for claim " + c.getId(), e);
        }
    }
}
