package com.chubb.reporting.entity;

import com.chubb.claims.events.ClaimEvent;
import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Reporting read model of one claim, overwritten by each newer event. */
@Entity
@Table(name = "claim_view")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimView {
    @Id
    private UUID claimId;
    private String claimNumber;
    @Enumerated(EnumType.STRING)
    private Market market;
    @Enumerated(EnumType.STRING)
    private ClaimType claimType;
    @Enumerated(EnumType.STRING)
    private ClaimStatus status;
    private String assignedOfficerId;
    private String currency;
    private BigDecimal estimatedAmount;
    private BigDecimal assessedAmount;
    private Instant submittedAt;
    private Instant closedAt;
    private long version;
    private Instant lastEventAt;

    public static ClaimView from(ClaimEvent e) {
        ClaimView v = new ClaimView();
        v.claimId = e.claimId();
        v.apply(e);
        return v;
    }

    /** Whether this event carries newer state than what we hold (false for duplicates and stale redeliveries). */
    public boolean isOlderThan(ClaimEvent e) {
        return version < e.version();
    }

    public void apply(ClaimEvent e) {
        this.claimNumber = e.claimNumber();
        this.market = e.market();
        this.claimType = e.claimType();
        this.status = e.status();
        this.assignedOfficerId = e.assignedOfficerId();
        this.currency = e.currency();
        this.estimatedAmount = e.estimatedAmount();
        this.assessedAmount = e.assessedAmount();
        this.submittedAt = e.submittedAt();
        this.closedAt = e.closedAt();
        this.version = e.version();
        this.lastEventAt = e.occurredAt();
    }

    /** Outstanding liability: the officer's assessment once made, otherwise the claimant's estimate. */
    public BigDecimal exposure() {
        return assessedAmount != null ? assessedAmount : estimatedAmount;
    }
}
