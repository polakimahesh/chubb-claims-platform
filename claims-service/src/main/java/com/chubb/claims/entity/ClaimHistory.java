package com.chubb.claims.entity;

import com.chubb.claims.events.ClaimStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Append-only audit trail of status changes. */
@Entity
@Table(name = "claim_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimHistory {
    @Id
    private UUID id;
    private UUID claimId;
    @Enumerated(EnumType.STRING)
    private ClaimStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private ClaimStatus toStatus;
    private String actor;
    private String note;
    private Instant occurredAt;

    public ClaimHistory(UUID claimId, ClaimStatus from, ClaimStatus to, String actor, String note, Instant now) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.fromStatus = from;
        this.toStatus = to;
        this.actor = actor;
        this.note = note;
        this.occurredAt = now;
    }
}
