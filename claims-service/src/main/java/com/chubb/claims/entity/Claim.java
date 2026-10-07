package com.chubb.claims.entity;

import static com.chubb.claims.events.ClaimStatus.APPROVED;
import static com.chubb.claims.events.ClaimStatus.INFO_REQUESTED;
import static com.chubb.claims.events.ClaimStatus.REJECTED;
import static com.chubb.claims.events.ClaimStatus.SETTLED;
import static com.chubb.claims.events.ClaimStatus.SUBMITTED;
import static com.chubb.claims.events.ClaimStatus.UNDER_REVIEW;

import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import com.chubb.claims.exception.BusinessRuleException;
import com.chubb.claims.exception.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Claim aggregate. All lifecycle rules live here so a controller or service cannot bypass them.
 *
 * <pre>
 * SUBMITTED -> UNDER_REVIEW -> APPROVED -> SETTLED
 *                 |   ^   \
 *                 v   |    +--> REJECTED
 *            INFO_REQUESTED
 * </pre>
 */
@Entity
@Table(name = "claims")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Claim {

    private static final Map<ClaimStatus, Set<ClaimStatus>> ALLOWED = Map.of(
            SUBMITTED, EnumSet.of(UNDER_REVIEW),
            UNDER_REVIEW, EnumSet.of(INFO_REQUESTED, APPROVED, REJECTED),
            INFO_REQUESTED, EnumSet.of(UNDER_REVIEW),
            APPROVED, EnumSet.of(SETTLED),
            REJECTED, EnumSet.noneOf(ClaimStatus.class),
            SETTLED, EnumSet.noneOf(ClaimStatus.class));

    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String claimNumber;
    @Enumerated(EnumType.STRING)
    private Market market;
    @Enumerated(EnumType.STRING)
    private ClaimType claimType;
    @Enumerated(EnumType.STRING)
    private ClaimStatus status;
    private String claimantName;
    private String claimantEmail;
    private String description;
    private LocalDate incidentDate;
    private String currency;
    private BigDecimal estimatedAmount;
    private BigDecimal assessedAmount;
    private String assignedOfficerId;
    private String rejectionReason;
    private Instant submittedAt;
    private Instant closedAt;
    @Version
    private Long version;

    public static Claim submit(Market market, ClaimType type, String claimantName, String claimantEmail,
                               String description, LocalDate incidentDate, String currency,
                               BigDecimal estimatedAmount, Instant now) {
        Claim c = new Claim();
        c.id = UUID.randomUUID();
        c.claimNumber = "CLM-" + market + "-" + now.atZone(ZoneOffset.UTC).getYear() + "-"
                + c.id.toString().substring(0, 8).toUpperCase();
        c.market = market;
        c.claimType = type;
        c.status = SUBMITTED;
        c.claimantName = claimantName;
        c.claimantEmail = claimantEmail;
        c.description = description;
        c.incidentDate = incidentDate;
        c.currency = currency;
        c.estimatedAmount = estimatedAmount;
        c.submittedAt = now;
        return c;
    }

    /** An officer picks the claim up from the intake queue. */
    public void assignTo(String officerId) {
        transitionTo(UNDER_REVIEW);
        this.assignedOfficerId = officerId;
    }

    public void requestInfo() {
        transitionTo(INFO_REQUESTED);
    }

    public void infoProvided() {
        transitionTo(UNDER_REVIEW);
    }

    /** Records the liability assessment; the claim stays under review. */
    public void assess(BigDecimal amount) {
        if (status != UNDER_REVIEW) {
            throw new InvalidStateTransitionException(
                    "Claim can only be assessed while UNDER_REVIEW, but is " + status);
        }
        this.assessedAmount = amount;
    }

    public void approve() {
        requireTransition(APPROVED);   // a closed or unassigned claim is a state conflict, checked before the assessment rule
        if (assessedAmount == null) {
            throw new BusinessRuleException("Claim must be assessed before it can be approved");
        }
        transitionTo(APPROVED);
    }

    public void reject(String reason, Instant now) {
        transitionTo(REJECTED);
        this.rejectionReason = reason;
        this.closedAt = now;
    }

    public void settle(Instant now) {
        transitionTo(SETTLED);
        this.closedAt = now;
    }

    /** Only the assigned officer may act on a claim that has been picked up. */
    public void requireAssignedTo(String officerId) {
        if (assignedOfficerId != null && !assignedOfficerId.equals(officerId)) {
            throw new BusinessRuleException("Claim is assigned to " + assignedOfficerId);
        }
    }

    private void requireTransition(ClaimStatus target) {
        if (!ALLOWED.get(status).contains(target)) {
            throw new InvalidStateTransitionException("Cannot move claim from " + status + " to " + target);
        }
    }

    private void transitionTo(ClaimStatus target) {
        requireTransition(target);
        this.status = target;
    }
}
