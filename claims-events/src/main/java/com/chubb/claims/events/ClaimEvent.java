package com.chubb.claims.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event-carried state transfer: every event holds the claim's full current state, so consumers
 * can be idempotent and out-of-order tolerant by comparing {@code version} (no replay of history needed).
 */
public record ClaimEvent(
        UUID eventId,
        ClaimEventType type,
        UUID claimId,
        String claimNumber,
        long version,
        Instant occurredAt,
        Market market,
        ClaimType claimType,
        ClaimStatus status,
        String assignedOfficerId,
        String currency,
        BigDecimal estimatedAmount,
        BigDecimal assessedAmount,
        Instant submittedAt,
        Instant closedAt) {

    public static final String TOPIC = "claims.events";
}
