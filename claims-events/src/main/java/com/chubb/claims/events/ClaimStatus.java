package com.chubb.claims.events;

public enum ClaimStatus {
    SUBMITTED, UNDER_REVIEW, INFO_REQUESTED, APPROVED, REJECTED, SETTLED;

    /** Terminal claims carry no outstanding liability. */
    public boolean isTerminal() {
        return this == REJECTED || this == SETTLED;
    }
}
