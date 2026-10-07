package com.chubb.reporting.dto;

import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ReportResponses {
    private ReportResponses() {
    }

    /** Outstanding liability for one market / claim type / currency. Currencies are never summed together. */
    public record Exposure(Market market, ClaimType claimType, String currency, long openClaims,
                           BigDecimal totalExposure) {
    }

    public record OfficerWorkload(String officerId, long openClaims, Map<ClaimStatus, Long> byStatus) {
    }

    public record OfficerPerformance(String officerId, long closedClaims, long settled, long rejected,
                                     double averageResolutionHours) {
    }

    public record Summary(Map<ClaimStatus, Long> claimsByStatus, long openClaims, long unassignedBacklog,
                          List<Exposure> exposure) {
    }
}
