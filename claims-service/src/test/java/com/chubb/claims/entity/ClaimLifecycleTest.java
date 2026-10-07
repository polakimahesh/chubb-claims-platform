package com.chubb.claims.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import com.chubb.claims.exception.BusinessRuleException;
import com.chubb.claims.exception.InvalidStateTransitionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ClaimLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private Claim newClaim() {
        return Claim.submit(Market.SG, ClaimType.MOTOR, "Tan", "tan@example.com", "Rear-ended",
                LocalDate.of(2026, 1, 10), "SGD", new BigDecimal("5000.00"), NOW);
    }

    private Claim underReview() {
        Claim c = newClaim();
        c.assignTo("officer-1");
        return c;
    }

    @Test
    void newClaimIsSubmittedWithClaimNumber() {
        Claim c = newClaim();
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(c.getClaimNumber()).startsWith("CLM-SG-2026-");
    }

    @Test
    void happyPathSubmitToSettled() {
        Claim c = underReview();
        c.assess(new BigDecimal("4200.00"));
        c.approve();
        c.settle(NOW.plusSeconds(60));
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(c.getClosedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void infoRequestRoundTripReturnsToReview() {
        Claim c = underReview();
        c.requestInfo();
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.INFO_REQUESTED);
        c.infoProvided();
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    }

    @Test
    void rejectClosesClaimWithReason() {
        Claim c = underReview();
        c.reject("Policy lapsed", NOW);
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(c.getRejectionReason()).isEqualTo("Policy lapsed");
        assertThat(c.getClosedAt()).isEqualTo(NOW);
    }

    @Test
    void cannotApproveWithoutAssessment() {
        assertThatThrownBy(() -> underReview().approve()).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cannotSkipReview() {
        Claim c = newClaim();
        assertThatThrownBy(c::approve).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> c.reject("no", NOW)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> c.settle(NOW)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void terminalStatesAreFinal() {
        Claim rejected = underReview();
        rejected.reject("x", NOW);
        assertThatThrownBy(() -> rejected.assignTo("o2")).isInstanceOf(InvalidStateTransitionException.class);

        Claim settled = underReview();
        settled.assess(BigDecimal.TEN);
        settled.approve();
        settled.settle(NOW);
        assertThatThrownBy(() -> settled.reject("x", NOW)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cannotAssessOutsideReview() {
        Claim c = underReview();
        c.requestInfo();
        assertThatThrownBy(() -> c.assess(BigDecimal.ONE)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void onlyAssignedOfficerMayAct() {
        Claim c = underReview();
        c.requireAssignedTo("officer-1");
        assertThatThrownBy(() -> c.requireAssignedTo("officer-2")).isInstanceOf(BusinessRuleException.class);
    }
}
