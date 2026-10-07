package com.chubb.claims.dto;

import com.chubb.claims.entity.Claim;
import com.chubb.claims.entity.ClaimHistory;
import com.chubb.claims.entity.InfoRequest;
import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** Outbound API models. */
public final class ClaimResponses {
    private ClaimResponses() {
    }

    public record Detail(UUID id, String claimNumber, Market market, ClaimType claimType, ClaimStatus status,
                         String claimantName, String claimantEmail, String description, LocalDate incidentDate,
                         String currency, BigDecimal estimatedAmount, BigDecimal assessedAmount,
                         String assignedOfficerId, String rejectionReason, Instant submittedAt, Instant closedAt) {
        public static Detail from(Claim c) {
            return new Detail(c.getId(), c.getClaimNumber(), c.getMarket(), c.getClaimType(), c.getStatus(),
                    c.getClaimantName(), c.getClaimantEmail(), c.getDescription(), c.getIncidentDate(),
                    c.getCurrency(), c.getEstimatedAmount(), c.getAssessedAmount(), c.getAssignedOfficerId(),
                    c.getRejectionReason(), c.getSubmittedAt(), c.getClosedAt());
        }
    }

    /** Lightweight row for list/queue views. */
    public record Summary(UUID id, String claimNumber, Market market, ClaimType claimType, ClaimStatus status,
                          String currency, BigDecimal estimatedAmount, BigDecimal assessedAmount,
                          String assignedOfficerId, Instant submittedAt) {
        public static Summary from(Claim c) {
            return new Summary(c.getId(), c.getClaimNumber(), c.getMarket(), c.getClaimType(), c.getStatus(),
                    c.getCurrency(), c.getEstimatedAmount(), c.getAssessedAmount(), c.getAssignedOfficerId(),
                    c.getSubmittedAt());
        }
    }

    public record Info(UUID id, String question, String requestedBy, Instant requestedAt, String response,
                       Instant respondedAt, boolean open) {
        public static Info from(InfoRequest r) {
            return new Info(r.getId(), r.getQuestion(), r.getRequestedBy(), r.getRequestedAt(), r.getResponse(),
                    r.getRespondedAt(), r.isOpen());
        }
    }

    public record HistoryEntry(ClaimStatus fromStatus, ClaimStatus toStatus, String actor, String note,
                               Instant occurredAt) {
        public static HistoryEntry from(ClaimHistory h) {
            return new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getActor(), h.getNote(), h.getOccurredAt());
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
        public static <E, T> PageResponse<T> of(Page<E> page, java.util.function.Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                    page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
