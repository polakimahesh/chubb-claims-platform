package com.chubb.claims.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event-carried state transfer: every event holds the claim's full current state, so consumers
 * can be idempotent and out-of-order tolerant by comparing {@code version} (no replay of history needed).
 *
 * <p>{@code claimantEmail}/{@code claimantName} let notification consumers reach the claimant without calling back
 * into claims-service. {@code note} carries the human-readable reason for the change (for example the question
 * asked of the claimant or the rejection reason).
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
        Instant closedAt,
        String claimantEmail,
        String claimantName,
        String note) {

    public static final String TOPIC = "claims.events";
}
