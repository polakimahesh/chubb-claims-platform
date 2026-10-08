package com.chubb.claims.dto;

import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Inbound API models. The claimant's identity comes from the authenticated user, never from the body. */
public final class ClaimRequests {
    private ClaimRequests() {
    }

    public record Submit(
            @NotBlank @Size(max = 200) String claimantName,
            @NotNull Market market,
            @NotNull ClaimType claimType,
            @NotBlank @Size(max = 4000) String description,
            @NotNull @PastOrPresent LocalDate incidentDate,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 code, e.g. SGD") String currency,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2) BigDecimal estimatedAmount) {
    }

    public record Assess(
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2) BigDecimal assessedAmount,
            @Size(max = 2000) String note) {
    }

    public record Reject(@NotBlank @Size(max = 1000) String reason) {
    }

    public record Reassign(@NotBlank @Size(max = 100) String toOfficerId, @Size(max = 500) String reason) {
    }

    public record InfoQuestion(@NotBlank @Size(max = 2000) String question) {
    }

    public record InfoAnswer(@NotBlank @Size(max = 4000) String response) {
    }
}
