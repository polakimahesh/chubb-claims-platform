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
import com.chubb.claims.exception.ForbiddenException;
import com.chubb.claims.exception.InvalidRequestException;
import com.chubb.claims.repository.ClaimHistoryRepository;
import com.chubb.claims.repository.ClaimRepository;
import com.chubb.claims.repository.InfoRequestRepository;
import com.chubb.platform.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the claims lifecycle. Each command runs in one transaction that updates the claim,
 * appends the audit trail and writes the Kafka event to the outbox, so the three can never diverge.
 *
 * <p>Authorisation: URL-level role rules live in SecurityConfig; record-level rules (a claimant only sees their own
 * claims, an officer only acts on claims assigned to them) are enforced here.
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
    public Detail submit(Actor claimant, ClaimRequests.Submit req) {
        if (!req.market().currency().equals(req.currency())) {
            throw new InvalidRequestException("Claims in market " + req.market() + " must be lodged in "
                    + req.market().currency() + ", not " + req.currency());
        }
        Instant now = clock.instant();
        Claim claim = claims.saveAndFlush(Claim.submit(req.market(), req.claimType(), req.claimantName(),
                claimant.username(), req.description(), req.incidentDate(), req.currency(),
                req.estimatedAmount(), now));
        recordChange(claim, null, ClaimEventType.CLAIM_SUBMITTED, claimant.username(), "Claim submitted", now);
        return Detail.from(claim);
    }

    @Transactional
    public Info provideInfo(UUID claimId, Actor claimant, UUID requestId, ClaimRequests.InfoAnswer answer) {
        Instant now = clock.instant();
        Claim claim = loadVisible(claimId, claimant);
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
    public Detail assign(UUID claimId, Actor officer) {
        Instant now = clock.instant();
        Claim claim = load(claimId);
        ClaimStatus from = claim.getStatus();
        claim.assignTo(officer.username());
        recordChange(claim, from, ClaimEventType.CLAIM_ASSIGNED, officer.username(),
                "Assigned to " + officer.username(), now);
        return Detail.from(claim);
    }

    /** A manager may reassign any in-review claim; an officer only their own. */
    @Transactional
    public Detail reassign(UUID claimId, Actor actor, ClaimRequests.Reassign req) {
        Instant now = clock.instant();
        Claim claim = load(claimId);
        if (!actor.isManager()) {
            claim.requireAssignedTo(actor.username());
        }
        String previous = claim.getAssignedOfficerId();
        ClaimStatus status = claim.getStatus();
        claim.reassignTo(req.toOfficerId());
        recordChange(claim, status, ClaimEventType.CLAIM_REASSIGNED, actor.username(),
                "Reassigned from " + previous + " to " + req.toOfficerId()
                        + (req.reason() == null ? "" : ": " + req.reason()), now);
        return Detail.from(claim);
    }

    @Transactional
    public Info requestInfo(UUID claimId, Actor officer, ClaimRequests.InfoQuestion q) {
        Instant now = clock.instant();
        Claim claim = load(claimId);
        claim.requireAssignedTo(officer.username());
        ClaimStatus from = claim.getStatus();
        claim.requestInfo();
        InfoRequest request = infoRequests.save(new InfoRequest(claimId, q.question(), officer.username(), now));
        recordChange(claim, from, ClaimEventType.INFO_REQUESTED, officer.username(), q.question(), now);
        return Info.from(request);
    }

    @Transactional
    public Detail assess(UUID claimId, Actor officer, ClaimRequests.Assess req) {
        Instant now = clock.instant();
        Claim claim = load(claimId);
        claim.requireAssignedTo(officer.username());
        claim.assess(req.assessedAmount());
        String note = "Assessed at " + req.assessedAmount() + " " + claim.getCurrency()
                + (req.note() == null ? "" : ": " + req.note());
        recordChange(claim, claim.getStatus(), ClaimEventType.CLAIM_ASSESSED, officer.username(), note, now);
        return Detail.from(claim);
    }

    @Transactional
    public Detail approve(UUID claimId, Actor officer) {
        return transition(claimId, officer, ClaimEventType.CLAIM_APPROVED, "Claim approved", Claim::approve);
    }

    @Transactional
    public Detail reject(UUID claimId, Actor officer, ClaimRequests.Reject req) {
        Instant now = clock.instant();
        return transition(claimId, officer, ClaimEventType.CLAIM_REJECTED, req.reason(),
                c -> c.reject(req.reason(), now));
    }

    @Transactional
    public Detail settle(UUID claimId, Actor officer) {
        Instant now = clock.instant();
        return transition(claimId, officer, ClaimEventType.CLAIM_SETTLED, "Claim settled", c -> c.settle(now));
    }

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public Detail get(UUID id, Actor actor) {
        return Detail.from(loadVisible(id, actor));
    }

    @Transactional(readOnly = true)
    public PageResponse<Summary> listMine(Actor claimant, int page, int size) {
        return PageResponse.of(claims.findByClaimantEmailIgnoreCase(claimant.username(),
                pageable(page, size, Sort.by("submittedAt").descending())), Summary::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<Summary> queue(Market market, ClaimType type, int page, int size) {
        return PageResponse.of(claims.findQueue(market, type, pageable(page, size, OLDEST_FIRST)), Summary::from);
    }

    /** Officers see their own claims; managers may look at any officer's. */
    @Transactional(readOnly = true)
    public PageResponse<Summary> forOfficer(Actor actor, String officerId, ClaimStatus status, int page, int size) {
        String target = officerId == null || officerId.isBlank() ? actor.username() : officerId;
        if (!actor.isManager() && !target.equals(actor.username())) {
            throw new ForbiddenException("Officers can only view their own claims");
        }
        return PageResponse.of(claims.findByOfficer(target, status, pageable(page, size, OLDEST_FIRST)),
                Summary::from);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID claimId, Actor actor) {
        loadVisible(claimId, actor);
        return history.findByClaimIdOrderByOccurredAtAsc(claimId).stream().map(HistoryEntry::from).toList();
    }

    @Transactional(readOnly = true)
    public List<Info> infoRequests(UUID claimId, Actor actor) {
        loadVisible(claimId, actor);
        return infoRequests.findByClaimIdOrderByRequestedAtAsc(claimId).stream().map(Info::from).toList();
    }

    // ---------- helpers ----------

    private Detail transition(UUID claimId, Actor officer, ClaimEventType type, String note, Consumer<Claim> action) {
        Instant now = clock.instant();
        Claim claim = load(claimId);
        claim.requireAssignedTo(officer.username());
        ClaimStatus from = claim.getStatus();
        action.accept(claim);
        recordChange(claim, from, type, officer.username(), note, now);
        return Detail.from(claim);
    }

    /** Flushes the claim (bumping its version), appends history and enqueues the outbox event. */
    private void recordChange(Claim claim, ClaimStatus from, ClaimEventType type, String actor, String note,
                              Instant now) {
        claims.saveAndFlush(claim);
        history.save(new ClaimHistory(claim.getId(), from, claim.getStatus(), actor, note, now));
        outbox.enqueue(type, claim, note, now);
    }

    private Claim load(UUID id) {
        return claims.findById(id).orElseThrow(() -> new ClaimNotFoundException(id));
    }

    /** Loads a claim the actor may see. Claimants get "not found" for other people's claims (no enumeration). */
    Claim loadVisible(UUID id, Actor actor) {
        Claim claim = load(id);
        if (actor.isClaimant() && !claim.getClaimantEmail().equalsIgnoreCase(actor.username())) {
            throw new ClaimNotFoundException(id);
        }
        return claim;
    }

    private static PageRequest pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
    }
}
