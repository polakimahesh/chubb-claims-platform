package com.chubb.claims.controller;

import com.chubb.claims.dto.ClaimRequests;
import com.chubb.claims.dto.ClaimResponses.Detail;
import com.chubb.claims.dto.ClaimResponses.Info;
import com.chubb.claims.dto.ClaimResponses.PageResponse;
import com.chubb.claims.dto.ClaimResponses.Summary;
import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import com.chubb.claims.service.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Claims-staff API. Authentication is out of scope: the acting officer is identified by the
 * {@code X-Officer-Id} header (see docs/decisions-and-assumptions.md).
 */
@RestController
@RequestMapping("/api/staff/claims")
@RequiredArgsConstructor
@Tag(name = "Claims staff")
public class StaffClaimController {

    private static final String OFFICER = "X-Officer-Id";

    private final ClaimService service;

    @GetMapping("/queue")
    @Operation(summary = "Intake queue: unassigned claims, oldest first, filterable by market and type")
    public PageResponse<Summary> queue(@RequestParam(required = false) Market market,
                                       @RequestParam(required = false) ClaimType claimType,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.queue(market, claimType, page, size);
    }

    @GetMapping
    @Operation(summary = "An officer's claims (workload), optionally filtered by status")
    public PageResponse<Summary> myClaims(@RequestParam @NotBlank String officerId,
                                          @RequestParam(required = false) ClaimStatus status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.forOfficer(officerId, status, page, size);
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Pick up a claim from the queue (SUBMITTED -> UNDER_REVIEW)")
    public Detail assign(@PathVariable UUID id, @RequestHeader(OFFICER) @NotBlank String officerId) {
        return service.assign(id, officerId);
    }

    @PostMapping("/{id}/info-requests")
    @Operation(summary = "Ask the claimant for more information (UNDER_REVIEW -> INFO_REQUESTED)")
    public Info requestInfo(@PathVariable UUID id, @RequestHeader(OFFICER) @NotBlank String officerId,
                            @Valid @RequestBody ClaimRequests.InfoQuestion question) {
        return service.requestInfo(id, officerId, question);
    }

    @PostMapping("/{id}/assess")
    @Operation(summary = "Record the liability assessment (claim stays UNDER_REVIEW)")
    public Detail assess(@PathVariable UUID id, @RequestHeader(OFFICER) @NotBlank String officerId,
                         @Valid @RequestBody ClaimRequests.Assess request) {
        return service.assess(id, officerId, request);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an assessed claim (UNDER_REVIEW -> APPROVED)")
    public Detail approve(@PathVariable UUID id, @RequestHeader(OFFICER) @NotBlank String officerId) {
        return service.approve(id, officerId);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a claim (UNDER_REVIEW -> REJECTED)")
    public Detail reject(@PathVariable UUID id, @RequestHeader(OFFICER) @NotBlank String officerId,
                         @Valid @RequestBody ClaimRequests.Reject request) {
        return service.reject(id, officerId, request);
    }

    @PostMapping("/{id}/settle")
    @Operation(summary = "Settle an approved claim (APPROVED -> SETTLED)")
    public Detail settle(@PathVariable UUID id, @RequestHeader(OFFICER) @NotBlank String officerId) {
        return service.settle(id, officerId);
    }
}
