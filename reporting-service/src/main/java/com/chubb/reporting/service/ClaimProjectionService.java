package com.chubb.reporting.service;

import com.chubb.claims.events.ClaimEvent;
import com.chubb.reporting.entity.ClaimView;
import com.chubb.reporting.repository.ClaimViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies claim events to the read model. Each event carries the claim's full state plus a monotonically
 * increasing version, so processing is idempotent (duplicates are ignored) and tolerant of redelivery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimProjectionService {

    private final ClaimViewRepository views;

    /** @return true if the event changed the read model, false if it was a duplicate or stale. */
    @Transactional
    public boolean apply(ClaimEvent event) {
        return views.findById(event.claimId())
                .map(view -> {
                    if (!view.isOlderThan(event)) {
                        log.debug("Ignoring duplicate/stale event {} v{} for claim {}", event.type(),
                                event.version(), event.claimId());
                        return false;
                    }
                    view.apply(event);
                    return true;
                })
                .orElseGet(() -> {
                    views.save(ClaimView.from(event));
                    return true;
                });
    }
}
