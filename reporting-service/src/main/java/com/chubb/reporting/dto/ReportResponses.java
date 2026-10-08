package com.chubb.reporting.dto;

import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReportResponses {
    private ReportResponses() {
    }

    /** Outstanding liability for one market / claim type / currency. Currencies are never summed together. */
    public record Exposure(Market market, ClaimType claimType, String currency, long openClaims,
                           BigDecimal totalExposure) {
    }

    /** All open exposure converted into one base currency, with the per-currency breakdown behind the total. */
    public record ExposureTotal(String baseCurrency, BigDecimal totalExposure, long openClaims,
                                List<ConvertedExposure> breakdown, String rateSource) {
    }

    public record ConvertedExposure(String currency, long openClaims, BigDecimal originalAmount,
                                    BigDecimal convertedAmount) {
    }

    public record OfficerWorkload(String officerId, long openClaims, Map<ClaimStatus, Long> byStatus) {
    }

    public record OfficerPerformance(String officerId, long closedClaims, long settled, long rejected,
                                     double averageResolutionHours) {
    }

    /** A claim that has breached a service-level threshold. */
    public record SlaBreach(UUID claimId, String claimNumber, Market market, ClaimType claimType,
                            ClaimStatus status, String assignedOfficerId, BreachType breachType, long ageHours,
                            long thresholdHours) {
    }

    public enum BreachType { UNASSIGNED_TOO_LONG, OPEN_TOO_LONG }

    public record Summary(Map<ClaimStatus, Long> claimsByStatus, long openClaims, long unassignedBacklog,
                          List<Exposure> exposure) {
    }
}
