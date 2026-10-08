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
import com.chubb.platform.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Claims-staff API. The acting officer is the authenticated user (roles OFFICER / MANAGER). */
@RestController
@RequestMapping("/api/staff/claims")
@RequiredArgsConstructor
@Tag(name = "Claims staff")
public class StaffClaimController {

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
    @Operation(summary = "Workload: my claims (officers), or any officer's via officerId (managers)")
    public PageResponse<Summary> workload(Authentication auth,
                                          @RequestParam(required = false) String officerId,
                                          @RequestParam(required = false) ClaimStatus status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.forOfficer(Actor.from(auth), officerId, status, page, size);
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Pick up a claim from the queue (SUBMITTED -> UNDER_REVIEW)")
    public Detail assign(Authentication auth, @PathVariable UUID id) {
        return service.assign(id, Actor.from(auth));
    }

    @PostMapping("/{id}/reassign")
    @Operation(summary = "Hand a claim in review to another officer (its officer, or any claim for a manager)")
    public Detail reassign(Authentication auth, @PathVariable UUID id,
                           @Valid @RequestBody ClaimRequests.Reassign request) {
        return service.reassign(id, Actor.from(auth), request);
    }

    @PostMapping("/{id}/info-requests")
    @Operation(summary = "Ask the claimant for more information (UNDER_REVIEW -> INFO_REQUESTED)")
    public Info requestInfo(Authentication auth, @PathVariable UUID id,
                            @Valid @RequestBody ClaimRequests.InfoQuestion question) {
        return service.requestInfo(id, Actor.from(auth), question);
    }

    @PostMapping("/{id}/assess")
    @Operation(summary = "Record the liability assessment (claim stays UNDER_REVIEW)")
    public Detail assess(Authentication auth, @PathVariable UUID id,
                         @Valid @RequestBody ClaimRequests.Assess request) {
        return service.assess(id, Actor.from(auth), request);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an assessed claim (UNDER_REVIEW -> APPROVED)")
    public Detail approve(Authentication auth, @PathVariable UUID id) {
        return service.approve(id, Actor.from(auth));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a claim (UNDER_REVIEW -> REJECTED)")
    public Detail reject(Authentication auth, @PathVariable UUID id,
                         @Valid @RequestBody ClaimRequests.Reject request) {
        return service.reject(id, Actor.from(auth), request);
    }

    @PostMapping("/{id}/settle")
    @Operation(summary = "Settle an approved claim (APPROVED -> SETTLED)")
    public Detail settle(Authentication auth, @PathVariable UUID id) {
        return service.settle(id, Actor.from(auth));
    }
}
