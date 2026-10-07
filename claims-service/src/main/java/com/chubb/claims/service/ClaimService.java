package com.chubb.claims.service;

import com.chubb.claims.dto.ClaimRequests;
import com.chubb.claims.dto.ClaimResponses.Detail;
import com.chubb.claims.dto.ClaimResponses.HistoryEntry;
import com.chubb.claims.dto.ClaimResponses.Info;
import com.chubb.claims.dto.ClaimResponses.PageResponse;
import com.chubb.claims.dto.ClaimResponses.Summary;
import com.chubb.claims.entity.Claim;
import com.chubb.claims.entity.ClaimHistory;
import com.chubb.claims.entity.InfoRequest;
import com.chubb.claims.events.ClaimEventType;
import com.chubb.claims.events.ClaimStatus;
import com.chubb.claims.events.ClaimType;
import com.chubb.claims.events.Market;
import com.chubb.claims.exception.BusinessRuleException;
import com.chubb.claims.exception.ClaimNotFoundException;
import com.chubb.claims.repository.ClaimHistoryRepository;
import com.chubb.claims.repository.ClaimRepository;
import com.chubb.claims.repository.InfoRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the claims lifecycle. Each command runs in one transaction that updates the claim,
 * appends the audit trail and writes the Kafka event to the outbox, so the three can never diverge.
 */
@Service
@RequiredArgsConstructor
public class ClaimService {

    private static final Sort OLDEST_FIRST = Sort.by("submittedAt").ascending();

    private final ClaimRepository claims;
    private final InfoRequestRepository infoRequests;
    private final ClaimHistoryRepository history;
    private final OutboxService outbox;
    private final Clock clock;

    // ---------- claimant commands ----------

    @Transactional
    public Detail submit(ClaimRequests.Submit req) {
        Instant now = clock.instant();
        Claim claim = claims.saveAndFlush(Claim.submit(req.market(), req.claimType(), req.claimantName(),
                req.claimantEmail(), req.description(), req.incidentDate(), req.currency(),
                req.estimatedAmount(), now));
        recordChange(claim, null, ClaimEventType.CLAIM_SUBMITTED, req.claimantEmail(), "Claim submitted", now);
        return Detail.from(claim);
    }

    @Transactional
    public Info provideInfo(UUID claimId, UUID requestId, ClaimRequests.InfoAnswer answer) {
        Instant now = clock.instant();
        Claim claim = load(claimId);
        InfoRequest request = infoRequests.findById(requestId)
                .filter(r -> r.getClaimId().equals(claimId))
                .orElseThrow(() -> new BusinessRuleException("Information request not found on this claim"));
        if (!request.isOpen()) {
            throw new BusinessRuleException("Information request has already been answered");
        }
        request.respond(answer.response(), now);
        infoRequests.saveAndFlush(request);
        ClaimStatus from = claim.getStatus();
        if (!infoRequests.existsByClaimIdAndResponseIsNull(claimId)) {
            claim.infoProvided();
            recordChange(claim, from, ClaimEventType.INFO_PROVIDED, claim.getClaimantEmail(),
                    "Claimant provided requested information", now);
        }
        return Info.from(request);
    }

    // ---------- officer commands ----------

    @Transactional
    public Detail assign(UUID claimId, String officerId) {
        Instant now = clock.instant();
        Claim claim = load(claimId);
        ClaimStatus from = claim.getStatus();
        claim.assignTo(officerId);
        recordChange(claim, from, ClaimEventType.CLAIM_ASSIGNED, officerId, "Assigned to " + officerId, now);
        return Detail.from(claim);
    }

    @Transactional
    public Info requestInfo(UUID claimId, String officerId, ClaimRequests.InfoQuestion q) {
        Instant now = clock.instant();
        Claim claim = load(claimId);
        claim.requireAssignedTo(officerId);
        ClaimStatus from = claim.getStatus();
        claim.requestInfo();
        InfoRequest request = infoRequests.save(new InfoRequest(claimId, q.question(), officerId, now));
        recordChange(claim, from, ClaimEventType.INFO_REQUESTED, officerId, q.question(), now);
        return Info.from(request);
    }

    @Transactional
    public Detail assess(UUID claimId, String officerId, ClaimRequests.Assess req) {
        Instant now = clock.instant();
        Claim claim = load(claimId);
        claim.requireAssignedTo(officerId);
        claim.assess(req.assessedAmount());
        String note = "Assessed at " + req.assessedAmount() + " " + claim.getCurrency()
                + (req.note() == null ? "" : ": " + req.note());
        recordChange(claim, claim.getStatus(), ClaimEventType.CLAIM_ASSESSED, officerId, note, now);
        return Detail.from(claim);
    }

    @Transactional
    public Detail approve(UUID claimId, String officerId) {
        return transition(claimId, officerId, ClaimEventType.CLAIM_APPROVED, "Claim approved", Claim::approve);
    }

    @Transactional
    public Detail reject(UUID claimId, String officerId, ClaimRequests.Reject req) {
        Instant now = clock.instant();
        return transition(claimId, officerId, ClaimEventType.CLAIM_REJECTED, req.reason(),
                c -> c.reject(req.reason(), now));
    }

    @Transactional
    public Detail settle(UUID claimId, String officerId) {
        Instant now = clock.instant();
        return transition(claimId, officerId, ClaimEventType.CLAIM_SETTLED, "Claim settled", c -> c.settle(now));
    }

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public Detail get(UUID id) {
        return Detail.from(load(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<Summary> listForClaimant(String email, int page, int size) {
        return PageResponse.of(claims.findByClaimantEmailIgnoreCase(email, pageable(page, size, Sort.by("submittedAt").descending())),
                Summary::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<Summary> queue(Market market, ClaimType type, int page, int size) {
        return PageResponse.of(claims.findQueue(market, type, pageable(page, size, OLDEST_FIRST)), Summary::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<Summary> forOfficer(String officerId, ClaimStatus status, int page, int size) {
        return PageResponse.of(claims.findByOfficer(officerId, status, pageable(page, size, OLDEST_FIRST)),
                Summary::from);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID claimId) {
        load(claimId);
        return history.findByClaimIdOrderByOccurredAtAsc(claimId).stream().map(HistoryEntry::from).toList();
    }

    @Transactional(readOnly = true)
    public List<Info> infoRequests(UUID claimId) {
        load(claimId);
        return infoRequests.findByClaimIdOrderByRequestedAtAsc(claimId).stream().map(Info::from).toList();
    }

    // ---------- helpers ----------

    private Detail transition(UUID claimId, String officerId, ClaimEventType type, String note,
                              java.util.function.Consumer<Claim> action) {
        Instant now = clock.instant();
        Claim claim = load(claimId);
        claim.requireAssignedTo(officerId);
        ClaimStatus from = claim.getStatus();
        action.accept(claim);
        recordChange(claim, from, type, officerId, note, now);
        return Detail.from(claim);
    }

    /** Flushes the claim (bumping its version), appends history and enqueues the outbox event. */
    private void recordChange(Claim claim, ClaimStatus from, ClaimEventType type, String actor, String note,
                              Instant now) {
        claims.saveAndFlush(claim);
        history.save(new ClaimHistory(claim.getId(), from, claim.getStatus(), actor, note, now));
        outbox.enqueue(type, claim, now);
    }

    private Claim load(UUID id) {
        return claims.findById(id).orElseThrow(() -> new ClaimNotFoundException(id));
    }

    private static PageRequest pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
    }
}
