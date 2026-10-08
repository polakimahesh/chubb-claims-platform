package com.chubb.claims.controller;

import com.chubb.claims.dto.ClaimRequests;
import com.chubb.claims.dto.ClaimResponses.Detail;
import com.chubb.claims.dto.ClaimResponses.HistoryEntry;
import com.chubb.claims.dto.ClaimResponses.Info;
import com.chubb.claims.dto.ClaimResponses.PageResponse;
import com.chubb.claims.dto.ClaimResponses.Summary;
import com.chubb.claims.service.ClaimService;
import com.chubb.platform.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Claimant-facing API: report an incident, track a claim, answer information requests. */
@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
@Tag(name = "Claimant")
public class ClaimController {

    private final ClaimService service;

    @PostMapping
    @Operation(summary = "Report an incident / submit a claim (role CLAIMANT; the authenticated user is the claimant)")
    public ResponseEntity<Detail> submit(Authentication auth, @Valid @RequestBody ClaimRequests.Submit request) {
        Detail created = service.submit(Actor.from(auth), request);
        return ResponseEntity.created(URI.create("/api/claims/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "List my claims (role CLAIMANT)")
    public PageResponse<Summary> listMine(Authentication auth,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.listMine(Actor.from(auth), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Track a claim (claimants see only their own)")
    public Detail get(Authentication auth, @PathVariable UUID id) {
        return service.get(id, Actor.from(auth));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Status timeline of a claim")
    public List<HistoryEntry> history(Authentication auth, @PathVariable UUID id) {
        return service.history(id, Actor.from(auth));
    }

    @GetMapping("/{id}/info-requests")
    @Operation(summary = "Information requested from the claimant (open and answered)")
    public List<Info> infoRequests(Authentication auth, @PathVariable UUID id) {
        return service.infoRequests(id, Actor.from(auth));
    }

    @PostMapping("/{id}/info-requests/{requestId}/response")
    @Operation(summary = "Provide additional information when asked (role CLAIMANT)")
    public Info respond(Authentication auth, @PathVariable UUID id, @PathVariable UUID requestId,
                        @Valid @RequestBody ClaimRequests.InfoAnswer answer) {
        return service.provideInfo(id, Actor.from(auth), requestId, answer);
    }
}
